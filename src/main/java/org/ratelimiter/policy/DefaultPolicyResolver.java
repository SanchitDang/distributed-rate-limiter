package org.ratelimiter.policy;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class DefaultPolicyResolver implements ResolvePolicy {

    @Override
    public List<String> resolveKeys(String user, String ip, String org) {

        List<String> keys = new ArrayList<>();

        // Order matters: strongest → weakest
        if (ip != null) {
            keys.add("rate_limit:ip:" + ip);
        }

        if (user != null) {
            keys.add("rate_limit:user:" + user);
        }

        if (org != null) {
            keys.add("rate_limit:org:" + org);
        }

        return keys;
    }
}
