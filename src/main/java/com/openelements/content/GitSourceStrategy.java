package com.openelements.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestClient;

/**
 * {@link ContentSourceStrategy} for {@link SourceType#GIT}: indexes Markdown files directly from a
 * GitHub repository.
 *
 * <p>Discovery lists the repo tree (GitHub Git Trees API) and keeps blob paths matching the source's
 * {@code paths} globs, using each file's blob SHA as the change marker. Fetch reads the raw file,
 * splits YAML frontmatter from the Markdown body (cleaning Hugo shortcodes), maps the file path to a
 * canonical website URL, and derives the locale from the filename suffix. Private repos are accessed
 * with a bearer token that is used server-side only and never logged.
 *
 * <p>A hard discovery failure (e.g. auth error) is surfaced as an exception so the caller (bootstrap
 * step / refresh scheduler) isolates the source rather than treating an empty listing as "everything
 * was deleted".
 */
@Component
public class GitSourceStrategy implements ContentSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(GitSourceStrategy.class);
    private static final Pattern SHORTCODE = Pattern.compile("\\{\\{[<%].*?[%>]\\}\\}", Pattern.DOTALL);
    private static final Pattern DATED_FILENAME = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})-(.+)$");

    private final RestClient restClient;
    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GitSourceStrategy(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SourceType type() {
        return SourceType.GIT;
    }

    @Override
    public List<DiscoveredItem> discover(ContentSource source) {
        GitConfig git = source.git();
        if (git == null || git.repo() == null || git.repo().isBlank()) {
            throw new IllegalStateException("Git source " + source.id() + " is missing its git configuration");
        }
        JsonNode tree;
        try {
            tree = restClient.get()
                .uri(URI.create("https://api.github.com/repos/" + git.repo() + "/git/trees/" + git.ref() + "?recursive=1"))
                .headers(headers -> applyGitHubHeaders(headers, git))
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            // Surface as a failure so the source is isolated (not mass-deleted). Never log the token.
            log.warn("Git discovery failed for source {} (repo {}): {}", source.id(), git.repo(), e.toString());
            throw new IllegalStateException("Git discovery failed for " + git.repo(), e);
        }

        List<DiscoveredItem> items = new ArrayList<>();
        if (tree != null) {
            for (JsonNode node : tree.path("tree")) {
                if (!"blob".equals(node.path("type").asText())) {
                    continue;
                }
                String path = node.path("path").asText("");
                if (!path.isEmpty() && matchesAnyGlob(path, git.paths())) {
                    items.add(new DiscoveredItem(path, node.path("sha").asText(null)));
                }
            }
        }
        return items;
    }

    @Override
    public FetchOutcome fetch(ContentSource source, DiscoveredItem item) {
        GitConfig git = source.git();
        String content;
        try {
            content = restClient.get()
                .uri(URI.create("https://raw.githubusercontent.com/" + git.repo() + "/" + git.ref() + "/" + item.url()))
                .headers(headers -> applyGitHubHeaders(headers, git))
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            log.warn("Skipping {} in {}: fetch failed ({})", item.url(), git.repo(), e.toString());
            return FetchOutcome.skip();
        }
        if (content == null || content.isBlank()) {
            return FetchOutcome.skip();
        }

        ParsedMarkdown parsed = parseMarkdown(content);
        if (parsed.body().isBlank()) {
            log.warn("Skipping {} in {}: no content after frontmatter", item.url(), git.repo());
            return FetchOutcome.skip();
        }
        Map<String, Object> frontmatter = parsed.frontmatter();
        ContentDocument document = new ContentDocument(
            ContentDocument.id(source.id(), item.url()),
            source.id(),
            localeFromPath(item.url()),
            mapToUrl(source, item.url(), frontmatter),
            firstNonBlank(string(frontmatter, "title"), slugOf(item.url())),
            firstNonBlank(string(frontmatter, "excerpt"), string(frontmatter, "description")),
            parsed.body(),
            string(frontmatter, "author"),
            categories(frontmatter),
            firstNonBlank(string(frontmatter, "date"), string(frontmatter, "publishedDate")),
            item.lastmod(),
            firstNonBlank(string(frontmatter, "previewImage"), string(frontmatter, "image")));
        return FetchOutcome.index(document);
    }

    private static void applyGitHubHeaders(HttpHeaders headers, GitConfig git) {
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        if (git.hasToken()) {
            headers.setBearerAuth(git.token());
        }
    }

    private boolean matchesAnyGlob(String path, List<String> globs) {
        return globs.stream().anyMatch(glob -> pathMatcher.match(glob, path));
    }

    // ---- markdown / frontmatter ----

    ParsedMarkdown parseMarkdown(String content) {
        Map<String, Object> frontmatter = Map.of();
        String body = content;
        if (content.startsWith("---")) {
            int firstNewline = content.indexOf('\n');
            int closing = firstNewline < 0 ? -1 : content.indexOf("\n---", firstNewline);
            if (firstNewline > 0 && closing > firstNewline) {
                String block = content.substring(firstNewline + 1, closing);
                int bodyStart = content.indexOf('\n', closing + 1);
                body = bodyStart >= 0 ? content.substring(bodyStart + 1) : "";
                frontmatter = parseYaml(block);
            }
        }
        return new ParsedMarkdown(frontmatter, SHORTCODE.matcher(body).replaceAll("").strip());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String block) {
        try {
            Map<String, Object> map = yamlMapper.readValue(block, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            log.debug("Ignoring unparseable frontmatter: {}", e.toString());
            return Map.of();
        }
    }

    // ---- path -> url / locale ----

    String mapToUrl(ContentSource source, String path, Map<String, Object> frontmatter) {
        String base = trimTrailingSlash(source.baseUrl());
        String fileName = stripMarkdownExtension(lastSegment(path));
        String withoutLocale = stripLocaleSuffix(fileName);
        var matcher = DATED_FILENAME.matcher(withoutLocale);
        if (matcher.matches()) {
            return base + "/posts/" + matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3)
                + "/" + matcher.group(4);
        }
        String slug = string(frontmatter, "slug");
        if (slug != null) {
            return base + "/posts/" + slug;
        }
        return base + "/" + stripMarkdownExtension(path);
    }

    String localeFromPath(String path) {
        String fileName = stripMarkdownExtension(lastSegment(path));
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String suffix = fileName.substring(dot + 1);
            if (suffix.length() == 2 && suffix.chars().allMatch(Character::isLetter)) {
                return suffix.toLowerCase(Locale.ROOT);
            }
        }
        return "en";
    }

    private static String slugOf(String path) {
        return stripLocaleSuffix(stripMarkdownExtension(lastSegment(path)));
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripMarkdownExtension(String value) {
        if (value.endsWith(".md")) {
            return value.substring(0, value.length() - 3);
        }
        if (value.endsWith(".mdx")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private static String stripLocaleSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String suffix = fileName.substring(dot + 1);
            if (suffix.length() == 2 && suffix.chars().allMatch(Character::isLetter)) {
                return fileName.substring(0, dot);
            }
        }
        return fileName;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---- frontmatter helpers ----

    @SuppressWarnings("unchecked")
    private static List<String> categories(Map<String, Object> frontmatter) {
        Object value = frontmatter.containsKey("categories") ? frontmatter.get("categories") : frontmatter.get("tags");
        List<String> categories = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> categories.add(String.valueOf(item)));
        } else if (value instanceof String text) {
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    categories.add(part.strip());
                }
            }
        }
        return categories;
    }

    private static String string(Map<String, Object> frontmatter, String key) {
        Object value = frontmatter.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** The frontmatter map and the cleaned Markdown body. */
    record ParsedMarkdown(Map<String, Object> frontmatter, String body) {
    }
}
