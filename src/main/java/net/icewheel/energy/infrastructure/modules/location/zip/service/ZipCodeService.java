package net.icewheel.energy.infrastructure.modules.location.zip.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.AppUserService;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.location.zip.configuration.CacheConfig;
import net.icewheel.energy.infrastructure.modules.location.zip.model.Place;
import net.icewheel.energy.infrastructure.modules.location.zip.model.ZipCodeCache;
import net.icewheel.energy.infrastructure.modules.location.zip.model.ZippopotamResponse;
import net.icewheel.energy.infrastructure.modules.location.zip.ratelimit.RateLimitingService;
import net.icewheel.energy.infrastructure.modules.location.zip.repository.ZipCodeCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ZipCodeService {

    private static final Logger log = LoggerFactory.getLogger(ZipCodeService.class);
    private static final Duration FAILED_LOOKUP_RETRY_DURATION = Duration.ofDays(1);
    private static final Duration SUCCESSFUL_LOOKUP_EXPIRATION = Duration.ofDays(30);

    private final ZipCodeCacheRepository zipCodeCacheRepository;
    private final RestClient restClient;
    private final RateLimitingService rateLimitingService;
    private final AppUserService appUserService;

    @Value("${app.weather.zip-code-rate-limit.capacity}")
    private int capacity;

    @Value("${app.weather.zip-code-rate-limit.refill-tokens}")
    private int refillTokens;

    @Value("${app.weather.zip-code-rate-limit.refill-duration-minutes}")
    private int refillDurationMinutes;

    /**
     * Retrieves the coordinates for a given zip code.
     * <p>
     * This method is suitable for user-facing web contexts, where the current user can be
     * resolved from the security context for rate-limiting purposes. It delegates to the
     * more explicit {@link #getCoordinates(String, User)} method.
     *
     * @param zipCode The zip code to look up.
     * @return An {@link Optional} containing a list of {@link ZipCodeCache} entries.
     */
    @Transactional
    public Optional<List<ZipCodeCache>> getCoordinates(String zipCode) {
        // This method is suitable for user-facing web contexts, where the user can be
        // resolved from the security context.
        User user = appUserService.getCurrentUser().orElse(null);
        return getCoordinates(zipCode, user);
    }

    /**
     * Retrieves the coordinates for a given zip code, with an explicit user for context.
     * <p>
     * This method is suitable for background jobs or other non-web contexts where the
     * user is known but not present in the SecurityContext. It is backed by a cache
     * ({@link CacheConfig#ZIP_COORDINATES_CACHE}) to minimize external API calls.
     *
     * @param zipCode The zip code to look up.
     * @param user The user performing the lookup, for rate-limiting purposes. Can be null, in which case rate limiting is applied to an "anonymous" user.
     * @return An {@link Optional} containing a list of {@link ZipCodeCache} entries.
     */
    @Transactional
    @Cacheable(value = CacheConfig.ZIP_COORDINATES_CACHE, key = "{#zipCode, #user != null ? #user.id : 'anonymous'}")
    public Optional<List<ZipCodeCache>> getCoordinates(String zipCode, User user) {
        if (zipCode == null || zipCode.isBlank()) {
            return Optional.empty();
        }

        List<ZipCodeCache> cached = zipCodeCacheRepository.findByZipCodeAndActiveTrue(zipCode);
        if (!cached.isEmpty()) {
            ZipCodeCache firstEntry = cached.getFirst();
            if (firstEntry.isLookupSuccessful()) {
                if (Duration.between(firstEntry.getLastLookup(), Instant.now()).compareTo(SUCCESSFUL_LOOKUP_EXPIRATION) < 0) {
                    log.info("Found fresh successful zip code lookup in DB cache for {}", zipCode);
                return Optional.of(cached);
                } else {
                    log.info("Found stale successful zip code lookup in DB cache for {}. Refreshing.", zipCode);
                }
            } else if (Duration.between(firstEntry.getLastLookup(), Instant.now()).compareTo(FAILED_LOOKUP_RETRY_DURATION) < 0) {
                log.warn("Found failed zip code lookup in cache for {}. Will not retry yet.", zipCode);
                return Optional.empty();
            }
        }

        log.info("No valid cache entry for zip code {}. Fetching from external API.", zipCode);
        return fetchFromApiAndCache(zipCode, user);
    }

    private Optional<List<ZipCodeCache>> fetchFromApiAndCache(String zipCode, User user) {
        String userId = (user != null) ? user.getId() : "anonymous";
        Bucket bucket = rateLimitingService.resolveBucket(userId, capacity, refillTokens, Duration.ofMinutes(refillDurationMinutes));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("User {} has exceeded the rate limit for zip code lookups. Try again in {} seconds.", userId, waitForRefill);
            throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "You have exhausted your API request quota. Please try again later.");
        }

        String url = "https://api.zippopotam.us/us/" + zipCode;
        Instant lookupTime = Instant.now();

        try {
            ZippopotamResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(ZippopotamResponse.class);

            if (response == null || response.getPlaces() == null || response.getPlaces().isEmpty()) {
                log.warn("Received empty or invalid response from zippopotam API for zip code {}", zipCode);
                cacheFailedLookup(zipCode, lookupTime);
                return Optional.empty();
            }

            // Perform an "upsert" and "soft delete" logic to maintain audit history
            List<ZipCodeCache> existingEntries = zipCodeCacheRepository.findByZipCode(zipCode);
            Map<String, ZipCodeCache> existingMap = existingEntries.stream()
                    .collect(Collectors.toMap(ZipCodeCache::getPlaceName, e -> e));

            List<ZipCodeCache> entriesToSave = new ArrayList<>();
            Set<String> activePlaceNames = new HashSet<>();

            for (Place place : response.getPlaces()) {
                activePlaceNames.add(place.getPlaceName());
                ZipCodeCache entry = existingMap.getOrDefault(place.getPlaceName(), null);
                if (entry != null) {
                    // Update existing entry
                    entriesToSave.add(updateEntryFromPlace(entry, response, place, lookupTime));
                } else {
                    // Create new entry
                    entriesToSave.add(toZipCodeCache(response, place, lookupTime));
                }
            }

            // Mark old entries that are no longer present as inactive
            existingEntries.stream()
                    .filter(entry -> !activePlaceNames.contains(entry.getPlaceName()))
                    .forEach(entry -> entriesToSave.add(markEntryAsInactive(entry, lookupTime)));

            return Optional.of(zipCodeCacheRepository.saveAll(entriesToSave));

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Zippopotam API returned 404 for zip code {}. Caching as failed lookup.", zipCode);
            cacheFailedLookup(zipCode, lookupTime);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching from zippopotam API for zip code {}", zipCode, e);
            cacheFailedLookup(zipCode, lookupTime);
            return Optional.empty();
        }
    }

    private void cacheFailedLookup(String zipCode, Instant lookupTime) {
        // Mark all existing entries for this zip code as inactive to preserve history
        List<ZipCodeCache> existingEntries = zipCodeCacheRepository.findByZipCode(zipCode);
        if (!existingEntries.isEmpty()) {
            existingEntries.forEach(entry -> markEntryAsInactive(entry, lookupTime));
            zipCodeCacheRepository.saveAll(existingEntries);
        }

        ZipCodeCache failedCache = ZipCodeCache.builder()
                .zipCode(zipCode)
                .lastLookup(lookupTime)
                .lookupSuccessful(false)
                .build();
        zipCodeCacheRepository.save(failedCache);
    }

    private ZipCodeCache toZipCodeCache(ZippopotamResponse response, Place place, Instant lookupTime) {
        return ZipCodeCache.builder()
                .zipCode(response.getPostCode())
                .country(response.getCountry())
                .countryAbbreviation(response.getCountryAbbreviation())
                .placeName(place.getPlaceName())
                .longitude(Double.valueOf(place.getLongitude()))
                .latitude(Double.valueOf(place.getLatitude()))
                .state(place.getState())
                .stateAbbreviation(place.getStateAbbreviation())
                .lastLookup(lookupTime)
                .lookupSuccessful(true)
                .build();
    }

    private ZipCodeCache updateEntryFromPlace(ZipCodeCache entry, ZippopotamResponse response, Place place, Instant lookupTime) {
        entry.setCountry(response.getCountry());
        entry.setCountryAbbreviation(response.getCountryAbbreviation());
        entry.setLatitude(Double.valueOf(place.getLatitude()));
        entry.setLongitude(Double.valueOf(place.getLongitude()));
        entry.setState(place.getState());
        entry.setStateAbbreviation(place.getStateAbbreviation());
        entry.setLastLookup(lookupTime);
        entry.setLookupSuccessful(true);
        entry.setActive(true); // Reactivate if it was previously inactive
        return entry;
    }

    private ZipCodeCache markEntryAsInactive(ZipCodeCache entry, Instant lookupTime) {
        entry.setActive(false);
        entry.setLastLookup(lookupTime);
        return entry;
    }
}
