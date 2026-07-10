package org.ratelimiter.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;

/**
 * Distributed hierarchical token bucket using Redis + Lua script
 * Step 5: Multi-level rate limiting (IP -> User -> Org)
 *
 * Each key in the hierarchy carries its own capacity/refill_rate, read from
 * a "<key>:config" hash (same convention RedisDynamicRateLimiter uses), so
 * an org can have a bigger budget than a user, which can have a bigger
 * budget than a single IP.
 *
 * DSA / Concepts:
 * - Redis hash for bucket state
 * - Lua script for atomic operations across multiple keys
 * - Fail-fast hierarchical check, reports which level blocked the request
 * - Distributed consistency across JVMs
 */
public class RedisHierarchicalRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final RedisFailMode failMode;
    private final String luaScript;

    public RedisHierarchicalRateLimiter(JedisPool jedisPool, RedisFailMode failMode) {
        this.jedisPool = jedisPool;
        this.failMode = failMode;

        // Lua script for atomic refill + check + decrement across multiple keys.
        // Stops at the first key that's out of tokens (fail-fast) and reports its index.
        this.luaScript = """
            local now = tonumber(ARGV[1])
            local allowed = 1
            local blockedIndex = 0
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
                    blockedIndex = i
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
            return {allowed, blockedIndex}
        """;
    }

    /**
     * Single-key method required by RateLimiter interface
     */
    @Override
    public boolean allowRequest(String key) {
        return allowRequest(Arrays.asList(key)).allowed();
    }

    /**
     * Multi-key hierarchical rate limiting.
     *
     * @param keys ordered Redis keys, e.g. [ip, user, org]
     * @return result telling whether the request passed, and if not, which key blocked it
     */
    public Result allowRequest(List<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();

            Object raw = jedis.eval(luaScript, keys, List.of(String.valueOf(now)));
            List<?> result = (List<?>) raw;

            boolean allowed = Long.parseLong(result.get(0).toString()) == 1;
            int blockedIndex = Integer.parseInt(result.get(1).toString());
            String blockedKey = blockedIndex > 0 ? keys.get(blockedIndex - 1) : null;

            return new Result(allowed, blockedKey);

        } catch (Exception e) {
            return new Result(failMode == RedisFailMode.FAIL_OPEN, null);
        }
    }

    public record Result(boolean allowed, String blockedKey) {
    }
}
