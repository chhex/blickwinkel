package ch.chhex.blickwinkel.clustering;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;
import ch.chhex.blickwinkel.db.VectorText;

/**
 * EMBEDDED → CLUSTERED: inkrementelles Clustering.
 *
 * <p>Jeder Artikel sucht die ähnlichste Story im Zeitfenster um seinen Zeitpunkt.
 * Liegt die Cosine-Ähnlichkeit über dem Schwellwert, wird er zugeordnet und der
 * Centroid als laufender Mittelwert nachgeführt, sonst entsteht eine neue Story.
 * Artikel werden in zeitlicher Reihenfolge verarbeitet, je Artikel eine Transaktion.
 * Die Suche ist exakt (kein ANN-Index), das Ergebnis also reproduzierbar.</p>
 *
 * <p>pgvector-Werte gehen als Text ({@code [0.1,0.2,...]}) mit {@code cast(... as vector)}
 * hinein und als {@code ::text} heraus, siehe {@link VectorText}.</p>
 */
@Service
public class ClusteringService {

    private static final Logger log = LoggerFactory.getLogger(ClusteringService.class);

    private static final String SELECT_PENDING = """
            select id
            from article
            where status = 'EMBEDDED'
            order by coalesce(published_at, fetched_at), id
            limit :limit
            """;

    private static final String SELECT_ARTICLE = """
            select embedding::text as embedding,
                   coalesce(published_at, fetched_at) as article_time
            from article
            where id = :id
            """;

    /** Exakte Nächster-Nachbar-Suche im Zeitfenster. {@code <=>} = Cosine-Distanz. */
    private static final String SELECT_NEAREST_STORY = """
            select id,
                   article_count,
                   centroid::text as centroid,
                   centroid <=> cast(:vector as vector) as distance
            from story
            where last_seen_at  >= :windowStart
              and first_seen_at <= :windowEnd
            order by distance, id
            limit 1
            """;

    private static final String UPDATE_STORY = """
            update story
            set centroid      = cast(:centroid as vector),
                article_count = article_count + 1,
                first_seen_at = least(first_seen_at, :time),
                last_seen_at  = greatest(last_seen_at, :time)
            where id = :id
            """;

    private static final String INSERT_STORY = """
            insert into story (centroid, article_count, first_seen_at, last_seen_at)
            values (cast(:centroid as vector), 1, :time, :time)
            returning id
            """;

    private static final String UPDATE_ARTICLE = """
            update article
            set story_id   = :storyId,
                similarity = :similarity,
                status     = 'CLUSTERED'
            where id = :id
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final double threshold;
    private final Duration window;
    private final int batchSize;

    public ClusteringService(JdbcClient jdbc, TransactionTemplate tx, BlickwinkelProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        var cfg = props.clustering();
        this.threshold = cfg.similarityThreshold();
        this.window = cfg.window();
        this.batchSize = cfg.batchSize();
    }

    private record ArticleRow(float[] vector, OffsetDateTime time) {
    }

    private record Candidate(long id, int articleCount, float[] centroid, double distance) {
    }

    @Scheduled(fixedDelayString = "${blickwinkel.clustering.interval}", initialDelayString = "PT30S")
    public void clusterPending() {
        List<Long> ids = jdbc.sql(SELECT_PENDING)
                .param("limit", batchSize)
                .query(Long.class)
                .list();

        int joined = 0;
        for (Long id : ids) {
            if (Boolean.TRUE.equals(tx.execute(status -> assign(id)))) {
                joined++;
            }
        }
        if (!ids.isEmpty()) {
            log.info("{} Artikel geclustert: {} zu bestehenden Stories, {} neue Stories",
                    ids.size(), joined, ids.size() - joined);
        }
    }

    /** @return true, wenn der Artikel einer bestehenden Story zugeordnet wurde */
    boolean assign(long articleId) {
        ArticleRow article = jdbc.sql(SELECT_ARTICLE)
                .param("id", articleId)
                .query((rs, n) -> new ArticleRow(
                        VectorText.parse(rs.getString("embedding")),
                        rs.getObject("article_time", OffsetDateTime.class)))
                .single();

        Optional<Candidate> nearest = jdbc.sql(SELECT_NEAREST_STORY)
                .param("vector", VectorText.format(article.vector()))
                .param("windowStart", article.time().minus(window))
                .param("windowEnd", article.time().plus(window))
                .query((rs, n) -> new Candidate(
                        rs.getLong("id"),
                        rs.getInt("article_count"),
                        VectorText.parse(rs.getString("centroid")),
                        rs.getDouble("distance")))
                .optional();

        long storyId;
        double similarity;
        boolean joined;

        if (nearest.isPresent() && 1.0 - nearest.get().distance() >= threshold) {
            Candidate story = nearest.get();
            storyId = story.id();
            similarity = 1.0 - story.distance();
            float[] centroid = Vectors.runningMean(story.centroid(), story.articleCount(), article.vector());

            jdbc.sql(UPDATE_STORY)
                    .param("centroid", VectorText.format(centroid))
                    .param("time", article.time())
                    .param("id", storyId)
                    .update();
            joined = true;
        } else {
            storyId = jdbc.sql(INSERT_STORY)
                    .param("centroid", VectorText.format(article.vector()))
                    .param("time", article.time())
                    .query(Long.class)
                    .single();
            similarity = 1.0;
            joined = false;
        }

        jdbc.sql(UPDATE_ARTICLE)
                .param("storyId", storyId)
                .param("similarity", (float) similarity)
                .param("id", articleId)
                .update();
        return joined;
    }
}
