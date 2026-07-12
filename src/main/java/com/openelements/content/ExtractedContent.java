package com.openelements.content;

import java.util.List;

/**
 * The result of extracting one page with {@link ContentExtractor}: the cleaned body plus the
 * metadata read from the document. The indexer (spec 008) maps this together with the URL and source
 * into a {@link ContentDocument}.
 *
 * <p>{@code categories} is normalized to a non-null list; the remaining metadata fields may be
 * {@code null} when the page does not provide them.
 *
 * @param title         the page title
 * @param excerpt       a short summary, or {@code null}
 * @param body          the cleaned, whitespace-normalized main text
 * @param author        the author name, or {@code null}
 * @param categories    category/tag values (never {@code null})
 * @param publishedDate the published date/time as found in the page, or {@code null}
 * @param previewImage  a preview image URL, or {@code null}
 * @param locale        the derived locale language code (e.g. {@code en}, {@code de})
 */
public record ExtractedContent(
    String title,
    String excerpt,
    String body,
    String author,
    List<String> categories,
    String publishedDate,
    String previewImage,
    String locale
) {

    public ExtractedContent {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
