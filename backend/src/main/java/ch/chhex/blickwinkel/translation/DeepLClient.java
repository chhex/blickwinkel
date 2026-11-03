package ch.chhex.blickwinkel.translation;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;

/**
 * DeepL API v2: {@code POST /v2/translate} und {@code GET /v2/usage}.
 * Ein schlanker HTTP-Client ohne DeepL-Bibliothek, analog zum Ollama-Client.
 */
@Component
public class DeepLClient implements TranslationClient {

    private static final Logger log = LoggerFactory.getLogger(DeepLClient.class);

    static final String FREE_URL = "https://api-free.deepl.com";
    static final String PRO_URL = "https://api.deepl.com";

    private final RestClient rest;
    private final boolean configured;

    public DeepLClient(RestClient.Builder builder, BlickwinkelProperties props) {
        this(builder, props, true);
    }

    /** {@code withTimeouts=false} nur für Tests, damit ein Mock-Server den Request-Factory behält. */
    DeepLClient(RestClient.Builder builder, BlickwinkelProperties props, boolean withTimeouts) {
        var cfg = props.translation();
        String key = cfg == null ? null : cfg.deeplApiKey();
        this.configured = key != null && !key.isBlank();

        if (withTimeouts) {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(30));
            builder.requestFactory(requestFactory);
        }
        builder.baseUrl(baseUrl(key, cfg == null ? null : cfg.deeplBaseUrl()));
        if (configured) {
            builder.defaultHeader("Authorization", "DeepL-Auth-Key " + key.trim());
        }
        this.rest = builder.build();
    }

    /** Gratis-Schlüssel enden auf ":fx" und funktionieren nur mit api-free. */
    static String baseUrl(String key, String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return key != null && key.trim().endsWith(":fx") ? FREE_URL : PRO_URL;
    }

    /** DeepL verlangt für Englisch als Zielsprache eine Variante. */
    static String targetCode(String lang) {
        String l = lang.toLowerCase(Locale.ROOT);
        return "en".equals(l) ? "EN-GB" : l.toUpperCase(Locale.ROOT);
    }

    @Override
    public String provider() {
        return "deepl";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Result translate(List<String> texts, String sourceLang, String targetLang) {
        if (!configured) {
            throw new TranslationException("Übersetzung ist nicht eingerichtet (DEEPL_API_KEY fehlt).");
        }
        var request = new TranslateRequest(
                texts,
                targetCode(targetLang),
                sourceLang == null ? null : sourceLang.trim().toUpperCase(Locale.ROOT));
        try {
            TranslateResponse response = rest.post()
                    .uri("/v2/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TranslateResponse.class);

            if (response == null || response.translations() == null
                    || response.translations().size() != texts.size()) {
                throw new TranslationException("DeepL lieferte eine unerwartete Antwort.");
            }
            List<String> translated = response.translations().stream().map(TranslatedText::text).toList();
            String detected = response.translations().get(0).detectedSourceLanguage();
            return new Result(translated, detected == null ? null : detected.toLowerCase(Locale.ROOT));
        } catch (RestClientResponseException e) {
            throw new TranslationException(message(e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            throw new TranslationException("DeepL ist nicht erreichbar.", e);
        }
    }

    @Override
    public Optional<Usage> usage() {
        if (!configured) {
            return Optional.empty();
        }
        try {
            UsageResponse u = rest.get().uri("/v2/usage").retrieve().body(UsageResponse.class);
            return u == null ? Optional.empty() : Optional.of(new Usage(u.characterCount(), u.characterLimit()));
        } catch (RestClientException e) {
            log.warn("DeepL-Kontingent nicht abrufbar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String message(int status) {
        return switch (status) {
            case 403 -> "DeepL lehnt den API-Schlüssel ab.";
            case 429 -> "Zu viele Anfragen an DeepL, bitte kurz warten.";
            case 456 -> "Das DeepL-Kontingent für diesen Monat ist aufgebraucht.";
            default -> "Übersetzung fehlgeschlagen (DeepL HTTP " + status + ").";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TranslateRequest(
            List<String> text,
            @JsonProperty("target_lang") String targetLang,
            @JsonProperty("source_lang") String sourceLang) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslateResponse(List<TranslatedText> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslatedText(
            @JsonProperty("detected_source_language") String detectedSourceLanguage,
            String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageResponse(
            @JsonProperty("character_count") long characterCount,
            @JsonProperty("character_limit") long characterLimit) {
    }
}
