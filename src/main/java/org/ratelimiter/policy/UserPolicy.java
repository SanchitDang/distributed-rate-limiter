package org.ratelimiter.policy;

/**
 * Step 9 (Policy Layer)
 * User-level rate limiting policy
 */
public class UserPolicy implements RateLimitPolicy {

    private final String userId;

    public UserPolicy(String userId) {
        this.userId = userId;
    }

    @Override
    public String getKey() {
        return "rate_limit:user:" + userId;
    }

    @Override
    public int priority() {
        return 2; // checked after IP, before Org
    }
}
