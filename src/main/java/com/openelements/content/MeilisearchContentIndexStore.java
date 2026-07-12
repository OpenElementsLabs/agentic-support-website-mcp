package com.openelements.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import com.openelements.spring.base.services.search.TaskOutcome;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ContentIndexStore} backed by the library's {@link MeilisearchClient}.
 *
 * <p>State is read with a filtered {@code multiSearch} (retrieving only {@code id}/{@code url}/
 * {@code lastmod}, paged); upserts use {@code addDocuments} in batches (the library {@code BatchWriter}
 * is package-private and not reusable here), waiting for each task; deletes use {@code deleteDocument}.
 * Write failures are logged and do not abort the pass.
 *
 * <p>Only created when the Meilisearch stack is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class MeilisearchContentIndexStore implements ContentIndexStore {

    private static final Logger log = LoggerFactory.getLogger(MeilisearchContentIndexStore.class);

    private static final int PAGE_SIZE = 1000;
    private static final int BATCH_SIZE = 500;
    private static final Duration TASK_WAIT = Duration.ofSeconds(10);

    private final MeilisearchClient client;
    private final String indexUid;

    public MeilisearchContentIndexStore(MeilisearchClient client, MeilisearchProperties properties) {
        this.client = client;
        this.indexUid = properties.resolveIndex("content");
    }

    @Override
    public Map<String, StoredDocument> loadState(String source) {
        Map<String, StoredDocument> state = new LinkedHashMap<>();
        int offset = 0;
        while (true) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("indexUid", indexUid);
            query.put("q", "");
            query.put("filter", "source = \"" + source.replace("\"", "\\\"") + "\"");
            query.put("attributesToRetrieve", List.of("id", "url", "lastmod"));
            query.put("limit", PAGE_SIZE);
            query.put("offset", offset);

            JsonNode response = client.multiSearch(Map.of("queries", List.of(query)));
            Map<String, StoredDocument> page = parseHits(response);
            state.putAll(page);
            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return state;
    }

    @Override
    public int upsert(List<Map<String, Object>> documents) {
        int written = 0;
        for (int start = 0; start < documents.size(); start += BATCH_SIZE) {
            List<Map<String, Object>> batch = documents.subList(start, Math.min(start + BATCH_SIZE, documents.size()));
            try {
                long task = client.addDocuments(indexUid, batch);
                TaskOutcome outcome = client.waitForTask(task, TASK_WAIT);
                if (outcome == TaskOutcome.SUCCEEDED) {
                    written += batch.size();
                } else {
                    log.warn("Upsert batch of {} did not succeed: {}", batch.size(), outcome);
                }
            } catch (Exception e) {
                log.warn("Failed to upsert a batch of {} documents: {}", batch.size(), e.toString());
            }
        }
        return written;
    }

    @Override
    public void delete(String id) {
        try {
            long task = client.deleteDocument(indexUid, id);
            client.waitForTask(task, TASK_WAIT);
        } catch (Exception e) {
            log.warn("Failed to delete document {}: {}", id, e.toString());
        }
    }

    /**
     * Parses the hits of a {@code multiSearch} response (first query result) into a document-id map.
     * Package-private and static so it can be unit-tested without a Meilisearch instance.
     *
     * @param response the raw multiSearch response
     * @return document id → stored projection
     */
    static Map<String, StoredDocument> parseHits(JsonNode response) {
        Map<String, StoredDocument> result = new LinkedHashMap<>();
        JsonNode results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return result;
        }
        JsonNode hits = results.get(0).path("hits");
        if (!hits.isArray()) {
            return result;
        }
        for (JsonNode hit : hits) {
            String id = textOrNull(hit, "id");
            if (id == null) {
                continue;
            }
            result.put(id, new StoredDocument(textOrNull(hit, "url"), textOrNull(hit, "lastmod")));
        }
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
