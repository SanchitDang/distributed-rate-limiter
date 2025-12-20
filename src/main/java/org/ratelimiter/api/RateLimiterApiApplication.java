package org.ratelimiter.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RateLimiterApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApiApplication.class, args);
    }
}
