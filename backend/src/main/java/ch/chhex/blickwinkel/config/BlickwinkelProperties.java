package ch.chhex.blickwinkel.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alle Einstellungen unter {@code blickwinkel.*}. Die Intervalle der Jobs werden
 * direkt in den {@code @Scheduled}-Annotationen gelesen und fehlen hier deshalb.
 */
@ConfigurationProperties("blickwinkel")
public record BlickwinkelProperties(
        Ingest ingest,
        Embedding embedding,
        Clustering clustering,
        List<SourceDefinition> sources) {

    public record Ingest(Duration timeout, int teaserMaxLength, String userAgent) {
    }

    public record Embedding(URI baseUrl, String model, int dimensions, int batchSize) {
    }

    public record Clustering(double similarityThreshold, Duration window, int batchSize) {
    }

    /**
     * Ein Eintrag aus sources.yml: eine Quelle (Redaktion) mit einem oder mehreren Feeds.
     * {@code camp} darf leer sein.
     */
    public record SourceDefinition(
            String code,
            String name,
            String country,
            String language,
            String section,
            List<URI> feedUrls,
            String camp) {
    }
}
