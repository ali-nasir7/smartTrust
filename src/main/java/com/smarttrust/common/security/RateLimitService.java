package com.smarttrust.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure Java rate limiting using Caffeine cache + fixed window counter.
 * No external Bucket4j dependency — avoids import errors in VS Code / offline builds.
 * Enterprise: Simple, testable, works for FYP load.
 */
@Slf4j
@Service
public class RateLimitService {

    @Value("${smarttrust.rate-limit.login-bucket-capacity:5}")
    private int loginCapacity;

    @Value("${smarttrust.rate-limit.login-refill-minutes:15}")
    private int loginRefillMinutes;

    private Cache<String, SimpleBucket> cache;

    @PostConstruct
    public void init() {
        cache = Caffeine.newBuilder()
                .maximumSize(20000)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .recordStats()
                .build();
        log.info("RateLimitService initialized loginCapacity={} refillMinutes={}", loginCapacity, loginRefillMinutes);
    }

    public RateLimitResult tryConsume(String key, EndpointType type) {
        String cacheKey = key + ":" + type.name();
        SimpleBucket bucket = cache.get(cacheKey, k -> createBucket(type));
        return bucket.tryConsume();
    }

    private SimpleBucket createBucket(EndpointType type) {
        return switch (type) {
            case LOGIN -> new SimpleBucket(loginCapacity, Duration.ofMinutes(loginRefillMinutes));
            case REGISTER -> new SimpleBucket(10, Duration.ofMinutes(60));
            case OTP_VERIFY -> new SimpleBucket(20, Duration.ofMinutes(15));
            case FORGOT_PASSWORD -> new SimpleBucket(5, Duration.ofMinutes(30));
        };
    }

    public enum EndpointType {
        LOGIN,
        REGISTER,
        OTP_VERIFY,
        FORGOT_PASSWORD
    }

    @Getter
    @AllArgsConstructor
    public static class RateLimitResult {
        private final boolean consumed;
        private final int remaining;
        private final long retryAfterSeconds;
    }

    /**
     * Fixed window bucket - thread-safe with synchronized
     */
    public static class SimpleBucket {
        private final int capacity;
        private final Duration refillDuration;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();

        public SimpleBucket(int capacity, Duration refillDuration) {
            this.capacity = capacity;
            this.refillDuration = refillDuration;
        }

        public synchronized RateLimitResult tryConsume() {
            Instant now = Instant.now();
            // If window expired, reset
            if (Duration.between(windowStart, now).compareTo(refillDuration) >= 0) {
                windowStart = now;
                count.set(0);
            }

            int current = count.get();
            if (current < capacity) {
                count.incrementAndGet();
                int remaining = capacity - count.get();
                return new RateLimitResult(true, remaining, 0);
            } else {
                // Rate limited
                long retrySeconds = refillDuration.minus(Duration.between(windowStart, now)).getSeconds();
                if (retrySeconds < 0) retrySeconds = 1;
                return new RateLimitResult(false, 0, retrySeconds);
            }
        }
    }
}
