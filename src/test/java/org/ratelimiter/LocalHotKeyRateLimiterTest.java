package org.ratelimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ratelimiter.core.LocalHotKeyRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalHotKeyRateLimiterTest {

    @Test
    @DisplayName("Random shard selection spreads a hot key's load across more than one shard")
    void spreadsLoadAcrossShards() {
        int shardCount = 10;
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        // capacity 1 per shard, no refill - each shard can only ever pass one request through
        LocalHotKeyRateLimiter limiter = new LocalHotKeyRateLimiter(1, 0, shardCount, metrics);

        int passedThrough = 0;
        for (int i = 0; i < 500; i++) {
            if (limiter.allowRequest("hot-key")) passedThrough++;
        }

        // if every request landed on the same shard (the old key-hash based bug),
        // this would be capped at 1 no matter how many attempts were made.
        assertTrue(passedThrough > 1,
                "expected requests for the same key to spread across more than one shard");
        assertTrue(passedThrough <= shardCount,
                "cannot pass through more than one request per shard when each shard's capacity is 1");
    }

    @Test
    @DisplayName("Only ever short-circuits a reject, passing through is not a terminal allow")
    void neverGrantsAnAllowanceOnItsOwn() {
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        LocalHotKeyRateLimiter limiter = new LocalHotKeyRateLimiter(1, 0, 1, metrics);

        assertTrue(limiter.allowRequest("key")); // shard has its one token
        // second call must be rejected locally (shard is out of tokens, no refill)
        assertFalse(limiter.allowRequest("key"));
    }
}
