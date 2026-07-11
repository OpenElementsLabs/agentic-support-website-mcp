package com.openelements.content;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration for the content pipeline, bound from the {@code open-elements.content.*} properties.
 *
 * <p>Holds the list of {@link ContentSource sources} plus the global crawl settings shared across
 * sources. Optional values fall back to sensible defaults (convention over configuration) so a
 * minimal configuration only needs to declare the sources.
 *
 * @param enabled           whether the content pipeline is active
 * @param refreshCron       cron expression for the incremental re-crawl (spec 010); defaults to hourly
 * @param userAgent         {@code User-Agent} sent when fetching (spec 005)
 * @param rateLimitPerHost  maximum requests per second per host (spec 005)
 * @param requestTimeout    HTTP request timeout (spec 005)
 * @param maxBodyBytes      maximum response body size accepted (spec 005)
 * @param sources           the configured content sources
 */
@ConfigurationProperties("open-elements.content")
public record ContentSourceProperties(
    boolean enabled,
    String refreshCron,
    String userAgent,
    double rateLimitPerHost,
    Duration requestTimeout,
    DataSize maxBodyBytes,
    List<ContentSource> sources
) {

    private static final String DEFAULT_REFRESH_CRON = "0 0 * * * *";
    private static final String DEFAULT_USER_AGENT =
        "OpenElementsContentBot/1.0 (+https://open-elements.com)";
    private static final double DEFAULT_RATE_LIMIT_PER_HOST = 2.0;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final DataSize DEFAULT_MAX_BODY_BYTES = DataSize.ofMegabytes(5);

    public ContentSourceProperties {
        refreshCron = (refreshCron == null || refreshCron.isBlank()) ? DEFAULT_REFRESH_CRON : refreshCron;
        userAgent = (userAgent == null || userAgent.isBlank()) ? DEFAULT_USER_AGENT : userAgent;
        rateLimitPerHost = rateLimitPerHost <= 0 ? DEFAULT_RATE_LIMIT_PER_HOST : rateLimitPerHost;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        maxBodyBytes = maxBodyBytes == null ? DEFAULT_MAX_BODY_BYTES : maxBodyBytes;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
