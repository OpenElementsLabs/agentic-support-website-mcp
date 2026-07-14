package com.openelements.content;

import java.util.List;

/**
 * A page of search/list results, optionally with category facet counts.
 *
 * @param hits           the results on this page
 * @param estimatedTotal Meilisearch's estimate of the total matching documents
 * @param facets         category facet counts for the query (empty unless facets were requested)
 */
public record SearchHits(List<SearchHit> hits, long estimatedTotal, List<CategoryCount> facets) {

    public SearchHits {
        hits = hits == null ? List.of() : List.copyOf(hits);
        facets = facets == null ? List.of() : List.copyOf(facets);
    }

    static SearchHits empty() {
        return new SearchHits(List.of(), 0, List.of());
    }
}
