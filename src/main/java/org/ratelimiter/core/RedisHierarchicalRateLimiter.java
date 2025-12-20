package org.ratelimiter.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;

/**
 * Distributed hierarchical token bucket using Redis + Lua script
 * Step 5: Multi-level rate limiting (IP -> User -> Org)
 *
 * DSA / Concepts:
 * - Redis hash for bucket state
 * - Lua script for atomic operations across multiple keys
 * - Sliding window refill logic
 * - Fail-fast hierarchical check
 * - Distributed consistency across JVMs
 */
public class RedisHierarchicalRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final long capacity;
    private final double refillRatePerMillis;
    private final String luaScript;

    public RedisHierarchicalRateLimiter(JedisPool jedisPool, long capacity, double refillRatePerSecond) {
        this.jedisPool = jedisPool;
        this.capacity = capacity;
        this.refillRatePerMillis = refillRatePerSecond / 1000.0;

        // Lua script for atomic refill + check + decrement across multiple keys
        this.luaScript = """
            local capacity = tonumber(ARGV[1])
            local refill_rate_per_ms = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local allowed = 1
            local buckets = {}
            for i, key in ipairs(KEYS) do
                local tokens = tonumber(redis.call("HGET", key, "tokens") or capacity)
                local last_refill = tonumber(redis.call("HGET", key, "last_refill") or now)
                local elapsed = now - last_refill
                tokens = math.min(capacity, tokens + elapsed * refill_rate_per_ms)
                if tokens < 1 then
                    allowed = 0
                    break
                end
                buckets[i] = tokens
            end
            if allowed == 1 then
                for i, key in ipairs(KEYS) do
                    local tokens = buckets[i] - 1
                    redis.call("HSET", key, "tokens", tokens, "last_refill", now)
                    redis.call("PEXPIRE", key, 60000)
                end
            end
            return allowed
        """;
    }

    /**
     * Single-key method required by RateLimiter interface
     * Converts the single key to a list internally
     */
    @Override
    public boolean allowRequest(String key) {
        // Treat single key as a list of size 1
        return allowRequest(Arrays.asList(key));
    }

    /**
     * Multi-key hierarchical rate limiting
     *
     * @param keys list of Redis keys (IP, User, Org)
     * @return true if request allowed across all keys, false if any key exceeds limit
     */
    public boolean allowRequest(List<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();

            Object result = jedis.eval(luaScript, keys,
                    Arrays.asList(
                            String.valueOf(capacity),
                            String.valueOf(refillRatePerMillis),
                            String.valueOf(now)
                    )
            );

            return Integer.parseInt(result.toString()) == 1;
        }
    }
}
