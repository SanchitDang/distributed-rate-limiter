package org.ratelimiter.api.config;

import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public InMemoryRateLimiterMetrics inMemoryRateLimiterMetrics() {
        return new InMemoryRateLimiterMetrics();
    }
}
