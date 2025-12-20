package org.ratelimiter.model;

public class TokenBucket {

    private final long capacity;
    private final double refillRatePerMillis;

    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucket(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    public long getCapacity() {
        return capacity;
    }

    public double getTokens() {
        return tokens;
    }

    public long getLastRefillTimestamp() {
        return lastRefillTimestamp;
    }

    void refill() {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - lastRefillTimestamp;

        if (elapsedMillis > 0) {
            double tokensToAdd = elapsedMillis * refillRatePerMillis;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }

    public boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}
