package net.icewheel.energy.api.rest.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.infrastructure.modules.weather.WeatherService;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class WeatherRestController {

    private final WeatherService weatherService;

    @GetMapping("/providers")
    public List<String> getWeatherProviders() {
        return Arrays.stream(WeatherProviderType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @GetMapping("/forecast")
    public ResponseEntity<?> getWeatherForecast(@RequestParam String zipCode) {
        log.info("getWeatherForecast called with zipCode: {}", zipCode);
        if (zipCode == null || zipCode.isBlank()) {
            return ResponseEntity.badRequest().body("A zip code is required.");
        }

        return weatherService.getForecastByZipCode(zipCode)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().body("Invalid or unsupported zip code."));
    }
}