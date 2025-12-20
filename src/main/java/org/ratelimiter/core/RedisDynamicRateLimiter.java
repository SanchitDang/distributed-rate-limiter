package org.ratelimiter.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

/**
 * Dynamic hierarchical token bucket
 * Step 6: Capacity and refill rate are fetched dynamically from Redis
 */
public class RedisDynamicRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final String luaScript;

    public RedisDynamicRateLimiter(JedisPool jedisPool) {
        this.jedisPool = jedisPool;

        this.luaScript = """
            local now = tonumber(ARGV[1])
            local allowed = 1
            local buckets = {}
            for i, key in ipairs(KEYS) do
                local configKey = key .. ":config"
                local capacity = tonumber(redis.call("HGET", configKey, "capacity") or 10)
                local refill_rate = tonumber(redis.call("HGET", configKey, "refill_rate") or 5)
                local refill_rate_per_ms = refill_rate / 1000.0
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

    @Override
    public boolean allowRequest(String key) {
        return allowRequest(List.of(key));
    }

    public boolean allowRequest(List<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();

            Object result = jedis.eval(luaScript, keys, List.of(String.valueOf(now)));
            return Integer.parseInt(result.toString()) == 1;
        }
    }
}
