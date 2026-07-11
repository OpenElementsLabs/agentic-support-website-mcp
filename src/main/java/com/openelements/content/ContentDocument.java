package com.openelements.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The canonical shape of one indexed content document — a single crawled page mapped into the
 * Meilisearch content index.
 *
 * <p>The record is immutable; {@code categories} is normalized to a non-null list. Producers
 * (spec 008) build the {@link #id} with {@link #id(String, String)} and hand the map from
 * {@link #toMap()} to the library's {@code MeilisearchClient}/{@code BatchWriter}.
 *
 * @param id            stable primary key derived from {@code source + url}; see {@link #id(String, String)}
 * @param source        source identifier (e.g. {@code open-elements})
 * @param locale        content locale ({@code en} | {@code de})
 * @param url           canonical URL of the page
 * @param title         page title
 * @param excerpt       short summary
 * @param body          full cleaned text / Markdown
 * @param author        author name, may be {@code null}
 * @param categories    category tags (filterable/facetable); never {@code null}
 * @param publishedDate ISO date (e.g. {@code 2026-03-12})
 * @param lastmod       ISO datetime change marker
 * @param previewImage  preview image URL/path, may be {@code null}
 */
public record ContentDocument(
    String id,
    String source,
    String locale,
    String url,
    String title,
    String excerpt,
    String body,
    String author,
    List<String> categories,
    String publishedDate,
    String lastmod,
    String previewImage
) {

    public ContentDocument {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /**
     * Computes the stable Meilisearch primary key for a document.
     *
     * <p>Meilisearch document ids may only contain {@code [A-Za-z0-9_-]}, so a raw URL cannot be used
     * directly. The id is a readable, sanitized source prefix joined by {@code _} to the SHA-256 hex
     * digest of the URL: {@code <sanitized-source>_<sha256(url)>}. It is deterministic for a given
     * {@code (source, url)} pair and differs whenever the URL differs.
     *
     * @param source the source identifier
     * @param url    the document URL
     * @return a deterministic id containing only Meilisearch-legal characters
     */
    public static String id(String source, String url) {
        return sanitize(source) + "_" + sha256Hex(url);
    }

    /**
     * Maps this document to the attribute map consumed by {@code MeilisearchClient.addDocuments}.
     * All indexed fields are present as keys; optional fields may carry a {@code null} value, which
     * Meilisearch tolerates.
     *
     * @return an ordered, mutable map of field name to value
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("source", source);
        map.put("locale", locale);
        map.put("url", url);
        map.put("title", title);
        map.put("excerpt", excerpt);
        map.put("body", body);
        map.put("author", author);
        map.put("categories", categories);
        map.put("publishedDate", publishedDate);
        map.put("lastmod", lastmod);
        map.put("previewImage", previewImage);
        return map;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm on every JVM; this should never happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
