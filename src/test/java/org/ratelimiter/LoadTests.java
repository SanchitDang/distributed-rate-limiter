package org.ratelimiter;

import org.junit.jupiter.api.*;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.core.RedisFailMode;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import redis.clients.jedis.JedisPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoadTests {

    private JedisPool jedisPool;
    private RedisDynamicRateLimiter limiter;
    private InMemoryRateLimiterMetrics metrics;

    @BeforeAll
    void setup() {
        metrics = new InMemoryRateLimiterMetrics();
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

    /**
     * Single-key burst traffic test
     */
    @Test
    void singleKeyBurstTrafficTest() throws Exception {
        String userKey = "rate_limit:user:burst-user";

        try (var jedis = jedisPool.getResource()) {
            Map<String, String> config = new HashMap<>();
            config.put("capacity", "50");
            config.put("refill_rate", "20");
            jedis.hset(userKey + ":config", config);
        }

        int threads = 100;
        int requests = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicInteger allowed = new AtomicInteger();

        long start = System.currentTimeMillis();

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
        long duration = System.currentTimeMillis() - start;

        System.out.println("Single-Key Burst Test:");
        System.out.println("Duration (ms) = " + duration);
        System.out.println("Allowed requests = " + allowed.get());
        System.out.println("Rejected requests = " + (requests - allowed.get()));

        assertTrue(allowed.get() <= 55, "Allowed requests should not exceed capacity + small refill allowance");
    }

    /**
     * Hierarchical burst traffic test (IP → User → Org)
     */
    @Test
    void hierarchicalBurstTrafficTest() throws Exception {

        String ipKey = "rate_limit:ip:10.0.0.1";
        String userKey = "rate_limit:user:burst-user2";
        String orgKey = "rate_limit:org:burst-org";

        try (var jedis = jedisPool.getResource()) {
            jedis.hset(ipKey + ":config", Map.of("capacity", "20", "refill_rate", "10"));
            jedis.hset(userKey + ":config", Map.of("capacity", "30", "refill_rate", "15"));
            jedis.hset(orgKey + ":config", Map.of("capacity", "50", "refill_rate", "20"));
        }

        List<String> keys = List.of(ipKey, userKey, orgKey);

        int threads = 100;
        int requests = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        long start = System.currentTimeMillis();

        for (int i = 0; i < requests; i++) {
            executor.submit(() -> {
                if (limiter.allowRequest(keys)) {
                    allowed.incrementAndGet();
                } else {
                    rejected.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - start;

        System.out.println("Hierarchical Burst Test:");
        System.out.println("Duration (ms) = " + duration);
        System.out.println("Allowed requests = " + allowed.get());
        System.out.println("Rejected requests = " + rejected.get());

        // Fail-fast ensures allowed requests respect the lowest bucket (IP)
        assertTrue(allowed.get() <= 25, "Allowed requests should respect the tightest level (IP) under burst");
    }
}
