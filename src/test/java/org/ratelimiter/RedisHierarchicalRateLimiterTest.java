package org.ratelimiter;

import org.junit.jupiter.api.*;
import org.ratelimiter.core.RedisFailMode;
import org.ratelimiter.core.RedisHierarchicalRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisHierarchicalRateLimiterTest {

    private JedisPool jedisPool;

    @BeforeAll
    void setup() {
        jedisPool = new JedisPool("localhost", 6379);
    }

    @AfterAll
    void teardown() {
        jedisPool.close();
    }

    @Test
    @DisplayName("Blocks at the tightest level and reports which key blocked it")
    void blocksAtTightestLevelAndReportsIt() {
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        RedisHierarchicalRateLimiter limiter =
                new RedisHierarchicalRateLimiter(jedisPool, metrics, RedisFailMode.FAIL_CLOSED);

        // unique suffix per run so this doesn't depend on the 60s Redis TTL
        String suffix = String.valueOf(System.nanoTime());
        String ipKey = "rate_limit:ip:test-" + suffix;
        String userKey = "rate_limit:user:test-" + suffix;
        String orgKey = "rate_limit:org:test-" + suffix;
        List<String> keys = List.of(ipKey, userKey, orgKey);

        try (var jedis = jedisPool.getResource()) {
            // ip is deliberately the tightest budget
            jedis.hset(ipKey + ":config", Map.of("capacity", "2", "refill_rate", "0"));
            jedis.hset(userKey + ":config", Map.of("capacity", "10", "refill_rate", "0"));
            jedis.hset(orgKey + ":config", Map.of("capacity", "50", "refill_rate", "0"));
        }

        assertTrue(limiter.allowRequest(keys).allowed());
        assertTrue(limiter.allowRequest(keys).allowed());

        RedisHierarchicalRateLimiter.Result result = limiter.allowRequest(keys);
        assertFalse(result.allowed(), "ip capacity should be exhausted by now");
        assertEquals(ipKey, result.blockedKey());
        assertEquals(1, metrics.blockedIp.get());
        assertEquals(0, metrics.blockedUser.get());
        assertEquals(0, metrics.blockedOrg.get());
    }

    @Test
    @DisplayName("Missing config falls back to defaults instead of failing the request")
    void missingConfigFallsBackToDefaults() {
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        RedisHierarchicalRateLimiter limiter =
                new RedisHierarchicalRateLimiter(jedisPool, metrics, RedisFailMode.FAIL_CLOSED);

        // no ":config" hash ever written for this key - default capacity/refill apply
        String key = "rate_limit:user:no-config-" + System.nanoTime();

        RedisHierarchicalRateLimiter.Result result = limiter.allowRequest(List.of(key));

        assertTrue(result.allowed(), "a fresh bucket with no config should still start full and allow the first request");
    }

    @Test
    @DisplayName("Refill math based on Redis's own clock is correct across a real elapsed wait")
    void refillUsesRedisClockCorrectly() throws InterruptedException {
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        RedisHierarchicalRateLimiter limiter =
                new RedisHierarchicalRateLimiter(jedisPool, metrics, RedisFailMode.FAIL_CLOSED);

        String key = "rate_limit:user:clock-" + System.nanoTime();
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(key + ":config", Map.of("capacity", "3", "refill_rate", "1000")); // 1 token/ms
        }

        assertTrue(limiter.allowRequest(List.of(key)).allowed());
        assertTrue(limiter.allowRequest(List.of(key)).allowed());
        assertTrue(limiter.allowRequest(List.of(key)).allowed());
        assertFalse(limiter.allowRequest(List.of(key)).allowed(), "capacity exhausted");

        try (var jedis = jedisPool.getResource()) {
            long lastRefill = Long.parseLong(jedis.hget(key, "last_refill"));
            long nowMs = System.currentTimeMillis();
            assertTrue(Math.abs(nowMs - lastRefill) < 5000,
                    "last_refill should track Redis's real clock, not be off by a unit-conversion bug in the TIME() math");
        }

        Thread.sleep(20); // plenty of real time for a 1 token/ms refill rate

        assertTrue(limiter.allowRequest(List.of(key)).allowed(),
                "should have refilled given real elapsed time and a fast refill rate");
    }
}
