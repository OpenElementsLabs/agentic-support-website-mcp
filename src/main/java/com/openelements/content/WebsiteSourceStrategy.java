package com.openelements.content;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ContentSourceStrategy} for {@link SourceType#WEBSITE}: discovery via {@link SitemapCrawler},
 * fetching via {@link PageFetcher}, and extraction via {@link ContentExtractor}.
 *
 * <p>The fetcher's classified {@link FetchResult} is mapped to a {@link FetchOutcome}: {@code 304} →
 * {@code UNCHANGED}, {@code 404}/{@code 410} → {@code DELETE}, transient/other errors → {@code SKIP},
 * and a successful fetch with extractable content → {@code INDEX} carrying a {@link ContentDocument}.
 * An empty extraction is treated as {@code SKIP}.
 */
@Component
public class WebsiteSourceStrategy implements ContentSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(WebsiteSourceStrategy.class);

    private final SitemapCrawler sitemapCrawler;
    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;

    public WebsiteSourceStrategy(SitemapCrawler sitemapCrawler, PageFetcher pageFetcher,
                                 ContentExtractor contentExtractor) {
        this.sitemapCrawler = sitemapCrawler;
        this.pageFetcher = pageFetcher;
        this.contentExtractor = contentExtractor;
    }

    @Override
    public SourceType type() {
        return SourceType.WEBSITE;
    }

    @Override
    public List<DiscoveredItem> discover(ContentSource source) {
        return sitemapCrawler.discover(source);
    }

    @Override
    public FetchOutcome fetch(ContentSource source, DiscoveredItem item) {
        // Prior ETag/Last-Modified for the conditional GET are supplied by the indexer's diff
        // (spec 008); until then this passes null and relies on the sitemap lastmod diff.
        FetchResult result = pageFetcher.fetch(item.url(), null, null);
        return switch (result.status()) {
            case NOT_MODIFIED -> FetchOutcome.unchanged();
            case NOT_FOUND -> FetchOutcome.delete();
            case ERROR -> {
                log.warn("Skipping {}: fetch failed (http {})", item.url(), result.httpStatus());
                yield FetchOutcome.skip();
            }
            case OK -> toIndexOutcome(source, item, result);
        };
    }

    private FetchOutcome toIndexOutcome(ContentSource source, DiscoveredItem item, FetchResult result) {
        ExtractedContent extracted = contentExtractor.extract(source, item.url(), result.html());
        if (extracted.body() == null || extracted.body().isBlank()) {
            log.warn("Skipping {}: no extractable content", item.url());
            return FetchOutcome.skip();
        }
        ContentDocument document = new ContentDocument(
            ContentDocument.id(source.id(), item.url()),
            source.id(),
            extracted.locale(),
            item.url(),
            extracted.title(),
            extracted.excerpt(),
            extracted.body(),
            extracted.author(),
            extracted.categories(),
            extracted.publishedDate(),
            item.lastmod(),
            extracted.previewImage());
        return FetchOutcome.index(document);
    }
}
