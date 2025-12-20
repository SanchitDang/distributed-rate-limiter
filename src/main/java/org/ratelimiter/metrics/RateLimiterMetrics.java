package org.ratelimiter.metrics;

public interface RateLimiterMetrics {

    void incrementTotalRequests();
    void incrementAllowed();
    void incrementRejected();

    void incrementBlockedIp();
    void incrementBlockedUser();
    void incrementBlockedOrg();

    void incrementLocalHit();
    void incrementRedisHit();
}
