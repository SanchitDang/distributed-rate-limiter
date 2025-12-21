package org.ratelimiter.api.controller;

import org.ratelimiter.api.service.RateLimiterMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MetricsController {

    private final RateLimiterMetricsService metricsService;

    public MetricsController(RateLimiterMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        return metricsService.getMetrics();
    }
}
