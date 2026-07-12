package com.openelements.content;

import com.openelements.spring.base.services.search.SearchReadinessState;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-runs incremental ingestion over all enabled sources, reusing {@link ContentIndexer}
 * so scheduled refresh and startup bootstrap (spec 009) share identical semantics.
 *
 * <p>Driven by the configurable {@code open-elements.content.refresh-cron} (default hourly). A tick is
 * a no-op while the content pipeline is disabled or Meilisearch is still bootstrapping, and runs never
 * overlap — if a refresh is still in progress when the next tick fires, that tick is skipped. A source
 * that fails to index is logged and does not stop the others.
 *
 * <p>Only created when the Meilisearch stack is enabled (it depends on {@link ContentIndexer}).
 */
@Component
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class ContentRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContentRefreshScheduler.class);

    private final ContentSourceProperties properties;
    private final ContentIndexer indexer;
    private final SearchReadinessState readinessState;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ContentRefreshScheduler(ContentSourceProperties properties, ContentIndexer indexer,
                                   SearchReadinessState readinessState) {
        this.properties = properties;
        this.indexer = indexer;
        this.readinessState = readinessState;
    }

    /**
     * Refreshes every enabled source once. Bound to the {@code refresh-cron} property (default hourly).
     */
    @Scheduled(cron = "${open-elements.content.refresh-cron:0 0 * * * *}")
    public void refresh() {
        if (!properties.enabled()) {
            log.debug("Content pipeline disabled; skipping refresh");
            return;
        }
        if (readinessState.isBootstrapping()) {
            log.debug("Bootstrap in progress; skipping refresh");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Previous refresh still running; skipping this tick");
            return;
        }
        try {
            for (ContentSource source : properties.sources()) {
                if (!source.enabled()) {
                    continue;
                }
                try {
                    IndexReport report = indexer.indexSource(source);
                    log.info("Refreshed source {}: {}", source.id(), report);
                } catch (Exception e) {
                    log.warn("Refresh failed for source {}: {}", source.id(), e.toString());
                }
            }
        } finally {
            running.set(false);
        }
    }
}
