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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.icewheel.energy.api.rest.dto.ScheduleHistoryResponse;
import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.application.scheduling.model.ScheduleAuditEvent;
import net.icewheel.energy.application.scheduling.model.ScheduleExecutionHistory;
import net.icewheel.energy.application.user.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * This interface acts as a blueprint for any service that manages Tesla Powerwall schedules.
 * It defines all the essential actions you can perform with your energy schedules,
 * such as creating new ones, changing existing ones, deleting them, and viewing their history.
 * Any part of the application that needs to interact with Powerwall schedules will use these defined actions.
 */
public interface PowerwallScheduleService {

	/**
	 * Creates a new energy schedule period for a user. This typically involves setting up
	 * on-peak and off-peak times with specific battery backup percentages.
	 *
	 * @param request The details for the new schedule period.
	 * @param user The user creating the schedule.
	 * @return A response object representing the newly created schedule.
	 */
    ScheduleResponse createSchedulePeriod(ScheduleRequest request, User user);

	/**
	 * Updates an existing energy schedule period. You can change its times, backup percentages,
	 * or other details.
	 *
	 * @param scheduleGroupId The unique ID of the schedule period to update.
	 * @param request The new details for the schedule period.
	 * @param user The user updating the schedule.
	 * @return A response object representing the updated schedule.
	 */
    ScheduleResponse updateSchedulePeriod(UUID scheduleGroupId, ScheduleRequest request, User user);

	/**
	 * Retrieves all energy schedule periods that a specific user has created.
	 *
	 * @param user The user whose schedules are to be retrieved.
	 * @return A list of schedule response objects.
	 */
    List<ScheduleResponse> findSchedulesByUser(User user);

	/**
	 * Finds a specific energy schedule period using its unique group identifier.
	 *
	 * @param scheduleGroupId The unique ID of the schedule period.
	 * @param user The user requesting the schedule.
	 * @return An Optional containing the schedule response if found, otherwise empty.
	 */
    Optional<ScheduleResponse> findScheduleByGroupId(UUID scheduleGroupId, User user);

	/**
	 * Deletes an entire energy schedule period.
	 *
	 * @param scheduleGroupId The unique ID of the schedule period to delete.
	 * @param user The user deleting the schedule.
	 */
    void deleteSchedulePeriod(UUID scheduleGroupId, User user);

	/**
	 * Retrieves a history of all changes (creations, updates, deletions) made to schedules by a user.
	 *
	 * @param user The user whose schedule change history is to be retrieved.
	 * @param pageable Pagination information.
	 * @return A paginated list of schedule history response objects.
	 */
	Page<ScheduleHistoryResponse> getScheduleHistory(User user, Pageable pageable, List<ScheduleAuditEvent.AuditAction> actions);

	/**
	 * Retrieves a history of when schedules were actually executed or skipped by the system.
	 *
	 * @param user The user whose schedule execution history is to be retrieved.
	 * @param pageable Pagination information.
	 * @return A paginated list of schedule history response objects detailing executions.
	 */
	Page<ScheduleHistoryResponse> getScheduleExecutionHistory(User user, Pageable pageable, List<ScheduleExecutionHistory.ExecutionStatus> statuses);

	/**
	 * Enables or disables an energy schedule period. When disabled, the schedule will not run.
	 *
	 * @param scheduleGroupId The unique ID of the schedule period to update.
	 * @param enabled True to enable, false to disable.
	 * @param user The user changing the schedule's status.
	 */
    void updateScheduleEnabledStatus(UUID scheduleGroupId, boolean enabled, User user);

	/**
	 * Retrieves all of a user's schedules in a format suitable for exporting to a file (e.g., JSON).
	 * This is useful for creating backups or migrating settings.
	 *
	 * @param user The user whose schedules are to be exported.
	 * @return A list of schedule request objects ready for serialization.
	 */
	List<ScheduleRequest> getSchedulesForExport(User user);

	/**
	 * Imports a list of schedules from a deserialized file (e.g., JSON) and creates them for the user.
	 * This is useful for restoring schedules from a backup.
	 *
	 * @param schedules The list of schedule request objects to import.
	 * @param user The user for whom the schedules will be created.
	 */
	ImportResult importSchedules(List<ScheduleRequest> schedules, User user);

	/**
	 * Checks if a forced charge is currently active for the given user.
	 * A forced charge can be active either through a temporary "FORCED_CHARGE" schedule
	 * or via a manual override setting in the user's preferences.
	 *
	 * @param user The user to check.
	 * @return true if forced charging is active, false otherwise.
	 */
	boolean isForcedChargeActive(User user);
}