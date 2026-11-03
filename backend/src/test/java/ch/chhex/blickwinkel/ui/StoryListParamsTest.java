package ch.chhex.blickwinkel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class StoryListParamsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T10:00:00Z");

    @Test
    void festerZeitraum() {
        var p = StoryListParams.of(48, null, null, 2, null, null);
        assertFalse(p.isRange());
        var f = p.toFilter(NOW);
        assertEquals(NOW.minusHours(48), f.from());
        assertEquals(NOW, f.to());
    }

    @Test
    void datumGewinntUeberFestenZeitraum() {
        var p = StoryListParams.of(48, LocalDate.of(2026, 11, 1), null, 2, null, null);
        assertTrue(p.isRange());
        assertEquals(0, p.hours());
        var f = p.toFilter(NOW);
        assertEquals(Instant.parse("2026-10-31T23:00:00Z"), f.from().toInstant());
        assertEquals(NOW, f.to(), "ohne «bis» gilt bis jetzt");
    }

    @Test
    void bisEinschliesslichAuchImSommer() {
        // 1. Juli: Sommerzeit, UTC+2
        var f = StoryListParams.of(0, null, LocalDate.of(2026, 7, 1), 2, null, null).toFilter(NOW);
        assertEquals(Instant.parse("2026-07-01T22:00:00Z"), f.to().toInstant());
    }

    @Test
    void vertauschteDatenWerdenGetauscht() {
        var p = StoryListParams.of(0, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 1), 2, null, null);
        assertEquals(LocalDate.of(2026, 11, 1), p.von());
        assertEquals(LocalDate.of(2026, 11, 3), p.bis());
    }

    @Test
    void ungueltigeWerteFallenAufStandard() {
        var p = StoryListParams.of(0, null, null, 0, "  ", null);
        assertEquals(StoryListParams.DEFAULT_HOURS, p.hours());
        assertEquals(1, p.minSources());
        assertEquals(null, p.q());
    }
}
