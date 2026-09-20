package dev.sift.fetch;

import java.time.Instant;

/**
 * One article as parsed from a feed, before it is stored.
 */
public record FetchedArticle(String title, String link, String content, Instant publishedAt) {
}
