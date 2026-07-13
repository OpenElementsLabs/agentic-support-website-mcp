package com.openelements.content;

import java.time.Duration;

/**
 * Decides whether the crawler may fetch a URL under the target host's {@code robots.txt}, and exposes
 * any {@code Crawl-delay} that host requests.
 *
 * <p>Modeled as an interface so the crawling components can be tested with the {@link #ALLOW_ALL}
 * no-op policy, while production uses {@link HttpRobotsPolicy}.
 */
public interface RobotsPolicy {

    /**
     * @param url an absolute URL
     * @return {@code true} if {@code robots.txt} permits fetching it for our user agent (a missing or
     *     unreachable {@code robots.txt} allows everything)
     */
    boolean isAllowed(String url);

    /**
     * @param host the target host
     * @return the {@code Crawl-delay} the host requests, or {@link Duration#ZERO} if none
     */
    Duration crawlDelay(String host);

    /** A permissive policy that allows every URL and requests no crawl delay. */
    RobotsPolicy ALLOW_ALL = new RobotsPolicy() {
        @Override
        public boolean isAllowed(String url) {
            return true;
        }

        @Override
        public Duration crawlDelay(String host) {
            return Duration.ZERO;
        }
    };
}
