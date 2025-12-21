package org.ratelimiter.api.simulation;

import org.junit.jupiter.api.*;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.ratelimiter.policy.ResolvePolicy;
import org.ratelimiter.policy.DefaultPolicyResolver;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrentRateLimiterTest {

    private RedisDynamicRateLimiter rateLimiter;
    private ResolvePolicy policyResolver;
    private InMemoryRateLimiterMetrics metrics;
    private JedisPool jedisPool;

    @BeforeAll
    public void setup() {
        jedisPool = new JedisPool("localhost", 6379);
        metrics = new InMemoryRateLimiterMetrics();
        rateLimiter = new RedisDynamicRateLimiter(jedisPool, metrics);
        policyResolver = new DefaultPolicyResolver();

        // Preload configs
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("rate_limit:user:concurrent-user:config", "capacity", "10");
            jedis.hset("rate_limit:user:concurrent-user:config", "refill_rate", "5");

            jedis.hset("rate_limit:ip:192.168.0.1:config", "capacity", "5");
            jedis.hset("rate_limit:ip:192.168.0.1:config", "refill_rate", "2");

            jedis.hset("rate_limit:org:orgABC:config", "capacity", "20");
            jedis.hset("rate_limit:org:orgABC:config", "refill_rate", "10");
        }
    }

    @Test
    @DisplayName("Concurrent requests: user + IP + org")
    public void testConcurrentRequests() throws InterruptedException {
        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";
        int totalRequests = 50;
        int concurrency = 10;

        List<String> keys = policyResolver.resolveKeys(user, ip, org);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                boolean allowed = rateLimiter.allowRequest(keys);
                String threadName = Thread.currentThread().getName();
                if (allowed) {
                    System.out.println(threadName + " -> ✅ Allowed");
                } else {
                    System.out.println(threadName + " -> ❌ Rate limited");
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("Total allowed requests: " + metrics.allowed.get());
        System.out.println("Total rejected requests: " + metrics.rejected.get());
    }

    @AfterAll
    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
