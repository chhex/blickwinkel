package ch.chhex.blickwinkel.ingest;

import java.net.URI;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Holt alle aktiven Feeds aktiver Quellen und legt neue Artikel mit Status NEW an.
 *
 * <p>Steht derselbe Artikel in mehreren Feeds einer Quelle (z.B. World und Startseite),
 * wird er nur einmal gespeichert; {@code feed_id} zeigt auf den Feed, der ihn zuerst lieferte.</p>
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private static final String SELECT_ACTIVE_FEEDS = """
            select f.id as feed_id, f.url, s.id as source_id, s.code, trim(s.language) as language
            from source_feed f
            join source s on s.id = f.source_id
            where f.active
              and s.active
            order by s.id, f.id
            """;

    private static final String INSERT_ARTICLE = """
            insert into article (source_id, feed_id, url, url_hash, title, teaser, published_at, language, status)
            values (:sourceId, :feedId, :url, :urlHash, :title, :teaser, :publishedAt, :language, 'NEW')
            on conflict (url_hash) do nothing
            """;

    private final JdbcClient jdbc;
    private final FeedFetcher fetcher;

    public IngestService(JdbcClient jdbc, FeedFetcher fetcher) {
        this.jdbc = jdbc;
        this.fetcher = fetcher;
    }

    record ActiveFeed(long feedId, String url, long sourceId, String code, String language) {
    }

    @Scheduled(fixedDelayString = "${blickwinkel.ingest.interval}", initialDelayString = "PT10S")
    public void ingestAll() {
        List<ActiveFeed> feeds = jdbc.sql(SELECT_ACTIVE_FEEDS)
                .query((rs, n) -> new ActiveFeed(
                        rs.getLong("feed_id"),
                        rs.getString("url"),
                        rs.getLong("source_id"),
                        rs.getString("code"),
                        rs.getString("language")))
                .list();

        for (ActiveFeed feed : feeds) {
            ingestFeed(feed);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    void ingestFeed(ActiveFeed feed) {
        try {
            List<FeedItem> items = fetcher.fetch(URI.create(feed.url()));
            int inserted = 0;
            int skipped = 0;
            for (FeedItem item : items) {
                try {
                    inserted += insert(feed, item);
                } catch (IllegalArgumentException e) {
                    skipped++;
                    log.debug("{}: Eintrag übersprungen ({})", feed.code(), e.getMessage());
                }
            }
            jdbc.sql("update source_feed set last_fetched_at = :now, last_error = null where id = :id")
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                    .param("id", feed.feedId())
                    .update();
            log.info("{} [{}]: {} Einträge, {} neu, {} übersprungen",
                    feed.code(), feed.url(), items.size(), inserted, skipped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{} [{}]: Feed fehlgeschlagen: {}", feed.code(), feed.url(), e.toString());
            jdbc.sql("update source_feed set last_error = :error where id = :id")
                    .param("error", abbreviate(e.toString()))
                    .param("id", feed.feedId())
                    .update();
        }
    }

    /** @return 1 wenn neu eingefügt, 0 wenn die URL schon bekannt war */
    private int insert(ActiveFeed feed, FeedItem item) {
        String normalized = UrlNormalizer.normalize(item.url());
        OffsetDateTime published = item.publishedAt() == null
                ? null
                : item.publishedAt().atOffset(ZoneOffset.UTC);

        return jdbc.sql(INSERT_ARTICLE)
                .param("sourceId", feed.sourceId())
                .param("feedId", feed.feedId())
                .param("url", item.url())
                .param("urlHash", UrlNormalizer.hash(normalized))
                .param("title", item.title())
                .param("teaser", item.teaser(), Types.VARCHAR)
                .param("publishedAt", published, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("language", feed.language())
                .update();
    }

    private static String abbreviate(String s) {
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
