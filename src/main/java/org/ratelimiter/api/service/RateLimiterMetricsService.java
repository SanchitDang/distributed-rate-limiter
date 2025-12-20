package org.ratelimiter.api.service;

import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RateLimiterMetricsService {

    private final InMemoryRateLimiterMetrics metrics;

    public RateLimiterMetricsService(InMemoryRateLimiterMetrics metrics) {
        this.metrics = metrics;
    }

    public Map<String, Long> getMetrics() {
        return Map.of(
                "allowed_requests", metrics.allowed.get(),
                "rejected_requests", metrics.rejected.get()
        );
    }
}
