package org.ratelimiter.model;

import java.util.concurrent.locks.ReentrantLock;

public class TokenBucket {

    private final long capacity;
    private final double refillRatePerMillis;

    private double tokens;
    private long lastRefillTimestamp;

    private final ReentrantLock lock = new ReentrantLock();

    public TokenBucket(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
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
        long elapsedMillis = now - lastRefillTimestamp;

        if (elapsedMillis > 0) {
            double tokensToAdd = elapsedMillis * refillRatePerMillis;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}