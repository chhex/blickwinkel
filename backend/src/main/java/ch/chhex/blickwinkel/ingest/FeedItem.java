package ch.chhex.blickwinkel.ingest;

import java.time.Instant;

/** Ein bereinigter Feed-Eintrag. {@code publishedAt} und {@code teaser} dürfen null sein. */
public record FeedItem(String url, String title, String teaser, Instant publishedAt) {
}
