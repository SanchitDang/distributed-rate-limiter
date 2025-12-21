package org.ratelimiter.api.controller;

import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.policy.ResolvePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RateLimiterController {

    private final RedisDynamicRateLimiter rateLimiter;
    private final ResolvePolicy policyResolver;

    public RateLimiterController(
            RedisDynamicRateLimiter rateLimiter,
            ResolvePolicy policyResolver
    ) {
        this.rateLimiter = rateLimiter;
        this.policyResolver = policyResolver;
    }

    @GetMapping("/request")
    public ResponseEntity<String> handleRequest(
            @RequestParam String user,
            @RequestParam String ip,
            @RequestParam String org
    ) {

        List<String> keys = policyResolver.resolveKeys(user, ip, org);
        boolean allowed = rateLimiter.allowRequest(keys);

        if (allowed) {
            return ResponseEntity.ok("Request allowed ✅");
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded ❌");
        }
    }
}
