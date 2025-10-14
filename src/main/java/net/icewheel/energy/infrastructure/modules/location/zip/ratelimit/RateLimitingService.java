package net.icewheel.energy.infrastructure.modules.location.zip.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key, int capacity, int refillTokens, Duration refillDuration) {
        return cache.computeIfAbsent(key, k -> createNewBucket(capacity, refillTokens, refillDuration));
    }

    private Bucket createNewBucket(int capacity, int refillTokens, Duration refillDuration) {
        Refill refill = Refill.intervally(refillTokens, refillDuration);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
