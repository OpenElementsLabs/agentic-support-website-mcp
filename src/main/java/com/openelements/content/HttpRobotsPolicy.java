package com.openelements.content;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link RobotsPolicy} that fetches and caches each host's {@code robots.txt}.
 *
 * <p>Rules are fetched once per host (over HTTPS) and cached with a TTL. The group matching our bot
 * {@code User-Agent} is used, falling back to the {@code *} group; a missing or unreachable
 * {@code robots.txt} allows everything. Path matching is longest-prefix with {@code Allow} winning
 * ties (the common robots semantics). The clock is injectable so cache expiry is testable.
 */
@Component
public class HttpRobotsPolicy implements RobotsPolicy {

    private static final Logger log = LoggerFactory.getLogger(HttpRobotsPolicy.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final String userAgent;
    private final LongSupplier clockNanos;
    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();

    @Autowired
    public HttpRobotsPolicy(RestClient.Builder restClientBuilder, ContentSourceProperties properties) {
        this(restClientBuilder, properties, System::nanoTime);
    }

    HttpRobotsPolicy(RestClient.Builder restClientBuilder, ContentSourceProperties properties, LongSupplier clockNanos) {
        this.restClient = restClientBuilder.build();
        this.userAgent = properties.userAgent();
        this.clockNanos = clockNanos;
    }

    @Override
    public boolean isAllowed(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return true;
        }
        boolean allowed = rulesFor(host).allows(pathOf(url));
        if (!allowed) {
            log.info("robots.txt disallows {} for {}", url, userAgent);
        }
        return allowed;
    }

    @Override
    public Duration crawlDelay(String host) {
        return host == null || host.isEmpty() ? Duration.ZERO : rulesFor(host).crawlDelay();
    }

    private RobotsRules rulesFor(String host) {
        CachedRules cached = cache.get(host);
        if (cached != null && cached.expiryNanos() > clockNanos.getAsLong()) {
            return cached.rules();
        }
        RobotsRules rules = fetchAndParse(host);
        cache.put(host, new CachedRules(rules, clockNanos.getAsLong() + CACHE_TTL.toNanos()));
        return rules;
    }

    private RobotsRules fetchAndParse(String host) {
        try {
            String body = restClient.get()
                .uri(URI.create("https://" + host + "/robots.txt"))
                .header("User-Agent", userAgent)
                .retrieve()
                .body(String.class);
            return parse(body == null ? "" : body, userAgent);
        } catch (Exception e) {
            // Missing/unreachable robots.txt (e.g. 404) means allow everything.
            log.debug("No usable robots.txt for {} ({}); allowing all", host, e.toString());
            return RobotsRules.ALLOW_ALL;
        }
    }

    /** Parses robots.txt, selecting the group for our user agent (falling back to {@code *}). */
    static RobotsRules parse(String body, String userAgent) {
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasRule = false;
        for (String rawLine : body.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (field.equals("user-agent")) {
                if (current == null || lastWasRule) {
                    current = new Group();
                    groups.add(current);
                }
                current.agents.add(value.toLowerCase(Locale.ROOT));
                lastWasRule = false;
            } else if (current != null) {
                switch (field) {
                    case "disallow" -> current.disallow.add(value);
                    case "allow" -> current.allow.add(value);
                    case "crawl-delay" -> current.crawlDelay = parseDelay(value);
                    default -> { /* ignore other directives */ }
                }
                lastWasRule = true;
            }
        }
        return selectGroup(groups, userAgent).toRules();
    }

    private static Group selectGroup(List<Group> groups, String userAgent) {
        String ua = userAgent.toLowerCase(Locale.ROOT);
        Group wildcard = null;
        for (Group group : groups) {
            for (String agent : group.agents) {
                if (agent.equals("*")) {
                    wildcard = group;
                } else if (!agent.isEmpty() && ua.contains(agent)) {
                    return group; // a group naming our agent wins over the wildcard
                }
            }
        }
        return wildcard == null ? new Group() : wildcard;
    }

    private static Duration parseDelay(String value) {
        try {
            return Duration.ofMillis(Math.round(Double.parseDouble(value) * 1000));
        } catch (NumberFormatException e) {
            return Duration.ZERO;
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<String> disallow = new ArrayList<>();
        private final List<String> allow = new ArrayList<>();
        private Duration crawlDelay = Duration.ZERO;

        private RobotsRules toRules() {
            return new RobotsRules(List.copyOf(disallow), List.copyOf(allow), crawlDelay);
        }
    }

    private record CachedRules(RobotsRules rules, long expiryNanos) {
    }

    /** Parsed robots rules for the applicable group. */
    record RobotsRules(List<String> disallow, List<String> allow, Duration crawlDelay) {

        static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), List.of(), Duration.ZERO);

        boolean allows(String path) {
            int longestDisallow = longestMatch(disallow, path);
            if (longestDisallow < 0) {
                return true;
            }
            return longestMatch(allow, path) >= longestDisallow; // Allow wins ties
        }

        private static int longestMatch(List<String> rules, String path) {
            int longest = -1;
            for (String rule : rules) {
                if (!rule.isEmpty() && path.startsWith(rule) && rule.length() > longest) {
                    longest = rule.length();
                }
            }
            return longest;
        }
    }
}
