package org.ratelimiter.core;

import org.ratelimiter.metrics.RateLimiterMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Step 6: RedisDynamicRateLimiter
 *
 * Implements a distributed, hierarchical token bucket with:
 *  - Dynamic configuration for capacity and refill_rate per key
 *  - Multi-key hierarchical support (IP -> User -> Org)
 *  - Atomic refill and consume via Lua script
 *
 * Each request will automatically pick up the latest Redis config for capacity/refill rate.
 */
/**
 * Update - Implemented LocalHotKeyRateLimiter with:
 * - Key Sharding
 * - Request Coalescing
 */
public class LocalHotKeyRateLimiter {

    private final ConcurrentHashMap<String, ShardedBucket> hotBuckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final double refillRatePerMillis;
    private final int shardCount;
    private final RateLimiterMetrics metrics;

    public LocalHotKeyRateLimiter(long capacity, double refillRatePerSecond, int shardCount, RateLimiterMetrics metrics) {
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;
        this.shardCount = shardCount;
        this.metrics = metrics;
    }

    public boolean allowRequest(String key) {
        metrics.incrementTotalRequests();

        // pick the shard
        int shardIndex = Math.abs(key.hashCode() % shardCount);
        String shardKey = key + "#" + shardIndex;

        ShardedBucket shard = hotBuckets.computeIfAbsent(shardKey,
                k -> new ShardedBucket(capacity, refillRatePerMillis));

        boolean allowed = shard.tryConsume();
        if (allowed) {
            metrics.incrementLocalHit();
            metrics.incrementAllowed();
        } else {
            metrics.incrementRejected();
        }
        return allowed;
    }

    // ShardedBucket handles request coalescing for each shard
    private static class ShardedBucket {
        private double tokens;
        private long lastRefill;
        private final long capacity;
        private final double refillRatePerMillis;
        private final ReentrantLock lock = new ReentrantLock();

        public ShardedBucket(long capacity, double refillRatePerMillis) {
            this.capacity = capacity;
            this.refillRatePerMillis = refillRatePerMillis;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        public boolean tryConsume() {
            lock.lock();  // coalescing: only one thread refills at a time
            try {
                refill();
                if (tokens >= 1) {
                    tokens -= 1;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillRatePerMillis);
                lastRefill = now;
            }
        }
    }
}
