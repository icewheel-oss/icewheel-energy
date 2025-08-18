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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.scheduling.annotation.WithTeslaApiRetries;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
import net.icewheel.energy.domain.energy.model.ScheduleExecutionHistory;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This class is the "engine" that makes your Tesla Powerwall schedules happen.
 * It constantly checks, usually every minute, for any schedules that are due to run.
 * When a schedule's time arrives, this executor sends the necessary commands to your Tesla Powerwall
 * (like setting the backup reserve percentage) and records whether the action was successful or not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PowerwallScheduleExecutor {

    private final PowerwallScheduleRepository scheduleRepository;
    private final ScheduleExecutionHistoryRepository historyRepository;
    private final TeslaEnergyService teslaEnergyService;
	private final CronParser cronParser;
	private final Clock clock;
    private final CronDescriptor descriptor = CronDescriptor.instance(Locale.US); // For English descriptions

	/**
	 * This method runs automatically, typically once every minute, to check for and execute
	 * any Powerwall schedules that are currently due.
	 * <p>
	 * It uses a "scheduler lock" to ensure that if the application is running on multiple servers,
	 * only one of them will execute the schedules at any given time, preventing duplicate actions.
	 * It also includes a retry mechanism for Tesla API calls, so temporary network glitches
	 * don't immediately cause a schedule to fail.
	 * </p>
	 */
    @Scheduled(cron = "0 * * * * *") // Run every minute on the minute
    // Why: Use ShedLock so only one instance executes schedules in clustered deployments, avoiding duplicate actions.
    @SchedulerLock(name = "executePowerwallSchedules", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
	@WithTeslaApiRetries
    public void executeSchedules() {
		log.info("Running Powerwall schedule check...");
        List<PowerwallSchedule> enabledSchedules = scheduleRepository.findAllEnabledWithUser();
		ZonedDateTime now = ZonedDateTime.now(clock);

        for (PowerwallSchedule schedule : enabledSchedules) {
            try {
                Cron cron = cronParser.parse(schedule.getCronExpression());
                ExecutionTime executionTime = ExecutionTime.forCron(cron);

                // Check if the schedule should run at this moment in its specified timezone
                if (executionTime.isMatch(now.withZoneSameInstant(schedule.getTimeZone()))) {
                    String eventDescription = switch (schedule.getEventType()) {
                        case START_CHARGE -> "start charging (off-peak)";
                        case START_DISCHARGE -> "start discharging (on-peak)";
                    };

                    log.info("Executing schedule '{}' (ID: {}): Triggering {} for user '{}'. Setting backup to {}%.",
                            schedule.getName(), schedule.getId(), eventDescription, schedule.getUser().getId(), schedule.getBackupPercent());

                    executeAndRecordHistory(schedule, eventDescription, cron);
                }
            } catch (Exception e) {
                log.error("Failed to process schedule '{}' (ID: {}). Error: {}",
                        schedule.getName(), schedule.getId(), e.getMessage(), e);
            }
        }
        log.debug("Powerwall schedule check finished.");
    }

	/**
	 * This private method is responsible for actually sending the command to the Tesla Powerwall
	 * and then recording the outcome (success or failure) in the schedule execution history.
	 * It captures details like the schedule's name, the action performed, and any errors that occurred.
	 *
	 * @param schedule The PowerwallSchedule that is being executed.
	 * @param eventDescription A human-readable description of the event (e.g., "start charging").
	 * @param cron The cron expression associated with the schedule, used for history logging.
	 */
    private void executeAndRecordHistory(PowerwallSchedule schedule, String eventDescription, Cron cron) {
        ScheduleExecutionHistory history = new ScheduleExecutionHistory();
		history.setExecutionType(ScheduleExecutionHistory.ExecutionType.REGULAR);
		history.setUserId(schedule.getUser().getId());
        history.setScheduleId(schedule.getId());
        history.setScheduleGroupId(schedule.getScheduleGroupId());
        history.setScheduleName(schedule.getName());
        history.setCronExpression(schedule.getCronExpression());
        try {
            // Generate a human-readable description of the cron expression
            history.setCronDescription(descriptor.describe(cron));
        } catch (Exception e) {
            log.warn("Could not generate cron description for expression '{}'", schedule.getCronExpression(), e);
            history.setCronDescription("N/A");
        }
        try {
            boolean success = teslaEnergyService.setBackupReserve(
                    schedule.getUser().getId(),
                    String.valueOf(schedule.getEnergySiteId()),
                    schedule.getBackupPercent()
            );
            if (success) {
                history.setStatus(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
                history.setDetails(String.format("Successfully triggered '%s' action. Set backup reserve to %d%%.",
                        eventDescription, schedule.getBackupPercent()));
                log.info("Successfully executed schedule '{}' (ID: {}).", schedule.getName(), schedule.getId());
            } else {
                history.setStatus(ScheduleExecutionHistory.ExecutionStatus.FAILURE);
                history.setDetails(String.format("API call failed for '%s' action. The command was not accepted by the Tesla API.",
                        eventDescription));
                log.error("API call failed during execution of schedule '{}' (ID: {}).",
                        schedule.getName(), schedule.getId());
            }
        } catch (Exception e) {
            history.setStatus(ScheduleExecutionHistory.ExecutionStatus.FAILURE);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "An unknown error occurred.";
			history.setDetails(String.format("Execution failed for '%s'. Error: %s (%s)",
                    eventDescription, errorMessage, e.getClass().getSimpleName()));
			log.error("Failed to execute schedule '{}' (ID: {}). Error: {}",
                    schedule.getName(), schedule.getId(), e.getMessage(), e);
        } finally {
            historyRepository.save(history);
        }
    }
}