package org.ratelimiter.api.config;

import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RateLimiterConfig {

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();

        // Disable Commons Pool JMX
        poolConfig.setJmxEnabled(false);

        // Host + Port + Timeout (ms)
        return new JedisPool(poolConfig, "localhost", 6379, 2000);
    }


    @Bean
    public RedisDynamicRateLimiter redisDynamicRateLimiter(
            JedisPool jedisPool,
            InMemoryRateLimiterMetrics metrics
    ) {
        return new RedisDynamicRateLimiter(jedisPool, metrics);
    }
}
