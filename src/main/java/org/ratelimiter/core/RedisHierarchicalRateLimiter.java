package org.ratelimiter.core;

import org.ratelimiter.metrics.RateLimiterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RedisHierarchicalRateLimiter.class);

    private final JedisPool jedisPool;
    private final RateLimiterMetrics metrics;
    private final RedisFailMode failMode;
    private final String luaScript;

    public RedisHierarchicalRateLimiter(JedisPool jedisPool, RateLimiterMetrics metrics, RedisFailMode failMode) {
        this.jedisPool = jedisPool;
        this.metrics = metrics;
        this.failMode = failMode;

        // Lua script for atomic refill + check + decrement across multiple keys.
        // Stops at the first key that's out of tokens (fail-fast) and reports its
        // index, plus whether any key was missing its config hash (defaults used).
        this.luaScript = """
            local time = redis.call("TIME")
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local allowed = 1
            local blockedIndex = 0
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
            return {allowed, blockedIndex, missingConfig}
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
        metrics.incrementTotalRequests();
        metrics.recordKeys(keys);

        try (Jedis jedis = jedisPool.getResource()) {
            long start = System.nanoTime();

            Object raw = jedis.eval(luaScript, keys, List.of());

            long end = System.nanoTime();
            metrics.recordRedisLatency((end - start) / 1_000_000);

            List<?> result = (List<?>) raw;
            boolean allowed = Long.parseLong(result.get(0).toString()) == 1;
            int blockedIndex = Integer.parseInt(result.get(1).toString());
            boolean missingConfig = Integer.parseInt(result.get(2).toString()) == 1;
            String blockedKey = blockedIndex > 0 ? keys.get(blockedIndex - 1) : null;

            if (missingConfig) {
                log.warn("no rate-limit config found for one or more of {}, defaults were used", keys);
            }

            if (allowed) {
                metrics.incrementRedisHit();
                metrics.incrementAllowed();
            } else {
                metrics.incrementRejected();
                recordBlockedLevel(blockedKey);
            }

            return new Result(allowed, blockedKey);

        } catch (Exception e) {
            log.warn("redis call failed, applying {}: {}", failMode, e.toString());
            metrics.incrementRedisFailure();

            boolean allowed = failMode == RedisFailMode.FAIL_OPEN;
            if (allowed) {
                metrics.incrementAllowed();
            } else {
                metrics.incrementRejected();
            }
            return new Result(allowed, null);
        }
    }

    private void recordBlockedLevel(String blockedKey) {
        if (blockedKey == null) {
            return;
        }
        if (blockedKey.startsWith("rate_limit:ip:")) {
            metrics.incrementBlockedIp();
        } else if (blockedKey.startsWith("rate_limit:user:")) {
            metrics.incrementBlockedUser();
        } else if (blockedKey.startsWith("rate_limit:org:")) {
            metrics.incrementBlockedOrg();
        }
    }

    public record Result(boolean allowed, String blockedKey) {
    }
}
