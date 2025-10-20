package net.icewheel.energy.application.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.application.scheduling.exception.ScheduleNotFoundException;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ReconciliationMode;
import net.icewheel.energy.application.scheduling.model.ScheduleAuditEvent;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.model.ScheduleType;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleAuditEventRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class PowerwallScheduleServiceImplTest {

    @Mock
    private PowerwallScheduleRepository scheduleRepository;

    @Mock
    private ScheduleAuditEventRepository auditEventRepository;

    @Mock
    private TeslaEnergyService teslaEnergyService;

    @Mock
    private Validator validator;

    @InjectMocks
    private PowerwallScheduleServiceImpl scheduleService;

    private User user;
    private ScheduleRequest scheduleRequest;
    private PowerwallSchedule startDischargeSchedule;
    private PowerwallSchedule startChargeSchedule;
    private UUID scheduleGroupId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user123");
        
        scheduleGroupId = UUID.randomUUID();

        scheduleRequest = new ScheduleRequest();
        scheduleRequest.setEnergySiteId("987654321");
        scheduleRequest.setName("Test Schedule");
        scheduleRequest.setStartTime(LocalTime.of(22, 0));
        scheduleRequest.setEndTime(LocalTime.of(6, 0));
        scheduleRequest.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        scheduleRequest.setOnPeakBackupPercent(20);
        scheduleRequest.setOffPeakBackupPercent(80);
        scheduleRequest.setScheduleType(ScheduleType.BASIC);
        scheduleRequest.setTimeZone("UTC");
        scheduleRequest.setEnabled(true);
        scheduleRequest.setReconciliationMode(ReconciliationMode.CONTINUOUS);

        startDischargeSchedule = new PowerwallSchedule();
        startDischargeSchedule.setScheduleGroupId(scheduleGroupId);
        startDischargeSchedule.setUser(user);
        startDischargeSchedule.setName("Existing Schedule");
        startDischargeSchedule.setEventType(ScheduleEventType.START_DISCHARGE);
        startDischargeSchedule.setTemporary(false);
        startDischargeSchedule.setScheduledTime(LocalTime.of(22, 0));
        startDischargeSchedule.setBackupPercent(20);
        startDischargeSchedule.setReconciliationMode(ReconciliationMode.CONTINUOUS);
        startDischargeSchedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        startChargeSchedule = new PowerwallSchedule();
        startChargeSchedule.setScheduleGroupId(scheduleGroupId);
        startChargeSchedule.setUser(user);
        startChargeSchedule.setName("Existing Schedule");
        startChargeSchedule.setEventType(ScheduleEventType.START_CHARGE);
        startChargeSchedule.setTemporary(false);
        startChargeSchedule.setScheduledTime(LocalTime.of(6, 0));
        startChargeSchedule.setBackupPercent(80);
        startChargeSchedule.setReconciliationMode(ReconciliationMode.CONTINUOUS);
    }

    @Test
    void testCreateSchedulePeriod() {
        when(scheduleRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.createSchedulePeriod(scheduleRequest, user);

        assertNotNull(response);
        assertEquals(scheduleRequest.getName(), response.getName());
        verify(scheduleRepository, times(1)).saveAll(any());
        verify(auditEventRepository, times(1)).save(any(ScheduleAuditEvent.class));
    }

    @Test
    void testUpdateSchedulePeriod() {
        when(scheduleRepository.findAllByScheduleGroupId(any(UUID.class)))
                .thenReturn(List.of(startDischargeSchedule, startChargeSchedule));
        when(scheduleRepository.findAllByScheduleGroupId(scheduleGroupId)).thenReturn(List.of(startDischargeSchedule, startChargeSchedule));

        ScheduleResponse response = scheduleService.updateSchedulePeriod(scheduleGroupId, scheduleRequest, user);

        assertNotNull(response);
        assertEquals(scheduleRequest.getName(), response.getName());
        verify(auditEventRepository, times(1)).save(any(ScheduleAuditEvent.class));
    }

    @Test
    void testUpdateSchedulePeriod_NotFound() {
        when(scheduleRepository.findAllByScheduleGroupId(any(UUID.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(ScheduleNotFoundException.class, () ->
                scheduleService.updateSchedulePeriod(UUID.randomUUID(), scheduleRequest, user));
    }

    @Test
    void testDeleteSchedulePeriod() {
        when(scheduleRepository.findAllByScheduleGroupId(any(UUID.class)))
                .thenReturn(List.of(startDischargeSchedule, startChargeSchedule));

        scheduleService.deleteSchedulePeriod(scheduleGroupId, user);

        verify(scheduleRepository, times(1)).deleteAll(any());
        verify(auditEventRepository, times(1)).save(any(ScheduleAuditEvent.class));
    }

    @Test
    void testUpdateScheduleEnabledStatus() {
        when(scheduleRepository.findAllByScheduleGroupId(any(UUID.class)))
                .thenReturn(List.of(startDischargeSchedule, startChargeSchedule));

        scheduleService.updateScheduleEnabledStatus(scheduleGroupId, false, user);

        assertFalse(startDischargeSchedule.isEnabled());
        assertFalse(startChargeSchedule.isEnabled());
        verify(auditEventRepository, times(1)).save(any(ScheduleAuditEvent.class));
    }

    @Test
    void testIsForcedChargeActive_NoPreference() {
        user.setPreference(null);
        boolean isActive = scheduleService.isForcedChargeActive(user);
        assertFalse(isActive);
    }

    @Test
    void testIsForcedChargeActive_PreferenceFalse() {
        UserPreference preferences = new UserPreference();
        preferences.setForcedChargingActive(false);
        user.setPreference(preferences);

        boolean isActive = scheduleService.isForcedChargeActive(user);

        assertFalse(isActive);
    }

    @Test
    void testIsForcedChargeActive_PreferenceTrue() {
        UserPreference preferences = new UserPreference();
        preferences.setForcedChargingActive(true);
        user.setPreference(preferences);

        boolean isActive = scheduleService.isForcedChargeActive(user);

        assertTrue(isActive);
    }

    @Test
    void testFindSchedulesByUser_NoSchedules() {
        when(scheduleRepository.findAllByUser(user)).thenReturn(Collections.emptyList());
        List<ScheduleResponse> responses = scheduleService.findSchedulesByUser(user);
        assertTrue(responses.isEmpty());
    }

    @Test
    void testFindScheduleByGroupId_NotFound() {
        when(scheduleRepository.findAllByScheduleGroupId(any(UUID.class)))
                .thenReturn(Collections.emptyList());
        Optional<ScheduleResponse> response = scheduleService.findScheduleByGroupId(UUID.randomUUID(), user);
        assertTrue(response.isEmpty());
    }
}