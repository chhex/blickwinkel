package ch.chhex.blickwinkel.embedding;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;
import ch.chhex.blickwinkel.db.VectorText;

/** NEW → EMBEDDED: Titel und Teaser werden in Batches eingebettet. */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final String UPDATE_EMBEDDING = """
            update article
            set embedding = cast(? as vector),
                status    = 'EMBEDDED'
            where id = ?
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient client;
    private final int batchSize;
    private final int dimensions;

    public EmbeddingService(JdbcClient jdbc, JdbcTemplate jdbcTemplate, EmbeddingClient client,
            BlickwinkelProperties props) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.client = client;
        this.batchSize = props.embedding().batchSize();
        this.dimensions = props.embedding().dimensions();
    }

    record PendingArticle(long id, String title, String teaser) {
    }

    @Scheduled(fixedDelayString = "${blickwinkel.embedding.interval}", initialDelayString = "PT20S")
    public void embedPending() {
        int total = 0;
        while (!Thread.currentThread().isInterrupted()) {
            List<PendingArticle> batch = jdbc.sql("""
                            select id, title, teaser
                            from article
                            where status = 'NEW'
                            order by id
                            limit :limit
                            """)
                    .param("limit", batchSize)
                    .query((rs, n) -> new PendingArticle(rs.getLong("id"), rs.getString("title"), rs.getString("teaser")))
                    .list();
            if (batch.isEmpty()) {
                break;
            }

            List<float[]> vectors;
            try {
                vectors = client.embed(batch.stream().map(a -> textFor(a.title(), a.teaser())).toList());
            } catch (RestClientException e) {
                // Ollama nicht erreichbar: nichts markieren, beim nächsten Lauf erneut versuchen
                log.warn("Embedding fehlgeschlagen, neuer Versuch im nächsten Lauf: {}", e.getMessage());
                break;
            }

            List<Object[]> args = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                float[] v = vectors.get(i);
                if (v.length != dimensions) {
                    throw new IllegalStateException("Modell liefert " + v.length + " Dimensionen, Schema erwartet "
                            + dimensions + ". Modell oder vector(n) in der Migration anpassen.");
                }
                args.add(new Object[] {VectorText.format(v), batch.get(i).id()});
            }
            jdbcTemplate.batchUpdate(UPDATE_EMBEDDING, args);
            total += batch.size();
        }
        if (total > 0) {
            log.info("{} Artikel eingebettet", total);
        }
    }

    static String textFor(String title, String teaser) {
        return teaser == null ? title : title + "\n" + teaser;
    }
}
