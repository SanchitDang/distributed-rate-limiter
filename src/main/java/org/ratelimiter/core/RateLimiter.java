package org.ratelimiter.core;

public interface RateLimiter {
    boolean allowRequest(String key);
}
