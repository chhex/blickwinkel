package ch.chhex.blickwinkel.embedding;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;

/**
 * Ollama-Endpoint {@code POST /api/embed}. Bewusst ohne Spring AI:
 * ein einziger, gut lesbarer HTTP-Call, leicht nach Quarkus zu portieren.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient rest;
    private final String model;

    public OllamaEmbeddingClient(RestClient.Builder builder, BlickwinkelProperties props) {
        var cfg = props.embedding();
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        // Auf CPU kann ein Batch dauern, besonders beim ersten Laden des Modells
        requestFactory.setReadTimeout(Duration.ofMinutes(3));

        this.rest = builder
                .baseUrl(cfg.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        this.model = cfg.model();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbedResponse response = rest.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(model, texts))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
            throw new IllegalStateException("Ollama lieferte " +
                    (response == null || response.embeddings() == null ? 0 : response.embeddings().size()) +
                    " Embeddings für " + texts.size() + " Texte");
        }
        return response.embeddings().stream().map(OllamaEmbeddingClient::toArray).toList();
    }

    private static float[] toArray(List<Float> values) {
        float[] v = new float[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(i);
        }
        return v;
    }

    public record EmbedRequest(String model, List<String> input) {
    }

    public record EmbedResponse(String model, List<List<Float>> embeddings) {
    }
}
