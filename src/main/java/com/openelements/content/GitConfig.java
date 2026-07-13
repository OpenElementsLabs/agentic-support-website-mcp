package com.openelements.content;

import java.util.List;

/**
 * Configuration for a {@link SourceType#GIT} source, bound from the {@code git:} sub-object of a
 * content source. Present only when {@code type: git}.
 *
 * @param provider the git provider ({@code github}); only GitHub is supported for now
 * @param repo     the repository in {@code owner/name} form
 * @param ref      the branch, tag, or commit to read (e.g. {@code main})
 * @param paths    Ant-glob patterns selecting which files to index (e.g. {@code content/posts/**​/*.md})
 * @param token    an access token for private repos; supplied via the environment, never in plaintext YAML
 */
public record GitConfig(
    String provider,
    String repo,
    String ref,
    List<String> paths,
    String token
) {

    public GitConfig {
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    /** @return {@code true} if an access token is configured (private repo) */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
