package com.openelements.content;

/**
 * Summary of one indexing pass over a source.
 *
 * @param discovered number of items discovery returned
 * @param upserted   documents fetched/extracted and written to the index
 * @param unchanged  items skipped because they were unchanged (by {@code lastmod} diff or a 304)
 * @param deleted    documents removed (vanished from discovery, or 404 on fetch)
 * @param skipped    items that failed to fetch or extract and were skipped
 */
public record IndexReport(int discovered, int upserted, int unchanged, int deleted, int skipped) {
}
