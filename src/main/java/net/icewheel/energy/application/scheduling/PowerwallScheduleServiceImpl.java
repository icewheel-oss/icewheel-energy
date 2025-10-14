/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.icewheel.energy.application.scheduling;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import net.icewheel.energy.api.rest.dto.ScheduleHistoryResponse;
import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.application.scheduling.exception.ScheduleImportException;
import net.icewheel.energy.application.scheduling.exception.ScheduleNotFoundException;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ReconciliationMode;
import net.icewheel.energy.application.scheduling.model.ScheduleAuditEvent;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.model.ScheduleExecutionHistory;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleAuditEventRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PowerwallScheduleServiceImpl implements PowerwallScheduleService {

    private final PowerwallScheduleRepository scheduleRepository;
    private final ScheduleExecutionHistoryRepository historyRepository;
    private final ScheduleAuditEventRepository auditEventRepository;
	private final Validator validator;
	private final TeslaEnergyService teslaEnergyService;

	private static final int MAX_SCHEDULES_PER_IMPORT = 100;

    @Override
    @Transactional
    public ScheduleResponse createSchedulePeriod(ScheduleRequest request, User user) {
		return createSchedulePeriodInternal(request, user, "New schedule period created.");
    }

    /**
	 * {@inheritDoc}
	 * <p>
	 * This implementation ensures that updates submitted by a user always apply to the
	 * permanent, base schedule, even if a temporary weather-based schedule is currently active.
	 * It explicitly finds and modifies only the non-temporary entities within the schedule group.
	 */
    @Override
    @Transactional
    public ScheduleResponse updateSchedulePeriod(UUID scheduleGroupId, ScheduleRequest request, User user) {
        List<PowerwallSchedule> schedules = findAndValidateSchedulesByGroup(scheduleGroupId, user);
        // When updating, we ALWAYS update the permanent base schedule, not a temporary one.
        PowerwallSchedule startDischarge = schedules.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_DISCHARGE && !s.isTemporary())
                .findFirst()
                .orElseThrow(() -> new ScheduleNotFoundException("Schedule group " + scheduleGroupId + " is malformed: missing a permanent START_DISCHARGE event."));

        PowerwallSchedule startCharge = schedules.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_CHARGE && !s.isTemporary())
                .findFirst()
                .orElseThrow(() -> new ScheduleNotFoundException("Schedule group " + scheduleGroupId + " is malformed: missing a permanent START_CHARGE event."));

        // Capture a detailed log of what is about to change for the audit trail.
        Map<String, Object> auditDetails = buildUpdateAuditDetails(startDischarge, startCharge, request);

        // Update the existing entities instead of deleting and recreating
        updateScheduleEvent(startDischarge, request, request.getStartTime(), request.getOnPeakBackupPercent());
        updateScheduleEvent(startCharge, request, request.getEndTime(), request.getOffPeakBackupPercent());
        logAuditEvent(user, scheduleGroupId, request.getName(), ScheduleAuditEvent.AuditAction.UPDATED, auditDetails);

        // Why: Re-fetch the schedules from the database before mapping the response.
        // This ensures the returned DTO reflects the truly persisted state, avoiding potential
        // issues where the in-memory state of the entities might be stale within the transaction.
        List<PowerwallSchedule> updatedSchedules = scheduleRepository.findAllByScheduleGroupId(scheduleGroupId);
        return mapGroupToResponse(updatedSchedules).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
	// Why: This method groups individual schedule events (start charge/discharge) into a single logical "Schedule Period"
	// for the user. It ensures that the UI can present a coherent view of the on-peak/off-peak settings,
	// even though they are stored as separate database entries.
    public List<ScheduleResponse> findSchedulesByUser(User user) {
        // Group schedules by their group ID to treat each period as a single unit
        List<PowerwallSchedule> schedules = scheduleRepository.findAllByUser(user);
        Map<UUID, List<PowerwallSchedule>> groupedSchedules = schedules.stream()
                .collect(Collectors.groupingBy(PowerwallSchedule::getScheduleGroupId));

        // Map each group to a single period response DTO
        return groupedSchedules.values().stream()
                .flatMap(group -> mapGroupToResponse(group).stream())
                .sorted(Comparator.comparing(ScheduleResponse::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduleResponse> findScheduleByGroupId(UUID scheduleGroupId, User user) {
        List<PowerwallSchedule> schedules = scheduleRepository.findAllByScheduleGroupId(scheduleGroupId);
        if (schedules.isEmpty() || !schedules.get(0).getUser().getId().equals(user.getId())) {
            return Optional.empty();
        }
        return mapGroupToResponse(schedules);
    }

    @Override
    @Transactional
    public void deleteSchedulePeriod(UUID scheduleGroupId, User user) {
        List<PowerwallSchedule> schedules = findAndValidateSchedulesByGroup(scheduleGroupId, user);
		PowerwallSchedule representative = schedules.get(0);
        // Log the deletion event before performing the hard delete
		Map<String, Object> details = Map.of(
				"info", "Schedule period was deleted.",
				"name", representative.getName()
		);
		logAuditEvent(user, scheduleGroupId, representative.getName(), ScheduleAuditEvent.AuditAction.DELETED, details);
        scheduleRepository.deleteAll(schedules);
    }

    @Override
    @Transactional
    public void updateScheduleEnabledStatus(UUID scheduleGroupId, boolean enabled, User user) {
        List<PowerwallSchedule> schedules = findAndValidateSchedulesByGroup(scheduleGroupId, user);
		schedules.forEach(schedule -> schedule.setEnabled(enabled)); // The changes are saved automatically by JPA dirty checking.

		// Why: This ensures the audit log format for a status change is consistent with other updates.
		// The UI expects a "changes" list, and this provides it, fixing a bug where it would display "Status changed to null".
		String fromStatus = !enabled ? "enabled" : "disabled";
		String toStatus = enabled ? "enabled" : "disabled";
		List<Map<String, String>> changes = List.of(
				Map.of("field", "Status", "from", fromStatus, "to", toStatus)
		);

		logAuditEvent(user, scheduleGroupId, schedules.getFirst()
				.getName(), ScheduleAuditEvent.AuditAction.UPDATED, Map.of("changes", changes));
    }

    @Override
    @Transactional(readOnly = true)
	public Page<ScheduleHistoryResponse> getScheduleHistory(User user, Pageable pageable, List<ScheduleAuditEvent.AuditAction> actions) {
		Page<ScheduleAuditEvent> auditEventPage;
		if (actions == null || actions.isEmpty()) {
			auditEventPage = auditEventRepository.findByUser(user, pageable);
		} else {
			auditEventPage = auditEventRepository.findByUserAndActionIn(user, actions, pageable);
		}
		return auditEventPage.map(event -> new ScheduleHistoryResponse(
				event.getAction().name(),
				event.getTimestamp(),
				event.getScheduleName(),
				event.getDetails(),
				null
		));
    }

    @Override
    @Transactional(readOnly = true)
	public Page<ScheduleHistoryResponse> getScheduleExecutionHistory(User user, Pageable pageable, List<ScheduleExecutionHistory.ExecutionStatus> statuses) {
		Page<ScheduleExecutionHistory> executionPage;
		if (statuses == null || statuses.isEmpty()) {
			executionPage = historyRepository.findByUserId(user.getId(), pageable);
		} else {
			executionPage = historyRepository.findByUserIdAndStatusIn(user.getId(), statuses, pageable);
		}
		return executionPage.map(event -> new ScheduleHistoryResponse(
				formatExecutionTypeForDisplay(event.getExecutionType()),
				event.getExecutionTime(),
				event.getScheduleName(),
				event.getDetails(),
				event.getStatus()
		));
    }

	/**
	 * Formats the internal ExecutionType enum into a user-friendly string for display in the UI.
	 * @param type The {@link ScheduleExecutionHistory.ExecutionType} to format.
	 * @return A human-readable string.
	 */
	private String formatExecutionTypeForDisplay(ScheduleExecutionHistory.ExecutionType type) {
		if (type == null) return "System Event"; // Fallback for old or unknown data
		return switch (type) {
			case REGULAR -> "Scheduled Run";
			case RECONCILIATION_CONTINUOUS -> "Continuous Correction";
			case RECONCILIATION_STARTUP -> "Startup Correction";
			case WEATHER_EVALUATION -> "Weather Evaluation";
		};
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleRequest> getSchedulesForExport(User user) {
		return findSchedulesByUser(user).stream()
				.map(this::mapResponseToRequest)
				.collect(Collectors.toList());
	}

	@Override
	@Transactional
	public ImportResult importSchedules(List<ScheduleRequest> schedules, User user) {
		if (schedules == null || schedules.isEmpty()) {
			return new ImportResult(0, Collections.emptyList(), Collections.emptyList());
		}

		if (schedules.size() > MAX_SCHEDULES_PER_IMPORT) {
			throw new ScheduleImportException("Import failed: A maximum of " + MAX_SCHEDULES_PER_IMPORT + " schedules can be imported at one time.");
		}

		// Why: The energy site ID is specific to a user's account. To make schedules portable,
		// we determine the site ID at import time based on the current user's account,
		// rather than relying on a potentially incorrect ID from the file.
		List<Product> schedulableSites = teslaEnergyService.getSchedulableEnergySites(user.getId());
		if (schedulableSites.isEmpty()) {
			throw new ScheduleImportException("Import failed: No schedulable Powerwall energy sites found on your Tesla account.");
		}
		// For simplicity, we'll use the first available energy site.
		// If a user has multiple sites, they can edit the imported schedule to change this.
		final String defaultEnergySiteId = schedulableSites.getFirst().getEnergySiteId();

		// Why: To prevent duplicate schedules, we first gather all existing schedule names for the user.
		// This allows for an efficient check during the validation phase.
		Set<String> existingScheduleNames = findSchedulesByUser(user).stream()
				.map(ScheduleResponse::getName)
				.collect(Collectors.toSet());
		List<ScheduleResponse> existingSchedules = findSchedulesByUser(user);

		// Separate new schedules from duplicates. Duplicates will be skipped, but new schedules must be valid.
		List<ScheduleRequest> schedulesToCreate = new ArrayList<>();
		List<String> skippedDuplicateNames = new ArrayList<>();
		List<String> skippedDuplicateContent = new ArrayList<>();

		for (ScheduleRequest request : schedules) {
			if (existingScheduleNames.contains(request.getName())) {
				skippedDuplicateNames.add(request.getName());
			}
			else if (isContentDuplicate(request, existingSchedules)) {
				skippedDuplicateContent.add(request.getName());
			}
			else {
				schedulesToCreate.add(request);
			}
		}

		// If there are no new schedules to create, we can return early.
		if (schedulesToCreate.isEmpty()) {
			return new ImportResult(0, skippedDuplicateNames, skippedDuplicateContent);
		}

		// Validate all new schedules before saving any. This remains an "all or nothing" check for the new items.
		List<String> validationErrors = new ArrayList<>();
		for (int i = 0; i < schedulesToCreate.size(); i++) {
			ScheduleRequest request = schedulesToCreate.get(i);
			request.setEnergySiteId(defaultEnergySiteId); // Assign the site ID before validation
			Set<ConstraintViolation<ScheduleRequest>> violations = validator.validate(request);
			if (!violations.isEmpty()) {
				String errorDetails = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", "));
				validationErrors.add("New schedule '" + request.getName() + "' is invalid: " + errorDetails);
			}
		}

		if (!validationErrors.isEmpty()) {
			throw new ScheduleImportException("Import failed due to validation errors in new schedules: " + String.join("; ", validationErrors));
		}

		// If all new schedules are valid, proceed with creation.
		for (ScheduleRequest request : schedulesToCreate) {
			// Use the internal helper to provide a specific audit message for imports.
			createSchedulePeriodInternal(request, user, "New schedule period imported.");
		}

		return new ImportResult(schedulesToCreate.size(), skippedDuplicateNames, skippedDuplicateContent);
	}

	@Override
	public boolean isForcedChargeActive(User user) {
		// A forced charge is active if the manual override flag is set in the user's preferences.
		return user.getPreference() != null && user.getPreference().isForcedChargingActive();
	}

	private ScheduleRequest mapResponseToRequest(ScheduleResponse response) {
		// Why: This mapping is intentionally selective. It only includes schedule configuration
		// and explicitly excludes any user-identifying information or environment-specific IDs (like energySiteId)
		// to ensure the export is secure, private, and portable.
		ScheduleRequest request = new ScheduleRequest();
		request.setName(response.getName());
		request.setDescription(response.getDescription());
		request.setDaysOfWeek(response.getDaysOfWeek());
		request.setStartTime(response.getStartTime());
		request.setEndTime(response.getEndTime());
		request.setTimeZone(response.getTimeZone().getId());
		request.setOnPeakBackupPercent(response.getOnPeakBackupPercent());
		request.setOffPeakBackupPercent(response.getOffPeakBackupPercent());
		request.setEnabled(response.isEnabled());
		request.setReconciliationMode(response.getReconciliationMode());
		request.setWeatherScalingFactor(response.getWeatherScalingFactor());
		return request;
	}

	/**
	 * Checks if a new schedule request has the same functional content as any existing schedule.
	 * This is used to detect duplicates even if the names are different.
	 *
	 * @param newRequest The incoming schedule request from an import.
	 * @param existingSchedules A list of the user's current schedules.
	 * @return true if a schedule with the same content already exists, false otherwise.
	 */
	private boolean isContentDuplicate(ScheduleRequest newRequest, List<ScheduleResponse> existingSchedules) {
		// Why: ReconciliationMode can be null in older exported schedules. Default to CONTINUOUS for comparison
		// to match the behavior in updateScheduleFromRequest.
		ReconciliationMode newReconMode = newRequest.getReconciliationMode() != null ? newRequest.getReconciliationMode() : ReconciliationMode.CONTINUOUS;

		for (ScheduleResponse existing : existingSchedules) {
			boolean isMatch = Objects.equals(newRequest.getDaysOfWeek(), existing.getDaysOfWeek()) &&
					Objects.equals(newRequest.getStartTime(), existing.getStartTime()) &&
					Objects.equals(newRequest.getEndTime(), existing.getEndTime()) &&
					Objects.equals(newRequest.getTimeZone(), existing.getTimeZone().getId()) &&
					newRequest.getOnPeakBackupPercent() == existing.getOnPeakBackupPercent() &&
					newRequest.getOffPeakBackupPercent() == existing.getOffPeakBackupPercent() &&
					Objects.equals(newReconMode, existing.getReconciliationMode());

			if (isMatch) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Internal helper to create a schedule period and log the creation event with a specific message.
	 * This centralizes creation logic and allows for distinct audit trails for manual vs. imported schedules.
	 *
	 * @param request The schedule details.
	 * @param user The owner of the schedule.
	 * @param auditInfo The specific message to include in the audit log's "info" field.
	 * @return A response object for the newly created schedule.
	 */
	private ScheduleResponse createSchedulePeriodInternal(ScheduleRequest request, User user, String auditInfo) {
		UUID groupId = UUID.randomUUID();
		PowerwallSchedule startDischarge = createScheduleEvent(request, user, groupId, ScheduleEventType.START_DISCHARGE, request.getStartTime(), request.getOnPeakBackupPercent());
		PowerwallSchedule startCharge = createScheduleEvent(request, user, groupId, ScheduleEventType.START_CHARGE, request.getEndTime(), request.getOffPeakBackupPercent());

		// Use saveAll for a more efficient batch operation.
		scheduleRepository.saveAll(List.of(startDischarge, startCharge));

		Map<String, Object> details = Map.of("info", auditInfo, "on-peak", request.getStartTime() + " @" + request.getOnPeakBackupPercent() + "%", "off-peak", request.getEndTime() + " @" + request.getOffPeakBackupPercent() + "%", "days", formatDaysForAudit(request.getDaysOfWeek()));
		logAuditEvent(user, groupId, request.getName(), ScheduleAuditEvent.AuditAction.CREATED, details);
		return mapGroupToResponse(List.of(startDischarge, startCharge)).orElseThrow();
	}

    private PowerwallSchedule createScheduleEvent(ScheduleRequest request, User user, UUID groupId, ScheduleEventType type, LocalTime time, int backupPercent) {
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setScheduleGroupId(groupId);
        schedule.setUser(user);
        schedule.setName(request.getName());
        schedule.setDescription(request.getDescription());
        schedule.setEnabled(request.getEnabled());

		updateScheduleFromRequest(schedule, request);

        // --- Set event-specific fields ---
        schedule.setEventType(type);
        schedule.setScheduledTime(time);
        schedule.setBackupPercent(backupPercent);
        schedule.setCronExpression(generateCronExpression(request.getDaysOfWeek(), time));
        return schedule;
    }

    private void updateScheduleEvent(PowerwallSchedule schedule, ScheduleRequest request, LocalTime time, int backupPercent) {
		updateScheduleFromRequest(schedule, request);

		// Update event-specific properties
		schedule.setScheduledTime(time);
		schedule.setBackupPercent(backupPercent);
		schedule.setCronExpression(generateCronExpression(request.getDaysOfWeek(), time));
	}

	private void updateScheduleFromRequest(PowerwallSchedule schedule, ScheduleRequest request) {
		schedule.setName(request.getName());
		schedule.setDescription(request.getDescription());
		schedule.setEnergySiteId(request.getEnergySiteId());
		schedule.setDaysOfWeek(request.getDaysOfWeek());
		schedule.setTimeZone(ZoneId.of(request.getTimeZone()));
		// Why: Ensure the reconciliation setting is persisted. Default to CONTINUOUS if not provided
		// to maintain existing behavior for clients that don't send the new flag.
		schedule.setReconciliationMode(
				request.getReconciliationMode() != null ? request.getReconciliationMode() : ReconciliationMode.CONTINUOUS);
		schedule.setScheduleType(request.getScheduleType() != null ? request.getScheduleType() : net.icewheel.energy.application.scheduling.model.ScheduleType.BASIC);
		schedule.setWeatherScalingFactor(request.getWeatherScalingFactor() != null ? request.getWeatherScalingFactor() : 70);
	}

    private void logAuditEvent(User user, UUID scheduleGroupId, String scheduleName, ScheduleAuditEvent.AuditAction action, Map<String, Object> details) {
        ScheduleAuditEvent event = new ScheduleAuditEvent();
        event.setUser(user);
        event.setScheduleGroupId(scheduleGroupId);
        event.setScheduleName(scheduleName);
        event.setAction(action);
        event.setDetails(details);
        auditEventRepository.save(event);
    }

    /**
     * Compares an existing schedule period with an update request to generate a detailed
     * map of all changes for auditing purposes.
     */
    private Map<String, Object> buildUpdateAuditDetails(PowerwallSchedule startDischarge, PowerwallSchedule startCharge, ScheduleRequest request) {
        final List<Map<String, String>> changes = new ArrayList<>();

        addChange(changes, "Name", startDischarge.getName(), request.getName());
        addChange(changes, "Description", String.valueOf(startDischarge.getDescription()), String.valueOf(request.getDescription()));
        addChange(changes, "Energy Site ID", String.valueOf(startDischarge.getEnergySiteId()), String.valueOf(request.getEnergySiteId()));
        addChange(changes, "Days", formatDaysForAudit(startDischarge.getDaysOfWeek()), formatDaysForAudit(request.getDaysOfWeek()));
        addChange(changes, "On-Peak Start Time", startDischarge.getScheduledTime().toString(), request.getStartTime().toString());
        addChange(changes, "Off-Peak Start Time", startCharge.getScheduledTime().toString(), request.getEndTime().toString());
        addChange(changes, "On-Peak Backup", startDischarge.getBackupPercent() + "%", request.getOnPeakBackupPercent() + "%");
        addChange(changes, "Off-Peak Backup", startCharge.getBackupPercent() + "%", request.getOffPeakBackupPercent() + "%");
		addChange(changes, "Correction Mode", startDischarge.getReconciliationMode().toString(),
				request.getReconciliationMode() != null ? request.getReconciliationMode().toString() : "CONTINUOUS");

        if (changes.isEmpty()) {
            return Map.of("info", "Schedule updated, but no values were changed.");
        } else {
            return Map.of("changes", changes);
        }
    }

    private void addChange(List<Map<String, String>> changes, String field, String from, String to) {
        if (!Objects.equals(from, to)) {
            changes.add(Map.of("field", field, "from", from, "to", to));
        }
    }

    private String formatDaysForAudit(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "None";
        return days.stream()
                .sorted()
                .map(d -> d.name().substring(0, 1).toUpperCase() + d.name().substring(1).toLowerCase())
                .collect(Collectors.joining(", "));
    }
    private List<PowerwallSchedule> findAndValidateSchedulesByGroup(UUID scheduleGroupId, User user) {
        List<PowerwallSchedule> schedules = scheduleRepository.findAllByScheduleGroupId(scheduleGroupId);
        if (schedules.isEmpty()) {
            throw new ScheduleNotFoundException("No schedule found for group ID: " + scheduleGroupId);
        }
        // All schedules in a group belong to the same user, so checking the first is sufficient.
        if (!schedules.get(0).getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not have permission to modify this schedule period.");
        }
        return schedules;
    }

    /**
	 * Maps a group of related {@link PowerwallSchedule} entities (representing a single logical
	 * schedule period for the user) into a single {@link ScheduleResponse} DTO.
	 * <p>
	 * This method contains critical logic to handle temporary weather-based overrides:
	 * <ol>
	 *     <li><b>Time Window:</b> The on-peak/off-peak time window is always determined by the
	 *     permanent, non-temporary schedules in the group.</li>
	 *     <li><b>Effective Percentages:</b> The backup percentages displayed to the user are taken from
	 *     the most relevant schedule, prioritizing temporary schedules if they exist.</li>
	 *     <li><b>Permanent Percentages:</b> It also populates the permanent percentages, so the UI
	 *     can show the user their base settings, especially during an override.</li>
	 * </ol>
	 *
	 * @param group A list of all schedule entities belonging to the same schedule group ID.
	 * @return An {@link Optional} containing the mapped response, or empty if the group is invalid.
	 */
    private Optional<ScheduleResponse> mapGroupToResponse(List<PowerwallSchedule> group) {
        if (group == null || group.isEmpty()) {
            return Optional.empty();
        }

        // Why: This logic is now robust to handle temporary overrides. It separates the concepts of
        // the schedule's time window (which is fixed) from the backup percentages (which can be temporarily overridden).

        // 1. Find the permanent schedules to define the core properties and time window.
        Optional<PowerwallSchedule> permanentStartDischargeOpt = group.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_DISCHARGE && !s.isTemporary())
                .findFirst();
        Optional<PowerwallSchedule> permanentStartChargeOpt = group.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_CHARGE && !s.isTemporary())
                .findFirst();

        // A valid permanent schedule must have both parts.
        if (permanentStartDischargeOpt.isEmpty() || permanentStartChargeOpt.isEmpty()) {
            // If there are only temporary schedules, we can't determine the base time window. This might happen
            // during deletion or other transient states. It's safest to treat the group as invalid.
            return Optional.empty();
        }

        // 2. Find the effective schedules for backup percentages, prioritizing temporary ones.
        PowerwallSchedule effectiveStartDischarge = group.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_DISCHARGE)
                .sorted(Comparator.comparing(PowerwallSchedule::isTemporary).reversed()) // true comes first
                .findFirst()
                .orElse(permanentStartDischargeOpt.get()); // Fallback to permanent

        PowerwallSchedule effectiveStartCharge = group.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_CHARGE)
                .sorted(Comparator.comparing(PowerwallSchedule::isTemporary).reversed()) // true comes first
                .findFirst()
                .orElse(permanentStartChargeOpt.get()); // Fallback to permanent

        PowerwallSchedule baseSchedule = permanentStartDischargeOpt.get();

        // 3. Build the response DTO.
        ScheduleResponse response = new ScheduleResponse();
        response.setScheduleGroupId(baseSchedule.getScheduleGroupId());
        response.setName(baseSchedule.getName());
        response.setDescription(baseSchedule.getDescription());
        response.setEnergySiteId(baseSchedule.getEnergySiteId());
        response.setDaysOfWeek(baseSchedule.getDaysOfWeek());
        response.setTimeZone(baseSchedule.getTimeZone());
        response.setEnabled(baseSchedule.isEnabled());
        response.setReconciliationMode(baseSchedule.getReconciliationMode());
        response.setScheduleType(baseSchedule.getScheduleType());
        response.setLastEvaluationDetails(baseSchedule.getLastEvaluationDetails());
        response.setWeatherScalingFactor(baseSchedule.getWeatherScalingFactor());
        response.setCreatedAt(baseSchedule.getCreatedAt());
        response.setUpdatedAt(baseSchedule.getUpdatedAt());

        // Time window is always from the permanent schedule.
        response.setStartTime(permanentStartDischargeOpt.get().getScheduledTime());
        response.setEndTime(permanentStartChargeOpt.get().getScheduledTime());

        // Percentages are from the effective (potentially temporary) schedules.
        response.setOnPeakBackupPercent(effectiveStartDischarge.getBackupPercent());
        response.setOffPeakBackupPercent(effectiveStartCharge.getBackupPercent());

        // Set permanent percentages for UI display and editing.
        response.setPermanentOnPeakBackupPercent(permanentStartDischargeOpt.get().getBackupPercent());
        response.setPermanentOffPeakBackupPercent(permanentStartChargeOpt.get().getBackupPercent());

        // Finally, check if any of the effective schedules are temporary, indicating a weather override.
        response.setOverriddenByWeather(effectiveStartDischarge.isTemporary() || effectiveStartCharge.isTemporary());

        return Optional.of(response);
    }

    /**
     * Generates a Spring-compatible cron expression from a set of days and a specific time.
     * Example: "0 30 22 ? * MON,TUE,WED" for 10:30 PM on Mon, Tue, Wed.
     *
     * @param days The set of {@link DayOfWeek} for the schedule.
     * @param time The {@link LocalTime} for the schedule.
     * @return A valid Spring cron expression string.
     */
    private String generateCronExpression(Set<DayOfWeek> days, LocalTime time) {
        String dayOfWeekField = days.stream()
                .map(DayOfWeek::name)
                .map(s -> s.substring(0, 3)) // e.g., MONDAY -> MON
                .collect(Collectors.joining(","));

        return String.format("0 %d %d ? * %s",
                time.getMinute(), time.getHour(), dayOfWeekField);
    }
}