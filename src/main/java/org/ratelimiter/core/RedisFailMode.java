package org.ratelimiter.core;

public enum RedisFailMode {
    FAIL_OPEN,   // allow traffic if Redis is down
    FAIL_CLOSED  // block traffic if Redis is down
}
