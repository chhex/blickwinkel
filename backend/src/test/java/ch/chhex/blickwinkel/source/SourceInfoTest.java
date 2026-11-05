package ch.chhex.blickwinkel.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourceInfoTest {

    @Test
    void werteAusDerYaml() {
        assertEquals(Lean.MITTE_LINKS, Lean.parse(" mitte-links "));
        assertEquals(Lean.MITTE_RECHTS, Lean.parse("MITTE_RECHTS"));
        assertNull(Lean.parse(""));
        assertEquals(Ownership.PRIVAT, Ownership.parse(null));
        assertEquals(Ownership.PARTEIEN, Ownership.parse("parteien"));
        assertThrows(IllegalArgumentException.class, () -> Lean.parse("ganz-links"));
    }

    @Test
    void herkunftStandardEigeneNurMitAusrichtung() {
        assertEquals(LeanSource.EIGENE, LeanSource.parse(null, Lean.MITTE));
        assertNull(LeanSource.parse(null, null));
        assertEquals(LeanSource.ALLSIDES, LeanSource.parse("AllSides", Lean.MITTE));
    }

    @Test
    void skalaUndTooltip() {
        var hoy = new SourceInfo("hoy", "Hoy", "DO", "es", "NEWS", Ownership.PARTEIEN, Lean.KEINE,
                LeanSource.EIGENE, " nimmt Geld von Parteien ");
        assertNull(hoy.leanPosition(), "keine Richtung: keine Skala");
        assertEquals("Hoy · DO · Parteigeld · Ausrichtung: keine Richtung (eigene Einschätzung) · nimmt Geld von Parteien",
                hoy.tooltip());

        var fox = new SourceInfo("foxnews", "Fox News", "US", "en", "NEWS", null, Lean.RECHTS,
                LeanSource.ALLSIDES, null);
        assertEquals(2, fox.leanPosition());
        assertEquals(Ownership.PRIVAT, fox.ownership());
        assertEquals("", fox.ownership().icon());
    }
}
