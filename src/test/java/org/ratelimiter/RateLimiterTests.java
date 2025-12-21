package org.ratelimiter;

import org.junit.jupiter.api.*;
import org.ratelimiter.core.*;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RateLimiterTests {

    private JedisPool jedisPool;

    @BeforeAll
    void setup() {
        jedisPool = new JedisPool("localhost", 6379);
    }

    @AfterAll
    void teardown() {
        jedisPool.close();
    }

    // ----------------- 1 In-Memory Token Bucket -----------------
    @Test
    @DisplayName("Deterministic In-Memory Token Bucket")
    void testInMemoryTokenBucket() throws InterruptedException {
        int capacity = 10;
        int refillRate = 5;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, refillRate);

        int totalRequests = 15; // <= capacity + small refill
        AtomicInteger allowedCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                if (limiter.allowRequest("user1")) allowedCount.incrementAndGet();
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Assertions.assertEquals(Math.min(totalRequests, capacity), allowedCount.get(),
                "Allowed requests should equal token bucket capacity");
    }

    // ----------------- 2 Redis Single-Key Token Bucket -----------------
    @Test
    @DisplayName("Deterministic Redis Single-Key Token Bucket")
    void testRedisSingleKey() throws InterruptedException {
        int capacity = 10;
        int refillRate = 5;
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(jedisPool, capacity, refillRate);

        int totalRequests = 15; // same reasoning
        AtomicInteger allowedCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                if (limiter.allowRequest("user1")) allowedCount.incrementAndGet();
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Assertions.assertEquals(Math.min(totalRequests, capacity), allowedCount.get());
    }

    // ----------------- 3 Redis Hierarchical Token Bucket -----------------
    @Test
    @DisplayName("Deterministic Redis Hierarchical Token Bucket")
    void testRedisHierarchical() throws InterruptedException {
        int capacity = 10;
        int refillRate = 5;
        RedisHierarchicalRateLimiter limiter = new RedisHierarchicalRateLimiter(jedisPool, capacity, refillRate);

        int totalRequests = 12; // test more than capacity to trigger rejection
        AtomicInteger allowedCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                if (limiter.allowRequest(
                        Arrays.asList(
                                "rate_limit:ip:" + ip,
                                "rate_limit:user:" + user,
                                "rate_limit:org:" + org
                        ))) allowedCount.incrementAndGet();
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Max allowed should not exceed capacity
        Assertions.assertEquals(Math.min(totalRequests, capacity), allowedCount.get());
    }

    // ----------------- 4 Redis Dynamic + Hot-Key Hierarchical -----------------
    @Test
    @DisplayName("Concurrent Hot-Key + Redis Dynamic Hierarchical Test (Metrics Compatible)")
    void testHotKeyRedisConcurrentWithMetrics() throws InterruptedException {

        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();

        /*
         * How to choose shardCount
         * - Low / moderate traffic 1–2
         * - Hot keys (bursts)	4–8
         * - Very high QPS	16+
         */
        LocalHotKeyRateLimiter hotKeyLimiter = new LocalHotKeyRateLimiter(5, 1, 4, metrics);
        RedisDynamicRateLimiter redisLimiter = new RedisDynamicRateLimiter(jedisPool, metrics, RedisFailMode.FAIL_CLOSED);

        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";

        // Redis dynamic config
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("rate_limit:user:" + user + ":config", Map.of("capacity", "10", "refill_rate", "0"));
            jedis.hset("rate_limit:ip:" + ip + ":config", Map.of("capacity", "20", "refill_rate", "0"));
            jedis.hset("rate_limit:org:" + org + ":config", Map.of("capacity", "50", "refill_rate", "0"));
        }

        int totalRequests = 30;
        AtomicInteger allowedCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        Random random = new Random();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(random.nextInt(50));

                    boolean allowed;
                    if (hotKeyLimiter.allowRequest(user)) {
                        allowed = true;
                    } else {
                        allowed = redisLimiter.allowRequest(
                                Arrays.asList(
                                        "rate_limit:ip:" + ip,
                                        "rate_limit:user:" + user,
                                        "rate_limit:org:" + org
                                ));
                    }

                    if (allowed) allowedCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("Total Allowed Requests: " + allowedCount.get());
        System.out.println("Local Hits: " + metrics.localHits.get());
        System.out.println("Redis Hits: " + metrics.redisHits.get());

        int hotKeyCapacity = 5;
        int redisCapacity = 10; // min(user=10, ip=20, org=50)
        int maxAllowed = hotKeyCapacity + redisCapacity;

        // Concurrent-safe assertions
        Assertions.assertTrue(allowedCount.get() <= maxAllowed,
                "Allowed requests should not exceed hot-key + Redis user capacity");

        Assertions.assertTrue(metrics.localHits.get() <= hotKeyCapacity,
                "Local hits should not exceed hot-key capacity");

        Assertions.assertTrue(metrics.redisHits.get() <= redisCapacity,
                "Redis hits should not exceed Redis min capacity");
    }

}
