package net.icewheel.energy.infrastructure.modules.weather.configuration;

import java.time.Duration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.weather")
@Data
public class WeatherProperties {

    private ZipToLatLon zipToLatLon = new ZipToLatLon();
    private Duration cacheTtl = Duration.ofHours(3);
    private boolean cacheEnabled = true;

    @Data
    public static class ZipToLatLon {
        private boolean enabled;
    }
}
