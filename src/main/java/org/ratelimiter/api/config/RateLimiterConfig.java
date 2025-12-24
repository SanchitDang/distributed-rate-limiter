package org.ratelimiter.api.config;

import org.ratelimiter.core.LocalHotKeyRateLimiter;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.core.RedisFailMode;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RateLimiterConfig {

    /* ---------------- Redis ---------------- */

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setJmxEnabled(false);
        return new JedisPool(poolConfig, redisHost, redisPort, 2000);
    }

    /**
     * Configurable Redis failure behavior.
     * Future enhancement: read from application.yml / env variable
     */
    @Bean
    public RedisFailMode redisFailMode() {
        return RedisFailMode.FAIL_OPEN; // safe default
    }

    @Bean
    public RedisDynamicRateLimiter redisDynamicRateLimiter(
            JedisPool jedisPool,
            InMemoryRateLimiterMetrics metrics,
            RedisFailMode redisFailMode
    ) {
        return new RedisDynamicRateLimiter(jedisPool, metrics, redisFailMode);
    }

    /* ---------------- Hot-Key Limiter ---------------- */

    @Bean
    public LocalHotKeyRateLimiter localHotKeyRateLimiter(
            InMemoryRateLimiterMetrics metrics
    ) {
        return new LocalHotKeyRateLimiter(
                5,  // hot-key capacity
                1,  // refill rate
                4,  // shard count
                metrics
        );
    }
}
