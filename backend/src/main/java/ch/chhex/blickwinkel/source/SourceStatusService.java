package ch.chhex.blickwinkel.source;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Betriebssicht: Zustand der Feeds und der Pipeline. */
@Service
public class SourceStatusService {

    public record FeedStatus(String code, String name, String country, String section, String url,
            OffsetDateTime lastFetchedAt, String lastError, long articlesTotal, long articles24h) {
    }

    public record SourceOption(String code, String name) {
    }

    /** Artikelzahlen pro Feed: gezählt beim Feed, über den ein Artikel zuerst kam. */
    private static final String SELECT_FEEDS = """
            select s.code, s.name, s.country, s.section::text as section, f.url,
                   f.last_fetched_at, f.last_error,
                   count(a.id) as articles_total,
                   count(a.id) filter (where a.fetched_at >= now() - interval '24 hours') as articles_24h
            from source s
            join source_feed f on f.source_id = s.id
            left join article a on a.feed_id = f.id
            where s.active
              and f.active
            group by s.code, s.name, s.country, s.section, f.id, f.url, f.last_fetched_at, f.last_error
            order by s.country, s.name, f.id
            """;

    private static final String SELECT_PIPELINE = """
            select status::text as status, count(*) as anzahl
            from article
            group by status
            """;

    private static final String SELECT_SOURCES = """
            select code, name
            from source
            where active
            order by name
            """;

    /** Reihenfolge der Pipeline, damit die Anzeige stabil ist. */
    private static final List<String> STATUS_ORDER = List.of("NEW", "EMBEDDED", "CLUSTERED", "FAILED");

    private final JdbcClient jdbc;

    public SourceStatusService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<FeedStatus> feeds() {
        return jdbc.sql(SELECT_FEEDS)
                .query((rs, n) -> new FeedStatus(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("country"),
                        rs.getString("section"),
                        rs.getString("url"),
                        rs.getObject("last_fetched_at", OffsetDateTime.class),
                        rs.getString("last_error"),
                        rs.getLong("articles_total"),
                        rs.getLong("articles_24h")))
                .list();
    }

    /** Anzahl Artikel pro Status, immer alle Status in fester Reihenfolge (fehlende = 0). */
    public Map<String, Long> pipeline() {
        Map<String, Long> counts = new LinkedHashMap<>();
        STATUS_ORDER.forEach(s -> counts.put(s, 0L));
        jdbc.sql(SELECT_PIPELINE)
                .query((rs, n) -> Map.entry(rs.getString("status"), rs.getLong("anzahl")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    public List<SourceOption> activeSources() {
        return jdbc.sql(SELECT_SOURCES)
                .query((rs, n) -> new SourceOption(rs.getString("code"), rs.getString("name")))
                .list();
    }
}
