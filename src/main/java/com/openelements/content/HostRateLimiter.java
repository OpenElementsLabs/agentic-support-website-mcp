package com.openelements.content;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * A simple per-host rate limiter that spaces requests to the same host to a maximum rate, while
 * treating different hosts independently.
 *
 * <p>The clock and the sleep operation are injected so the limiter is fully testable without real
 * time: production wires {@link System#nanoTime()} and {@link Thread#sleep(long)}, tests supply a
 * virtual clock and a recording sleeper.
 */
public class HostRateLimiter {

    private final long minIntervalNanos;
    private final LongSupplier clockNanos;
    private final LongConsumer sleeperMillis;
    private final Map<String, Long> nextAllowedNanos = new HashMap<>();

    /**
     * @param permitsPerSecond maximum requests per second per host (values {@code <= 0} disable throttling)
     * @param clockNanos       monotonic time source in nanoseconds
     * @param sleeperMillis    blocks the caller for the given number of milliseconds
     */
    public HostRateLimiter(double permitsPerSecond, LongSupplier clockNanos, LongConsumer sleeperMillis) {
        this.minIntervalNanos = permitsPerSecond <= 0 ? 0L : (long) (1_000_000_000.0 / permitsPerSecond);
        this.clockNanos = clockNanos;
        this.sleeperMillis = sleeperMillis;
    }

    /**
     * Blocks until a request to {@code host} is allowed under the configured rate, then reserves the
     * next slot for that host.
     *
     * @param host the target host (an empty or {@code null} host shares a single bucket)
     */
    public void acquire(String host) {
        acquire(host, Duration.ZERO);
    }

    /**
     * Like {@link #acquire(String)}, but enforces at least {@code minInterval} between requests to the
     * host — used to honor a {@code robots.txt} {@code Crawl-delay}, taking the stricter of the
     * configured rate and the crawl delay.
     *
     * @param host        the target host
     * @param minInterval a minimum interval to enforce (e.g. from {@code Crawl-delay}); may be zero
     */
    public void acquire(String host, Duration minInterval) {
        long effectiveIntervalNanos = Math.max(minIntervalNanos, minInterval == null ? 0L : minInterval.toNanos());
        if (effectiveIntervalNanos == 0L) {
            return;
        }
        String key = host == null ? "" : host;
        long waitNanos;
        synchronized (this) {
            long now = clockNanos.getAsLong();
            long start = Math.max(now, nextAllowedNanos.getOrDefault(key, now));
            waitNanos = start - now;
            nextAllowedNanos.put(key, start + effectiveIntervalNanos);
        }
        if (waitNanos > 0) {
            sleeperMillis.accept(Math.max(1, Math.round(waitNanos / 1_000_000.0)));
        }
    }

    /** Production sleeper: blocks the current thread, restoring the interrupt flag if interrupted. */
    static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
