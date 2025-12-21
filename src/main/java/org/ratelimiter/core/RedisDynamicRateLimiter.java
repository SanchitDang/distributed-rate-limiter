package org.ratelimiter.core;

import org.ratelimiter.metrics.RateLimiterMetrics;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

/**
 * Dynamic hierarchical token bucket
 * Step 6: Capacity and refill rate are fetched dynamically from Redis
 *
 * Failure Handling:
 * - FAIL_OPEN   → allow traffic if Redis is unavailable
 * - FAIL_CLOSED → block traffic if Redis is unavailable
 */
public class RedisDynamicRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final String luaScript;
    private final RateLimiterMetrics metrics;
    private final RedisFailMode failMode;

    public RedisDynamicRateLimiter(
            JedisPool jedisPool,
            RateLimiterMetrics metrics,
            RedisFailMode failMode
    ) {
        this.jedisPool = jedisPool;
        this.metrics = metrics;
        this.failMode = failMode;

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

        metrics.incrementTotalRequests();
        metrics.recordKeys(keys);

        try (Jedis jedis = jedisPool.getResource()) {

            long now = System.currentTimeMillis();
            long start = System.nanoTime();

            Object result = jedis.eval(luaScript, keys, List.of(String.valueOf(now)));

            long end = System.nanoTime();
            metrics.recordRedisLatency((end - start) / 1_000_000);

            boolean allowed = Integer.parseInt(result.toString()) == 1;

            if (allowed) {
                metrics.incrementRedisHit();
                metrics.incrementAllowed();
            } else {
                metrics.incrementRejected();
            }

            return allowed;

        } catch (Exception e) {
            // Redis failure handling
            metrics.incrementRedisFailure();

            if (failMode == RedisFailMode.FAIL_OPEN) {
                metrics.incrementAllowed();
                return true;
            }

            metrics.incrementRejected();
            return false;
        }
    }
}