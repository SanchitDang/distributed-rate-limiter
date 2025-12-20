package org.ratelimiter;

import org.ratelimiter.core.TokenBucketRateLimiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(10, 5); // 10 tokens, 5/sec

        ExecutorService executor = Executors.newFixedThreadPool(20);
        String user = "concurrent-user";

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                boolean allowed = limiter.allowRequest(user);
                System.out.println(Thread.currentThread().getName()
                        + " -> " + allowed);
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
