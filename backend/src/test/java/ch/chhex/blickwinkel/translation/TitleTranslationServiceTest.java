package ch.chhex.blickwinkel.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.chhex.blickwinkel.translation.TitleTranslationService.TitleRef;
import ch.chhex.blickwinkel.translation.TitleTranslationService.TitleTranslations;

class TitleTranslationServiceTest {

    @Test
    void gruppiertNachSpracheUndTeiltInPakete() {
        List<TitleRef> refs = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            refs.add(new TitleRef(i, "t" + i, "fr"));
        }
        refs.add(new TitleRef(500, "Hola", "es"));
        refs.add(new TitleRef(500, "Hola", "es")); // derselbe Artikel in zwei Stories

        var batches = TitleTranslationService.batches(refs);

        assertEquals(List.of("fr", "es"), List.copyOf(batches.keySet()));
        assertEquals(List.of(50, 50, 20), batches.get("fr").stream().map(List::size).toList());
        assertEquals(1, batches.get("es").size());
        assertEquals(1, batches.get("es").get(0).size(), "doppelter Artikel nur einmal");
        assertEquals(0L, batches.get("fr").get(0).get(0).articleId(), "Reihenfolge bleibt");
    }

    @Test
    void ohneUebersetzungDasOriginal() {
        var t = new TitleTranslations("en", Map.of(7L, "Hello"), null);
        assertEquals("Hello", t.of(7, "Hallo"));
        assertEquals("Servus", t.of(8, "Servus"));
        assertTrue(t.translated(7));
        assertFalse(t.translated(8));
        assertEquals("Hallo", TitleTranslations.NONE.of(7, "Hallo"));
    }
}
