package org.ratelimiter.policy;

/**
 * IP-level rate limiting policy
 * Protects against abuse
 */
public class IpPolicy implements RateLimitPolicy {

    private final String ip;

    public IpPolicy(String ip) {
        this.ip = ip;
    }

    @Override
    public String getKey() {
        return "rate_limit:ip:" + ip;
    }

    @Override
    public int priority() {
        return 1; // fail-fast
    }
}
