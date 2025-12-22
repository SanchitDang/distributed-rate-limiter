package org.ratelimiter.api.controller;

import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrometheusMetricsController {

    private final InMemoryRateLimiterMetrics metrics;

    public PrometheusMetricsController(InMemoryRateLimiterMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping(value = "/prometheus/metrics", produces = "text/plain; version=0.0.4")
    public String prometheusMetrics() {
        return """
            # HELP ratelimiter_allowed_requests Total allowed requests
            # TYPE ratelimiter_allowed_requests counter
            ratelimiter_allowed_requests %d

            # HELP ratelimiter_rejected_requests Total rejected requests
            # TYPE ratelimiter_rejected_requests counter
            ratelimiter_rejected_requests %d

            # HELP ratelimiter_local_hits Local hot-key hits
            # TYPE ratelimiter_local_hits counter
            ratelimiter_local_hits %d

            # HELP ratelimiter_redis_hits Redis hits
            # TYPE ratelimiter_redis_hits counter
            ratelimiter_redis_hits %d

            # HELP ratelimiter_redis_latency_avg_ms Average Redis latency in ms
            # TYPE ratelimiter_redis_latency_avg_ms gauge
            ratelimiter_redis_latency_avg_ms %.2f

            # HELP ratelimiter_key_cardinality Active key cardinality
            # TYPE ratelimiter_key_cardinality gauge
            ratelimiter_key_cardinality %d
            """.formatted(
                metrics.allowed.get(),
                metrics.rejected.get(),
                metrics.localHits.get(),
                metrics.redisHits.get(),
                metrics.getAverageRedisLatencyMs(),
                metrics.getKeyCardinality()
        );
    }
}
