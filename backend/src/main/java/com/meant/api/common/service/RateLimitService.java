package com.meant.api.common.service;

import com.meant.api.common.properties.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RateLimitService {

    private final List<RateLimitPolicy> policies;
    private final Clock clock;
    private final ConcurrentMap<String, List<TokenBucket>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(List<RateLimitProperties.Limit> limits, Clock clock) {
        this.policies = limits.stream()
                .map(RateLimitPolicy::from)
                .toList();
        this.clock = clock;
    }

    public RateLimitDecision consume(String key) {
        Instant now = clock.instant();
        List<TokenBucket> keyBuckets = buckets.computeIfAbsent(key, ignored -> newBuckets(now));
        synchronized (keyBuckets) {
            List<Duration> retryAfters = new ArrayList<>();
            for (TokenBucket bucket : keyBuckets) {
                bucket.refill(now);
                if (!bucket.hasToken()) {
                    retryAfters.add(bucket.retryAfter());
                }
            }
            if (!retryAfters.isEmpty()) {
                Duration retryAfter = retryAfters.stream()
                        .max(Comparator.naturalOrder())
                        .orElse(Duration.ofSeconds(1));
                return RateLimitDecision.denied(retryAfter);
            }
            keyBuckets.forEach(TokenBucket::consume);
            return RateLimitDecision.accepted();
        }
    }

    private List<TokenBucket> newBuckets(Instant now) {
        return policies.stream()
                .map(policy -> new TokenBucket(policy, now))
                .toList();
    }

    public record RateLimitDecision(boolean allowed, Duration retryAfter) {

        private static RateLimitDecision accepted() {
            return new RateLimitDecision(true, Duration.ZERO);
        }

        private static RateLimitDecision denied(Duration retryAfter) {
            return new RateLimitDecision(false, retryAfter);
        }
    }

    private record RateLimitPolicy(
            String name,
            int capacity,
            int refillTokens,
            Duration refillPeriod,
            long refillPeriodNanos
    ) {

        private static RateLimitPolicy from(RateLimitProperties.Limit limit) {
            return new RateLimitPolicy(
                    limit.name(),
                    limit.capacity(),
                    limit.refillTokens(),
                    limit.refillPeriod(),
                    limit.refillPeriod().toNanos()
            );
        }
    }

    private static final class TokenBucket {

        private final RateLimitPolicy policy;
        private double tokens;
        private Instant lastRefill;

        private TokenBucket(RateLimitPolicy policy, Instant now) {
            this.policy = policy;
            this.tokens = policy.capacity();
            this.lastRefill = now;
        }

        private void refill(Instant now) {
            long elapsedNanos = Duration.between(lastRefill, now).toNanos();
            if (elapsedNanos <= 0) {
                return;
            }
            double refillAmount = ((double) elapsedNanos / policy.refillPeriodNanos()) * policy.refillTokens();
            tokens = Math.min(policy.capacity(), tokens + refillAmount);
            lastRefill = now;
        }

        private boolean hasToken() {
            return tokens >= 1.0d;
        }

        private void consume() {
            tokens -= 1.0d;
        }

        private Duration retryAfter() {
            double missingTokens = Math.max(0.0d, 1.0d - tokens);
            long nanos = Math.max(
                    1L,
                    (long) Math.ceil((missingTokens * policy.refillPeriodNanos()) / policy.refillTokens())
            );
            return Duration.ofNanos(nanos);
        }
    }
}
