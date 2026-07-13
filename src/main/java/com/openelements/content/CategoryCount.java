package com.openelements.content;

/**
 * A category facet with its document count.
 *
 * @param category the category value
 * @param count    the number of matching documents
 */
public record CategoryCount(String category, long count) {
}
