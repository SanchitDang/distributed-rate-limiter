// TokenBucket.java
package org.ratelimiter.model;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-token bucket per key
 * Step 1: Core Token Bucket logic (capacity, refill, consume)
 * Step 2: Thread-safe bucket using ReentrantLock
 *
 * DSA/Concepts Used:
 * - Sliding window logic (time-based refill)
 * - O(1) per request
 * - Lock for atomic operations in concurrent scenarios
 */
public class TokenBucket {

    private final long capacity;                // Maximum tokens in the bucket
    private final double refillRatePerMillis;   // Tokens added per millisecond

    private double tokens;                      // Current token count (can be fractional)
    private long lastRefillTimestamp;           // Last refill time in ms

    // Step 2: Lock ensures thread-safe refill + consume
    private final ReentrantLock lock = new ReentrantLock();

    public TokenBucket(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        // Convert tokens/sec to tokens/ms for precise refill calculation
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;
        this.tokens = capacity;                 // Start full
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    /**
     * Try to consume a single token
     * Step 1: Check if token available
     * Step 2: Thread-safe (lock) and refill tokens
     *
     * @return true if token consumed, false if rate limit exceeded
     */
    public boolean tryConsume() {
        lock.lock(); // Step 2: critical section
        try {
            refill();             // Refill tokens based on elapsed time
            if (tokens >= 1) {   // Token available
                tokens -= 1;     // Consume 1 token
                return true;
            }
            return false;        // No tokens available → reject request
        } finally {
            lock.unlock();       // Release lock
        }
    }

    /**
     * Refill tokens based on elapsed time
     * Step 1: Sliding window time-based logic
     * Step 2: Atomic inside lock
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - lastRefillTimestamp;

        if (elapsedMillis > 0) {
            // Step 1: refill proportional to time elapsed
            double tokensToAdd = elapsedMillis * refillRatePerMillis;
            tokens = Math.min(capacity, tokens + tokensToAdd); // cap at max
            lastRefillTimestamp = now;
        }
    }
}
