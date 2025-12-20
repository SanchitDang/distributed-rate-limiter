package org.ratelimiter.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.Collections;

/**
 * Distributed token bucket using Redis + Lua script
 * Step 4: Atomic refill + consume across multiple nodes
 *
 * DSA / Concepts:
 * - Redis hash for bucket state
 * - Lua script for atomic operations
 * - Sliding window logic for refill
 * - Distributed consistency across JVMs
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final long capacity;
    private final double refillRatePerMillis;
    private final String luaScript;

    public RedisTokenBucketRateLimiter(JedisPool jedisPool, long capacity, double refillRatePerSecond) {
        this.jedisPool = jedisPool;
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;

        // Lua script embedded as string
        this.luaScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate_per_ms = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local tokens = tonumber(redis.call("HGET", key, "tokens") or capacity)
            local last_refill = tonumber(redis.call("HGET", key, "last_refill") or now)
            local elapsed = now - last_refill
            tokens = math.min(capacity, tokens + elapsed * refill_rate_per_ms)
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end
            redis.call("HSET", key, "tokens", tokens, "last_refill", now)
            redis.call("PEXPIRE", key, 60000)
            return allowed
        """;
    }

    @Override
    public boolean allowRequest(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();

            Object result = jedis.eval(
                    luaScript,
                    Collections.singletonList("rate_limit:user:" + key),
                    // ARGV[1] = capacity, ARGV[2] = refillRatePerMillis, ARGV[3] = now_ms
                    Arrays.asList(String.valueOf(capacity), String.valueOf(refillRatePerMillis), String.valueOf(now))
            );

            return Integer.valueOf(result.toString()) == 1;
        }
    }
}
