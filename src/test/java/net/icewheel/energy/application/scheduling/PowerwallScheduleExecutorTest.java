package net.icewheel.energy.application.scheduling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleExecutionHistoryRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link PowerwallScheduleExecutor} class.
 */
@ExtendWith(MockitoExtension.class)
class PowerwallScheduleExecutorTest {

    @Mock
    private PowerwallScheduleRepository scheduleRepository;
    @Mock
    private ScheduleExecutionHistoryRepository historyRepository;
    @Mock
    private TeslaEnergyService teslaEnergyService;
    @Mock
    private UserRepository userRepository;

    private CronParser cronParser;
    private Clock clock;
    private PowerwallScheduleExecutor executor;

    @BeforeEach
    void setUp() {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        cronParser = new CronParser(cronDefinition);
        clock = Clock.fixed(Instant.parse("2025-10-06T10:00:00Z"), ZoneId.of("UTC"));
        executor = new PowerwallScheduleExecutor(scheduleRepository, historyRepository, teslaEnergyService, userRepository, cronParser, clock);
    }

    /**
     * Test that nothing happens when there are no schedules.
     */
    @Test
    void testExecuteSchedules_NoSchedules() {
        // Arrange
        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.emptyList());

        // Act
        executor.executeSchedules();

        // Assert
        verify(historyRepository, never()).save(any());
    }

    /**
     * Test that nothing happens when a schedule is not due to run.
     */
    @Test
    void testExecuteSchedules_ScheduleNotDue() {
        // Arrange
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setCronExpression("0 0 12 * * ?"); // Noon every day
        schedule.setUser(new User());
        schedule.setTimeZone(ZoneId.of("UTC"));
        schedule.setEventType(ScheduleEventType.START_CHARGE);

        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.singletonList(schedule));

        // Act
        executor.executeSchedules();

        // Assert
        verify(teslaEnergyService, never()).setBackupReserve(any(), any(), anyInt());
        verify(historyRepository, never()).save(any());
    }

    /**
     * Test that the schedule is executed successfully when it's due.
     */
    @Test
    void testExecuteSchedules_ScheduleDue_Success() {
        // Arrange
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setCronExpression("0 0 10 * * ?"); // 10 AM every day
        schedule.setUser(new User());
        schedule.setTimeZone(ZoneId.of("UTC"));
        schedule.setEventType(ScheduleEventType.START_CHARGE);

        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.singletonList(schedule));
        when(teslaEnergyService.setBackupReserve(any(), any(), anyInt())).thenReturn(true);

        // Act
        executor.executeSchedules();

        // Assert
        verify(teslaEnergyService).setBackupReserve(any(), any(), anyInt());
        verify(historyRepository).save(any());
    }

    /**
     * Test the case where the Tesla API call fails.
     */
    @Test
    void testExecuteSchedules_ScheduleDue_ApiFailure() {
        // Arrange
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setCronExpression("0 0 10 * * ?"); // 10 AM every day
        schedule.setUser(new User());
        schedule.setTimeZone(ZoneId.of("UTC"));
        schedule.setEventType(ScheduleEventType.START_CHARGE);

        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.singletonList(schedule));
        when(teslaEnergyService.setBackupReserve(any(), any(), anyInt())).thenReturn(false);

        // Act
        executor.executeSchedules();

        // Assert
        verify(teslaEnergyService).setBackupReserve(any(), any(), anyInt());
        verify(historyRepository).save(any());
    }

    /**
     * Test the case where `teslaEnergyService.setBackupReserve()` throws an exception.
     */
    @Test
    void testExecuteSchedules_ScheduleDue_Exception() {
        // Arrange
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setCronExpression("0 0 10 * * ?"); // 10 AM every day
        schedule.setUser(new User());
        schedule.setTimeZone(ZoneId.of("UTC"));
        schedule.setEventType(ScheduleEventType.START_CHARGE);

        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.singletonList(schedule));
        when(teslaEnergyService.setBackupReserve(any(), any(), anyInt())).thenThrow(new RuntimeException("API is down"));

        // Act
        executor.executeSchedules();

        // Assert
        verify(teslaEnergyService).setBackupReserve(any(), any(), anyInt());
        verify(historyRepository).save(any());
    }

    /**
     * Test that a temporary schedule is disabled after execution.
     */
    @Test
    void testExecuteSchedules_TemporarySchedule() {
        // Arrange
        PowerwallSchedule schedule = new PowerwallSchedule();
        schedule.setCronExpression("0 0 10 * * ?"); // 10 AM every day
        schedule.setTemporary(true);
        schedule.setUser(new User());
        schedule.setTimeZone(ZoneId.of("UTC"));
        schedule.setEventType(ScheduleEventType.START_CHARGE);
        schedule.getUser().setPreference(new net.icewheel.energy.application.user.model.UserPreference());

        when(scheduleRepository.findAllEnabledWithUser()).thenReturn(Collections.singletonList(schedule));
        when(teslaEnergyService.setBackupReserve(any(), any(), anyInt())).thenReturn(true);

        // Act
        executor.executeSchedules();

        // Assert
        verify(scheduleRepository).save(schedule);
        verify(userRepository).save(schedule.getUser());
        assertFalse(schedule.isEnabled());
    }
}