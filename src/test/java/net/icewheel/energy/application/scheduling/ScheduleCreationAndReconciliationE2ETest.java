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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.ReconciliationMode;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E test simulating a user creating a schedule via the API, then verifying the reconciler
 * correctly self-heals the system state based on that new schedule.
 * This test verifies the entire flow from the web controller to the background reconciliation job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScheduleCreationAndReconciliationE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PowerwallStateReconciler reconciler;

	@Autowired
	private PowerwallScheduleRepository scheduleRepository;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private TeslaEnergyService teslaEnergyService;

	private User testUser;
	private OAuth2User oauth2User;

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		public Clock testClock() {
			// Set time to Monday 8:00 AM, after the on-peak period should have started.
			return Clock.fixed(Instant.parse("2025-08-18T12:00:00Z"), ZoneId.of("America/New_York"));
		}
	}

	@BeforeEach
	void setUp() {
		scheduleRepository.deleteAll();
		userRepository.deleteAll();

		// Set up the user entity and the corresponding OAuth2User for security context
		String userId = "sample-user-123";
		testUser = new User();
		testUser.setId(userId);
		testUser.setName("Sample User");
		userRepository.save(testUser);

		oauth2User = new DefaultOAuth2User(
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
				Map.of("sub", userId, "name", "Sample User"),
				"sub"
		);
	}

	@Test
	@DisplayName("Given a user creates a schedule via API, when a trigger is missed, then the reconciler corrects the state")
	void sampleSchedule_whenCreatedAndTriggerMissed_isCorrectedByReconciler() throws Exception {
		// GIVEN: The user has a valid, schedulable energy site on their account.
		Product mockSite = new Product();
		mockSite.setEnergySiteId("9876543210");
		mockSite.setSiteName("My Test Home");
		when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(List.of(mockSite));

		// AND: A user creates a new schedule by sending a request to the API endpoint.
		// Why: We build the request body from a Map to bypass the ScheduleRequest DTO's
		// WRITE_ONLY rule on the energySiteId, which is a security feature for exports.
		// This allows us to test the controller validation accurately.
		Map<String, Object> requestMap = createSampleScheduleRequestMap();

		mockMvc.perform(post("/api/schedules")
						.with(oauth2Login().oauth2User(oauth2User)) // Simulate authenticated user
						.with(csrf()) // Add CSRF token to the request
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(requestMap)))
				.andExpect(status().isCreated());

		// AND: The schedule now exists in the database.
		assertThat(scheduleRepository.count()).isEqualTo(2);

		// AND: It's after 7 AM, but the Powerwall is still at the old off-peak setting.
		int incorrectOffPeakPercent = 80;
		when(teslaEnergyService.getBackupReservePercent(anyString(), anyString())).thenReturn(incorrectOffPeakPercent);

		// WHEN: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// THEN: The reconciler should correct the state to the on-peak level.
		int expectedOnPeakPercent = 20;
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), anyString(), eq(expectedOnPeakPercent));
	}

	/**
	 * Creates a Map object that mirrors a sample utility rate plan request.
	 */
	private Map<String, Object> createSampleScheduleRequestMap() {
		Map<String, Object> request = new HashMap<>();
		request.put("name", "IceWheel Weekday Saver");
		request.put("description", "Charges at night, discharges during the day on weekdays.");
		request.put("energySiteId", "9876543210");
		request.put("daysOfWeek", Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
		request.put("timeZone", "America/New_York");
		request.put("enabled", true);
		request.put("reconciliationMode", ReconciliationMode.CONTINUOUS);
		request.put("startTime", LocalTime.of(7, 0));
		request.put("endTime", LocalTime.of(21, 0));
		request.put("onPeakBackupPercent", 20);
		request.put("offPeakBackupPercent", 80);
		return request;
	}
}