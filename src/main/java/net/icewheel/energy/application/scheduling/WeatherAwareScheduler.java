package net.icewheel.energy.application.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.model.ScheduleExecutionHistory;
import net.icewheel.energy.application.scheduling.model.ScheduleType;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.modules.weather.SolarForecast;
import net.icewheel.energy.infrastructure.modules.weather.WeatherForecastEvaluator;
import net.icewheel.energy.infrastructure.modules.weather.exception .ForecastEvaluationException;
import net.icewheel.energy.application.scheduling.model.ScheduleAuditEvent;
import net.icewheel.energy.application.scheduling.repository.ScheduleAuditEventRepository;

import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Acts as the "Planner" in the scheduling system, responsible for the full lifecycle of
 * weather-based schedule adjustments. This scheduler proactively looks into the future by
 * evaluating weather forecasts to make strategic decisions about charging the Powerwall.
 * <p>
 * It runs periodically (hourly by default) to perform the following actions for each user:
 * <ol>
 *     <li><b>Cleanup:</b> It first cleans up any old, expired (disabled) temporary schedules
 *     from previous runs.</li>
 *     <li><b>Evaluation:</b> It checks if the current time is off-peak. If so, it calls the
 *     weather service to evaluate the upcoming solar forecast.</li>
 *     <li><b>Planning:</b> If the forecast predicts a significant solar shortfall, it creates
 *     a new set of temporary schedules to ensure the Powerwall is sufficiently charged
 *     from the grid beforehand.</li>
 *     <li><b>Auditing:</b> Its decisions are recorded as {@link net.icewheel.energy.application.scheduling.model.ScheduleAuditEvent}s,
 *     which are visible on the "Change History" page.</li>
 * </ol>
 * This component does not directly command the Powerwall. Instead, it manipulates the schedule,
 * which is then acted upon by the "Enforcer" of the system, the {@link PowerwallStateReconciler}.
 *
 * @see PowerwallStateReconciler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherAwareScheduler {

	/**
	 * The repository for accessing user data.
	 */
	private final UserRepository userRepository;

	/**
	 * The repository for accessing Powerwall schedule data.
	 */
	private final PowerwallScheduleRepository scheduleRepository;

	/**
	 * The evaluator for determining if the weather forecast is considered bad.
	 */
	private final WeatherForecastEvaluator weatherForecastEvaluator;

	/**
	 * The repository for storing weather audit events.
	 */
	private final ScheduleAuditEventRepository auditEventRepository;

	/**
	 * The clock used for getting the current time.
	 */
	private final Clock clock;

	/**
	 * Runs a daily check for poor solar conditions and creates temporary charging schedules if needed.
	 *
	 * <p>This method is scheduled to run every day at 4 AM local time. It iterates through all users
	 * and, for those with weather-aware schedules, it triggers a solar forecast evaluation.
	 * If the forecast indicates poor solar generation potential, a temporary charging schedule is
	 * created.
	 */
	@Scheduled(cron = "${app.weather-check.cron:0 0 * * * ?}") // Run every hour by default
	public void checkForBadWeather() {
		log.info("Running weather-aware scheduling check...");

		List<User> users = userRepository.findAll();

		for (User user : users) {
			// --- 1. Lifecycle Management: Clean up old temporary schedules ---
			// Before each run, we find all schedules for the user and delete any that are both
			// temporary and disabled (meaning they have already executed) or have expired.
			// This makes the scheduler self-cleaning and robust against stale data.
			List<PowerwallSchedule> allUserSchedules = scheduleRepository.findAllByUser(user);
			Instant now = clock.instant();
			List<PowerwallSchedule> expiredTempSchedules = allUserSchedules.stream()
					.filter(s -> s.isTemporary() && (!s.isEnabled() || (s.getExpirationTime() != null && s.getExpirationTime().isBefore(now))))
					.toList();

			if (!expiredTempSchedules.isEmpty()) {
				log.info("Cleaning up {} expired temporary schedule(s) for user {}.", expiredTempSchedules.size(), user.getId());
				scheduleRepository.deleteAll(expiredTempSchedules);
			}

			// Continue with a clean list of only the active, relevant schedules for this user.
			List<PowerwallSchedule> weatherAwareSchedules = allUserSchedules.stream()
					.filter(s -> !expiredTempSchedules.contains(s) && s.getScheduleType() == ScheduleType.WEATHER_AWARE)
					.toList();

			if (weatherAwareSchedules.isEmpty()) {
				continue;
			}

			// --- 2. Pre-computation and Safety Checks ---

			// Find the permanent on-peak and off-peak schedules to determine the time window.
			Optional<PowerwallSchedule> onPeakScheduleOpt = weatherAwareSchedules.stream()
					.filter(s -> !s.isTemporary() && s.getEventType() == ScheduleEventType.START_DISCHARGE)
					.findFirst();
			Optional<PowerwallSchedule> offPeakScheduleOpt = weatherAwareSchedules.stream()
					.filter(s -> !s.isTemporary() && s.getEventType() == ScheduleEventType.START_CHARGE)
					.findFirst();

			// Ensure the base schedule is valid before proceeding.
			if (onPeakScheduleOpt.isEmpty() || offPeakScheduleOpt.isEmpty()) {
				log.warn("User {} has an incomplete weather-aware schedule configuration. Skipping.", user.getId());
				continue;
			}

			// Efficiency: Check if it's on-peak *before* calling the weather API.
			// This prevents making unnecessary external API calls when no action can be taken.
			if (isCurrentlyOnPeak(onPeakScheduleOpt.get(), offPeakScheduleOpt.get(), clock)) {
				log.info("Weather check for user {} is running during an on-peak period. Skipping to avoid expensive grid charging.", user.getId());
				continue;
			}

			// Ensure the user has a location set for the weather forecast.
			if (user.getPreference() == null || user.getPreference().getZipCode() == null) {
				log.warn("User {} has weather-aware schedules but no location configured. Skipping.", user.getId());
				continue;
			}

						try {
				// --- 3. Weather Evaluation ---
							// This is the only part of the process that makes an external API call.
							log.info("Evaluating weather forecast for user {}.", user.getId());
							SolarForecast solarForecast = weatherForecastEvaluator.evaluate(user);
			
							PowerwallSchedule onPeakSchedule = onPeakScheduleOpt.get();
							PowerwallSchedule offPeakSchedule = offPeakScheduleOpt.get();
			
							int sunshinePercentage = solarForecast.sunshinePercentage();
							// The base backup percent for charging should be the off-peak target.
							int baseBackupPercent = offPeakSchedule.getBackupPercent();
			
							// The solar shortfall is the inverse of the sunshine percentage.
							// This represents the percentage of solar energy that is expected to be lost due to weather.
							int solarShortfall = 100 - sunshinePercentage;
			
							// --- 4. Planning and Action ---
			
							if (solarShortfall > 5) { // Only adjust if the shortfall is more than 5%
																										// The new charge target is the user's off-peak backup percentage plus a scaled portion of the solar shortfall.
																										// This prevents the battery from being over-charged and provides a more reasonable adjustment.
																										// The user-configurable scaling factor allows them to control the aggressiveness of this adjustment.
										int scalingFactor = offPeakSchedule.getWeatherScalingFactor() != null ? offPeakSchedule.getWeatherScalingFactor() : 100;
										double adjustment = (solarShortfall / 100.0) * (100.0 - baseBackupPercent) * (scalingFactor / 100.0);
										int adjustedChargeTarget = baseBackupPercent + (int) Math.round(adjustment);
								// The final target cannot exceed 90% to preserve battery health.
								int finalChargeTarget = Math.min(90, adjustedChargeTarget);
			
								// If a forced charge is already active, only create a new one if the new target is higher.
								if (user.getPreference() != null && user.getPreference().isForcedChargingActive()) {
									Optional<PowerwallSchedule> currentTempChargeOpt = weatherAwareSchedules.stream()
											.filter(s -> s.isTemporary() && s.getEventType() == ScheduleEventType.START_CHARGE && s.isEnabled())
											.findFirst();
			
									if (currentTempChargeOpt.isPresent()) {
										int currentTarget = currentTempChargeOpt.get().getBackupPercent();
										if (finalChargeTarget > currentTarget) {
											log.info("Worsening weather detected. Overriding existing temporary schedule. New target: {}%, Old target: {}%", finalChargeTarget, currentTarget);
										} else {
											log.info("Forced charge is already active with a target of {}% or higher. New target of {}% is not needed. Skipping.", currentTarget, finalChargeTarget);
											// Also update the evaluation details on the permanent schedule so the log shows the latest reason, even if no action was taken.
											String reason = String.format("Forced charge already active at %d%%. New, lower target of %d%% ignored. Forecast reason: %s", currentTarget, finalChargeTarget, solarForecast.reason());
											updateEvaluationDetails(weatherAwareSchedules, reason);
											return; // Do nothing
										}
									}
								}
			
								String reason = String.format(
										"Solar shortfall of %d%% detected. Adjusting charge target from %d%% to %d%%. Forecast reason: %s",
										solarShortfall, baseBackupPercent, finalChargeTarget, solarForecast.reason()
								);
								log.info(reason);
								updateEvaluationDetails(weatherAwareSchedules, reason);
								logWeatherUpdate(user, onPeakSchedule, reason);
								createTemporaryChargeSchedule(onPeakSchedule, user, finalChargeTarget);
							} else {
								// If solar potential is good, just log the fact and do nothing.
								log.info("Good solar potential detected for user {}. Reason: {}. No charge adjustment needed.", user.getId(), solarForecast.reason());
								String reason = String.format("Good solar potential detected. Reason: %s", solarForecast.reason());
								updateEvaluationDetails(weatherAwareSchedules, reason);
								logWeatherUpdate(user, onPeakSchedule, reason);
							}
						} catch (ForecastEvaluationException e) {				log.warn("Weather evaluation failed for user {}: {}", user.getId(), e.getMessage());
				// Audit with the first available schedule if evaluation fails early.
				logWeatherUpdate(user, weatherAwareSchedules.getFirst(), e.getMessage());
				// Continue to next user without creating a temporary schedule.
			}
		}

		log.info("Weather-aware scheduling check finished.");
	}

	/**
	 * Creates a temporary, one-time charging schedule to run before the next on-peak period.
	 *
	 * <p>This method creates two schedules:
	 * <ol>
	 *     <li>A "start charging" schedule set to run in 5 minutes to charge the Powerwall to the target percentage.</li>
	 *     <li>A "stop charging" schedule set to run at the beginning of the next on-peak period, which effectively
	 *     stops the forced charge by setting the backup reserve to a low value.</li>
	 * </ol>
	 *
	 * @param originalSchedule The original weather-aware schedule.
	 * @param user The user for whom the schedule is created.
	 * @param chargePercent The dynamically calculated percentage to charge the battery to.
	 */
	private void createTemporaryChargeSchedule(PowerwallSchedule originalSchedule, User user, int chargePercent) {
		user.getPreference().setForcedChargingActive(true);
		userRepository.save(user);

		// Create a "start charging" schedule
		PowerwallSchedule startChargeSchedule = new PowerwallSchedule();
		startChargeSchedule.setId(UUID.randomUUID());
		startChargeSchedule.setScheduleGroupId(originalSchedule.getScheduleGroupId());
		startChargeSchedule.setUser(user);
		startChargeSchedule.setName("Temporary Start Charge for " + originalSchedule.getName());
		startChargeSchedule.setDescription("Temporary charging schedule created due to bad weather forecast.");
		startChargeSchedule.setEnergySiteId(originalSchedule.getEnergySiteId());
		startChargeSchedule.setDaysOfWeek(originalSchedule.getDaysOfWeek());
		startChargeSchedule.setTimeZone(originalSchedule.getTimeZone());
		startChargeSchedule.setEnabled(true);
		startChargeSchedule.setEventType(ScheduleEventType.START_CHARGE);
		startChargeSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
		startChargeSchedule.setBackupPercent(chargePercent);
		startChargeSchedule.setTemporary(true);

		ZonedDateTime now = ZonedDateTime.now(clock.withZone(originalSchedule.getTimeZone()));
		ZonedDateTime startExecutionTime = now.plusMinutes(5);
		String startCronExpression = String.format("%d %d %d %d %d ?",
				startExecutionTime.getSecond(),
				startExecutionTime.getMinute(),
				startExecutionTime.getHour(),
				startExecutionTime.getDayOfMonth(),
				startExecutionTime.getMonthValue());
		startChargeSchedule.setScheduledTime(startExecutionTime.toLocalTime());
		startChargeSchedule.setCronExpression(startCronExpression);
		scheduleRepository.save(startChargeSchedule);

		// Create a "stop charging" schedule
		PowerwallSchedule stopChargeSchedule = new PowerwallSchedule();
		stopChargeSchedule.setId(UUID.randomUUID());
		stopChargeSchedule.setScheduleGroupId(originalSchedule.getScheduleGroupId());
		stopChargeSchedule.setUser(user);
		stopChargeSchedule.setName("Temporary Stop Charge for " + originalSchedule.getName());
		stopChargeSchedule.setDescription("Temporary schedule to stop charging after bad weather event.");
		stopChargeSchedule.setEnergySiteId(originalSchedule.getEnergySiteId());
		stopChargeSchedule.setDaysOfWeek(originalSchedule.getDaysOfWeek());
		stopChargeSchedule.setTimeZone(originalSchedule.getTimeZone());
		stopChargeSchedule.setEnabled(true);
		stopChargeSchedule.setEventType(ScheduleEventType.START_DISCHARGE); // This will set backup to a low value
		stopChargeSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
		stopChargeSchedule.setBackupPercent(20); // A low value to stop charging
		stopChargeSchedule.setTemporary(true);

		ZonedDateTime stopExecutionTime = originalSchedule.getScheduledTime().atDate(now.toLocalDate()).atZone(originalSchedule.getTimeZone());
		if (now.toLocalTime().isAfter(originalSchedule.getScheduledTime())) {
			stopExecutionTime = stopExecutionTime.plusDays(1);
		}

		startChargeSchedule.setExpirationTime(stopExecutionTime.toInstant());
		stopChargeSchedule.setExpirationTime(stopExecutionTime.toInstant());

		String stopCronExpression = String.format("%d %d %d %d %d ?",
				stopExecutionTime.getSecond(),
				stopExecutionTime.getMinute(),
				stopExecutionTime.getHour(),
				stopExecutionTime.getDayOfMonth(),
				stopExecutionTime.getMonthValue());
		stopChargeSchedule.setScheduledTime(stopExecutionTime.toLocalTime());
		stopChargeSchedule.setCronExpression(stopCronExpression);
		scheduleRepository.save(stopChargeSchedule);


		log.info("Created temporary charging schedules for user {}", user.getId());
	}

	private void updateEvaluationDetails(List<PowerwallSchedule> schedules, String reason) {
		for (PowerwallSchedule schedule : schedules) {
			schedule.setLastEvaluationDetails(reason);
		}
		scheduleRepository.saveAll(schedules);
	}

	/**
	 * Creates and saves a weather audit event.
	 *
	 * <p>This method is used to record important events related to the weather-aware
	 * scheduling feature. The audit events are stored in the database and can be used
	 * for debugging and monitoring purposes.
	 * </p>
	 * @param user The user associated with the event.
	 * @param schedule The schedule associated with the event.
	 */
	private void logWeatherUpdate(User user, PowerwallSchedule schedule, String reason) {
		ScheduleAuditEvent event = new ScheduleAuditEvent();
		event.setUser(user);
		event.setScheduleGroupId(schedule.getScheduleGroupId());
		event.setScheduleName(schedule.getName());
		event.setAction(ScheduleAuditEvent.AuditAction.WEATHER_UPDATE);
		event.setDetails(Map.of("info", reason));
		auditEventRepository.save(event);
	}

	/**
	 * Checks if the current time is within the on-peak window of a given set of schedules.
	 * @param onPeakSchedule The schedule that defines the start of the on-peak period.
	 * @param offPeakSchedule The schedule that defines the end of the on-peak period.
	 * @return true if the current time is within the on-peak period, false otherwise.
	 */
	private boolean isCurrentlyOnPeak(PowerwallSchedule onPeakSchedule, PowerwallSchedule offPeakSchedule, Clock clock) {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(onPeakSchedule.getTimeZone()));
		LocalTime currentTimeInScheduleTz = now.toLocalTime();
		LocalTime onPeakStart = onPeakSchedule.getScheduledTime();
		LocalTime offPeakStart = offPeakSchedule.getScheduledTime(); // This is the on-peak END time

		if (onPeakStart == null || offPeakStart == null) {
			return false; // Cannot determine window if times are not set
		}

		if (onPeakStart.isBefore(offPeakStart)) {
			// Standard day peak (e.g., 7 AM - 9 PM)
			return !currentTimeInScheduleTz.isBefore(onPeakStart) && currentTimeInScheduleTz.isBefore(offPeakStart);
		} else {
			// Overnight peak (e.g., 9 PM - 7 AM)
			return !currentTimeInScheduleTz.isBefore(onPeakStart) || currentTimeInScheduleTz.isBefore(offPeakStart);
		}
	}

}