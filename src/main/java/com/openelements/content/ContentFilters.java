package com.openelements.content;

/**
 * Optional filters for a content search or listing. Any {@code null} field is not applied; non-null
 * fields are AND-combined.
 *
 * @param source   restrict to a source id
 * @param locale   restrict to a locale (e.g. {@code en}, {@code de})
 * @param category restrict to a category tag
 * @param since    restrict to documents with {@code publishedDate >=} this ISO date
 */
public record ContentFilters(String source, String locale, String category, String since) {

    /** @return filters with no restrictions */
    public static ContentFilters none() {
        return new ContentFilters(null, null, null, null);
    }
}
