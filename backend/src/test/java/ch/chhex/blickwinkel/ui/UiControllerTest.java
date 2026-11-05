package ch.chhex.blickwinkel.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.chhex.blickwinkel.source.Lean;
import ch.chhex.blickwinkel.source.LeanSource;
import ch.chhex.blickwinkel.source.Ownership;
import ch.chhex.blickwinkel.source.SourceCatalog;
import ch.chhex.blickwinkel.source.SourceInfo;
import ch.chhex.blickwinkel.source.SourceStatusService;
import ch.chhex.blickwinkel.source.SourceStatusService.FeedStatus;
import ch.chhex.blickwinkel.source.SourceStatusService.SourceOption;
import ch.chhex.blickwinkel.story.StoryFilter;
import ch.chhex.blickwinkel.story.StoryQueryService;
import ch.chhex.blickwinkel.story.StorySort;
import ch.chhex.blickwinkel.story.StoryQueryService.Singleton;
import ch.chhex.blickwinkel.story.StoryQueryService.StoryArticle;
import ch.chhex.blickwinkel.story.StoryQueryService.StoryDetail;
import ch.chhex.blickwinkel.story.StoryQueryService.StorySummary;
import ch.chhex.blickwinkel.translation.TranslationClient.Usage;
import ch.chhex.blickwinkel.translation.TranslationException;
import ch.chhex.blickwinkel.translation.TranslationService;
import ch.chhex.blickwinkel.translation.TranslationService.ArticleText;
import ch.chhex.blickwinkel.translation.TranslationService.Translation;

/**
 * Rendert alle Seiten mit Beispieldaten. Fängt Fehler in Templates und
 * Ausdrücken ab, die sonst erst im Browser auffallen würden.
 */
@WebMvcTest(UiController.class)
@Import(Formats.class)
class UiControllerTest {

    private static final OffsetDateTime T = OffsetDateTime.of(2026, 11, 1, 15, 33, 0, 0, ZoneOffset.UTC);

    /** 300 Zeichen Anfang plus ein erkennbares Ende, damit der aufklappbare Teil greift. */
    private static final String LANGER_TEASER = "x".repeat(300) + "ENDE-DES-TEASERS";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StoryQueryService stories;

    @MockitoBean
    SourceStatusService status;

    @MockitoBean
    TranslationService translations;

    @MockitoBean
    SourceCatalog catalog;

    private static final ArticleText LE_TEMPS = new ArticleText(3, "https://letemps.ch/c",
            "Le budget militaire augmente", "Le Conseil fédéral a décidé.", "fr");

