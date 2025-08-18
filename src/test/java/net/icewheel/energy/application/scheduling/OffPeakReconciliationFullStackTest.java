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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled
class OffPeakReconciliationFullStackTest {

	public static MockWebServer mockWebServer;

	@Autowired
	private PowerwallStateReconciler reconciler;

	@Autowired
	private PowerwallScheduleRepository scheduleRepository;

	@Autowired
	private UserRepository userRepository;

	private User testUser;
	private static final ZoneId utilityTimeZone = ZoneId.of("America/New_York");

	@TestConfiguration
	static class OffPeakTestConfig {
		private static final Instant NOW = Instant.parse("2025-08-19T02:00:00Z"); // 10:00 PM EDT on Monday the 18th

		@Bean
		@Primary
		public Clock testClock() {
			return Clock.fixed(NOW, utilityTimeZone);
		}
	}

	@DynamicPropertySource
	static void registerTeslaApiBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("tesla.api-base-url", () -> mockWebServer.url("/").toString());
		registry.add("tesla.token-url", () -> mockWebServer.url("/").toString() + "oauth2/v3/token");
	}

	@BeforeAll
	static void startMockWebServer() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
	}

	@AfterAll
	static void stopMockWebServer() throws IOException {
		mockWebServer.shutdown();
	}

	@BeforeEach
	void setUp() {
		scheduleRepository.deleteAll();
		userRepository.deleteAll();

		testUser = new User();
		testUser.setId(UUID.randomUUID().toString());
		testUser.setName("utility-user");
		userRepository.save(testUser);
	}

	@Test
	@DisplayName("Given a missed 9 PM trigger, reconciler calls Tesla API with valid JWT to set off-peak backup")
	void reconciler_whenOffPeakTriggerIsMissed_sendsCorrectApiCall() throws Exception {
		// Given: A sample utility schedule exists
		createSampleUtilitySchedule(20, 80);

		// And: The mock Tesla API will provide a valid JWT access token
		String mockJwt = createMockJwt(testUser.getId());
		String tokenBody = String.format("{\"access_token\":\"%s\",\"expires_in\":3600}", mockJwt);
		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(tokenBody));

		// And: The API shows the wrong state (20%)
		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody(readJsonFile("json/site_info_response_20_percent.json")));

		// And: The API will accept the correction
		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody(readJsonFile("json/set_backup_reserve_success_response.json")));

		// When: The reconciliation job runs
		reconciler.reconcileContinuously();

		// Then: A call to the token endpoint should have been made
		RecordedRequest tokenRequest = mockWebServer.takeRequest();
		assertThat(tokenRequest.getPath()).isEqualTo("/oauth2/v3/token");

		// And: An API call to get site info should have been made with the JWT
		RecordedRequest getRequest = mockWebServer.takeRequest();
		assertThat(getRequest.getPath()).isEqualTo("/api/1/sites/SITE_ID_67890/site_info");
		assertThat(getRequest.getHeader("Authorization")).isEqualTo("Bearer " + mockJwt);

		// And: An API call to set the backup reserve should have been made with the JWT
		RecordedRequest postRequest = mockWebServer.takeRequest();
		assertThat(postRequest.getMethod()).isEqualTo("POST");
		assertThat(postRequest.getPath()).isEqualTo("/api/1/sites/SITE_ID_67890/backup");
		assertThat(postRequest.getHeader("Authorization")).isEqualTo("Bearer " + mockJwt);
		assertThat(postRequest.getBody().readUtf8()).contains("\"backup_percent\":80");
	}

	private String readJsonFile(String filePath) throws IOException {
		ClassPathResource resource = new ClassPathResource(filePath);
		try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
			return FileCopyUtils.copyToString(reader);
		}
	}

	private String createMockJwt(String userId) {
		long nowMillis = System.currentTimeMillis();
		Date now = new Date(nowMillis);
		long expMillis = nowMillis + 3600000; // 1 hour
		Date exp = new Date(expMillis);
		byte[] apiKeySecretBytes = "a-super-secret-key-that-is-long-enough-for-the-algorithm".getBytes(StandardCharsets.UTF_8);
		SecretKeySpec signingKey = new SecretKeySpec(apiKeySecretBytes, SignatureAlgorithm.HS256.getJcaName());

		return Jwts.builder()
				.setSubject(userId)
				.setIssuedAt(now)
				.setExpiration(exp)
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
		String numericSiteId = "SITE_ID_67890";
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