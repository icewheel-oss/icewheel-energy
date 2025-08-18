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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
import net.icewheel.energy.domain.energy.model.ReconciliationMode;
import net.icewheel.energy.domain.energy.model.ScheduleEventType;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for the {@link PowerwallStateReconciler} focused on the off-peak scenario.
 * This test verifies that if a scheduled 9 PM off-peak trigger is missed, the reconciler will
 * self-heal the system by correcting the Powerwall's state.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OffPeakReconciliationScenarioIT {

	@Autowired
	private PowerwallStateReconciler reconciler;

	@Autowired
	private PowerwallScheduleRepository scheduleRepository;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private TeslaEnergyService teslaEnergyService;

	@MockBean
	private TokenService tokenService;

	private User testUser;
	private final ZoneId utilityTimeZone = ZoneId.of("America/New_York");

	/**
	 * Provides a fixed clock for the test to ensure predictable timing.
	 */
	@TestConfiguration
	static class OffPeakTestConfig {
		@Bean
		@Primary
		public Clock testClock() {
			// Set time to Monday 10:00 PM, one hour after the off-peak period should have started.
			return Clock.fixed(Instant.parse("2025-08-19T02:00:00Z"), ZoneId.of("America/New_York"));
		}
	}

	@BeforeEach
	void setUp() {
		scheduleRepository.deleteAll();
		userRepository.deleteAll();

		testUser = new User();
		testUser.setId(UUID.randomUUID().toString());
		testUser.setName("utility-user");
		userRepository.save(testUser);

		// Why: The TeslaEnergyServiceImpl requires a valid token to make API calls.
		// We mock the TokenService to provide a valid, non-expired JWT, allowing the
		// energy service to proceed with its logic without making real auth calls.
		String mockJwt = createMockJwt(testUser.getId());
		when(tokenService.getValidAccessToken(testUser.getId())).thenReturn(mockJwt);
	}

	@Test
	@DisplayName("Given a missed 9 PM off-peak trigger, reconciler should correct state to off-peak level")
	void reconciler_whenOffPeakTriggerIsMissed_correctsState() {
		// Given: A sample utility schedule exists for the weekday.
		int incorrectOnPeakPercent = 20;
		int expectedOffPeakPercent = 80;
		createSampleUtilitySchedule(incorrectOnPeakPercent, expectedOffPeakPercent);

		// And: The Powerwall is still at the old on-peak setting, simulating a missed trigger.
		when(teslaEnergyService.getBackupReservePercent(eq(testUser.getId()), anyString())).thenReturn(incorrectOnPeakPercent);
		when(teslaEnergyService.setBackupReserve(eq(testUser.getId()), anyString(), anyInt())).thenReturn(true);

		// When: The reconciliation job runs.
		reconciler.reconcileContinuously();

		// Then: The reconciler should detect the mismatch and send an API call to correct the state.
		verify(teslaEnergyService).setBackupReserve(eq(testUser.getId()), anyString(), eq(expectedOffPeakPercent));
	}

	/**
	 * Creates a mock JWT with a valid structure and future expiration date.
	 * This is used to satisfy the authentication checks within the service layer.
	 * @param userId The user ID to embed as the token's subject.
	 * @return A signed, valid JWT string.
	 */
	private String createMockJwt(String userId) {
		long nowMillis = System.currentTimeMillis();
		Date now = new Date(nowMillis);
		long expMillis = nowMillis + 3600000; // 1 hour
		Date exp = new Date(expMillis);
		byte[] apiKeySecretBytes = "a-super-secret-key-that-is-long-enough-for-the-algorithm".getBytes(StandardCharsets.UTF_8);
		SecretKeySpec signingKey = new SecretKeySpec(apiKeySecretBytes, SignatureAlgorithm.HS256.getJcaName());

		return Jwts.builder()
				.setSubject(userId) // "sub"
				.setIssuedAt(now) // "iat"
				.setExpiration(exp) // "exp"
				.claim("iss", "https://fleet-auth.tesla.com/oauth2/v3/nts")
				.claim("aud", List.of("https://fleet-api.prd.na.vn.cloud.tesla.com", "https://fleet-api.prd.eu.vn.cloud.tesla.com"))
				.claim("azp", "ab00de97-0586-4aa4-b83b-270c01faac68")
				.claim("scp", List.of("openid", "user_data", "energy_device_data", "energy_cmds"))
				.claim("amr", List.of("pwd", "mfa"))
				.claim("ou_code", "NA")
				.claim("locale", "en-US")
				.claim("account_type", "person")
				.claim("open_source", false)
				.claim("account_id", "mock-account-id-" + userId)
				.claim("auth_time", now.getTime() / 1000)
				.signWith(signingKey)
				.compact();
	}

	private void createSampleUtilitySchedule(int onPeakPercent, int offPeakPercent) {
		UUID groupId = UUID.randomUUID();
		String numericSiteId = "SITE_ID_12345";
		Set<DayOfWeek> weekdays = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
		String cronDays = weekdays.stream().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));

		PowerwallSchedule onPeak = new PowerwallSchedule();
		LocalTime onPeakTime = LocalTime.of(7, 0);
		onPeak.setId(UUID.randomUUID());
		onPeak.setScheduleGroupId(groupId);
		onPeak.setUser(testUser);
		onPeak.setName("IceWheel Electric Weekday Saver");
		onPeak.setEnergySiteId(numericSiteId);
		onPeak.setDaysOfWeek(weekdays);
		onPeak.setTimeZone(utilityTimeZone);
		onPeak.setEnabled(true);
		onPeak.setReconciliationMode(ReconciliationMode.CONTINUOUS);
		onPeak.setEventType(ScheduleEventType.START_DISCHARGE);
		onPeak.setScheduledTime(onPeakTime);
		onPeak.setBackupPercent(onPeakPercent);
		onPeak.setCronExpression(String.format("0 %d %d ? * %s", onPeakTime.getMinute(), onPeakTime.getHour(), cronDays));

		PowerwallSchedule offPeak = new PowerwallSchedule();
		LocalTime offPeakTime = LocalTime.of(21, 0);
		offPeak.setId(UUID.randomUUID());
		offPeak.setScheduleGroupId(groupId);
		offPeak.setUser(testUser);
		offPeak.setName("IceWheel Electric Weekday Saver");
		offPeak.setEnergySiteId(numericSiteId);
		offPeak.setDaysOfWeek(weekdays);
		offPeak.setTimeZone(utilityTimeZone);
		offPeak.setEnabled(true);
		offPeak.setReconciliationMode(ReconciliationMode.CONTINUOUS);
		offPeak.setEventType(ScheduleEventType.START_CHARGE);
		offPeak.setScheduledTime(offPeakTime);
		offPeak.setBackupPercent(offPeakPercent);
		offPeak.setCronExpression(String.format("0 %d %d ? * %s", offPeakTime.getMinute(), offPeakTime.getHour(), cronDays));

		scheduleRepository.saveAll(List.of(onPeak, offPeak));
	}
}