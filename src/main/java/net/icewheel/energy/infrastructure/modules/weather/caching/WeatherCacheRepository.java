package net.icewheel.energy.infrastructure.modules.weather.caching;

import java.util.Optional;

import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WeatherCacheRepository extends JpaRepository<WeatherCache, Long> {

    Optional<WeatherCache> findFirstByLatitudeAndLongitudeAndProviderOrderByTimestampDesc(
            double latitude, double longitude, WeatherProviderType provider);
}