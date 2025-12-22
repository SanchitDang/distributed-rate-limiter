package org.ratelimiter.api.controller;

import org.ratelimiter.core.LocalHotKeyRateLimiter;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.policy.ResolvePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RateLimiterController {

    private final LocalHotKeyRateLimiter hotKeyLimiter;
    private final RedisDynamicRateLimiter redisRateLimiter;
    private final ResolvePolicy policyResolver;

    public RateLimiterController(
            LocalHotKeyRateLimiter hotKeyLimiter,
            RedisDynamicRateLimiter redisRateLimiter,
            ResolvePolicy policyResolver
    ) {
        this.hotKeyLimiter = hotKeyLimiter;
        this.redisRateLimiter = redisRateLimiter;
        this.policyResolver = policyResolver;
    }

    @GetMapping("/request")
    public ResponseEntity<String> handleRequest(
            @RequestParam String user,
            @RequestParam String ip,
            @RequestParam String org
    ) {

        // 1 Hot-key fast path (local memory)
        if (hotKeyLimiter.allowRequest(user)) {
            return ResponseEntity.ok("Request allowed (local hot-key) ✅");
        }

        // 2 Redis authoritative path (hierarchical + dynamic)
        List<String> keys = policyResolver.resolveKeys(user, ip, org);
        boolean allowed = redisRateLimiter.allowRequest(keys);

        if (allowed) {
            return ResponseEntity.ok("Request allowed (redis) ✅");
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Rate limit exceeded ❌");
    }
}
