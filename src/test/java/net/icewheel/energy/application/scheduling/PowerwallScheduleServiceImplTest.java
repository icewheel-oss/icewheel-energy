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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.exception.ScheduleImportException;
import net.icewheel.energy.domain.energy.model.PowerwallSchedule;
import net.icewheel.energy.domain.energy.model.ReconciliationMode;
import net.icewheel.energy.domain.energy.model.ScheduleAuditEvent;
import net.icewheel.energy.domain.energy.model.ScheduleEventType;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleAuditEventRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PowerwallScheduleServiceImplTest {

	@Mock
	private PowerwallScheduleRepository scheduleRepository;
	@Mock
	private ScheduleExecutionHistoryRepository historyRepository;
	@Mock
	private ScheduleAuditEventRepository auditEventRepository;
	@Mock
	private Validator validator;
	@Mock
	private TeslaEnergyService teslaEnergyService;

	@InjectMocks
	private PowerwallScheduleServiceImpl scheduleService;

	private User testUser;
	private Product testProduct;

	@BeforeEach
	void setUp() {
		testUser = new User();
		testUser.setId("user123");

		testProduct = new Product();
		testProduct.setEnergySiteId("987654321");
		testProduct.setSiteName("Home Powerwall");
	}

	@Nested
	@DisplayName("Export Schedules")
	class ExportSchedulesTests {

		@Test
		@DisplayName("Should correctly map schedules for export and exclude sensitive data")
		void getSchedulesForExport_Success() {
			// Given
			ScheduleResponse scheduleResponse = createTestScheduleResponse("12345");
			when(scheduleRepository.findAllByUser(testUser)).thenReturn(createTestScheduleList(scheduleResponse.getEnergySiteId()));

			// When
			List<ScheduleRequest> exportData = scheduleService.getSchedulesForExport(testUser);

			// Then
			assertThat(exportData).hasSize(1);
			ScheduleRequest exportedRequest = exportData.getFirst();

			// Crucial Security Check: Ensure environment-specific and user-specific data is NOT exported.
			assertThat(exportedRequest.getId()).isNull();
			assertThat(exportedRequest.getEnergySiteId()).isNull();

			// Verify other fields are mapped correctly
			assertThat(exportedRequest.getName()).isEqualTo("Existing Schedule");
			assertThat(exportedRequest.getOnPeakBackupPercent()).isEqualTo(25);
			assertThat(exportedRequest.getOffPeakBackupPercent()).isEqualTo(75);
		}
	}

	@Nested
	@DisplayName("Import Schedules")
	class ImportSchedulesTests {

		@Test
		@DisplayName("Should successfully import valid schedules")
		void importSchedules_Success() {
			// Given
			ScheduleRequest requestToImport = createTestScheduleRequest();
			when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(List.of(testProduct));
			when(scheduleRepository.findAllByUser(testUser)).thenReturn(Collections.emptyList());
			when(validator.validate(any(ScheduleRequest.class))).thenReturn(Collections.emptySet());

			// When
			ImportResult result = scheduleService.importSchedules(List.of(requestToImport), testUser);

			// Then
			assertThat(result).isNotNull();
			assertThat(result.getImportedCount()).isEqualTo(1);
			assertThat(result.getSkippedDuplicateNames()).isEmpty();
			assertThat(result.getSkippedDuplicateContent()).isEmpty();
			verify(scheduleRepository).saveAll(anyList());

			ArgumentCaptor<ScheduleAuditEvent> auditCaptor = ArgumentCaptor.forClass(ScheduleAuditEvent.class);
			verify(auditEventRepository).save(auditCaptor.capture());
			assertThat(auditCaptor.getValue().getDetails()).containsEntry("info", "New schedule period imported.");
		}

		@Test
		@DisplayName("Should fail if no schedulable energy sites are found")
		void importSchedules_FailsWhenNoSitesFound() {
			// Given
			ScheduleRequest requestToImport = createTestScheduleRequest();
			when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(Collections.emptyList());

			// When & Then
			assertThatThrownBy(() -> scheduleService.importSchedules(List.of(requestToImport), testUser))
					.isInstanceOf(ScheduleImportException.class)
					.hasMessage("Import failed: No schedulable Powerwall energy sites found on your Tesla account.");
		}

		@Test
		@DisplayName("Should skip duplicate schedules and import new ones")
		void importSchedules_SkipsDuplicatesAndImportsNew() {
			// Given
			ScheduleRequest duplicateRequest = createTestScheduleRequest();
			duplicateRequest.setName("Existing Schedule"); // This one should be skipped

			ScheduleRequest newRequest = createTestScheduleRequest();
			newRequest.setName("New Schedule"); // This one should be imported

			when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(List.of(testProduct));
			when(scheduleRepository.findAllByUser(testUser)).thenReturn(createTestScheduleList("12345")); // Simulate existing schedules
			when(validator.validate(any(ScheduleRequest.class))).thenReturn(Collections.emptySet());

			// When
			ImportResult result = scheduleService.importSchedules(List.of(duplicateRequest, newRequest), testUser);

			// Then
			assertThat(result.getImportedCount()).isEqualTo(1);
			assertThat(result.getSkippedDuplicateNames()).containsExactly("Existing Schedule");
			assertThat(result.getSkippedDuplicateContent()).isEmpty();

			// Verify that saveAll was called exactly once (for the new schedule)
			verify(scheduleRepository, times(1)).saveAll(anyList());
			ArgumentCaptor<ScheduleAuditEvent> auditCaptor = ArgumentCaptor.forClass(ScheduleAuditEvent.class);
			verify(auditEventRepository).save(auditCaptor.capture());
			assertThat(auditCaptor.getValue().getScheduleName()).isEqualTo("New Schedule");
		}

		@Test
		@DisplayName("Should skip schedules with duplicate content but different names")
		void importSchedules_SkipsContentDuplicates() {
			// Given
			// This request has the same content as the one from createTestScheduleResponse()
			ScheduleRequest contentDuplicateRequest = createTestScheduleRequest();
			contentDuplicateRequest.setName("A Brand New Name");
			contentDuplicateRequest.setDaysOfWeek(Set.of(DayOfWeek.TUESDAY));
			contentDuplicateRequest.setStartTime(LocalTime.of(8, 0));
			contentDuplicateRequest.setEndTime(LocalTime.of(12, 0));
			contentDuplicateRequest.setOnPeakBackupPercent(25);
			contentDuplicateRequest.setOffPeakBackupPercent(75);

			when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(List.of(testProduct));
			when(scheduleRepository.findAllByUser(testUser)).thenReturn(createTestScheduleList("12345")); // Simulate existing schedules

			// When
			ImportResult result = scheduleService.importSchedules(List.of(contentDuplicateRequest), testUser);

			// Then
			assertThat(result.getImportedCount()).isZero();
			assertThat(result.getSkippedDuplicateNames()).isEmpty();
			assertThat(result.getSkippedDuplicateContent()).containsExactly("A Brand New Name");
		}

		@Test
		@DisplayName("Should fail if validation fails for any schedule")
		void importSchedules_FailsOnValidationError() {
			// Given
			ScheduleRequest requestToImport = createTestScheduleRequest();
			requestToImport.setOnPeakBackupPercent(999); // Invalid value

			when(teslaEnergyService.getSchedulableEnergySites(testUser.getId())).thenReturn(List.of(testProduct));
			when(scheduleRepository.findAllByUser(testUser)).thenReturn(Collections.emptyList());

			// Mock the validator to return a violation
			@SuppressWarnings("unchecked")
			ConstraintViolation<ScheduleRequest> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
			Path propertyPath = org.mockito.Mockito.mock(Path.class);
			when(propertyPath.toString()).thenReturn("onPeakBackupPercent");
			when(violation.getPropertyPath()).thenReturn(propertyPath);
			when(violation.getMessage()).thenReturn("must be less than or equal to 80");
			when(validator.validate(any(ScheduleRequest.class))).thenReturn(Set.of(violation));

			// When & Then
			assertThatThrownBy(() -> scheduleService.importSchedules(List.of(requestToImport), testUser))
					.isInstanceOf(ScheduleImportException.class)
					.hasMessageContaining("is invalid: onPeakBackupPercent must be less than or equal to 80");
		}

		@Test
		@DisplayName("Should fail if the number of schedules exceeds the limit")
		void importSchedules_FailsWhenTooManySchedules() {
			// Given
			List<ScheduleRequest> tooManySchedules = IntStream.range(0, 101)
					.mapToObj(i -> createTestScheduleRequest())
					.toList();

			// When & Then
			assertThatThrownBy(() -> scheduleService.importSchedules(tooManySchedules, testUser))
					.isInstanceOf(ScheduleImportException.class)
					.hasMessage("Import failed: A maximum of 100 schedules can be imported at one time.");
		}

		@Test
		@DisplayName("Should do nothing for a null or empty list")
		void importSchedules_HandlesNullOrEmptyList() {
			// When
			ImportResult result1 = scheduleService.importSchedules(null, testUser);
			ImportResult result2 = scheduleService.importSchedules(Collections.emptyList(), testUser);

			// Then
			assertThat(result1.getImportedCount()).isZero();
			assertThat(result1.getSkippedDuplicateNames()).isEmpty();
			assertThat(result1.getSkippedDuplicateContent()).isEmpty();
			assertThat(result2.getImportedCount()).isZero();
			assertThat(result2.getSkippedDuplicateNames()).isEmpty();
			assertThat(result2.getSkippedDuplicateContent()).isEmpty();

			verify(teslaEnergyService, never()).getSchedulableEnergySites(any());
			verify(scheduleRepository, never()).saveAll(any());
		}
	}

	// --- Helper Methods ---

	private ScheduleRequest createTestScheduleRequest() {
		ScheduleRequest request = new ScheduleRequest();
		request.setName("Test Schedule");
		request.setDescription("A test description");
		request.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
		request.setStartTime(LocalTime.of(17, 0));
		request.setEndTime(LocalTime.of(21, 0));
		request.setTimeZone("America/New_York");
		request.setOnPeakBackupPercent(20);
		request.setOffPeakBackupPercent(80);
		request.setEnabled(true);
		request.setReconciliationMode(ReconciliationMode.CONTINUOUS);
		// Note: energySiteId is intentionally left null, as it should be ignored during import.
		return request;
	}

	private ScheduleResponse createTestScheduleResponse(String energySiteId) {
		ScheduleResponse response = new ScheduleResponse();
		response.setScheduleGroupId(UUID.randomUUID());
		response.setName("Existing Schedule");
		response.setEnergySiteId(energySiteId);
		response.setDaysOfWeek(Set.of(DayOfWeek.TUESDAY));
		response.setStartTime(LocalTime.of(8, 0));
		response.setEndTime(LocalTime.of(12, 0));
		response.setTimeZone(ZoneId.of("America/New_York"));
		response.setOnPeakBackupPercent(25);
		response.setOffPeakBackupPercent(75);
		response.setEnabled(true);
		response.setReconciliationMode(ReconciliationMode.CONTINUOUS);
		return response;
	}

	private List<PowerwallSchedule> createTestScheduleList(String energySiteId) {
		UUID groupId = UUID.randomUUID();
		PowerwallSchedule start = new PowerwallSchedule();
		start.setScheduleGroupId(groupId);
		start.setName("Existing Schedule");
		start.setEnergySiteId(energySiteId);
		start.setEventType(ScheduleEventType.START_DISCHARGE);
		start.setScheduledTime(LocalTime.of(8, 0));
		start.setBackupPercent(25);
		start.setTimeZone(ZoneId.of("America/New_York"));
		start.setDaysOfWeek(Set.of(DayOfWeek.TUESDAY));
		start.setEnabled(true);
		start.setReconciliationMode(ReconciliationMode.CONTINUOUS);

		PowerwallSchedule end = new PowerwallSchedule();
		end.setScheduleGroupId(groupId);
		end.setName("Existing Schedule");
		end.setEnergySiteId(energySiteId);
		end.setEventType(ScheduleEventType.START_CHARGE);
		end.setScheduledTime(LocalTime.of(12, 0));
		end.setBackupPercent(75);
		end.setTimeZone(ZoneId.of("America/New_York"));
		end.setDaysOfWeek(Set.of(DayOfWeek.TUESDAY));
		end.setEnabled(true);
		end.setReconciliationMode(ReconciliationMode.CONTINUOUS);

		return List.of(start, end);
	}
}