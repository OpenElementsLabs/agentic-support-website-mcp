package com.openelements.content;

import java.util.List;
import java.util.Map;

/**
 * The persistence seam the {@link ContentIndexer} uses to read and write index state.
 *
 * <p>The Meilisearch content index <em>is</em> the state: {@link #loadState(String)} returns the
 * stored change-marker per document so the indexer can diff without a separate datastore. Isolating
 * these three operations keeps the indexer's orchestration logic testable with an in-memory
 * implementation, independent of a running Meilisearch.
 */
public interface ContentIndexStore {

    /**
     * Loads the currently-indexed documents for a source.
     *
     * @param source the source id
     * @return a map of document id → {@link StoredDocument} (its URL and change marker)
     */
    Map<String, StoredDocument> loadState(String source);

    /**
     * Upserts documents (keyed by their {@code id}); existing documents with the same id are replaced.
     *
     * @param documents document attribute maps (from {@link ContentDocument#toMap()})
     * @return the number of documents written
     */
    int upsert(List<Map<String, Object>> documents);

    /**
     * Removes a document from the index.
     *
     * @param id the stable document id
     */
    void delete(String id);

    /**
     * The stored projection of an indexed document needed for diffing.
     *
     * @param url     the document URL
     * @param lastmod the stored change marker, or {@code null}
     */
    record StoredDocument(String url, String lastmod) {
    }
}
