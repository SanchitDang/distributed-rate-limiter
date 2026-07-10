package org.ratelimiter.core;

import org.ratelimiter.metrics.RateLimiterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RedisDynamicRateLimiter.class);

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
            local time = redis.call("TIME")
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local allowed = 1
            local missingConfig = 0
            local buckets = {}
            for i, key in ipairs(KEYS) do
                local configKey = key .. ":config"
                local capacityRaw = redis.call("HGET", configKey, "capacity")
                local refillRaw = redis.call("HGET", configKey, "refill_rate")
                if not capacityRaw or not refillRaw then
                    missingConfig = 1
                end
                local capacity = tonumber(capacityRaw or 10)
                local refill_rate = tonumber(refillRaw or 5)
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
            return {allowed, missingConfig}
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

            long start = System.nanoTime();

            Object raw = jedis.eval(luaScript, keys, List.of());

            long end = System.nanoTime();
            metrics.recordRedisLatency((end - start) / 1_000_000);

            List<?> result = (List<?>) raw;
            boolean allowed = Long.parseLong(result.get(0).toString()) == 1;
            boolean missingConfig = Integer.parseInt(result.get(1).toString()) == 1;

            if (missingConfig) {
                log.warn("no rate-limit config found for one or more of {}, defaults were used", keys);
            }

            if (allowed) {
                metrics.incrementRedisHit();
                metrics.incrementAllowed();
            } else {
                metrics.incrementRejected();
            }

            return allowed;

        } catch (Exception e) {
            log.warn("redis call failed, applying {}: {}", failMode, e.toString());
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