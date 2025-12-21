package org.ratelimiter;

import org.junit.jupiter.api.Test;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.core.RedisFailMode;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisDynamicRateLimiterTest {

    private JedisPool jedisPool;
    private RedisDynamicRateLimiter limiter;

    @BeforeAll
    void setup() {
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();
        jedisPool = new JedisPool("localhost", 6379);
        limiter = new RedisDynamicRateLimiter(jedisPool, metrics, RedisFailMode.FAIL_CLOSED);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== RATE LIMITER METRICS ===");
            System.out.println("Total Requests : " + metrics.total.get());
            System.out.println("Allowed        : " + metrics.allowed.get());
            System.out.println("Rejected       : " + metrics.rejected.get());
            System.out.println("Local Hits     : " + metrics.localHits.get());
            System.out.println("Redis Hits     : " + metrics.redisHits.get());
        }));
    }

    @AfterAll
    void teardown() {
        jedisPool.close();
    }

    @Test
    void concurrentRequests_shouldNotExceedLimit() throws Exception {

        String userKey = "rate_limit:user:test-user";

        try (var jedis = jedisPool.getResource()) {
            Map<String, String> config = new HashMap<>();
            config.put("capacity", "10");
            config.put("refill_rate", "5");
            jedis.hset(userKey + ":config", config);
        }

        int threads = 50;
        int requests = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < requests; i++) {
            executor.submit(() -> {
                if (limiter.allowRequest(List.of(userKey))) {
                    allowed.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("Allowed = " + allowed.get());

        // Capacity 10 + refill window
        assertTrue(allowed.get() <= 12);
    }

    @Test
    void hierarchicalLimit_shouldFailIfAnyLevelExceeds() {

        List<String> keys = List.of(
                "rate_limit:ip:1.1.1.1",
                "rate_limit:user:test",
                "rate_limit:org:test-org"
        );

        // Setup tight limit to force failure
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("rate_limit:user:test:config",
                    Map.of("capacity", "2", "refill_rate", "0"));
        }

        boolean blockedSeen = false;

        for (int i = 0; i < 50; i++) {
            boolean allowed = limiter.allowRequest(keys);
            if (!allowed) {
                blockedSeen = true;
                break;
            }
        }

        assertTrue(blockedSeen);
    }
}
