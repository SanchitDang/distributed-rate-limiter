package org.ratelimiter.metrics;

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
    public void incrementRedisHit() { redisHits.incrementAndGet(); }
}
