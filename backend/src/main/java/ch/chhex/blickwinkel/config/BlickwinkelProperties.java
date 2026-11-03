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
        Translation translation,
        List<SourceDefinition> sources) {

    public record Ingest(Duration timeout, int teaserMaxLength, String userAgent) {
    }

    public record Embedding(URI baseUrl, String model, int dimensions, int batchSize) {
    }

    public record Clustering(double similarityThreshold, Duration window, int batchSize) {
    }

    /**
     * Übersetzung auf Abruf. Ohne {@code deeplApiKey} ist die Funktion aus.
     * {@code deeplBaseUrl} leer = automatisch: Gratis-Schlüssel (enden auf ":fx")
     * gehen an api-free.deepl.com, alle anderen an api.deepl.com.
     */
    public record Translation(String deeplApiKey, String deeplBaseUrl, List<String> targetLanguages) {
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