    @BeforeEach
    void beispieldaten() {
        when(stories.search(any())).thenReturn(List.of(
                new StorySummary(505, "Bundesrat erhöht Armeebudget", T, T.plusHours(7), 4,
                        // watson fehlt bewusst im Katalog: Platzhalter mit Code
                        List.of("nzz", "srf", "watson"))));

        when(catalog.snapshot()).thenReturn(new SourceCatalog.Snapshot(Map.of(
                "nzz", new SourceInfo("nzz", "NZZ", "CH", "de", "NEWS", Ownership.PRIVAT, Lean.MITTE_RECHTS,
                        LeanSource.EIGENE, null),
                "srf", new SourceInfo("srf", "SRF News", "CH", "de", "NEWS", Ownership.OEFFENTLICH,
                        Lean.MITTE_LINKS, LeanSource.EIGENE, "Gebühren (Serafe)"),
                "guardian", new SourceInfo("guardian", "The Guardian", "GB", "en", "NEWS", Ownership.PRIVAT,
                        Lean.MITTE_LINKS, LeanSource.ALLSIDES, null),
                "sputnik", new SourceInfo("sputnik", "Sputnik Mundo", "RU", "es", "NEWS", Ownership.STAATLICH,
                        Lean.KEINE, LeanSource.EIGENE, "russischer Staat"))));

        when(stories.story(anyLong())).thenReturn(Optional.empty());
        when(stories.story(505)).thenReturn(Optional.of(new StoryDetail(505, "Bundesrat erhöht Armeebudget",
                T, T.plusHours(7), List.of(
                        // in Erscheinungsreihenfolge, wie sie die Abfrage liefert
                        new StoryArticle(1, "nzz-meinung", "NZZ Meinung", "OPINION", "CH", "de",
                                "Die Armee braucht mehr", "Ein Kommentar", "https://nzz.ch/a", T, 0.81f),
                        new StoryArticle(2, "srf", "SRF News", "NEWS", "CH", "de", "Mehr Geld für die Armee",
                                null, "https://srf.ch/b", T.plusHours(1), null),
                        new StoryArticle(3, "letemps", "Le Temps", "NEWS", "CH", "fr",
                                "Le budget de l'armée augmente", LANGER_TEASER, "https://letemps.ch/c",
                                T.plusHours(2), 0.74f),
                        new StoryArticle(4, "guardian", "The Guardian", "NEWS", "GB", "en",
                                "Swiss raise defence spending", null, "https://theguardian.com/d",
                                T.plusHours(3), 0.72f)))));

        when(stories.singletons(anyInt(), any(), anyInt())).thenReturn(List.of(
                new Singleton(1, "NZZ", "Knapp verpasst", "https://nzz.ch/c", T, 505L, "Bundesrat erhöht Armeebudget", 0.68),
                new Singleton(2, "SRF News", "Ganz allein", "https://srf.ch/d", T, null, null, null)));

        Map<String, Long> pipeline = new LinkedHashMap<>();
        pipeline.put("NEW", 3L);
        pipeline.put("EMBEDDED", 0L);
        pipeline.put("CLUSTERED", 560L);
        pipeline.put("FAILED", 0L);
        when(status.pipeline()).thenReturn(pipeline);
        when(status.feeds()).thenReturn(List.of(
                new FeedStatus("nzz", "NZZ", "CH", "NEWS", "https://www.nzz.ch/recent.rss", T, null, 120, 16),
                new FeedStatus("ft", "Financial Times", "GB", "NEWS", "https://www.ft.com/rss/home", null, "HTTP 403", 0, 0)));
        when(status.activeSources()).thenReturn(List.of(new SourceOption("nzz", "NZZ")));

        when(translations.isEnabled()).thenReturn(true);
        when(translations.targetLanguages()).thenReturn(List.of("de", "en", "es"));
        when(translations.article(anyLong())).thenReturn(Optional.empty());
        when(translations.article(3)).thenReturn(Optional.of(LE_TEMPS));
        when(translations.translate(eq(LE_TEMPS), eq("de"))).thenReturn(new Translation("de",
                "Das Armeebudget steigt", "Der Bundesrat hat entschieden.", "fr", "deepl"));
        when(translations.translate(eq(LE_TEMPS), eq("es")))
                .thenThrow(new TranslationException("Das DeepL-Kontingent für diesen Monat ist aufgebraucht."));
        when(translations.usage()).thenReturn(Optional.of(new Usage(12345, 500000)));
    }

