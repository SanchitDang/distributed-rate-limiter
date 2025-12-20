// TokenBucketRateLimiter.java
package org.ratelimiter.core;

import org.ratelimiter.model.TokenBucket;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe per-key token bucket rate limiter
 * Step 1: Core logic (TokenBucket per key)
 * Step 2: Thread-safe via ConcurrentHashMap + per-bucket locks
 *
 * DSA/Concepts Used:
 * - O(1) access per key using HashMap
 * - Thread-safety using ConcurrentHashMap
 * - Lazy bucket initialization (computeIfAbsent)
 */
public class TokenBucketRateLimiter implements RateLimiter {

    // Mapping from key (user/IP/etc.) to their respective token bucket
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final long capacity;                 // Max tokens per bucket
    private final double refillRatePerSecond;    // Tokens added per second

    public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    /**
     * Attempt to consume a token for the given key
     * Step 1: Core consumption logic
     * Step 2: Thread-safe via per-bucket lock in TokenBucket
     *
     * @param key unique identifier (user/IP)
     * @return true if allowed, false if rate limit exceeded
     */
    @Override
    public boolean allowRequest(String key) {
        // Lazily create a bucket for this key if it doesn't exist
        TokenBucket bucket = buckets.computeIfAbsent(
                key,
                k -> new TokenBucket(capacity, refillRatePerSecond)
        );

        // Step 2: Thread-safe consume
        return bucket.tryConsume();
    }
}
