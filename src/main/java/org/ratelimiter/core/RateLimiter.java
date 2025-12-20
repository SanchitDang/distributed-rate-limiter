// RateLimiter.java
package org.ratelimiter.core;

/**
 * Core RateLimiter interface
 * Step 1: Core Token Bucket
 * Step 2: Thread-safe single-node rate limiter
 * <p>
 * DSA/Concepts Used:
 * - Abstraction to define common rate-limiting behavior
 * - Allows swapping single-node or distributed implementations
 */

public interface RateLimiter {
    boolean allowRequest(String key);
}
