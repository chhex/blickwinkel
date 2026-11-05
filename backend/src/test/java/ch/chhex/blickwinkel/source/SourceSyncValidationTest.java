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
        return classified(code, null, null, null, urls);
    }

    private static SourceDefinition classified(String code, String ownership, String lean, String leanSource,
            String... urls) {
        return new SourceDefinition(code, code, "CH", "de", "NEWS",
                List.of(urls).stream().map(URI::create).toList(), null, ownership, lean, leanSource, null);
    }

    @Test
    void gueltigeEinordnung() {
        assertDoesNotThrow(() -> SourceSync.validate(List.of(
                classified("srf", "oeffentlich", "mitte-links", null, "https://s/1"),
                classified("fox", "privat", "RECHTS", "allsides", "https://f/1"),
                classified("telesur", "staatlich", "keine", null, "https://t/1"),
                classified("hoy", "parteien", null, null, "https://h/1"))));
    }

    @Test
    void unbekannteEinordnung() {
        var e = assertThrows(IllegalStateException.class, () -> SourceSync.validate(List.of(
                classified("a", "konzern", "ganz-links", "mbfc", "https://a/1"),
                classified("b", null, null, "allsides", "https://b/1"))));
        assertTrue(e.getMessage().contains("a: ownership unbekannt: konzern"), e.getMessage());
        assertTrue(e.getMessage().contains("a: lean unbekannt: ganz-links"), e.getMessage());
        assertTrue(e.getMessage().contains("a: lean-source unbekannt: mbfc"), e.getMessage());
        assertTrue(e.getMessage().contains("b: lean-source ohne lean"), e.getMessage());
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
