package org.ratelimiter;

import org.junit.jupiter.api.*;
import org.ratelimiter.core.RedisDynamicRateLimiter;
import org.ratelimiter.metrics.InMemoryRateLimiterMetrics;
import org.ratelimiter.policy.ResolvePolicy;
import org.ratelimiter.policy.DefaultPolicyResolver;
import redis.clients.jedis.JedisPool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HierarchicalRateLimiterTest {

    private RedisDynamicRateLimiter rateLimiter;
    private ResolvePolicy policyResolver;
    private JedisPool jedisPool;

    @BeforeAll
    public void setup() {
        // Initialize JedisPool
        jedisPool = new JedisPool("localhost", 6379);

        // Metrics collector
        InMemoryRateLimiterMetrics metrics = new InMemoryRateLimiterMetrics();

        // Redis Dynamic Rate Limiter
        rateLimiter = new RedisDynamicRateLimiter(jedisPool, metrics);

        // Policy Resolver
        policyResolver = new DefaultPolicyResolver();

        // Preload rate limit configs
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("rate_limit:user:concurrent-user:config", "capacity", "10");
            jedis.hset("rate_limit:user:concurrent-user:config", "refill_rate", "5");

            jedis.hset("rate_limit:ip:192.168.0.1:config", "capacity", "5");
            jedis.hset("rate_limit:ip:192.168.0.1:config", "refill_rate", "2");

            jedis.hset("rate_limit:org:orgABC:config", "capacity", "20");
            jedis.hset("rate_limit:org:orgABC:config", "refill_rate", "10");
        }
    }

    @Test
    @DisplayName("Test user-level rate limiting")
    public void testUserLevelLimit() {
        String user = "concurrent-user";
        List<String> keys = policyResolver.resolveKeys(user, null, null);

        boolean allowed = true;
        for (int i = 0; i < 12; i++) {
            allowed = rateLimiter.allowRequest(keys);
        }
        assertFalse(allowed, "User-level limit should block after capacity reached");
    }

    @Test
    @DisplayName("Test IP-level rate limiting")
    public void testIpLevelLimit() {
        String ip = "192.168.0.1";
        List<String> keys = policyResolver.resolveKeys(null, ip, null);

        boolean allowed = true;
        for (int i = 0; i < 6; i++) {
            allowed = rateLimiter.allowRequest(keys);
        }
        assertFalse(allowed, "IP-level limit should block after capacity reached");
    }

    @Test
    @DisplayName("Test org-level rate limiting")
    public void testOrgLevelLimit() {
        String org = "orgABC";
        List<String> keys = policyResolver.resolveKeys(null, null, org);

        boolean allowed = true;
        for (int i = 0; i < 22; i++) {
            allowed = rateLimiter.allowRequest(keys);
        }
        assertFalse(allowed, "Org-level limit should block after capacity reached");
    }

    @Test
    @DisplayName("Test combined user + IP + org hierarchical limit")
    public void testCombinedLimit() {
        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";

        List<String> keys = policyResolver.resolveKeys(user, ip, org);

        boolean allowed = true;
        for (int i = 0; i < 100; i++) {
            allowed = rateLimiter.allowRequest(keys);
        }

        assertFalse(allowed, "Combined limit should block at the first policy that exceeds capacity");
    }

    @AfterAll
    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
