package ch.chhex.blickwinkel.story;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lesender Zugriff zum Kontrollieren des Clusterings:
 * {@code GET /api/stories?hours=24&minSources=2}
 */
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    /**
     * Erst die passenden Stories bestimmen (CTE mit Limit), dann deren Artikel dazujoinen.
     * Eine Zeile pro Artikel; gruppiert wird in Java.
     */
    private static final String SELECT_STORIES = """
            with selected as (
                select st.id, st.first_seen_at, st.last_seen_at
                from story st
                where st.last_seen_at >= :since
                  and (select count(distinct a.source_id)
                       from article a
                       where a.story_id = st.id) >= :minSources
                order by st.last_seen_at desc, st.id desc
                limit :limit
            )
            select s.id, s.first_seen_at, s.last_seen_at,
                   src.name as source_name, a.title, a.url, a.published_at, a.similarity
            from selected s
            join article a  on a.story_id = s.id
            join source src on src.id = a.source_id
            order by s.last_seen_at desc, s.id desc, src.name, a.published_at
            """;

    private final JdbcClient jdbc;

    public StoryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ArticleView(String source, String title, String url, OffsetDateTime publishedAt, Float similarity) {
    }

    public record StoryView(long id, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt, List<ArticleView> articles) {
    }

    private record Row(long storyId, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt, ArticleView article) {
    }

    @GetMapping
    public List<StoryView> recent(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "2") int minSources,
            @RequestParam(defaultValue = "100") int limit) {

        List<Row> rows = jdbc.sql(SELECT_STORIES)
                .param("since", OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofHours(hours)))
                .param("minSources", minSources)
                .param("limit", Math.min(Math.max(limit, 1), 500))
                .query((rs, n) -> {
                    float similarity = rs.getFloat("similarity");
                    return new Row(
                            rs.getLong("id"),
                            rs.getObject("first_seen_at", OffsetDateTime.class),
                            rs.getObject("last_seen_at", OffsetDateTime.class),
                            new ArticleView(
                                    rs.getString("source_name"),
                                    rs.getString("title"),
                                    rs.getString("url"),
                                    rs.getObject("published_at", OffsetDateTime.class),
                                    rs.wasNull() ? null : similarity));
                })
                .list();

        Map<Long, StoryView> stories = new LinkedHashMap<>();
        for (Row r : rows) {
            stories.computeIfAbsent(r.storyId(),
                    id -> new StoryView(id, r.firstSeenAt(), r.lastSeenAt(), new ArrayList<>()))
                    .articles().add(r.article());
        }
        return List.copyOf(stories.values());
    }
}
