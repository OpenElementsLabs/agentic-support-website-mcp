# Behaviors: Search Enhancements (Optional)

## Synonyms & stop words

### Synonym expands a query
- **Given** a configured synonym (e.g. "ai" ⇔ "artificial intelligence")
- **When** a user searches "artificial intelligence"
- **Then** posts tagged/matching "ai" are also returned

### Stop word is ignored in ranking
- **Given** configured stop words for a language
- **When** a query contains a stop word
- **Then** it does not distort ranking/matching

### Settings applied without reindex disruption
- **Given** new synonym/stop-word settings
- **When** they are pushed via `updateSettings`
- **Then** they take effect on subsequent searches without requiring a full data reindex

## Facet surfacing

### Search response includes facet distribution
- **Given** facet surfacing enabled
- **When** `search_content` runs
- **Then** the response includes category (and optionally source/locale) counts alongside hits

### list_categories reflects enriched facets
- **Given** enriched faceting
- **When** `list_categories` runs
- **Then** it returns categories with accurate counts scoped by any filters

## Semantic search (optional)

### Hybrid search returns semantically related results
- **Given** embeddings indexed and hybrid mode enabled
- **When** a user searches a paraphrase not containing the exact keywords
- **Then** semantically related posts are returned

### Semantic search is opt-in
- **Given** semantic search disabled by config
- **When** the app runs
- **Then** no embeddings are generated and keyword search behaves exactly as before

## Non-regression

### Enhancements do not break core search
- **Given** any enhancement enabled
- **When** existing keyword searches/filters run
- **Then** they return the same or better results (no regression in the Phase 1 behaviors)
