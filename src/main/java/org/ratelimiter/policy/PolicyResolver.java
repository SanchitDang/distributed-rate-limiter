package org.ratelimiter.policy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Step 9: Policy Resolver
 *
 * Resolves which rate limiting policies apply
 * to a given request.
 */
@Component
public class PolicyResolver implements ResolvePolicy {

    @Override
    public List<String> resolveKeys(String user, String ip, String org) {

        List<RateLimitPolicy> policies = new ArrayList<>();

        if (ip != null) {
            policies.add(new IpPolicy(ip));
        }

        if (user != null) {
            policies.add(new UserPolicy(user));
        }

        if (org != null) {
            policies.add(new OrgPolicy(org));
        }

        // Sort by priority (fail-fast)
        policies.sort(Comparator.comparingInt(RateLimitPolicy::priority));

        // Convert policies → Redis keys
        List<String> keys = new ArrayList<>();
        for (RateLimitPolicy policy : policies) {
            keys.add(policy.getKey());
        }

        return keys;
    }
}
