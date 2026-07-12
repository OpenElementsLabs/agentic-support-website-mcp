package com.openelements.content;

import java.util.List;

/**
 * Strategy for discovering and fetching the content of one {@link SourceType}.
 *
 * <p>The indexer (spec 008) depends only on this interface, so a new source type (e.g. {@code git},
 * spec 016) is added as a new bean without changing the orchestration or the Meilisearch flow. One
 * implementation exists per {@link SourceType}; {@link SourceStrategyRegistry} selects the right one.
 */
public interface ContentSourceStrategy {

    /** @return the source type this strategy handles */
    SourceType type();

    /**
     * Discovers the candidate items of a source.
     *
     * @param source the source to discover
     * @return the discovered items (URL + change marker)
     */
    List<DiscoveredItem> discover(ContentSource source);

    /**
     * Fetches a single discovered item and decides what the indexer should do with it.
     *
     * @param source the owning source
     * @param item   the item to fetch
     * @return the outcome (index/unchanged/delete/skip)
     */
    FetchOutcome fetch(ContentSource source, DiscoveredItem item);
}
