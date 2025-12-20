package org.ratelimiter;

import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;

public class DistributedTest {

    public static void main(String[] args) {

        InMemoryRateLimiterMetrics metrics =
                new InMemoryRateLimiterMetrics();

        JedisPool jedisPool = new JedisPool("localhost", 6379);
        RedisDynamicRateLimiter limiter = new RedisDynamicRateLimiter(jedisPool, metrics);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== RATE LIMITER METRICS ===");
            System.out.println("Total Requests : " + metrics.total.get());
            System.out.println("Allowed        : " + metrics.allowed.get());
            System.out.println("Rejected       : " + metrics.rejected.get());
            System.out.println("Local Hits     : " + metrics.localHits.get());
            System.out.println("Redis Hits     : " + metrics.redisHits.get());
        }));

        String key = "rate_limit:user:distributed";

        try (var jedis = jedisPool.getResource()) {
            jedis.del(key);
            jedis.del(key + ":config");

            jedis.hset(key + ":config", Map.of(
                    "capacity", "10",
                    "refill_rate", "0"
            ));
        }

        for (int i = 0; i < 20; i++) {
            boolean allowed = limiter.allowRequest(List.of(key));
            System.out.println("PID " + ProcessHandle.current().pid() +
                    " -> " + allowed);
        }
    }
}
