package org.ratelimiter.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.Set;

/**
 * Lets capacity/refill be changed for a key without redeploying - just an
 * HSET on the same "<key>:config" hash the Lua scripts already read from.
 * No auth: this is meant for local/demo use, not a real admin surface.
 */
@RestController
@RequestMapping("/admin")
public class AdminConfigController {

    private static final Set<String> VALID_SCOPES = Set.of("ip", "user", "org");

    private final JedisPool jedisPool;

    public AdminConfigController(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @PostMapping("/config")
    public ResponseEntity<String> setConfig(
            @RequestParam String scope,
            @RequestParam String id,
            @RequestParam long capacity,
            @RequestParam double refillRate
    ) {
        if (!VALID_SCOPES.contains(scope)) {
            return ResponseEntity.badRequest().body("scope must be one of " + VALID_SCOPES);
        }
        if (id.isBlank() || capacity <= 0 || refillRate < 0) {
            return ResponseEntity.badRequest().body("id must be non-blank, capacity must be positive, refillRate must not be negative");
        }

        String configKey = "rate_limit:" + scope + ":" + id + ":config";
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(configKey, Map.of(
                    "capacity", String.valueOf(capacity),
                    "refill_rate", String.valueOf(refillRate)
            ));
        }

        return ResponseEntity.ok("updated " + configKey);
    }
}
