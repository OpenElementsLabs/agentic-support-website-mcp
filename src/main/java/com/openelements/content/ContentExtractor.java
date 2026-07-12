package com.openelements.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts a clean body and metadata from raw HTML using jsoup.
 *
 * <p>Body: metadata is read first (before any {@code <script>} is stripped, so JSON-LD survives),
 * then {@code script}/{@code style}/{@code noscript}/{@code template} elements and comments are
 * removed. The main container is chosen by the source's {@code contentSelector} (a comma-separated
 * fallback list; {@code body} means the whole document; a readability fallback applies when nothing
 * matches), {@code contentExclude} elements are removed, and the text is whitespace-normalized with
 * paragraph separation preserved.
 *
 * <p>Metadata is read independently of the content selector, preferring structured sources:
 * OpenGraph/Article {@code <meta>} tags, JSON-LD, and {@code <time datetime>}.
 */
@Component
public class ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractor.class);

    private static final String STRIP_SELECTOR = "script, style, noscript, template";
    private static final String BLOCK_SELECTOR = "p, li, h1, h2, h3, h4, h5, h6, blockquote, pre";

    private final ContentLocaleResolver localeResolver;
    private final ObjectMapper objectMapper;

    public ContentExtractor(ContentLocaleResolver localeResolver, ObjectMapper objectMapper) {
        this.localeResolver = localeResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts the body and metadata of a page.
     *
     * @param source the source (supplies {@code contentSelector}/{@code contentExclude})
     * @param url    the page URL (used for locale derivation)
     * @param html   the raw HTML
     * @return the extracted content
     */
    public ExtractedContent extract(ContentSource source, String url, String html) {
        Document doc = Jsoup.parse(html, url);

        // Read metadata before stripping scripts (JSON-LD lives in a <script> tag).
        Metadata metadata = extractMetadata(doc);
        String locale = localeResolver.resolve(url).getLanguage();

        // Now strip non-content nodes for body extraction.
        doc.select(STRIP_SELECTOR).remove();
        removeComments(doc);
        String body = extractBody(doc, source);

        return new ExtractedContent(
            metadata.title, metadata.excerpt, body, metadata.author,
            metadata.categories, metadata.publishedDate, metadata.previewImage, locale);
    }

    // ---- body extraction ----

    private String extractBody(Document doc, ContentSource source) {
        Element container = selectContainer(doc, source);
        if (container == null) {
            return "";
        }
        for (String exclude : source.contentExclude()) {
            container.select(exclude).remove();
        }
        return normalizeText(container);
    }

    private Element selectContainer(Document doc, ContentSource source) {
        String selector = source.contentSelector();
        if (selector != null && !selector.isBlank()) {
            if (selector.strip().equals("body")) {
                return doc.body();
            }
            for (String part : selector.split(",")) {
                String candidate = part.strip();
                if (candidate.isEmpty()) {
                    continue;
                }
                Element element = doc.selectFirst(candidate);
                if (element != null && !element.text().isBlank()) {
                    return element;
                }
            }
        }
        return readabilityFallback(doc);
    }

    /**
     * Simple readability fallback: prefer a semantic content element, otherwise the {@code <div>} or
     * {@code <section>} carrying the most text.
     */
    private Element readabilityFallback(Document doc) {
        for (String semantic : List.of("article", "main", "[role=main]")) {
            Element element = doc.selectFirst(semantic);
            if (element != null && !element.text().isBlank()) {
                return element;
            }
        }
        Element best = null;
        int bestLength = -1;
        for (Element element : doc.select("div, section")) {
            int length = element.text().length();
            if (length > bestLength) {
                bestLength = length;
                best = element;
            }
        }
        return best != null ? best : doc.body();
    }

    /**
     * Extracts block-separated text: each block element's collapsed text becomes a paragraph, joined
     * by blank lines. Falls back to the container's collapsed text when it has no block elements.
     */
    private String normalizeText(Element container) {
        List<String> paragraphs = new ArrayList<>();
        for (Element block : container.select(BLOCK_SELECTOR)) {
            String text = block.text().strip();
            if (!text.isEmpty()) {
                paragraphs.add(text);
            }
        }
        if (paragraphs.isEmpty()) {
            return container.text().strip();
        }
        return String.join("\n\n", paragraphs);
    }

    private static void removeComments(Document doc) {
        List<Node> comments = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            for (Node child : element.childNodes()) {
                if (child instanceof Comment) {
                    comments.add(child);
                }
            }
        }
        comments.forEach(Node::remove);
    }

    // ---- metadata extraction ----

    private Metadata extractMetadata(Document doc) {
        Metadata metadata = new Metadata();

        metadata.title = firstNonBlank(
            metaContent(doc, "meta[property=og:title]"),
            blankToNull(doc.title()),
            textOf(doc.selectFirst("h1")));
        metadata.excerpt = firstNonBlank(
            metaContent(doc, "meta[name=description]"),
            metaContent(doc, "meta[property=og:description]"));
        metadata.previewImage = metaContent(doc, "meta[property=og:image]");

        String metaPublished = firstNonBlank(
            metaContent(doc, "meta[property=article:published_time]"),
            attrOf(doc.selectFirst("time[datetime]"), "datetime"));
        String metaAuthor = firstNonBlank(
            metaContent(doc, "meta[name=author]"),
            metaContent(doc, "meta[property=article:author]"));

        Set<String> categories = new LinkedHashSet<>();
        doc.select("meta[property=article:tag]").forEach(tag -> {
            String value = tag.attr("content").strip();
            if (!value.isEmpty()) {
                categories.add(value);
            }
        });

        JsonLd jsonLd = readJsonLd(doc);
        metadata.publishedDate = firstNonBlank(metaPublished, jsonLd.datePublished);
        metadata.author = firstNonBlank(metaAuthor, jsonLd.author);
        if (categories.isEmpty()) {
            categories.addAll(jsonLd.keywords);
        }
        metadata.categories = List.copyOf(categories);

        return metadata;
    }

    private JsonLd readJsonLd(Document doc) {
        JsonLd result = new JsonLd();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = objectMapper.readTree(script.data());
            } catch (Exception e) {
                log.debug("Ignoring unparseable JSON-LD block: {}", e.toString());
                continue;
            }
            for (JsonNode node : flatten(root)) {
                if (result.datePublished == null && node.hasNonNull("datePublished")) {
                    result.datePublished = node.get("datePublished").asText();
                }
                if (result.author == null && node.has("author")) {
                    result.author = authorName(node.get("author"));
                }
                if (result.keywords.isEmpty() && node.has("keywords")) {
                    result.keywords.addAll(keywords(node.get("keywords")));
                }
            }
        }
        return result;
    }

    private static String authorName(JsonNode author) {
        if (author.isObject()) {
            return blankToNull(author.path("name").asText(""));
        }
        if (author.isArray() && !author.isEmpty()) {
            return authorName(author.get(0));
        }
        if (author.isTextual()) {
            return blankToNull(author.asText());
        }
        return null;
    }

    private static List<String> keywords(JsonNode keywords) {
        List<String> values = new ArrayList<>();
        if (keywords.isArray()) {
            keywords.forEach(k -> {
                String value = k.asText("").strip();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            });
        } else if (keywords.isTextual()) {
            for (String part : keywords.asText().split(",")) {
                String value = part.strip();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    /** Flattens JSON-LD into candidate objects, following arrays and {@code @graph} containers. */
    private static List<JsonNode> flatten(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            JsonNode node = stack.pop();
            if (node == null) {
                continue;
            }
            if (node.isArray()) {
                node.forEach(stack::push);
            } else {
                out.add(node);
                if (node.has("@graph")) {
                    stack.push(node.get("@graph"));
                }
            }
        }
        return out;
    }

    // ---- small helpers ----

    private static String metaContent(Document doc, String cssQuery) {
        return attrOf(doc.selectFirst(cssQuery), "content");
    }

    private static String attrOf(Element element, String attribute) {
        if (element == null) {
            return null;
        }
        return blankToNull(element.attr(attribute));
    }

    private static String textOf(Element element) {
        return element == null ? null : blankToNull(element.text());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static final class Metadata {
        String title;
        String excerpt;
        String author;
        String publishedDate;
        String previewImage;
        List<String> categories = List.of();
    }

    private static final class JsonLd {
        String datePublished;
        String author;
        final List<String> keywords = new ArrayList<>();
    }
}
