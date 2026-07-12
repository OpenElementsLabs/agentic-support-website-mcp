package com.openelements.content;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP-related beans for the content pipeline: the timeout-configured {@link RestClient} and the
 * {@link HostRateLimiter} used by {@link PageFetcher}.
 *
 * <p>Kept separate from {@link ContentConfig} so configuration that has nothing to do with HTTP
 * (e.g. the Meilisearch index settings) can be exercised in isolation without providing an HTTP
 * client.
 */
@Configuration
public class ContentHttpConfig {

    /**
     * The {@link RestClient} used by {@link PageFetcher}, configured with the source's request
     * timeout for both connect and read. The bot {@code User-Agent} is applied per request by the
     * fetcher rather than as a client default.
     *
     * @param builder    the Spring-provided builder
     * @param properties content properties supplying the request timeout
     * @return a timeout-configured client
     */
    @Bean
    RestClient contentRestClient(RestClient.Builder builder, ContentSourceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.requestTimeout());
        factory.setReadTimeout(properties.requestTimeout());
        return builder.requestFactory(factory).build();
    }

    /**
     * The per-host rate limiter used by {@link PageFetcher}, wired with the real wall clock and
     * {@link Thread#sleep(long)}.
     *
     * @param properties content properties supplying the per-host request rate
     * @return the rate limiter
     */
    @Bean
    HostRateLimiter hostRateLimiter(ContentSourceProperties properties) {
        return new HostRateLimiter(properties.rateLimitPerHost(), System::nanoTime, HostRateLimiter::sleepMillis);
    }
}
