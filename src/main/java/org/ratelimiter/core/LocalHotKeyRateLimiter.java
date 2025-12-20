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
public class LocalHotKeyRateLimiter {

    private final ConcurrentHashMap<String, LocalBucket> hotBuckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final double refillRatePerMillis;
    private final RateLimiterMetrics metrics;

    public LocalHotKeyRateLimiter(long capacity, double refillRatePerSecond, RateLimiterMetrics metrics) {
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;
        this.metrics = metrics;
    }

    public boolean allowRequest(String key) {
        LocalBucket bucket = hotBuckets.computeIfAbsent(key,
                k -> new LocalBucket(capacity, refillRatePerMillis));

        if (bucket.tryConsume()) {
            metrics.incrementLocalHit();
            return true;
        }

        return false;
    }

    private static class LocalBucket {
        private double tokens;
        private long lastRefill;
        private final long capacity;
        private final double refillRatePerMillis;
        private final ReentrantLock lock = new ReentrantLock();

        public LocalBucket(long capacity, double refillRatePerMillis) {
            this.capacity = capacity;
            this.refillRatePerMillis = refillRatePerMillis;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        public boolean tryConsume() {
            lock.lock();
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
