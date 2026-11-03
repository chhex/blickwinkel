package ch.chhex.blickwinkel.story;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;

/**
 * Lesende Abfragen auf Stories. Wird von der REST-API und vom UI benutzt, damit
 * ein späteres Frontend dieselbe Logik verwendet.
 *
 * <p>Story-Titel: Eine Story hat keinen eigenen Titel. Angezeigt wird der Titel des
 * Artikels, der dem Centroid am nächsten liegt, also der «typischste» Artikel.</p>
 */
@Service
public class StoryQueryService {

    public static final int MAX_LIMIT = 500;

    // --- Records für die REST-API (Form unverändert gegenüber vorher) ---------------

    public record ArticleView(String source, String title, String url, OffsetDateTime publishedAt, Float similarity) {
    }

    public record StoryView(long id, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt, List<ArticleView> articles) {
    }

    // --- Records für das UI ---------------------------------------------------------

    public record StorySummary(long id, String title, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt,
            int articleCount, List<String> sources) {
    }

    public record StoryArticle(long id, String source, String section, String country, String language,
            String title, String teaser, String url, OffsetDateTime publishedAt, Float similarity) {
    }

    public record StoryDetail(long id, String title, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt,
            List<StoryArticle> articles) {
    }

    /** Ein Artikel, den keine andere Quelle aufgegriffen hat, mit der nächstbesten fremden Story. */
    public record Singleton(long articleId, String source, String title, String url, OffsetDateTime articleTime,
            Long nearestStoryId, String nearestTitle, Double nearestSimilarity) {
    }

    // --- SQL ------------------------------------------------------------------------

    /**
     * Story-Übersicht. Eine Story erscheint, wenn sich ihr Zeitraum mit [:from, :to] überschneidet,
     * sie mindestens :minSources Quellen hat und (falls :query gesetzt) mindestens einer ihrer
     * Artikel alle Suchbegriffe in Titel oder Teaser enthält. Suche ohne Gross/Klein und Akzente,
     * als Teilwort (V4__search.sql). {ORDER} wird durch einen fest hinterlegten Ausdruck aus
     * {@link StorySort} ersetzt.
     */
    private static final String SELECT_SUMMARIES = """
            with candidates as (
                select st.id, st.first_seen_at, st.last_seen_at, st.article_count, st.centroid,
                       (select count(distinct a.source_id)
                        from article a
                        where a.story_id = st.id) as source_count
                from story st
                where st.last_seen_at  >= :from
                  and st.first_seen_at <= :to
                  and (cast(:query as text) is null or exists (
                        select 1
                        from article a
                        where a.story_id = st.id
                          and not exists (
                              select 1
                              from regexp_split_to_table(blickwinkel_norm(cast(:query as text)), ' ') as term
                              where blickwinkel_norm(a.title || ' ' || coalesce(a.teaser, ''))
                                    not like '%' || term || '%')))
            ),
            selected as (
                select *
                from candidates
                where source_count >= :minSources
                order by {ORDER}
                limit :limit
            )
            select s.id, s.first_seen_at, s.last_seen_at, s.article_count,
                   t.title, src.names
            from selected s
            cross join lateral (
                select a.title
                from article a
                where a.story_id = s.id
                order by a.embedding <=> s.centroid, a.id
                limit 1
            ) t
            cross join lateral (
                select string_agg(distinct x.name, '|' order by x.name) as names
                from article a
                join source x on x.id = a.source_id
                where a.story_id = s.id
            ) src
            order by {ORDER}
            """;

    private static final String SELECT_STORY = """
            select st.id, st.first_seen_at, st.last_seen_at,
                   (select a.title
                    from article a
                    where a.story_id = st.id
                    order by a.embedding <=> st.centroid, a.id
                    limit 1) as title
            from story st
            where st.id = :id
            """;

    private static final String SELECT_STORY_ARTICLES = """
            select a.id, src.name as source_name, src.section::text as section, src.country,
                   trim(a.language) as language, a.title, a.teaser, a.url, a.published_at, a.similarity
            from article a
            join source src on src.id = a.source_id
            where a.story_id = :id
            order by src.country, src.name, a.published_at, a.id
            """;

    /**
     * Einzelmeldungen: Artikel, deren Story keine andere Quelle enthält. Dazu die
     * ähnlichste fremde Story im Clustering-Zeitfenster, als Kalibrier-Hilfe.
     */
    private static final String SELECT_SINGLETONS = """
            with singles as (
                select a.id, a.story_id, a.embedding, a.title, a.url, src.name as source_name,
                       coalesce(a.published_at, a.fetched_at) as article_time
                from article a
                join source src on src.id = a.source_id
                where a.status = 'CLUSTERED'
                  and coalesce(a.published_at, a.fetched_at) >= :since
                  and (cast(:sourceCode as text) is null or src.code = cast(:sourceCode as text))
                  and not exists (select 1
                                  from article o
                                  where o.story_id = a.story_id
                                    and o.source_id <> a.source_id)
                order by article_time desc, a.id desc
                limit :limit
            )
            select s.id, s.source_name, s.title, s.url, s.article_time,
                   n.story_id as nearest_story_id, n.similarity as nearest_similarity,
                   nt.title as nearest_title
            from singles s
            left join lateral (
                select st.id as story_id, st.centroid, 1 - (st.centroid <=> s.embedding) as similarity
                from story st
                where st.id <> s.story_id
                  and st.last_seen_at  >= s.article_time - cast(:window as interval)
                  and st.first_seen_at <= s.article_time + cast(:window as interval)
                order by st.centroid <=> s.embedding, st.id
                limit 1
            ) n on true
            left join lateral (
                select a.title
                from article a
                where a.story_id = n.story_id
                order by a.embedding <=> n.centroid, a.id
                limit 1
            ) nt on true
            order by s.article_time desc, s.id desc
            """;

