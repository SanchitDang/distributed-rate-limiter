package org.ratelimiter.metrics;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRateLimiterMetrics implements RateLimiterMetrics {

    public final AtomicLong total = new AtomicLong();
    public final AtomicLong allowed = new AtomicLong();
    public final AtomicLong rejected = new AtomicLong();

    public final AtomicLong blockedIp = new AtomicLong();
    public final AtomicLong blockedUser = new AtomicLong();
    public final AtomicLong blockedOrg = new AtomicLong();

    public final AtomicLong localHits = new AtomicLong();
    public final AtomicLong redisHits = new AtomicLong();

    public final AtomicLong redisFailure = new AtomicLong();

    private final Set<String> uniqueKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalRedisLatencyMs = new AtomicLong();
    private final AtomicLong redisCalls = new AtomicLong();

    @Override
    public void incrementTotalRequests() { total.incrementAndGet(); }

    @Override
    public void incrementAllowed() { allowed.incrementAndGet(); }

    @Override
    public void incrementRejected() { rejected.incrementAndGet(); }

    @Override
    public void incrementBlockedIp() { blockedIp.incrementAndGet(); }

    @Override
    public void incrementBlockedUser() { blockedUser.incrementAndGet(); }

    @Override
    public void incrementBlockedOrg() { blockedOrg.incrementAndGet(); }

    @Override
    public void incrementLocalHit() { localHits.incrementAndGet(); }

    @Override
    public void incrementRedisFailure() { redisFailure.incrementAndGet(); }

    @Override
    public void incrementRedisHit() { redisHits.incrementAndGet(); }

    public void recordKeys(List<String> keys) { uniqueKeys.addAll(keys); }

    public int getKeyCardinality() { return uniqueKeys.size(); }

    public void recordRedisLatency(long ms) {
        totalRedisLatencyMs.addAndGet(ms);
        redisCalls.incrementAndGet();
    }
    public double getAverageRedisLatencyMs() {
        long calls = redisCalls.get();
        return calls == 0 ? 0 : (double) totalRedisLatencyMs.get() / calls;
    }
}
