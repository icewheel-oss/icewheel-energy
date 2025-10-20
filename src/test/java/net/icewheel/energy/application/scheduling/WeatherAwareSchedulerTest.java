package net.icewheel.energy.application.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.application.scheduling.model.ScheduleEventType;
import net.icewheel.energy.application.scheduling.model.ScheduleType;
import net.icewheel.energy.application.scheduling.repository.PowerwallScheduleRepository;
import net.icewheel.energy.application.scheduling.repository.ScheduleAuditEventRepository;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.modules.weather.SolarForecast;
import net.icewheel.energy.infrastructure.modules.weather.WeatherForecastEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeatherAwareSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PowerwallScheduleRepository scheduleRepository;

    @Mock
    private WeatherForecastEvaluator weatherForecastEvaluator;

    @Mock
    private ScheduleAuditEventRepository auditEventRepository;

    private Clock clock;

    private WeatherAwareScheduler weatherAwareScheduler;

    private User user;
    private PowerwallSchedule onPeakSchedule;
    private PowerwallSchedule offPeakSchedule;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user123");
        UserPreference preference = new UserPreference();
        preference.setZipCode("12345");
        user.setPreference(preference);

        onPeakSchedule = new PowerwallSchedule();
        onPeakSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
        onPeakSchedule.setEventType(ScheduleEventType.START_DISCHARGE);
        onPeakSchedule.setTemporary(false);
        onPeakSchedule.setBackupPercent(20);
        onPeakSchedule.setScheduledTime(LocalTime.of(17, 0)); // 5 PM
        onPeakSchedule.setTimeZone(ZoneId.of("UTC"));

        offPeakSchedule = new PowerwallSchedule();
        offPeakSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
        offPeakSchedule.setEventType(ScheduleEventType.START_CHARGE);
        offPeakSchedule.setTemporary(false);
        offPeakSchedule.setBackupPercent(80);
        offPeakSchedule.setTimeZone(ZoneId.of("UTC"));

        clock = Clock.fixed(Instant.parse("2025-10-06T10:00:00Z"), ZoneId.of("UTC"));
        weatherAwareScheduler = new WeatherAwareScheduler(userRepository, scheduleRepository, weatherForecastEvaluator, auditEventRepository, clock);
    }

    @Test
    void testCheckForBadWeather_ScaledAdjustment() {
        // Arrange
        offPeakSchedule.setWeatherScalingFactor(50); // 50% aggressiveness
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule));
        when(weatherForecastEvaluator.evaluate(user)).thenReturn(new SolarForecast(50, "Cloudy", Map.of())); // 50% sunshine -> 50% shortfall

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        ArgumentCaptor<PowerwallSchedule> scheduleCaptor = ArgumentCaptor.forClass(PowerwallSchedule.class);
        verify(scheduleRepository, times(2)).save(scheduleCaptor.capture());

        List<PowerwallSchedule> savedSchedules = scheduleCaptor.getAllValues();
        PowerwallSchedule startChargeSchedule = savedSchedules.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_CHARGE)
                .findFirst()
                .orElseThrow();

        // Base is 80. Shortfall is 50. Available to charge is 20.
        // Scaling factor is 50%.
        // Adjustment = (50 / 100.0) * (100.0 - 80) * (50 / 100.0) = 0.5 * 20 * 0.5 = 5
        // Expected target = 80 + 5 = 85
        assertEquals(85, startChargeSchedule.getBackupPercent());
    }

    @Test
    void testCheckForBadWeather_NoAdjustmentNeeded() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule));
        when(weatherForecastEvaluator.evaluate(user)).thenReturn(new SolarForecast(98, "Sunny", Map.of())); // 2% shortfall

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void testCheckForBadWeather_ForcedChargeAlreadyActive_HigherTarget() {
        // Arrange
        user.getPreference().setForcedChargingActive(true);
        offPeakSchedule.setWeatherScalingFactor(100); // 100% aggressiveness

        PowerwallSchedule existingTempSchedule = new PowerwallSchedule();
        existingTempSchedule.setTemporary(true);
        existingTempSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
        existingTempSchedule.setEventType(ScheduleEventType.START_CHARGE);
        existingTempSchedule.setEnabled(true);
        existingTempSchedule.setBackupPercent(85);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule, existingTempSchedule));
        when(weatherForecastEvaluator.evaluate(user)).thenReturn(new SolarForecast(0, "Stormy", Map.of())); // 100% shortfall

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        ArgumentCaptor<PowerwallSchedule> scheduleCaptor = ArgumentCaptor.forClass(PowerwallSchedule.class);
        verify(scheduleRepository, times(2)).save(scheduleCaptor.capture());

        List<PowerwallSchedule> savedSchedules = scheduleCaptor.getAllValues();
        PowerwallSchedule startChargeSchedule = savedSchedules.stream()
                .filter(s -> s.getEventType() == ScheduleEventType.START_CHARGE)
                .findFirst()
                .orElseThrow();

        // Base is 80. Shortfall is 100. Available is 20. Adjustment is 100% of (100% of 20) = 20
        // Expected target = 80 + 20 = 100, but capped at 90.
        assertEquals(90, startChargeSchedule.getBackupPercent());
    }

    @Test
    void testCheckForBadWeather_ForcedChargeAlreadyActive_LowerTarget() {
        // Arrange
        user.getPreference().setForcedChargingActive(true);
        offPeakSchedule.setWeatherScalingFactor(50);

        PowerwallSchedule existingTempSchedule = new PowerwallSchedule();
        existingTempSchedule.setTemporary(true);
        existingTempSchedule.setScheduleType(ScheduleType.WEATHER_AWARE);
        existingTempSchedule.setEventType(ScheduleEventType.START_CHARGE);
        existingTempSchedule.setEnabled(true);
        existingTempSchedule.setBackupPercent(90);

        lenient().when(userRepository.findAll()).thenReturn(List.of(user));
        lenient().when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule, existingTempSchedule));
        when(weatherForecastEvaluator.evaluate(user)).thenReturn(new SolarForecast(50, "Cloudy", Map.of())); // 50% shortfall -> 85% target

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        // Assert that no new schedule is created, but the evaluation details are updated.
        verify(scheduleRepository, never()).save(any(PowerwallSchedule.class));
        verify(scheduleRepository).saveAll(any());
    }

    @Test
    void testCheckForBadWeather_OnPeak() {
        // Arrange
        onPeakSchedule.setScheduledTime(LocalTime.of(8, 0));
        offPeakSchedule.setScheduledTime(LocalTime.of(20, 0));
        clock = Clock.fixed(Instant.parse("2025-10-06T10:00:00Z"), ZoneId.of("UTC"));
        weatherAwareScheduler = new WeatherAwareScheduler(userRepository, scheduleRepository, weatherForecastEvaluator, auditEventRepository, clock);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule));

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void testCheckForBadWeather_NoLocation() {
        // Arrange
        user.getPreference().setZipCode(null);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule));

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        verify(weatherForecastEvaluator, never()).evaluate(any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void testCheckForBadWeather_Cleanup() {
        // Arrange
        PowerwallSchedule expiredTempSchedule = new PowerwallSchedule();
        expiredTempSchedule.setTemporary(true);
        expiredTempSchedule.setEnabled(false); // Disabled schedules are considered expired

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(scheduleRepository.findAllByUser(user)).thenReturn(List.of(onPeakSchedule, offPeakSchedule, expiredTempSchedule));
        when(weatherForecastEvaluator.evaluate(user)).thenReturn(new SolarForecast(100, "Sunny", Map.of())); // Not relevant for this test

        // Act
        weatherAwareScheduler.checkForBadWeather();

        // Assert
        verify(scheduleRepository).deleteAll(List.of(expiredTempSchedule));
    }
}
