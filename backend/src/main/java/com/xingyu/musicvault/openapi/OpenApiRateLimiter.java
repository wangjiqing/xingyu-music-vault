package com.xingyu.musicvault.openapi;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class OpenApiRateLimiter {
    private static final long WINDOW_MILLIS = 60_000L;
    private static final long CLEANUP_INTERVAL_REQUESTS = 100L;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final Clock clock;

    public OpenApiRateLimiter() {
        this(Clock.systemUTC());
    }

    OpenApiRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String clientIp, int limit) {
        if (limit < 1) {
            return false;
        }
        long window = clock.millis() / WINDOW_MILLIS;
        if (requestCount.incrementAndGet() % CLEANUP_INTERVAL_REQUESTS == 0) {
            cleanupExpiredBuckets(window);
        }
        Bucket bucket = buckets.computeIfAbsent(clientIp, ignored -> new Bucket(window));
        synchronized (bucket) {
            if (bucket.window != window) {
                bucket.window = window;
                bucket.count = 0;
            }
            if (bucket.count >= limit) {
                return false;
            }
            bucket.count++;
            return true;
        }
    }

    void reset() {
        buckets.clear();
        requestCount.set(0);
    }

    int bucketCount() {
        return buckets.size();
    }

    private void cleanupExpiredBuckets(long currentWindow) {
        buckets.entrySet().removeIf(entry -> entry.getValue().window < currentWindow);
    }

    private static final class Bucket {
        private volatile long window;
        private int count;

        private Bucket(long window) {
            this.window = window;
        }
    }
}
