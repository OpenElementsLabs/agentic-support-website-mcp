package com.openelements.content;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Performs robust, polite HTTP GETs of discovered pages.
 *
 * <p>Each request carries the configured bot {@code User-Agent} and, when a prior {@code ETag} or
 * {@code Last-Modified} is known, the matching conditional headers so an unchanged page returns
 * {@code 304}. The body is read under a size cap (bounded memory). Requests are spaced per host by a
 * {@link HostRateLimiter}, and transient failures (5xx and network/timeout errors) are retried with
 * exponential backoff; {@code 404}/{@code 410} are treated as deletions and never retried.
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 200;

    private final RestClient restClient;
    private final String userAgent;
    private final long maxBodyBytes;
    private final HostRateLimiter rateLimiter;
    private final LongConsumer backoffSleeper;

    @Autowired
    public PageFetcher(RestClient contentRestClient, ContentSourceProperties properties,
                       HostRateLimiter rateLimiter) {
        this(contentRestClient, properties, rateLimiter, PageFetcher::sleep);
    }

    PageFetcher(RestClient restClient, ContentSourceProperties properties,
                HostRateLimiter rateLimiter, LongConsumer backoffSleeper) {
        this.restClient = restClient;
        this.userAgent = properties.userAgent();
        this.maxBodyBytes = properties.maxBodyBytes().toBytes();
        this.rateLimiter = rateLimiter;
        this.backoffSleeper = backoffSleeper;
    }

    /**
     * Fetches a URL, retrying transient failures with backoff.
     *
     * @param url           the URL to fetch
     * @param priorEtag     a previously seen {@code ETag} for conditional requests, or {@code null}
     * @param priorLastmod  a previously seen {@code Last-Modified} for conditional requests, or {@code null}
     * @return the classified {@link FetchResult}
     */
    public FetchResult fetch(String url, String priorEtag, String priorLastmod) {
        int attempt = 0;
        while (true) {
            attempt++;
            FetchResult result;
            try {
                result = attemptOnce(url, priorEtag, priorLastmod);
            } catch (ResourceAccessException e) {
                log.warn("Transient network error fetching {} (attempt {}/{}): {}",
                    url, attempt, MAX_ATTEMPTS, e.toString());
                result = FetchResult.error(0);
            }
            boolean transientFailure = result.status() == FetchResult.Status.ERROR
                && (result.httpStatus() == 0 || result.httpStatus() >= 500);
            if (!transientFailure || attempt >= MAX_ATTEMPTS) {
                return result;
            }
            backoffSleeper.accept(BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
        }
    }

    private FetchResult attemptOnce(String url, String priorEtag, String priorLastmod) {
        rateLimiter.acquire(hostOf(url));
        return restClient.get()
            .uri(URI.create(url))
            .header(HttpHeaders.USER_AGENT, userAgent)
            .headers(headers -> {
                if (priorEtag != null && !priorEtag.isBlank()) {
                    headers.set(HttpHeaders.IF_NONE_MATCH, priorEtag);
                }
                if (priorLastmod != null && !priorLastmod.isBlank()) {
                    headers.set(HttpHeaders.IF_MODIFIED_SINCE, priorLastmod);
                }
            })
            .exchange((request, response) -> handle(url, response));
    }

    private FetchResult handle(String url, ClientHttpResponse response) throws IOException {
        int code = response.getStatusCode().value();
        HttpHeaders headers = response.getHeaders();
        String etag = headers.getETag();
        String lastModified = headers.getFirst(HttpHeaders.LAST_MODIFIED);

        if (code == 304) {
            return FetchResult.notModified(etag, lastModified, code);
        }
        if (code == 404 || code == 410) {
            return FetchResult.notFound(code);
        }
        if (response.getStatusCode().is2xxSuccessful()) {
            byte[] body = readCapped(response);
            if (body == null) {
                log.warn("Aborting oversized body for {} (exceeds {} bytes)", url, maxBodyBytes);
                return FetchResult.error(code);
            }
            return FetchResult.ok(new String(body, charsetOf(headers)), etag, lastModified, code);
        }
        return FetchResult.error(code);
    }

    /** Reads up to {@code maxBodyBytes} bytes; returns {@code null} if the body exceeds the cap. */
    private byte[] readCapped(ClientHttpResponse response) throws IOException {
        int readLimit = (int) Math.min(maxBodyBytes + 1, Integer.MAX_VALUE);
        byte[] bytes = response.getBody().readNBytes(readLimit);
        return bytes.length > maxBodyBytes ? null : bytes;
    }

    private static Charset charsetOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
