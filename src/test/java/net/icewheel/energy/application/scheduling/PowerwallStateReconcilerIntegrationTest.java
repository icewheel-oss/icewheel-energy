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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
import net.icewheel.energy.domain.energy.model.ReconciliationMode;
import net.icewheel.energy.domain.energy.model.ScheduleEventType;
import net.icewheel.energy.domain.energy.model.ScheduleExecutionHistory;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the {@link PowerwallStateReconciler}.
 * <p>
 * This class verifies that the reconciler behaves correctly under various conditions.
 * It uses a full Spring Boot context ({@code @SpringBootTest}) to ensure that the reconciler,
 * its dependencies, and the persistence layer are all working together as expected.
 * A fixed clock and a mocked {@link TeslaEnergyService} are used to create predictable and repeatable test scenarios.
 * </p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PowerwallStateReconcilerIntegrationTest {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
	private static final String TEST_SITE_ID = "1234567890";

	@TestConfiguration
	static class TestConfig {
		// Set a fixed point in time: Saturday, August 16, 2025 1:00 PM in New York
		// This is 17:00:00 UTC
		private static final Instant NOW = Instant.parse("2025-08-16T17:00:00Z");
		private static final ZoneId TZ = ZoneId.of("America/New_York");

		@Bean
		@Primary
		public Clock testClock() {
			return Clock.fixed(NOW, TZ);
		}

		@Bean
		@Primary
		public TeslaEnergyService mockTeslaEnergyService() {
			TeslaEnergyService mockService = Mockito.mock(TeslaEnergyService.class);
			// Default mock behavior for successful API calls
			when(mockService.setBackupReserve(anyString(), anyString(), anyInt())).thenReturn(true);
			return mockService;
		}
	}

	@Autowired
	private PowerwallStateReconciler reconciler;

	@Autowired
	private PowerwallScheduleRepository scheduleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ScheduleExecutionHistoryRepository historyRepository;

	@Autowired
	private TeslaEnergyService teslaEnergyService;

	private User testUser;
	private final ZoneId userTimeZone = ZoneId.of("America/New_York");

	@BeforeEach
	void setUp() {
		historyRepository.deleteAll();
		scheduleRepository.deleteAll();
		userRepository.deleteAll();

		testUser = new User();
		testUser.setId(UUID.randomUUID().toString());
		testUser.setName("testuser");
		userRepository.save(testUser);
	}

	/**
	 * Verifies that when the Powerwall's state already matches the scheduled on-peak reserve,
	 * the reconciler logs a SKIPPED event and does not make any corrective API calls.
	 */
	@Test
	void reconcile_whenStateMatches_logsSkippedHistory() {
		// Given: An active on-peak schedule where the actual state matches the expected state.
		int onPeakPercent = 80;
		createScheduleGroup(true, onPeakPercent, 20, LocalTime.of(12, 0), LocalTime.of(18, 0)); // On-peak now
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(onPeakPercent);

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should check the state but not attempt to change it.
		verify(teslaEnergyService).getBackupReservePercent(eq(testUser.getId()), eq(TEST_SITE_ID));
		verify(teslaEnergyService, never()).setBackupReserve(anyString(), anyString(), anyInt());

		// And: A history record should be created to show the check was performed.
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(1);
		ScheduleExecutionHistory record = history.get(0);
		assertThat(record.getStatus()).isEqualTo(ScheduleExecutionHistory.ExecutionStatus.SKIPPED);
		assertThat(record.getExecutionType()).isEqualTo(ScheduleExecutionHistory.ExecutionType.RECONCILIATION_CONTINUOUS);
		String expectedDetails = String.format(
				"Automatic check during an on-peak period for schedule '%s'. The Powerwall's backup reserve is already correctly set to %d%%. No action was needed.",
				"Test Schedule", onPeakPercent
		);
		assertThat(record.getDetails()).isEqualTo(expectedDetails);
	}

	/**
	 * Verifies that if the Powerwall's state is incorrect during an on-peak period,
	 * the reconciler detects the mismatch, corrects the state, and logs a SUCCESS event.
	 */
	@Test
	void reconcile_whenOnPeakStateMismatches_correctsStateAndLogsHistory() {
		// Given: An active on-peak schedule with a state mismatch.
		int onPeakPercent = 80;
		int offPeakPercent = 20;
		int actualPercent = 50;
		LocalTime startTime = LocalTime.of(12, 0);
		LocalTime endTime = LocalTime.of(18, 0);
		createScheduleGroup(true, onPeakPercent, offPeakPercent, startTime, endTime); // On-peak now
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(actualPercent);

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should correct the state via the Tesla API.
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), eq(TEST_SITE_ID), eq(onPeakPercent));

		// And: A success history event with details about the correction should be logged.
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(1);
		ScheduleExecutionHistory record = history.get(0);
		assertThat(record.getExecutionType()).isEqualTo(ScheduleExecutionHistory.ExecutionType.RECONCILIATION_CONTINUOUS);
		assertThat(record.getStatus()).isEqualTo(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
		String expectedDetails = String.format(
				"Automatic correction for schedule '%s' during its on-peak window (%s - %s). The backup reserve was at %d%% and has been corrected to the scheduled %d%%.",
				"Test Schedule", formatTime(startTime), formatTime(endTime), actualPercent, onPeakPercent
		);
		assertThat(record.getDetails()).isEqualTo(expectedDetails);
	}

	/**
	 * Verifies that if the Powerwall's state is incorrect during an off-peak period,
	 * the reconciler detects the mismatch, corrects the state, and logs a SUCCESS event.
	 */
	@Test
	void reconcile_whenOffPeakStateMismatches_correctsStateAndLogsHistory() {
		// Given: A schedule whose on-peak period has passed, with a state mismatch during the off-peak period.
		int onPeakPercent = 80;
		int offPeakPercent = 20;
		int actualPercent = 50;
		createScheduleGroup(true, onPeakPercent, offPeakPercent, LocalTime.of(9, 0), LocalTime.of(12, 0)); // Off-peak now
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(actualPercent);

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should correct the state to the off-peak setting.
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), eq(TEST_SITE_ID), eq(offPeakPercent));

		// And: A success history event with details about the off-peak correction should be logged.
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(1);
		ScheduleExecutionHistory record = history.get(0);
		assertThat(record.getExecutionType()).isEqualTo(ScheduleExecutionHistory.ExecutionType.RECONCILIATION_CONTINUOUS);
		assertThat(record.getStatus()).isEqualTo(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
		String expectedDetails = String.format(
				"Automatic correction during an off-peak period. The backup reserve was at %d%% and has been corrected to the scheduled %d%% (based on schedule '%s').",
				actualPercent, offPeakPercent, "Test Schedule"
		);
		assertThat(record.getDetails()).isEqualTo(expectedDetails);
	}

	/**
	 * Verifies that the reconciler does nothing if no schedules are active on the current day of the week.
	 */
	@Test
	void reconcile_whenNotScheduledDay_doesNothing() {
		// Given: A schedule that is not active on the current day (test runs on Saturday, schedule is for Monday).
		createScheduleGroup(true, 80, 20, LocalTime.of(12, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.MONDAY));

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should not even check the Powerwall state, preventing unnecessary API calls.
		verify(teslaEnergyService, never()).getBackupReservePercent(anyString(), anyString());
	}

	/**
	 * Verifies that the reconciler correctly ignores schedules that are explicitly disabled.
	 */
	@Test
	void reconcile_whenScheduleIsDisabled_doesNothing() {
		// Given: A disabled schedule.
		createScheduleGroup(false, 80, 20, LocalTime.of(12, 0), LocalTime.of(18, 0));

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should ignore the disabled schedule and not make any API calls.
		verify(teslaEnergyService, never()).getBackupReservePercent(anyString(), anyString());
	}

	/**
	 * Verifies that if the reconciler attempts a correction and the Tesla API reports a failure,
	 * a FAILURE event is logged in the history.
	 */
	@Test
	void reconcile_whenApiSetFails_logsFailureHistory() {
		// Given: A state mismatch, but the API call to set the new value fails.
		int onPeakPercent = 80;
		int actualPercent = 50;
		createScheduleGroup(true, onPeakPercent, 20, LocalTime.of(12, 0), LocalTime.of(18, 0));
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(actualPercent);
		when(teslaEnergyService.setBackupReserve(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Integer.class))).thenReturn(false); // Simulate failure

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should still attempt the correction.
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), eq(TEST_SITE_ID), eq(onPeakPercent));

		// And: A failure history event should be logged.
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(1);
		ScheduleExecutionHistory record = history.get(0);
		assertThat(record.getStatus()).isEqualTo(ScheduleExecutionHistory.ExecutionStatus.FAILURE);
		String expectedDetails = String.format(
				"Automatic correction failed for schedule '%s'. The API call to set backup reserve to %d%% was not accepted by Tesla.",
				"Test Schedule", onPeakPercent
		);
		assertThat(record.getDetails()).isEqualTo(expectedDetails);
	}

	/**
	 * Verifies that the continuous reconciliation job ignores schedules marked for STARTUP_ONLY reconciliation.
	 */
	@Test
	void reconcileContinuously_whenModeIsStartupOnly_doesNothing() {
		// Given: An active schedule with a state mismatch, but reconciliation mode is STARTUP_ONLY.
		createScheduleGroup(true, 80, 20, LocalTime.of(12, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.SATURDAY), ReconciliationMode.STARTUP_ONLY);
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(50);

		// When: The continuous reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should not check the state, as it filters for CONTINUOUS mode only.
		verify(teslaEnergyService, never()).getBackupReservePercent(anyString(), anyString());
		verify(teslaEnergyService, never()).setBackupReserve(anyString(), anyString(), anyInt());
		assertThat(historyRepository.count()).isZero();
	}

	/**
	 * Verifies that the startup reconciliation job correctly processes all relevant schedules,
	 * including those marked as STARTUP_ONLY.
	 */
	@Test
	void reconcileOnStartup_whenStateMismatches_correctsStateAndLogsHistory() {
		// Given: A schedule with a state mismatch, with any reconciliation mode.
		int onPeakPercent = 80;
		int actualPercent = 50;
		createScheduleGroup(true, onPeakPercent, 20, LocalTime.of(12, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.SATURDAY), ReconciliationMode.STARTUP_ONLY);
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(actualPercent);

		// When: The startup reconciliation job runs.
		reconciler.reconcileOnStartup();

		// Then: The reconciler should correct the state.
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), eq(TEST_SITE_ID), eq(onPeakPercent));

		// And: A success history event should be logged for the startup execution.
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(1);
		ScheduleExecutionHistory record = history.get(0);
		assertThat(record.getExecutionType()).isEqualTo(ScheduleExecutionHistory.ExecutionType.RECONCILIATION_STARTUP);
		assertThat(record.getStatus()).isEqualTo(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
		String expectedDetails = String.format(
				"Automatic correction for schedule '%s' during its on-peak window (%s - %s). The backup reserve was at %d%% and has been corrected to the scheduled %d%%.",
				"Test Schedule", formatTime(LocalTime.of(12, 0)), formatTime(LocalTime.of(18, 0)), actualPercent, onPeakPercent
		);
		assertThat(record.getDetails()).isEqualTo(expectedDetails);
	}

	private void createScheduleGroup(boolean enabled, int onPeakPercent, int offPeakPercent, LocalTime startTime, LocalTime endTime) {
		createScheduleGroup(enabled, onPeakPercent, offPeakPercent, startTime, endTime, Set.of(DayOfWeek.SATURDAY), ReconciliationMode.CONTINUOUS);
	}

	private void createScheduleGroup(boolean enabled, int onPeakPercent, int offPeakPercent, LocalTime startTime, LocalTime endTime, Set<DayOfWeek> days) {
		createScheduleGroup(enabled, onPeakPercent, offPeakPercent, startTime, endTime, days, ReconciliationMode.CONTINUOUS);
	}

	private void createScheduleGroup(boolean enabled, int onPeakPercent, int offPeakPercent, LocalTime startTime, LocalTime endTime, Set<DayOfWeek> days, ReconciliationMode mode) {
		UUID groupId = UUID.randomUUID();
		String scheduleName = "Test Schedule";

		// On-Peak (Discharge) Schedule
		PowerwallSchedule onPeak = new PowerwallSchedule();
		onPeak.setId(UUID.randomUUID());
		onPeak.setScheduleGroupId(groupId);
		onPeak.setUser(testUser);
		onPeak.setName(scheduleName);
		onPeak.setEnergySiteId(TEST_SITE_ID);
		onPeak.setDaysOfWeek(days);
		onPeak.setTimeZone(userTimeZone);
		onPeak.setEnabled(enabled);
		onPeak.setReconciliationMode(mode);
		onPeak.setEventType(ScheduleEventType.START_DISCHARGE);
		onPeak.setScheduledTime(startTime);
		onPeak.setBackupPercent(onPeakPercent);
		onPeak.setCronExpression(String.format("0 %d %d ? * %s", startTime.getMinute(), startTime.getHour(), formatDaysForCron(days)));

		// Off-Peak (Charge) Schedule
		PowerwallSchedule offPeak = new PowerwallSchedule();
		offPeak.setId(UUID.randomUUID());
		offPeak.setScheduleGroupId(groupId);
		offPeak.setUser(testUser);
		offPeak.setName(scheduleName);
		offPeak.setEnergySiteId(TEST_SITE_ID);
		offPeak.setDaysOfWeek(days);
		offPeak.setTimeZone(userTimeZone);
		offPeak.setEnabled(enabled);
		offPeak.setReconciliationMode(mode);
		offPeak.setEventType(ScheduleEventType.START_CHARGE);
		offPeak.setScheduledTime(endTime);
		offPeak.setBackupPercent(offPeakPercent);
		offPeak.setCronExpression(String.format("0 %d %d ? * %s", endTime.getMinute(), endTime.getHour(), formatDaysForCron(days)));

		scheduleRepository.saveAll(List.of(onPeak, offPeak));
	}

	private String formatDaysForCron(Set<DayOfWeek> days) {
		return days.stream()
				.map(day -> day.name().substring(0, 3))
				.collect(Collectors.joining(","));
	}

	private String formatTime(LocalTime time) {
		return time.format(TIME_FORMATTER);
	}
}
