package org.ratelimiter.policy;

import java.util.List;

public interface ResolvePolicy {
    List<String> resolveKeys(String user, String ip, String org);
}