    /** Für die REST-API: eine Zeile pro Artikel, gruppiert wird in Java. */
    private static final String SELECT_STORIES_WITH_ARTICLES = """
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
    private final Duration window;

    public StoryQueryService(JdbcClient jdbc, BlickwinkelProperties props) {
        this.jdbc = jdbc;
        this.window = props.clustering().window();
    }

    public List<StorySummary> search(StoryFilter filter) {
        return jdbc.sql(SELECT_SUMMARIES.replace("{ORDER}", filter.sort().orderBy()))
                .param("from", filter.from())
                .param("to", filter.to())
                .param("minSources", filter.minSources())
                .param("query", filter.likeQuery(), Types.VARCHAR)
                .param("limit", clamp(filter.limit()))
                .query((rs, n) -> new StorySummary(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getObject("first_seen_at", OffsetDateTime.class),
                        rs.getObject("last_seen_at", OffsetDateTime.class),
                        rs.getInt("article_count"),
                        splitNames(rs.getString("names"))))
                .list();
    }

    public Optional<StoryDetail> story(long id) {
        return jdbc.sql(SELECT_STORY)
                .param("id", id)
                .query((rs, n) -> new StoryDetail(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getObject("first_seen_at", OffsetDateTime.class),
                        rs.getObject("last_seen_at", OffsetDateTime.class),
                        storyArticles(id)))
                .optional();
    }

    public List<Singleton> singletons(int hours, String sourceCode, int limit) {
        String code = sourceCode == null || sourceCode.isBlank() ? null : sourceCode;
        return jdbc.sql(SELECT_SINGLETONS)
                .param("since", since(hours))
                .param("sourceCode", code, Types.VARCHAR)
                .param("window", window.toString())
                .param("limit", clamp(limit))
                .query((rs, n) -> new Singleton(
                        rs.getLong("id"),
                        rs.getString("source_name"),
                        rs.getString("title"),
                        rs.getString("url"),
                        rs.getObject("article_time", OffsetDateTime.class),
                        nullableLong(rs, "nearest_story_id"),
                        rs.getString("nearest_title"),
                        nullableDouble(rs, "nearest_similarity")))
                .list();
    }

    /** Form der bisherigen REST-API: Stories mit allen Artikeln. */
    public List<StoryView> recentStoriesWithArticles(int hours, int minSources, int limit) {
        record Row(long storyId, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt, ArticleView article) {
        }
        List<Row> rows = jdbc.sql(SELECT_STORIES_WITH_ARTICLES)
                .param("since", since(hours))
                .param("minSources", minSources)
                .param("limit", clamp(limit))
                .query((rs, n) -> new Row(
                        rs.getLong("id"),
                        rs.getObject("first_seen_at", OffsetDateTime.class),
                        rs.getObject("last_seen_at", OffsetDateTime.class),
                        new ArticleView(
                                rs.getString("source_name"),
                                rs.getString("title"),
                                rs.getString("url"),
                                rs.getObject("published_at", OffsetDateTime.class),
                                nullableFloat(rs, "similarity"))))
                .list();

        Map<Long, StoryView> stories = new LinkedHashMap<>();
        for (Row r : rows) {
            stories.computeIfAbsent(r.storyId(),
                    id -> new StoryView(id, r.firstSeenAt(), r.lastSeenAt(), new ArrayList<>()))
                    .articles().add(r.article());
        }
        return List.copyOf(stories.values());
    }

    private List<StoryArticle> storyArticles(long storyId) {
        return jdbc.sql(SELECT_STORY_ARTICLES)
                .param("id", storyId)
                .query((rs, n) -> new StoryArticle(
                        rs.getLong("id"),
                        rs.getString("source_name"),
                        rs.getString("section"),
                        rs.getString("country"),
                        rs.getString("language"),
                        rs.getString("title"),
                        rs.getString("teaser"),
                        rs.getString("url"),
                        rs.getObject("published_at", OffsetDateTime.class),
                        nullableFloat(rs, "similarity")))
                .list();
    }

    private static OffsetDateTime since(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofHours(Math.max(hours, 1)));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    static List<String> splitNames(String names) {
        return names == null || names.isEmpty() ? List.of() : Arrays.asList(names.split("\\|"));
    }

    private static Float nullableFloat(ResultSet rs, String column) throws SQLException {
        float v = rs.getFloat(column);
        return rs.wasNull() ? null : v;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
