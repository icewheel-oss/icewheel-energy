package net.icewheel.energy.infrastructure.modules.weather.caching;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;


@Entity
@Table(name = "weather_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherCache {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private double latitude;

    private double longitude;

    @Enumerated(EnumType.STRING)
    private WeatherProviderType provider;

    @Column(columnDefinition = "TEXT")
    private String response;

    private Instant timestamp;
}