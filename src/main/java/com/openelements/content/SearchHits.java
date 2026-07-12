package com.openelements.content;

import java.util.List;

/**
 * A page of search/list results.
 *
 * @param hits           the results on this page
 * @param estimatedTotal Meilisearch's estimate of the total matching documents
 */
public record SearchHits(List<SearchHit> hits, long estimatedTotal) {

    public SearchHits {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    static SearchHits empty() {
        return new SearchHits(List.of(), 0);
    }
}
