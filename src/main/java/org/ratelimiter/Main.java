package org.ratelimiter;

import org.ratelimiter.core.TokenBucketRateLimiter;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(5, 1); // 5 tokens, 1/sec

        String user = "user-123";

        for (int i = 1; i <= 100; i++) {
            boolean allowed = limiter.allowRequest(user);
            System.out.println("Request " + i + ": " + allowed);
            Thread.sleep(300);
        }
    }
}
