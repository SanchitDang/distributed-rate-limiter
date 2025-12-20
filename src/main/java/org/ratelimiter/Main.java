package org.ratelimiter;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Distributed Rate Limiter ===");
        System.out.println("This application demonstrates the concept of distributed and hierarchical rate limiting.");
        System.out.println("For thorough testing and usage examples, please refer to the provided JUnit test classes.");
        System.out.println("You can run the tests to simulate various scenarios: hot-key mitigation, hierarchical limits, and dynamic Redis-based rate limiting.");
        System.out.println("Ensure Redis is running locally on port 6379 before executing tests.");
    }
}
