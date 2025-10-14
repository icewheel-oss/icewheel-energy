package net.icewheel.energy.infrastructure.modules.weather.configuration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.weather.nws")
@Data
public class NwsProperties {

    private String userAgent = "(MyWeatherApp, contact@myweatherapp.com)";
    private int dailyQuota = 1000;
    private String baseUrl = "https://api.weather.gov";
    private int rateLimit = 20;
}
