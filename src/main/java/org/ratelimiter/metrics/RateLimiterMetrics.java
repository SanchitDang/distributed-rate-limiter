package org.ratelimiter.metrics;

import java.util.List;

public interface RateLimiterMetrics {

    void incrementTotalRequests();
    void incrementAllowed();
    void incrementRejected();

    void incrementBlockedIp();
    void incrementBlockedUser();
    void incrementBlockedOrg();

    void incrementLocalHit();
    void incrementRedisHit();

    void incrementRedisFailure();

    void recordKeys(List<String> keys);
    void recordRedisLatency(long latencyMs);

    int getKeyCardinality();
    double getAverageRedisLatencyMs();
}