    @Test
    void startseiteLeitetWeiter() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/stories"));
    }

    @Test
    void storiesGanzeSeite() throws Exception {
        mvc.perform(get("/stories"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("Bundesrat erhöht Armeebudget")))
                .andExpect(content().string(containsString("3 Quellen · 4 Artikel · 01.11. 16:33 – 23:33")))
                .andExpect(content().string(containsString("/vendor/htmx-2.0.11.min.js")));
    }

    private StoryFilter captureFilter() {
        var captor = ArgumentCaptor.forClass(StoryFilter.class);
        verify(stories).search(captor.capture());
        return captor.getValue();
    }

    @Test
    void storiesFilterKommenImServiceAn() throws Exception {
        mvc.perform(get("/stories").param("q", "  Armee  Budget ").param("sort", "quellen")
                        .param("minSources", "3").param("hours", "48").header("HX-Request", "true"))
                .andExpect(status().isOk());
        var f = captureFilter();
        assertEquals("Armee Budget", f.query());
        assertEquals(StorySort.QUELLEN, f.sort());
        assertEquals(3, f.minSources());
        long stunden = java.time.Duration.between(f.from(), f.to()).toHours();
        assertEquals(48, stunden);
    }

    @Test
    void datumsbereichInSchweizerZeit() throws Exception {
        mvc.perform(get("/stories").param("von", "2026-11-01").param("bis", "2026-11-02"))
                .andExpect(status().isOk())
                // im Formular steht jetzt «Datumsbereich» statt eines festen Zeitraums
                .andExpect(content().string(containsString("<option value=\"0\" selected=\"selected\">Datumsbereich</option>")))
                .andExpect(content().string(containsString("value=\"2026-11-01\"")));
        var f = captureFilter();
        // 1.11. 00:00 Zürich (Winterzeit, UTC+1) bis 3.11. 00:00 Zürich, also «bis» einschliesslich
        assertEquals(java.time.Instant.parse("2026-10-31T23:00:00Z"), f.from().toInstant());
        assertEquals(java.time.Instant.parse("2026-11-02T23:00:00Z"), f.to().toInstant());
    }

    @Test
    void ohneSucheUndMitUnbekannterSortierung() throws Exception {
        mvc.perform(get("/stories").param("q", "   ").param("sort", "quatsch")).andExpect(status().isOk());
        var f = captureFilter();
        assertNull(f.query());
        assertEquals(StorySort.NEU, f.sort());
        assertEquals(24, java.time.Duration.between(f.from(), f.to()).toHours());
    }

    @Test
    void leeresSuchergebnisNenntDenBegriff() throws Exception {
        when(stories.search(any())).thenReturn(List.of());
        mvc.perform(get("/stories").param("q", "Fondue").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keine Stories mit «Fondue» in diesem Zeitraum.")));
    }

    @Test
    void anzahlTreffer() throws Exception {
        mvc.perform(get("/stories").header("HX-Request", "true"))
                .andExpect(content().string(containsString("1 Story")));
    }

    @Test
    void sortierungImFormular() throws Exception {
        mvc.perform(get("/stories").param("sort", "artikel"))
                .andExpect(content().string(containsString("<option value=\"artikel\" selected=\"selected\">Meiste Artikel</option>")));
    }

    @Test
    void storiesNurFragmentFuerHtmx() throws Exception {
        mvc.perform(get("/stories").param("hours", "48").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("id=\"list\"")))
                .andExpect(content().string(containsString("Bundesrat erhöht Armeebudget")));
    }

    @Test
    void storyDetail() throws Exception {
        mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Die Armee braucht mehr")))
                .andExpect(content().string(containsString("Meinung")))
                .andExpect(content().string(containsString("0.81")));
    }

    @Test
    void langerTeaserIstAufklappbarUndVollstaendig() throws Exception {
        mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<details class=\"teaser\">")))
                .andExpect(content().string(containsString("… mehr")))
                .andExpect(content().string(containsString("ENDE-DES-TEASERS")))
                // kurzer Teaser bleibt ohne Aufklappen
                .andExpect(content().string(containsString(">Ein Kommentar<")));
    }

    @Test
    void storyDetailZeigtFilterUndMarkiertErstenArtikel() throws Exception {
        String html = mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"quelle\" value=\"guardian\"")))
                .andExpect(content().string(containsString("name=\"sprache\" value=\"fr\"")))
                .andExpect(content().string(containsString("name=\"land\" value=\"GB\"")))
                .andExpect(content().string(containsString(">zuerst<")))
                .andExpect(content().string(containsString("class=\"badge land\"")))
                // ohne Filter keine Zeile «x von y»
                .andExpect(content().string(not(containsString("von 4 Artikeln"))))
                .andReturn().getResponse().getContentAsString();
        // Reihenfolge der Abfrage bleibt erhalten
        int nzz = html.indexOf("Die Armee braucht mehr");
        int guardian = html.indexOf("Swiss raise defence spending");
        assertEquals(true, nzz > 0 && guardian > nzz, "NZZ vor Guardian");
    }

    @Test
    void storyDetailFiltertNachQuelle() throws Exception {
        mvc.perform(get("/stories/505").param("quelle", "srf").param("quelle", "guardian"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mehr Geld für die Armee")))
                .andExpect(content().string(containsString("Swiss raise defence spending")))
                .andExpect(content().string(not(containsString("Le budget de l&#39;armée augmente"))))
                .andExpect(content().string(containsString("2 von 4 Artikeln")))
                // Auswahl bleibt angekreuzt, «zuerst» bleibt beim ältesten Artikel der ganzen Story
                .andExpect(content().string(containsString("value=\"srf\" checked")))
                .andExpect(content().string(not(containsString(">zuerst<"))));
    }

    @Test
    void storyDetailFilterKombiniertSpracheUndLand() throws Exception {
        mvc.perform(get("/stories/505").param("sprache", "DE").param("land", "ch"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 von 4 Artikeln")))
                .andExpect(content().string(not(containsString("Swiss raise defence spending"))));
    }

    @Test
    void storyDetailFilterOhneTreffer() throws Exception {
        mvc.perform(get("/stories/505").param("quelle", "gibtsnicht"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keine Artikel für diese Auswahl.")))
                .andExpect(content().string(containsString("alle anzeigen")));
    }

    @Test
    void storyDetailNurTabelleFuerHtmx() throws Exception {
        mvc.perform(get("/stories/505").param("land", "GB").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"articles\"")))
                .andExpect(content().string(containsString("Swiss raise defence spending")))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("<legend>Quelle</legend>"))));
    }

    @Test
    void uebersichtZeigtEinordnungDerQuellen() throws Exception {
        mvc.perform(get("/stories"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">SRF News<")))
                // öffentlich: Zeichen; privat: keins
                .andExpect(content().string(containsString("aria-label=\"öffentlich finanziert\"")))
                .andExpect(content().string(containsString("aria-label=\"Ausrichtung mitte-links\"")))
                .andExpect(content().string(containsString(
                        "title=\"SRF News · CH · öffentlich finanziert · Ausrichtung: mitte-links (eigene Einschätzung) · Gebühren (Serafe)\"")))
                .andExpect(content().string(containsString("class=\"on\"")))
                // nicht im Katalog: Code als Name, ohne Skala
                .andExpect(content().string(containsString(">watson<")))
                .andExpect(content().string(containsString("watson · privat · Ausrichtung: nicht eingeordnet")));
    }

    @Test
    void storyDetailZeigtEinordnung() throws Exception {
        mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "The Guardian · GB · privat · Ausrichtung: mitte-links (AllSides)")));
    }

    @Test
    void unbekannteStory() throws Exception {
        mvc.perform(get("/stories/999")).andExpect(status().isNotFound());
    }

    @Test
    void einzelmeldungenMarkiertKnappeFaelle() throws Exception {
        mvc.perform(get("/einzelmeldungen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Knapp verpasst")))
                .andExpect(content().string(containsString("class=\"near\"")))
                .andExpect(content().string(containsString("keine im Zeitfenster")));
    }

    @Test
    void einzelmeldungenFilterNachQuelle() throws Exception {
        mvc.perform(get("/einzelmeldungen").param("source", "nzz").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))));
        org.mockito.Mockito.verify(stories).singletons(anyInt(), eq("nzz"), anyInt());
    }

    @Test
    void storyDetailZeigtUebersetzungsKnoepfeOhneEigeneSprache() throws Exception {
        mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                // NZZ-Artikel (de): EN und ES angeboten, DE nicht
                .andExpect(content().string(containsString("hx-get=\"/articles/1/text?lang=en\"")))
                .andExpect(content().string(containsString("hx-get=\"/articles/1/text?lang=es\"")))
                .andExpect(content().string(not(containsString("hx-get=\"/articles/1/text?lang=de\""))))
                // Le-Temps-Artikel (fr): alle drei
                .andExpect(content().string(containsString("hx-get=\"/articles/3/text?lang=de\"")));
    }

    @Test
    void ohneSchluesselKeineKnoepfe() throws Exception {
        when(translations.isEnabled()).thenReturn(false);
        mvc.perform(get("/stories/505"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/text?lang="))));
    }

    @Test
    void uebersetzterTextAlsFragment() throws Exception {
        mvc.perform(get("/articles/3/text").param("lang", "de").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("Das Armeebudget steigt")))
                .andExpect(content().string(containsString("Der Bundesrat hat entschieden.")))
                .andExpect(content().string(containsString("übersetzt aus FR · DeepL ·")))
                .andExpect(content().string(containsString(">Original<")))
                // DE ist jetzt aktiv und wird nicht mehr angeboten, EN und ES schon
                .andExpect(content().string(not(containsString("/articles/3/text?lang=de"))))
                .andExpect(content().string(containsString("/articles/3/text?lang=en")));
    }

    @Test
    void originalAlsFragment() throws Exception {
        mvc.perform(get("/articles/3/text").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Le budget militaire augmente")))
                .andExpect(content().string(not(containsString("übersetzt"))));
    }

    @Test
    void fehlerErscheintImFragment() throws Exception {
        mvc.perform(get("/articles/3/text").param("lang", "es").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kontingent für diesen Monat ist aufgebraucht")))
                .andExpect(content().string(containsString("Le budget militaire augmente")));
    }

    @Test
    void unbekannterArtikel() throws Exception {
        mvc.perform(get("/articles/99/text").param("lang", "de")).andExpect(status().isNotFound());
    }

    @Test
    void statusZeigtKontingent() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12’345")))
                .andExpect(content().string(containsString("500’000")))
                .andExpect(content().string(containsString("2 %")));
    }

    @Test
    void statusSeite() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("560")))
                .andExpect(content().string(containsString("HTTP 403")))
                .andExpect(content().string(containsString("nie")));
    }
}
