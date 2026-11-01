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

/** Holt alle aktiven Feeds und legt neue Artikel mit Status NEW an. */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private static final String INSERT_ARTICLE = """
            insert into article (source_id, url, url_hash, title, teaser, published_at, language, status)
            values (:sourceId, :url, :urlHash, :title, :teaser, :publishedAt, :language, 'NEW')
            on conflict (url_hash) do nothing
            """;

    private final JdbcClient jdbc;
    private final FeedFetcher fetcher;

    public IngestService(JdbcClient jdbc, FeedFetcher fetcher) {
        this.jdbc = jdbc;
        this.fetcher = fetcher;
    }

    record ActiveSource(long id, String code, String feedUrl, String language) {
    }

    @Scheduled(fixedDelayString = "${blickwinkel.ingest.interval}", initialDelayString = "PT10S")
    public void ingestAll() {
        List<ActiveSource> sources = jdbc.sql("""
                        select id, code, feed_url, trim(language) as language
                        from source
                        where active
                        order by id
                        """)
                .query((rs, n) -> new ActiveSource(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("feed_url"),
                        rs.getString("language")))
                .list();

        for (ActiveSource s : sources) {
            ingestSource(s);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    void ingestSource(ActiveSource source) {
        try {
            List<FeedItem> items = fetcher.fetch(URI.create(source.feedUrl()));
            int inserted = 0;
            int skipped = 0;
            for (FeedItem item : items) {
                try {
                    inserted += insert(source, item);
                } catch (IllegalArgumentException e) {
                    skipped++;
                    log.debug("{}: Eintrag übersprungen ({})", source.code(), e.getMessage());
                }
            }
            jdbc.sql("update source set last_fetched_at = :now, last_error = null where id = :id")
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                    .param("id", source.id())
                    .update();
            log.info("{}: {} Einträge, {} neu, {} übersprungen", source.code(), items.size(), inserted, skipped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{}: Feed fehlgeschlagen: {}", source.code(), e.toString());
            jdbc.sql("update source set last_error = :error where id = :id")
                    .param("error", abbreviate(e.toString()))
                    .param("id", source.id())
                    .update();
        }
    }

    /** @return 1 wenn neu eingefügt, 0 wenn die URL schon bekannt war */
    private int insert(ActiveSource source, FeedItem item) {
        String normalized = UrlNormalizer.normalize(item.url());
        OffsetDateTime published = item.publishedAt() == null
                ? null
                : item.publishedAt().atOffset(ZoneOffset.UTC);

        return jdbc.sql(INSERT_ARTICLE)
                .param("sourceId", source.id())
                .param("url", item.url())
                .param("urlHash", UrlNormalizer.hash(normalized))
                .param("title", item.title())
                .param("teaser", item.teaser(), Types.VARCHAR)
                .param("publishedAt", published, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("language", source.language())
                .update();
    }

    private static String abbreviate(String s) {
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
