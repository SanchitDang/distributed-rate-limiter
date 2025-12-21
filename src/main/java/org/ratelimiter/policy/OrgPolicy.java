package org.ratelimiter.policy;

/**
 * Step 9 (Policy Layer)
 * Organization-level rate limiting policy
 */
public class OrgPolicy implements RateLimitPolicy {

    private final String orgId;

    public OrgPolicy(String orgId) {
        this.orgId = orgId;
    }

    @Override
    public String getKey() {
        return "rate_limit:org:" + orgId;
    }

    @Override
    public int priority() {
        return 3; // checked last
    }
}
