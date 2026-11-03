package ch.chhex.blickwinkel.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;
import ch.chhex.blickwinkel.config.BlickwinkelProperties.Translation;

class DeepLClientTest {

    private static BlickwinkelProperties props(String key) {
        return new BlickwinkelProperties(null, null, null,
                new Translation(key, null, List.of("de", "en", "es")), List.of());
    }

    private record Setup(DeepLClient client, MockRestServiceServer server) {
    }

    private static Setup setup(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new DeepLClient(builder, props(key), false), server);
    }

    @Test
    void uebersetztTitelUndTeaserInEinemAufruf() {
        var s = setup("geheim:fx");
        s.server().expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "DeepL-Auth-Key geheim:fx"))
                .andExpect(jsonPath("$.target_lang").value("EN-GB"))
                .andExpect(jsonPath("$.source_lang").value("FR"))
                .andExpect(jsonPath("$.text[0]").value("Le titre"))
                .andExpect(jsonPath("$.text[1]").value("Le teaser"))
                .andRespond(withSuccess("""
                        {"translations": [
                          {"detected_source_language": "FR", "text": "The title"},
                          {"detected_source_language": "FR", "text": "The teaser", "model_type_used": "x"}
                        ]}""", MediaType.APPLICATION_JSON));

        var result = s.client().translate(List.of("Le titre", "Le teaser"), "fr", "en");

        assertEquals(List.of("The title", "The teaser"), result.texts());
        assertEquals("fr", result.detectedSourceLang());
        s.server().verify();
    }

    @Test
    void ohneAusgangsspracheWirdSieNichtMitgeschickt() {
        var s = setup("geheim:fx");
        s.server().expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andExpect(jsonPath("$.source_lang").doesNotExist())
                .andExpect(jsonPath("$.target_lang").value("DE"))
                .andRespond(withSuccess("""
                        {"translations": [{"detected_source_language": "EN", "text": "Titel"}]}""",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of("Titel"), s.client().translate(List.of("Title"), null, "de").texts());
    }

    @Test
    void kontingentAufgebrauchtGibtVerstaendlicheMeldung() {
        var s = setup("geheim:fx");
        s.server().expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andRespond(withStatus(HttpStatusCode.valueOf(456)));

        var e = assertThrows(TranslationException.class,
                () -> s.client().translate(List.of("Titel"), "de", "en"));
        assertTrue(e.getMessage().contains("Kontingent"));
    }

    @Test
    void falscherSchluessel() {
        var s = setup("geheim:fx");
        s.server().expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andRespond(withStatus(HttpStatusCode.valueOf(403)));

        var e = assertThrows(TranslationException.class,
                () -> s.client().translate(List.of("Titel"), "de", "en"));
        assertTrue(e.getMessage().contains("Schlüssel"));
    }

    @Test
    void kontingentAbfragen() {
        var s = setup("geheim:fx");
        s.server().expect(requestTo("https://api-free.deepl.com/v2/usage"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"character_count": 12345, "character_limit": 500000}""", MediaType.APPLICATION_JSON));

        var usage = s.client().usage().orElseThrow();
        assertEquals(12345, usage.characterCount());
        assertEquals(500000, usage.characterLimit());
    }

    @Test
    void ohneSchluesselAus() {
        var s = setup("");
        assertFalse(s.client().isConfigured());
        assertTrue(s.client().usage().isEmpty());
        assertThrows(TranslationException.class, () -> s.client().translate(List.of("x"), "de", "en"));
    }

    @Test
    void basisUrlNachSchluesseltyp() {
        assertEquals(DeepLClient.FREE_URL, DeepLClient.baseUrl("abc:fx", null));
        assertEquals(DeepLClient.PRO_URL, DeepLClient.baseUrl("abc", null));
        assertEquals("https://eigener.server", DeepLClient.baseUrl("abc:fx", " https://eigener.server "));
    }

    @Test
    void zielsprachen() {
        assertEquals("EN-GB", DeepLClient.targetCode("en"));
        assertEquals("DE", DeepLClient.targetCode("de"));
        assertEquals("ES", DeepLClient.targetCode("ES"));
    }
}
