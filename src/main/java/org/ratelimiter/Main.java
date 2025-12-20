// Main.java
package org.ratelimiter;

import org.ratelimiter.core.RedisHierarchicalRateLimiter;
import org.ratelimiter.core.RedisTokenBucketRateLimiter;
import org.ratelimiter.core.TokenBucketRateLimiter;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
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

    public static void main(String[] args) throws InterruptedException {

        /// Using in memory based TokenLimiter
//        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);

        JedisPool jedisPool = new JedisPool("localhost", 6379);

        /// Using Redis TokenLimiter
//        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(jedisPool, 10, 5);

        /// Using Redis hierarchical TokenLimiter
        RedisHierarchicalRateLimiter limiter = new RedisHierarchicalRateLimiter(jedisPool, 10, 5);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        String user = "concurrent-user";
        String org = "orgABC";
        String ip = "192.168.0.1";

        // Submit 100 concurrent requests
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                /// Single-key
//                boolean allowed = limiter.allowRequest(user);

                /// Hierarchical (IP + User + Org)
                boolean allowed = limiter.allowRequest(
                        Arrays.asList("rate_limit:ip:" + ip,
                                "rate_limit:user:" + user,
                                "rate_limit:org:" + org)
                );
                System.out.println(Thread.currentThread().getName() + " -> " + allowed);
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        jedisPool.close();
    }
}
