package org.ratelimiter.policy;

/**
 * Represents a single rate limiting policy
 * (User, Org, IP, API Key, etc.)
 */
public interface RateLimitPolicy {

    /**
     * @return Redis rate-limit key for this policy
     * Example: rate_limit:user:123
     */
    String getKey();

    /**
     * @return priority of this policy
     * Lower value = checked earlier (fail-fast)
     */
    int priority();
}
