package ch.chhex.blickwinkel.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.chhex.blickwinkel.config.BlickwinkelProperties.SourceDefinition;

class SourceSyncValidationTest {

    private static SourceDefinition source(String code, String... urls) {
        return new SourceDefinition(code, code, "CH", "de", "NEWS",
                List.of(urls).stream().map(URI::create).toList(), null);
    }

    @Test
    void gueltigeDefinitionen() {
        assertDoesNotThrow(() -> SourceSync.validate(List.of(
                source("nyt", "https://a/world", "https://a/home"),
                source("bbc", "https://b/world"))));
    }

    @Test
    void quelleOhneFeeds() {
        var e = assertThrows(IllegalStateException.class,
                () -> SourceSync.validate(List.of(source("leer"))));
        assertTrue(e.getMessage().contains("leer: keine feed-urls"));
    }

    @Test
    void doppelterCode() {
        var e = assertThrows(IllegalStateException.class, () -> SourceSync.validate(List.of(
                source("nyt", "https://a/1"),
                source("nyt", "https://a/2"))));
        assertTrue(e.getMessage().contains("code doppelt: nyt"));
    }

    @Test
    void feedBeiZweiQuellen() {
        var e = assertThrows(IllegalStateException.class, () -> SourceSync.validate(List.of(
                source("a", "https://x/feed"),
                source("b", "https://x/feed"))));
        assertTrue(e.getMessage().contains("mehrfach eingetragen"));
    }

    @Test
    void sammeltAlleFehler() {
        var e = assertThrows(IllegalStateException.class, () -> SourceSync.validate(List.of(
                source("a"),
                source("b"))));
        assertTrue(e.getMessage().contains("a: keine feed-urls"));
        assertTrue(e.getMessage().contains("b: keine feed-urls"));
    }
}
