package org.ratelimiter.api.simulation;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RateLimiterLoadTest {

    public static void main(String[] args) throws InterruptedException {

        String user = "concurrent-user";
        String ip = "192.168.0.1";
        String org = "orgABC";
        int totalRequests = 100;   // total number of requests to send
        int concurrency = 20;      // number of threads hitting endpoint simultaneously

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        System.out.println("Starting hierarchical rate limiter load test...");

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    URL url = new URL("http://localhost:8080/api/request?user=" + user
                            + "&ip=" + ip + "&org=" + org);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();

                    if (code == 200) {
                        System.out.println(Thread.currentThread().getName() + " -> 200 ✅ Allowed");
                    } else if (code == 429) {
                        System.out.println(Thread.currentThread().getName() + " -> 429 ❌ Rate limited");
                    } else {
                        System.out.println(Thread.currentThread().getName() + " -> " + code);
                    }

                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        System.out.println("Load test completed!");
    }
}
