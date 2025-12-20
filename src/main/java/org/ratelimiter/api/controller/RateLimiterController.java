package org.ratelimiter.api.controller;

import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RateLimiterController {

    private final RedisDynamicRateLimiter rateLimiter;

    public RateLimiterController(InMemoryRateLimiterMetrics metrics) {

        JedisPool jedisPool = new JedisPool("localhost", 6379);
        this.rateLimiter = new RedisDynamicRateLimiter(jedisPool, metrics);

        // Optional: preload dynamic configs
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("rate_limit:user:concurrent-user:config", "capacity", "10");
            jedis.hset("rate_limit:user:concurrent-user:config", "refill_rate", "5");

            jedis.hset("rate_limit:ip:192.168.0.1:config", "capacity", "20");
            jedis.hset("rate_limit:ip:192.168.0.1:config", "refill_rate", "10");

            jedis.hset("rate_limit:org:orgABC:config", "capacity", "50");
            jedis.hset("rate_limit:org:orgABC:config", "refill_rate", "15");
        }
    }

    @GetMapping("/request")
    public ResponseEntity<String> handleRequest(
            @RequestParam String user,
            @RequestParam String ip,
            @RequestParam String org
    ) {
        List<String> keys = Arrays.asList(
                "rate_limit:ip:" + ip,
                "rate_limit:user:" + user,
                "rate_limit:org:" + org
        );

        boolean allowed = rateLimiter.allowRequest(keys);

        if (allowed) {
            return ResponseEntity.ok("Request allowed ✅");
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded ❌");
        }
    }
}
