package com.openelements.content;

/**
 * The outcome of a {@link PageFetcher} request.
 *
 * @param status       the classified outcome
 * @param html         the response body, or {@code null} unless {@link Status#OK}
 * @param etag         the response {@code ETag}, or {@code null} if absent
 * @param lastModified the response {@code Last-Modified}, or {@code null} if absent
 * @param httpStatus   the raw HTTP status code ({@code 0} if the request failed before a response)
 */
public record FetchResult(
    Status status,
    String html,
    String etag,
    String lastModified,
    int httpStatus
) {

    /** Classified fetch outcome. */
    public enum Status {
        /** 2xx with a usable body. */
        OK,
        /** 304 Not Modified — the caller's cached copy is still current. */
        NOT_MODIFIED,
        /** 404/410 — the page is gone; the indexer should delete it. */
        NOT_FOUND,
        /** A transient failure that exhausted retries, an oversized body, or a non-retryable error. */
        ERROR
    }

    static FetchResult ok(String html, String etag, String lastModified, int httpStatus) {
        return new FetchResult(Status.OK, html, etag, lastModified, httpStatus);
    }

    static FetchResult notModified(String etag, String lastModified, int httpStatus) {
        return new FetchResult(Status.NOT_MODIFIED, null, etag, lastModified, httpStatus);
    }

    static FetchResult notFound(int httpStatus) {
        return new FetchResult(Status.NOT_FOUND, null, null, null, httpStatus);
    }

    static FetchResult error(int httpStatus) {
        return new FetchResult(Status.ERROR, null, null, null, httpStatus);
    }
}
