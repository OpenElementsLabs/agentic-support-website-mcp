package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HostRateLimiter} using a virtual clock and a recording sleeper, so the
 * throttling logic is verified without real time passing.
 */
@DisplayName("Per-host rate limiter")
class HostRateLimiterTest {

    private final AtomicLong nowNanos = new AtomicLong(0);
    private final List<Long> sleeps = new ArrayList<>();
    /** Recording sleeper that also advances the virtual clock, simulating time passing. */
    private final LongConsumer sleeper = millis -> {
        sleeps.add(millis);
        nowNanos.addAndGet(millis * 1_000_000L);
    };

    @Test
    @DisplayName("consecutive requests to the same host are spaced by the configured interval")
    void sameHostIsThrottled() {
        HostRateLimiter limiter = new HostRateLimiter(2.0, nowNanos::get, sleeper);

        limiter.acquire("a"); // first request: no wait
        limiter.acquire("a");
        limiter.acquire("a");

        // 2 requests/second -> 500 ms between requests.
        assertThat(sleeps).containsExactly(500L, 500L);
    }

    @Test
    @DisplayName("different hosts are throttled independently")
    void differentHostsAreIndependent() {
        HostRateLimiter limiter = new HostRateLimiter(2.0, nowNanos::get, sleeper);

        limiter.acquire("a");
        limiter.acquire("b");

        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("a non-positive rate disables throttling")
    void nonPositiveRateDisablesThrottling() {
        HostRateLimiter limiter = new HostRateLimiter(0, nowNanos::get, sleeper);

        limiter.acquire("a");
        limiter.acquire("a");

        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("a crawl-delay tightens the effective interval beyond the configured rate")
    void crawlDelayTightensInterval() {
        HostRateLimiter limiter = new HostRateLimiter(2.0, nowNanos::get, sleeper); // base 500 ms

        limiter.acquire("a", Duration.ofSeconds(5)); // first request: no wait
        limiter.acquire("a", Duration.ofSeconds(5));

        // 5 s crawl-delay is stricter than the 500 ms configured interval.
        assertThat(sleeps).containsExactly(5000L);
    }
}
