package com.xingyu.musicvault.openapi;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiRateLimiterTest {
    @Test
    void periodicallyCleansExpiredBuckets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-28T00:00:00Z"));
        OpenApiRateLimiter limiter = new OpenApiRateLimiter(clock);

        for (int i = 0; i < 99; i++) {
            assertTrue(limiter.allow("10.0.0." + i, 120));
        }
        assertEquals(99, limiter.bucketCount());

        clock.instant = Instant.parse("2026-05-28T00:01:01Z");
        assertTrue(limiter.allow("10.0.1.1", 120));

        assertEquals(1, limiter.bucketCount());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
