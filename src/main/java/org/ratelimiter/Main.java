// Main.java
package org.ratelimiter;

import org.ratelimiter.core.*;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Demonstration / Test class
 * Step 1: Validate token bucket logic
 * Step 2: Test thread-safe single-node limiter
 *
 * Concepts Tested:
 * - Concurrency (ExecutorService)
 * - Thread-safety per bucket
 * - Burst handling and token refill
 */
public class Main {

    // Adding hot-key mitigation to other limiters for increase in performance
    public static void main(String[] args) throws InterruptedException {

        JedisPool jedisPool = new JedisPool("localhost", 6379);

        // STEP 7: Local hot-key mitigation (fast, in-memory)
        LocalHotKeyRateLimiter hotKeyLimiter =
                new LocalHotKeyRateLimiter(5, 5); // small local burst

        // STEP 6: Distributed + dynamic + hierarchical limiter
        RedisDynamicRateLimiter redisLimiter =
                new RedisDynamicRateLimiter(jedisPool);

        // ---- Dynamic configs (can be changed at runtime) ----
        try (var jedis = jedisPool.getResource()) {

            Map<String, String> userConfig = new HashMap<>();
            userConfig.put("capacity", "10");
            userConfig.put("refill_rate", "5");
            jedis.hset("rate_limit:user:concurrent-user:config", userConfig);

            Map<String, String> ipConfig = new HashMap<>();
            ipConfig.put("capacity", "20");
            ipConfig.put("refill_rate", "10");
            jedis.hset("rate_limit:ip:192.168.0.1:config", ipConfig);

            Map<String, String> orgConfig = new HashMap<>();
            orgConfig.put("capacity", "50");
            orgConfig.put("refill_rate", "15");
            jedis.hset("rate_limit:org:orgABC:config", orgConfig);
        }

        ExecutorService executor = Executors.newFixedThreadPool(20);

        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {

                boolean allowed;

                // STEP 7: Local pre-check (hot-key protection)
                if (hotKeyLimiter.allowRequest(user)) {
                    allowed = true;
                } else {
                    // STEP 4–6: Redis hierarchical + dynamic check
                    allowed = redisLimiter.allowRequest(
                            Arrays.asList(
                                    "rate_limit:ip:" + ip,
                                    "rate_limit:user:" + user,
                                    "rate_limit:org:" + org
                            )
                    );
                }

                System.out.println(Thread.currentThread().getName()
                        + " -> " + allowed);
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        jedisPool.close();
    }

//    public static void main(String[] args) throws InterruptedException {
//
//        /// Using in memory based TokenLimiter
////        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);
//
//        JedisPool jedisPool = new JedisPool("localhost", 6379);
//
//        /// Using Redis TokenLimiter
////        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(jedisPool, 10, 5);
//
//        /// Using Redis hierarchical TokenLimiter
////        RedisHierarchicalRateLimiter limiter = new RedisHierarchicalRateLimiter(jedisPool, 10, 5);
//
//        /// Initialize RedisDynamicRateLimiter
//        RedisDynamicRateLimiter limiter = new RedisDynamicRateLimiter(jedisPool);
//
//        /// Set dynamic configs for this (optional)
//        try (var jedis = jedisPool.getResource()) {
//            // User config
//            jedis.hset("rate_limit:user:concurrent-user:config", "capacity", "10");
//            jedis.hset("rate_limit:user:concurrent-user:config", "refill_rate", "5");
//
//            // IP config
//            jedis.hset("rate_limit:ip:192.168.0.1:config", "capacity", "20");
//            jedis.hset("rate_limit:ip:192.168.0.1:config", "refill_rate", "10");
//
//            // Org config
//            jedis.hset("rate_limit:org:orgABC:config", "capacity", "50");
//            jedis.hset("rate_limit:org:orgABC:config", "refill_rate", "15");
//        }
//
//        ExecutorService executor = Executors.newFixedThreadPool(20);
//        String user = "concurrent-user";
//        String org = "orgABC";
//        String ip = "192.168.0.1";
//
//        // Submit 100 concurrent requests
//        for (int i = 0; i < 100; i++) {
//            executor.submit(() -> {
//                /// Single-key
////                boolean allowed = limiter.allowRequest(user);
//
//                /// Hierarchical (IP + User + Org)
//                boolean allowed = limiter.allowRequest(
//                        Arrays.asList("rate_limit:ip:" + ip,
//                                "rate_limit:user:" + user,
//                                "rate_limit:org:" + org)
//                );
//                System.out.println(Thread.currentThread().getName() + " -> " + allowed);
//            });
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(5, TimeUnit.SECONDS);
//        jedisPool.close();
//    }
}
