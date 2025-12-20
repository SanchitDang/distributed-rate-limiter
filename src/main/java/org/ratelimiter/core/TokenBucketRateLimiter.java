package org.ratelimiter.core;

import org.ratelimiter.model.TokenBucket;

import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final double refillRatePerSecond;

    public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public boolean allowRequest(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(
                key,
                k -> new TokenBucket(capacity, refillRatePerSecond)
        );

        return bucket.tryConsume();
    }
}
