# Spec Index

Specs derived from [`docs/roadmap.md`](../roadmap.md), which itself is derived from
[`docs/content-mcp-technical-design.md`](../content-mcp-technical-design.md). One spec per
roadmap step, to be implemented sequentially (each builds on the previous).

| ID  | Spec-Folder | Name | Areas | Description | GitHub Issue | Status |
|-----|-------------|------|-------|-------------|--------------|--------|
| 001 | 001-project-skeleton | Project skeleton | build, architecture, mcp | Standalone Spring Boot app that pulls in `spring-services` and imports its MCP + search config | #1 | done |
| 002 | 002-content-source-config | Content source config | backend, architecture, configuration | `ContentSource` abstraction + `@ConfigurationProperties` + Ant-glob URL matching | #3 | done |
| 003 | 003-content-document-index | Content document & index | backend, search, data-model | `ContentDocument` record + Meilisearch `IndexSettings` bean | #5 | done |
| 004 | 004-sitemap-crawler | Sitemap crawler | backend, crawler | `SitemapCrawler` — sitemap discovery of URLs + lastmod | #7 | done |
| 005 | 005-page-fetcher | Page fetcher | backend, crawler | `PageFetcher` — robust HTTP fetch (ETag/IMS, rate limit, retry) | #9 | done |
| 006 | 006-content-extractor | Content extractor | backend, crawler | `ContentExtractor` — jsoup content + metadata extraction | #11 | done |
| 007 | 007-source-strategy | Source strategy | backend, architecture | `ContentSourceStrategy` interface + `WebsiteSourceStrategy` | #13 | done |
| 008 | 008-content-indexer | Content indexer | backend, search, crawler | `ContentIndexer` — orchestration, diff, upsert/delete | #15 | done |
| 009 | 009-content-bootstrap-step | Content bootstrap step | backend, search | `ContentBootstrapStep` — initial reindex via `SearchIndexBootstrapStep` | — | open |
| 010 | 010-content-refresh-scheduler | Content refresh scheduler | backend, scheduling | `ContentRefreshScheduler` — `@Scheduled` incremental re-crawl | — | open |
| 011 | 011-content-search-service | Content search service | backend, search | `ContentSearchService` — `multiSearch` + highlighting facade | — | open |
| 012 | 012-content-mcp-tools | Content MCP tools | backend, mcp, api | `ContentMcpToolProvider` — the 4 MCP tools | — | open |
| 013 | 013-scoped-search-key | Scoped search key | backend, search, security | Read-only scoped Meilisearch key for the content index | — | open |
| 014 | 014-ops-robustness | Ops & robustness | backend, infrastructure, observability | robots.txt handling, fault tolerance, logging/metrics | — | open |
| 015 | 015-additional-web-sources | Additional web sources | backend, configuration | hiero.org/blog + Support & Care as config-only sources | — | open |
| 016 | 016-git-markdown-source | Git markdown source | backend, architecture, security | `type: git` source + `GitSourceStrategy` (GitHub Markdown) | — | open |
| 017 | 017-search-enhancements | Search enhancements | backend, search | Facets, synonyms/stop words, optional semantic search | — | open |
