package org.ratelimiter.api.controller;

import org.ratelimiter.core.LocalHotKeyRateLimiter;
import org.ratelimiter.core.RedisHierarchicalRateLimiter;
import org.ratelimiter.policy.ResolvePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RateLimiterController {

    private final LocalHotKeyRateLimiter hotKeyLimiter;
    private final RedisHierarchicalRateLimiter redisRateLimiter;
    private final ResolvePolicy policyResolver;

    public RateLimiterController(
            LocalHotKeyRateLimiter hotKeyLimiter,
            RedisHierarchicalRateLimiter redisRateLimiter,
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

        // Local hot-key pre-filter: sheds load on a known-hot key before it hits Redis.
        // Only ever short-circuits a reject, never an allow - Redis stays the source of truth.
        if (!hotKeyLimiter.allowRequest(user)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded (hot-key) ❌");
        }

        // Redis authoritative path (hierarchical + dynamic)
        List<String> keys = policyResolver.resolveKeys(user, ip, org);
        boolean allowed = redisRateLimiter.allowRequest(keys).allowed();

        if (allowed) {
            return ResponseEntity.ok("Request allowed (redis) ✅");
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Rate limit exceeded ❌");
    }
}
