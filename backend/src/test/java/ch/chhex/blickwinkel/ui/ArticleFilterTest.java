package ch.chhex.blickwinkel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ch.chhex.blickwinkel.story.StoryQueryService.StoryArticle;

class ArticleFilterTest {

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    private static StoryArticle art(long id, String code, String name, String country, String lang) {
        return new StoryArticle(id, code, name, "NEWS", country, lang, "t" + id, null, "u" + id, T.plusHours(id), null);
    }

    private static final List<StoryArticle> ALLE = List.of(
            art(1, "nzz", "NZZ", "CH", "de"),
            art(2, "letemps", "Le Temps", "CH", "fr"),
            art(3, "nzz", "NZZ", "CH", "de"),
            art(4, "guardian", "The Guardian", "GB", "en"));

    private static List<Long> ids(List<StoryArticle> list) {
        return list.stream().map(StoryArticle::id).toList();
    }

    @Test
    void ohneAuswahlAlles() {
        var f = ArticleFilter.of(null, List.of(" ", ""), null);
        assertTrue(f.isEmpty());
        assertEquals(ALLE, f.apply(ALLE));
    }

    @Test
    void innerhalbOderDazwischenUnd() {
        assertEquals(List.of(2L, 4L), ids(ArticleFilter.of(List.of("letemps", "guardian"), null, null).apply(ALLE)));
        assertEquals(List.of(2L), ids(ArticleFilter.of(null, List.of("fr", "en"), List.of("CH")).apply(ALLE)));
    }

    @Test
    void schreibweiseWieInDerDatenbank() {
        var f = ArticleFilter.of(List.of(" NZZ "), List.of("DE"), List.of("ch"));
        assertEquals(Set.of("nzz"), f.quellen());
        assertEquals(Set.of("de"), f.sprachen());
        assertEquals(Set.of("CH"), f.laender());
        assertEquals(List.of(1L, 3L), ids(f.apply(ALLE)));
    }

    @Test
    void auswahlZeigtAllesMitAnzahlSortiertNachName() {
        var facets = ArticleFilter.facets(ALLE);
        assertEquals(List.of(
                new ArticleFilter.Option("letemps", "Le Temps", 1),
                new ArticleFilter.Option("nzz", "NZZ", 2),
                new ArticleFilter.Option("guardian", "The Guardian", 1)), facets.quellen());
        assertEquals(Arrays.asList("DE", "EN", "FR"),
                facets.sprachen().stream().map(ArticleFilter.Option::label).toList());
        assertEquals(List.of(
                new ArticleFilter.Option("CH", "CH", 3),
                new ArticleFilter.Option("GB", "GB", 1)), facets.laender());
    }
}
