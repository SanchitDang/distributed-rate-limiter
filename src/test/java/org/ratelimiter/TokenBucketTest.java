package org.ratelimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ratelimiter.model.TokenBucket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenBucketTest {

    @Test
    @DisplayName("Never allows more than capacity requests back-to-back")
    void doesNotExceedCapacity() {
        TokenBucket bucket = new TokenBucket(5, 1); // slow refill, irrelevant here

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (bucket.tryConsume()) allowed++;
        }

        assertEquals(5, allowed, "only capacity tokens should be available with negligible elapsed time");
    }

    @Test
    @DisplayName("Zero refill rate never grants new tokens, no matter how long you wait")
    void zeroRefillRateNeverRefills() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(3, 0);

        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume(), "capacity is exhausted");

        Thread.sleep(50);

        assertFalse(bucket.tryConsume(), "refill rate 0 means it should still be empty after waiting");
    }

    @Test
    @DisplayName("Refills back up to (but never past) capacity after enough time passes")
    void refillsUpToCapacityOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(2, 1000); // 1 token/ms, refills fast

        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());

        Thread.sleep(20); // plenty of time to refill past capacity if unclamped

        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            if (bucket.tryConsume()) allowed++;
        }

        assertEquals(2, allowed, "refill should cap at capacity, not accumulate unbounded");
    }
}
