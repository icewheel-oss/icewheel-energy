package net.icewheel.energy.infrastructure.modules.location.zip.configuration;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ZIP_COORDINATES_CACHE = "zip-coordinates";
    public static final String ZIP_CODE_RATE_LIMIT_CACHE = "zip-code-rate-limit";

    @Bean
    public CacheManager cacheManager() {
        // A single CacheManager bean that is aware of all application caches.
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Register the zip code cache with its specific configuration.
        cacheManager.registerCustomCache(ZIP_COORDINATES_CACHE, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(1))
                .maximumSize(500)
                .recordStats()
                .build());

        cacheManager.registerCustomCache(ZIP_CODE_RATE_LIMIT_CACHE, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(1000)
                .recordStats()
                .build());

        return cacheManager;
    }
}
