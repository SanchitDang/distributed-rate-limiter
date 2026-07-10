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
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String org
    ) {
        if (user != null && user.isBlank()) {
            return ResponseEntity.badRequest().body("user must not be blank");
        }
        if (ip != null && ip.isBlank()) {
            return ResponseEntity.badRequest().body("ip must not be blank");
        }
        if (org != null && org.isBlank()) {
            return ResponseEntity.badRequest().body("org must not be blank");
        }
        if (user == null && ip == null && org == null) {
            return ResponseEntity.badRequest().body("at least one of user, ip, org is required");
        }

        // Local hot-key pre-filter: sheds load on a known-hot key before it hits Redis.
        // Only ever short-circuits a reject, never an allow - Redis stays the source of truth.
        // Skipped when there's no user, since it's a per-user local budget.
        if (user != null && !hotKeyLimiter.allowRequest(user)) {
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
