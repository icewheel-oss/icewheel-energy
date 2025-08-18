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
package net.icewheel.energy.integration.web;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

import net.icewheel.energy.api.rest.dto.ScheduleHistoryResponse;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
import net.icewheel.energy.domain.energy.model.ScheduleAuditEvent;
import net.icewheel.energy.domain.energy.model.ScheduleExecutionHistory;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleAuditEventRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SchedulePageControllerSecurityIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ScheduleExecutionHistoryRepository executionHistoryRepository;

	@Autowired
	private ScheduleAuditEventRepository auditEventRepository;

	@Autowired
	private net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository scheduleRepository;

	@MockBean
	private TokenService tokenService;

	private User userA;
	private User userB;

	@BeforeEach
	void setup() {
		auditEventRepository.deleteAll();
		scheduleRepository.deleteAll();
		executionHistoryRepository.deleteAll();
		userRepository.deleteAll();
		when(tokenService.isUserConnected(any())).thenReturn(true);

		userA = new User();
		userA.setId("user-a-id");
		userA.setEmail("a@example.com");
		userA.setName("Alice");
		userRepository.save(userA);

		userB = new User();
		userB.setId("user-b-id");
		userB.setEmail("b@example.com");
		userB.setName("Bob");
		userRepository.save(userB);

		// Why: Create a realistic data model. History events must be linked to an actual schedule.
		// This prevents potential persistence errors caused by orphaned records.
		PowerwallSchedule scheduleA1 = buildSchedule(userA, "Test-A-Group-1");
		PowerwallSchedule scheduleA2 = buildSchedule(userA, "Test-A-Group-2");
		PowerwallSchedule scheduleB1 = buildSchedule(userB, "B-Group-1");
		scheduleRepository.saveAll(java.util.List.of(scheduleA1, scheduleA2, scheduleB1));

		// Seed execution history: 2 for A, 1 for B
		executionHistoryRepository.save(buildExec(scheduleA1));
		executionHistoryRepository.save(buildExec(scheduleA2));
		executionHistoryRepository.save(buildExec(scheduleB1));

		// Seed audit events: 2 for A, 1 for B
		auditEventRepository.save(buildAudit(scheduleA1, "A-Name-1"));
		auditEventRepository.save(buildAudit(scheduleA1, "A-Name-2")); // Can link to same schedule
		auditEventRepository.save(buildAudit(scheduleB1, "B-Name-1"));
	}

	@Test
	void scheduleHistoryPage_showsOnlyAuthenticatedUsersData() throws Exception {
		// GIVEN: A logged-in user (userA)
		SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor userALogin =
				SecurityMockMvcRequestPostProcessors.oauth2Login()
						.attributes(attrs -> {
							attrs.put("email", userA.getEmail());
							attrs.put("name", userA.getName());
							attrs.put("zoneinfo", "UTC");
						});

		// WHEN: The user accesses the schedule history page
		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		// This prevents Thymeleaf from trying to create a session late in the lifecycle, which causes the "response committed" error.
		MvcResult result = mockMvc.perform(get("/schedules/history").with(userALogin).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("schedule-history"))
				.andReturn();

		// THEN: The model should contain a page with only that user's audit events
		Object historyPageObj = result.getModelAndView().getModel().get("historyPage");
		assertThat(historyPageObj).isInstanceOf(Page.class);

		@SuppressWarnings("unchecked")
		Page<ScheduleHistoryResponse> historyPage = (Page<ScheduleHistoryResponse>) historyPageObj;

		assertThat(historyPage.getTotalElements()).isEqualTo(2);
		assertThat(historyPage.getContent())
				.extracting(ScheduleHistoryResponse::getScheduleName)
				.containsExactlyInAnyOrder("A-Name-1", "A-Name-2");
	}

	@Test
	void scheduleExecutionHistoryPage_showsOnlyAuthenticatedUsersData() throws Exception {
		// GIVEN: A logged-in user (userA)
		SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor userALogin =
				SecurityMockMvcRequestPostProcessors.oauth2Login()
						.attributes(attrs -> {
							attrs.put("email", userA.getEmail());
							attrs.put("name", userA.getName());
							attrs.put("zoneinfo", "UTC");
						});

		// WHEN: The user accesses the schedule execution history page
		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		// This prevents Thymeleaf from trying to create a session late in the lifecycle, which causes the "response committed" error.
		MvcResult result = mockMvc.perform(get("/schedules/executions").with(userALogin).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("schedule-execution-history"))
				.andReturn();

		// THEN: The model should contain a page with only that user's execution events
		Object executionPageObj = result.getModelAndView().getModel().get("executionPage");
		assertThat(executionPageObj).isInstanceOf(Page.class);
		@SuppressWarnings("unchecked")
		Page<ScheduleHistoryResponse> executionPage = (Page<ScheduleHistoryResponse>) executionPageObj;
		assertThat(executionPage.getTotalElements()).isEqualTo(2);
		assertThat(executionPage.getContent())
				.extracting(ScheduleHistoryResponse::getScheduleName)
				.containsExactlyInAnyOrder("Test-A-Group-1", "Test-A-Group-2");
	}

	private ScheduleExecutionHistory buildExec(PowerwallSchedule schedule) {
		ScheduleExecutionHistory e = new ScheduleExecutionHistory();
		// Why: The ID is auto-generated by the database (@GeneratedValue). Manually setting it
		// confuses JPA, leading to an ObjectOptimisticLockingFailureException. Removing this allows JPA to correctly persist it as a new entity.
		e.setScheduleId(schedule.getId());
		e.setScheduleGroupId(schedule.getScheduleGroupId());
		e.setScheduleName(schedule.getName());
		e.setUserId(schedule.getUser().getId());
		e.setExecutionTime(Instant.now());
		e.setStatus(ScheduleExecutionHistory.ExecutionStatus.SUCCESS);
		e.setDetails("ok");
		e.setCronExpression("0 0 * * * ?");
		e.setCronDescription("hourly");
		e.setExecutionType(ScheduleExecutionHistory.ExecutionType.REGULAR);
		return e;
	}

	private ScheduleAuditEvent buildAudit(PowerwallSchedule schedule, String name) {
		ScheduleAuditEvent ev = new ScheduleAuditEvent();
		// Why: The ID is auto-generated by the database (@GeneratedValue). Manually setting it
		// confuses JPA, leading to an ObjectOptimisticLockingFailureException. Removing this allows JPA to correctly persist it as a new entity.
		ev.setScheduleGroupId(schedule.getScheduleGroupId());
		ev.setUser(schedule.getUser());
		ev.setScheduleName(name);
		ev.setAction(ScheduleAuditEvent.AuditAction.UPDATED);
		ev.setTimestamp(Instant.now());
		ev.setDetails(java.util.Map.of("field", "value"));
		return ev;
	}

	private PowerwallSchedule buildSchedule(User user, String name) {
		PowerwallSchedule s = new PowerwallSchedule();
		s.setId(UUID.randomUUID());
		s.setScheduleGroupId(UUID.randomUUID());
		s.setUser(user);
		s.setName(name);
		s.setDaysOfWeek(java.util.Set.of(DayOfWeek.MONDAY));
		// Why: The entity has several non-nullable fields. This ensures the test data is valid
		// and can be persisted without violating database constraints, fixing the DataIntegrityViolationException.
		s.setEnergySiteId("12345");
		s.setTimeZone(java.time.ZoneId.of("UTC"));
		s.setEnabled(true);
		s.setEventType(net.icewheel.energy.domain.energy.model.ScheduleEventType.START_DISCHARGE);
		s.setScheduledTime(java.time.LocalTime.of(17, 0));
		s.setBackupPercent(20);
		s.setCronExpression("0 0 17 ? * MON");
		return s;
	}
}
