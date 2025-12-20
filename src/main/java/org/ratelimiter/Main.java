// Main.java
package org.ratelimiter;

import org.ratelimiter.core.TokenBucketRateLimiter;

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

        // 10 tokens max, refill 5 tokens/sec
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        String user = "concurrent-user";

        // Submit 100 concurrent requests
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                boolean allowed = limiter.allowRequest(user);
                System.out.println(Thread.currentThread().getName() + " -> " + allowed);
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
