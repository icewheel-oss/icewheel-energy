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
package net.icewheel.energy.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.icewheel.energy.application.scheduling.PowerwallScheduleExecutor;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests in simple, layman terms:
 * - We have three different users, each with a schedule that should run "right now".
 * - We pretend to be Tesla's API by using a fake service (a mock):
 *   - For the first user, Tesla says "OK" when we try to set the Powerwall battery level.
 *   - For the second user, Tesla says "No" (returns false).
 *   - For the third user, Tesla "breaks" and throws an error.
 * - We run our scheduler once. We expect:
 *   - The first user's run is recorded as SUCCESS.
 *   - The second and third users' runs are recorded as FAILURE.
 *   - Importantly, one user's problem doesn't stop the others from running.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MultiUserSchedulerFailureIT {

	@TestConfiguration
	static class TestBeans {
		@Bean
		@Primary
		public Clock fixedClock() {
			// Freeze time at 2025-08-16 13:00:00 America/New_York
			return Clock.fixed(Instant.parse("2025-08-16T17:00:00Z"), ZoneId.of("America/New_York"));
		}

		@Bean
		@Primary
		public TeslaEnergyService mockedTeslaService() {
			// We'll fine-tune behavior per-user in the test method using Mockito.when(...)
			return Mockito.mock(TeslaEnergyService.class);
		}
	}

	@Autowired
	private PowerwallScheduleExecutor executor;
	@Autowired
	private PowerwallScheduleRepository scheduleRepository;
	@Autowired
	private ScheduleExecutionHistoryRepository historyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private TeslaEnergyService teslaEnergyService;
	@Autowired
	private Clock clock;

	private final ZoneId userTz = ZoneId.of("America/New_York");

	@BeforeEach
	void cleanDb() {
		historyRepository.deleteAll();
		scheduleRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void scheduler_handles_more_than_two_users_with_failures_independently() {
		// Create three users
		User alice = createUser("alice");
		User bob = createUser("bob");
		User charlie = createUser("charlie");

		// Each user gets one schedule that should run at the fixed time (top of the hour at 13:00 NY time)
		createHourlyTopOfHourSchedule(alice, "siteA", 30);
		createHourlyTopOfHourSchedule(bob, "siteB", 40);
		createHourlyTopOfHourSchedule(charlie, "siteC", 50);

		// Configure the mock Tesla API responses per user/site
		when(teslaEnergyService.setBackupReserve(eq(alice.getId()), eq("siteA"), anyInt())).thenReturn(true); // success
		when(teslaEnergyService.setBackupReserve(eq(bob.getId()), eq("siteB"), anyInt())).thenReturn(false); // API rejects
		when(teslaEnergyService.setBackupReserve(eq(charlie.getId()), eq("siteC"), anyInt()))
				.thenThrow(new RuntimeException("Simulated Tesla outage")); // exception

		// Run the scheduler once (simulates the minute where all these should trigger)
		executor.executeSchedules();

		// Verify: We recorded exactly three executions
		List<ScheduleExecutionHistory> history = historyRepository.findAll();
		assertThat(history).hasSize(3);

		// Easy-to-read check per user
		var byUser = history.stream().collect(Collectors.groupingBy(ScheduleExecutionHistory::getUserId));
		assertThat(byUser).containsKeys(alice.getId(), bob.getId(), charlie.getId());

		ScheduleExecutionHistory aliceRun = byUser.get(alice.getId()).getFirst();
		ScheduleExecutionHistory bobRun = byUser.get(bob.getId()).getFirst();
		ScheduleExecutionHistory charlieRun = byUser.get(charlie.getId()).getFirst();

		assertThat(aliceRun.getStatus()).as("Alice should succeed")
				.isEqualTo(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
		assertThat(bobRun.getStatus()).as("Bob should fail because API returned false")
				.isEqualTo(ScheduleExecutionHistory.ExecutionStatus.FAILURE);
		assertThat(charlieRun.getStatus()).as("Charlie should fail due to exception")
				.isEqualTo(ScheduleExecutionHistory.ExecutionStatus.FAILURE);

		// Sanity: Even with an exception for Charlie, we still have records for Alice and Bob
	}

	// Helper: Create a simple schedule that triggers every hour at minute 0 in user's timezone
	private void createHourlyTopOfHourSchedule(User user, String siteId, int backupPercent) {
		PowerwallSchedule schedule = new PowerwallSchedule();
		schedule.setId(UUID.randomUUID());
		schedule.setScheduleGroupId(UUID.randomUUID());
		schedule.setUser(user);
		schedule.setName("Hourly Schedule for " + user.getName());
		schedule.setEnergySiteId(siteId);
		schedule.setDaysOfWeek(Set.of(java.time.DayOfWeek.values()));
		schedule.setTimeZone(userTz);
		schedule.setEnabled(true);
		schedule.setEventType(ScheduleEventType.START_CHARGE);
		schedule.setScheduledTime(LocalTime.of(13, 0)); // not used by cron match, but good for readability
		schedule.setBackupPercent(backupPercent);
		// Cron: "at minute 0 of every hour on any day": matches our fixed time (13:00 user local time)
		schedule.setCronExpression("0 0 * * * ?");
		scheduleRepository.save(schedule);
	}

	private User createUser(String name) {
		User u = new User();
		u.setId(UUID.randomUUID().toString());
		u.setName(name);
		return userRepository.save(u);
	}
}
