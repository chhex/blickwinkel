package ch.chhex.blickwinkel.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    @Test
    void entferntTrackingFragmentUndDefaultPort() {
        assertEquals("https://www.nzz.ch/schweiz/artikel-ld.123",
                UrlNormalizer.normalize("HTTPS://WWW.NZZ.CH:443/schweiz/artikel-ld.123?utm_source=rss&utm_medium=feed#top"));
    }

    @Test
    void behaeltFachlicheParameterSortiert() {
        assertEquals("https://example.ch/a?id=7&page=2",
                UrlNormalizer.normalize("https://example.ch/a?page=2&fbclid=xyz&id=7"));
    }

    @Test
    void leererPfadWirdSlash() {
        assertEquals("https://example.ch/", UrlNormalizer.normalize("https://example.ch"));
    }

    @Test
    void pfadBleibtCaseSensitiv() {
        assertNotEquals(UrlNormalizer.normalize("https://example.ch/A"), UrlNormalizer.normalize("https://example.ch/a"));
    }

    @Test
    void hashHat64Zeichen() {
        assertEquals(64, UrlNormalizer.hash("https://example.ch/").length());
    }

    @Test
    void ungueltigeUrl() {
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize("kein link"));
    }
}
