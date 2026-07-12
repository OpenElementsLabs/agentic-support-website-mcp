package com.openelements.content;

/**
 * What a {@link ContentSourceStrategy} determined should happen with one discovered item, plus the
 * document when there is one to index.
 *
 * <p>This widens the design's {@code ContentDocument fetch(item)} sketch so the non-happy paths
 * (unchanged, deleted, skipped) are first-class rather than encoded as {@code null} or exceptions.
 *
 * @param result   the action the indexer should take
 * @param document the document to upsert (non-null only for {@link Result#INDEX})
 */
public record FetchOutcome(Result result, ContentDocument document) {

    /** The action the indexer should take for a discovered item. */
    public enum Result {
        /** A document to upsert into the index. */
        INDEX,
        /** Unchanged since last seen (e.g. 304); nothing to do. */
        UNCHANGED,
        /** The item no longer exists (e.g. 404/410); remove it from the index. */
        DELETE,
        /** Fetch or extraction failed for this item; log and continue with the batch. */
        SKIP
    }

    static FetchOutcome index(ContentDocument document) {
        return new FetchOutcome(Result.INDEX, document);
    }

    static FetchOutcome unchanged() {
        return new FetchOutcome(Result.UNCHANGED, null);
    }

    static FetchOutcome delete() {
        return new FetchOutcome(Result.DELETE, null);
    }

    static FetchOutcome skip() {
        return new FetchOutcome(Result.SKIP, null);
    }
}
