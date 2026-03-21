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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ReconciliationMode;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.model.ScheduleExecutionHistory;
import net.icewheel.energy.application.scheduling.model.ScheduleType;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Acts as the "Enforcer" in the scheduling system. This service is a self-healing guardian
 * that ensures the Powerwall's actual state always matches the desired state defined by the
 * currently active schedule.
 * <p>
 * It runs frequently (every 15 minutes by default) to perform the following check:
 * <ol>
 *     <li>Identify the currently active schedule for each user.</li>
 *     <li>Determine the backup reserve percentage that *should* be set according to that schedule.</li>
 *     <li>Compare this desired state with the *actual* backup reserve reported by the Powerwall.</li>
 *     <li>If there is a mismatch, it sends a command to correct the Powerwall's state.</li>
 * </ol>
 * This component is purely reactive and focuses on state enforcement. It does not create or
 * modify schedules itself. For the proactive, planning part of the system, see the
 * {@link WeatherAwareScheduler}.
 *
 * @see WeatherAwareScheduler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PowerwallStateReconciler {

	private final PowerwallScheduleService scheduleService;
	private final TeslaEnergyService teslaEnergyService;
	private final UserRepository userRepository;
	private final ScheduleExecutionHistoryRepository historyRepository;
	private final PowerwallScheduleRepository powerwallScheduleRepository;
	// Why: A Clock is injected instead of using ZonedDateTime.now() directly. This makes the class
	// highly testable, as the clock can be replaced with a fixed or manipulated version in tests
	// to verify behavior at specific moments in time without changing the system clock.
	private final Clock clock;

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

	/**
	 * Runs a scheduled job to enforce schedules marked as CONTINUOUS.
	 * It iterates through all users and ensures their Powerwall's backup reserve is correctly set.
	 * A scheduler lock prevents concurrent executions in a multi-instance environment.
	 */
	@Scheduled(cron = "${app.state-reconciliation.cron:0 */15 * * * *}")
	@SchedulerLock(name = "reconcilePowerwallState", lockAtMostFor = "10m", lockAtLeastFor = "1m")
	public void reconcileContinuously() {
		log.info("Starting periodic Powerwall state reconciliation job for CONTINUOUS schedules.");
		List<User> allUsers = userRepository.findAll();

		for (User user : allUsers) {
			try {
				List<ScheduleResponse> schedules = scheduleService.findSchedulesByUser(user);
				List<ScheduleResponse> continuousSchedules = schedules.stream()
						.filter(s -> ReconciliationMode.CONTINUOUS.equals(s.getReconciliationMode()))
						.toList();

				if (!continuousSchedules.isEmpty()) {
					reconcilePowerwallStateForUser(user, continuousSchedules, ScheduleExecutionHistory.ExecutionType.RECONCILIATION_CONTINUOUS);
				}
			}
			catch (Exception e) {
				log.error("Failed to reconcile state for user {}: {}", user.getId(), e.getMessage(), e);
			}
		}
		log.info("Periodic Powerwall state reconciliation job finished. Checked {} users.", allUsers.size());
	}

	/**
	 * Runs a one-time reconciliation for all schedules upon application startup.
	 * This ensures the system state is correct even if the application was down when a schedule should have changed.
	 */
	public void reconcileOnStartup() {
		log.info("Starting one-time Powerwall state reconciliation on startup for ALL schedules.");
		List<User> allUsers = userRepository.findAll();
		for (User user : allUsers) {
			try {
				List<ScheduleResponse> schedules = scheduleService.findSchedulesByUser(user);
				boolean hasEnabledSchedules = schedules.stream().anyMatch(ScheduleResponse::isEnabled);

				if (hasEnabledSchedules) {
					reconcilePowerwallStateForUser(user, schedules, ScheduleExecutionHistory.ExecutionType.RECONCILIATION_STARTUP);
				}
			}
			catch (Exception e) {
				log.error("Failed to reconcile state for user {}: {}", user.getId(), e.getMessage(), e);
			}
		}
		log.info("One-time Powerwall state reconciliation on startup finished. Checked {} users.", allUsers.size());
	}

	/**
	 * Triggers an ad-hoc reconciliation for a single user.
	 * This is useful for ensuring state is correct immediately after a significant change, like the creation of a temporary weather schedule.
	 * @param user The user to reconcile.
	 */
	public void reconcileUser(User user) {
		log.info("Triggering ad-hoc reconciliation for user {}", user.getId());
		List<ScheduleResponse> schedules = scheduleService.findSchedulesByUser(user);
		if (!schedules.isEmpty()) {
			// We can reuse the CONTINUOUS type here, as the logging and logic are appropriate.
			reconcilePowerwallStateForUser(user, schedules, ScheduleExecutionHistory.ExecutionType.RECONCILIATION_CONTINUOUS);
		}
	}

	/**
	 * Reconciles the Powerwall state for a single user based on their schedules.
	 * It determines the expected backup reserve by checking if the current time falls within an on-peak or off-peak period.
	 *
	 * @param user The user to reconcile.
	 * @param allUserSchedules All of the user's schedules.
	 * @param executionType The type of reconciliation being performed (e.g., continuous or startup).
	 */
	private void reconcilePowerwallStateForUser(User user, List<ScheduleResponse> allUserSchedules, ScheduleExecutionHistory.ExecutionType executionType) {
		ZonedDateTime now = ZonedDateTime.now(clock);
		// Why: This filter was the source of a bug. The original implementation performed the time zone conversion
		// inside the stream's filter operation. This is unreliable and can lead to subtle errors in date/time
		// comparison. By extracting the day of the week into a variable first, based on the schedule's specific
		// time zone, we ensure the check is robust and correct, fixing the bug where schedules were not found.
		List<ScheduleResponse> activeTodaySchedules = allUserSchedules.stream()
				.filter(ScheduleResponse::isEnabled)
				.filter(s -> {
					DayOfWeek currentDayInScheduleTz = now.withZoneSameInstant(s.getTimeZone()).getDayOfWeek();
					return s.getDaysOfWeek().contains(currentDayInScheduleTz);
				})
				.toList();

		if (activeTodaySchedules.isEmpty()) {
			return;
		}

		List<ScheduleResponse> onPeakSchedules = activeTodaySchedules.stream()
				.filter(s -> isTimeInOnPeakWindow(now, s))
				.toList();

		if (!onPeakSchedules.isEmpty()) {
			List<Integer> onPeakTargets = onPeakSchedules.stream().map(ScheduleResponse::getOnPeakBackupPercent)
					.toList();
			handleReconciliation(user, onPeakSchedules, onPeakTargets, executionType, "on-peak");
		}
		else {
			List<Integer> offPeakTargets = activeTodaySchedules.stream().map(ScheduleResponse::getOffPeakBackupPercent)
					.toList();
			handleReconciliation(user, activeTodaySchedules, offPeakTargets, executionType, "off-peak");
		}
	}

	/**
	 * Handles the core reconciliation logic after the active schedules and target percentages have been determined.
	 * It compares the actual Powerwall state to the expected state and issues a correction if needed.
	 *
	 * @param user The user being reconciled.
	 * @param activeSchedules The list of schedules governing the current period (on-peak or off-peak).
	 * @param targets The list of potential backup percentages from the active schedules.
	 * @param executionType The type of reconciliation being performed.
	 * @param activePeriod A string descriptor ("on-peak" or "off-peak") for logging and history.
	 */
	private void handleReconciliation(User user, List<ScheduleResponse> activeSchedules, List<Integer> targets, ScheduleExecutionHistory.ExecutionType executionType, String activePeriod) {
		final int expectedBackupPercent;
		final ScheduleResponse winningSchedule;

		if ("on-peak".equals(activePeriod)) {
			expectedBackupPercent = Collections.min(targets);
			winningSchedule = activeSchedules.stream().filter(s -> s.getOnPeakBackupPercent() == expectedBackupPercent)
					.findFirst().orElse(activeSchedules.getFirst());
		}
		else { // off-peak
			expectedBackupPercent = Collections.max(targets);
			winningSchedule = activeSchedules.stream().filter(s -> s.getOffPeakBackupPercent() == expectedBackupPercent)
					.findFirst().orElse(activeSchedules.getFirst());
		}

		int actualBackupPercent;
		try {
			actualBackupPercent = teslaEnergyService.getBackupReservePercent(user.getId(), String.valueOf(winningSchedule.getEnergySiteId()));
		}
		catch (Exception e) {
			log.error("API error while fetching backup reserve for user {}: {}", user.getId(), e.getMessage());
			return;
		}

		try {
			boolean shouldReconcile = false;
			String reconciliationDecisionReason = "";

			if ("on-peak".equals(activePeriod)) {
				if (actualBackupPercent > expectedBackupPercent) {
					log.info("On-peak reconciliation: actual backup reserve ({}) is higher than scheduled ({}). Overriding.", actualBackupPercent, expectedBackupPercent);
					shouldReconcile = true;
				} else if (actualBackupPercent < expectedBackupPercent) {
					reconciliationDecisionReason = String.format("Skipping reconciliation: User has manually set backup reserve lower (%d%%) than scheduled (%d%%) during on-peak.", actualBackupPercent, expectedBackupPercent);
				}
			} else { // off-peak
				if (actualBackupPercent < expectedBackupPercent) {
					log.info("Off-peak reconciliation: actual backup reserve ({}) is lower than scheduled ({}). Overriding.", actualBackupPercent, expectedBackupPercent);
					shouldReconcile = true;
				} else if (actualBackupPercent > expectedBackupPercent) {
					reconciliationDecisionReason = String.format("Skipping reconciliation: User has manually set backup reserve higher (%d%%) than scheduled (%d%%) during off-peak.", actualBackupPercent, expectedBackupPercent);
				}
			}

			if (shouldReconcile) {
				log.warn("State mismatch for site {} (user {}). Optimal: {}%, Actual: {}%. Reconciling...", winningSchedule.getEnergySiteId(), user.getId(), expectedBackupPercent, actualBackupPercent);
				boolean success = teslaEnergyService.setBackupReserve(user.getId(), String.valueOf(winningSchedule.getEnergySiteId()), expectedBackupPercent);
				if (success) {
					String details;
					if (winningSchedule.getScheduleType() == ScheduleType.WEATHER_AWARE) {
						// The detailed reason is logged in the ScheduleAuditEvent (Change History).
						// This log entry focuses on the action taken.
						details = String.format("Automatic correction for weather-aware schedule '%s'. Backup reserve was at %d%% and has been corrected to the weather-adjusted target of %d%%.", winningSchedule.getName(), actualBackupPercent, expectedBackupPercent);
					} else {
						if ("on-peak".equals(activePeriod)) {
							details = String.format("Automatic correction for schedule '%s' during its on-peak window (%s - %s). The backup reserve was at %d%% and has been corrected to the scheduled %d%%.", winningSchedule.getName(), formatTime(winningSchedule.getStartTime()), formatTime(winningSchedule.getEndTime()), actualBackupPercent, expectedBackupPercent);
						}
						else { // off-peak
							details = String.format("Automatic correction during an off-peak period. The backup reserve was at %d%% and has been corrected to the scheduled %d%% (based on schedule '%s').", actualBackupPercent, expectedBackupPercent, winningSchedule.getName());
						}
					}
					saveReconciliationHistoryEntry(user, winningSchedule, activePeriod, details, ScheduleExecutionHistory.ExecutionStatus.SUCCESS, executionType);
				}
				else {
					log.error("Failed to reconcile state for site {}. API call to set backup reserve was not successful.", winningSchedule.getEnergySiteId());
					String details = String.format("Automatic correction failed for schedule '%s'. The API call to set backup reserve to %d%% was not accepted by Tesla.", winningSchedule.getName(), expectedBackupPercent);
					saveReconciliationHistoryEntry(user, winningSchedule, activePeriod, details, ScheduleExecutionHistory.ExecutionStatus.FAILURE, executionType);
				}
			}
			else if (actualBackupPercent == expectedBackupPercent) {
				String details;
				if (winningSchedule.getScheduleType() == ScheduleType.WEATHER_AWARE) {
					// The detailed reason is logged in the ScheduleAuditEvent (Change History).
					// This log entry confirms the state is correct according to the weather plan.
					details = String.format("Automatic check for weather-aware schedule '%s'. The backup reserve of %d%% already matches the weather-adjusted target. No action was needed.", winningSchedule.getName(), expectedBackupPercent);
				}
				else {
					details = String.format(
							"Automatic check during an %s period for schedule '%s'. The Powerwall's backup reserve is already correctly set to %d%%. No action was needed.",
							activePeriod, winningSchedule.getName(), expectedBackupPercent);
				}
				saveReconciliationHistoryEntry(user, winningSchedule, activePeriod, details,
						ScheduleExecutionHistory.ExecutionStatus.SKIPPED, executionType);
			} else { // User override case
                log.info(reconciliationDecisionReason);
                saveReconciliationHistoryEntry(user, winningSchedule, activePeriod, reconciliationDecisionReason,
                        ScheduleExecutionHistory.ExecutionStatus.SKIPPED, executionType);
            }
		}
		catch (Exception e) {
			log.error("Failed to save reconciliation history for user {}: {}", user.getId(), e.getMessage(), e);
		}
	}

	/**
	 * Checks if the current time is within the on-peak window of a given schedule, accounting for time zones.
	 * @param now The current ZonedDateTime.
	 * @param schedule The schedule to check.
	 * @return true if the current time is within the schedule's on-peak period, false otherwise.
	 */
	private boolean isTimeInOnPeakWindow(ZonedDateTime now, ScheduleResponse schedule) {
		LocalTime currentTimeInScheduleTz = now.withZoneSameInstant(schedule.getTimeZone()).toLocalTime();
		LocalTime onPeakStart = schedule.getStartTime();
		LocalTime offPeakStart = schedule.getEndTime();
		if (onPeakStart.isBefore(offPeakStart)) {
			return !currentTimeInScheduleTz.isBefore(onPeakStart) && currentTimeInScheduleTz.isBefore(offPeakStart);
		}
		else {
			return !currentTimeInScheduleTz.isBefore(onPeakStart) || currentTimeInScheduleTz.isBefore(offPeakStart);
		}
	}

	/**
	 * Formats a LocalTime into a user-friendly string (e.g., "5:30 PM").
	 * @param time The time to format.
	 * @return A formatted time string.
	 */
	private String formatTime(LocalTime time) {
		if (time == null) return "N/A";
		return time.format(TIME_FORMATTER);
	}

	/**
	 * Records the details of a reconciliation action in the database for auditing and history.
	 * It finds the specific schedule event (e.g., START_DISCHARGE) to ensure the history is accurately attributed.
	 *
	 * @param user The user whose Powerwall was adjusted.
	 * @param schedule The user's active schedule settings.
	 * @param activePeriod A string indicating if the event was "on-peak" or "off-peak".
	 * @param details A descriptive string explaining what happened.
	 * @param status The final status of the event (e.g., SUCCESS, SKIPPED).
	 * @param executionType The type of reconciliation that was performed.
	 */
	private void saveReconciliationHistoryEntry(User user, ScheduleResponse schedule, String activePeriod, String details, ScheduleExecutionHistory.ExecutionStatus status, ScheduleExecutionHistory.ExecutionType executionType) {
		var schedules = powerwallScheduleRepository.findAllByScheduleGroupId(schedule.getScheduleGroupId());
		if (schedules == null || schedules.isEmpty()) {
			log.warn("Could not save reconciliation history. No schedules found for group ID: {}", schedule.getScheduleGroupId());
			return;
		}
		PowerwallSchedule pws;
		if (activePeriod != null) {
			ScheduleEventType eventType = "on-peak".equals(activePeriod) ? ScheduleEventType.START_DISCHARGE : ScheduleEventType.START_CHARGE;
			pws = schedules.stream().filter(s -> s.getEventType() == eventType).findFirst().orElse(null);
			if (pws == null) {
				log.warn("Could not save reconciliation history. Malformed schedule group {} is missing a {} event.", schedule.getScheduleGroupId(), eventType);
				return;
			}
		}
		else {
			pws = schedules.stream().findFirst().orElse(null);
			if (pws == null) {
				log.warn("Could not save reconciliation history. No schedules found for group ID: {}", schedule.getScheduleGroupId());
				return;
			}
		}
		ScheduleExecutionHistory history = new ScheduleExecutionHistory();
		history.setExecutionType(executionType);
		history.setStatus(status);
		history.setUserId(user.getId());
		history.setScheduleId(pws.getId());
		history.setScheduleGroupId(pws.getScheduleGroupId());
		history.setScheduleName(pws.getName());
		history.setCronExpression(pws.getCronExpression());
		history.setDetails(details);
		historyRepository.save(history);
	}
}