package ch.chhex.blickwinkel.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class StoryFilterTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T10:00:00Z");

    @Test
    void suchbegriffeWerdenBereinigt() {
        assertEquals("Armee Budget", StoryFilter.normalizeQuery("  Armee \t  Budget "));
        assertNull(StoryFilter.normalizeQuery("   "));
        assertNull(StoryFilter.normalizeQuery(null));
    }

    @Test
    void likePlatzhalterWerdenWoertlich() {
        assertEquals("100\\%", StoryFilter.escapeLike("100%"));
        assertEquals("x\\_y", StoryFilter.escapeLike("x_y"));
        assertEquals("a\\\\b", StoryFilter.escapeLike("a\\b"));
    }

    @Test
    void standardwerte() {
        var f = new StoryFilter(NOW.minusHours(24), NOW, 2, " ", null, 200);
        assertNull(f.query());
        assertNull(f.likeQuery());
        assertEquals(StorySort.NEU, f.sort());
    }

    @Test
    void zeitraumPflicht() {
        assertThrows(IllegalArgumentException.class, () -> new StoryFilter(null, NOW, 2, null, null, 10));
    }

    @Test
    void sortierungAusCode() {
        assertEquals(StorySort.QUELLEN, StorySort.fromCode(" Quellen "));
        assertEquals(StorySort.NEU, StorySort.fromCode("drop table"));
        assertEquals(StorySort.NEU, StorySort.fromCode(null));
    }
}
