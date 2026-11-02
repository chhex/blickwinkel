package ch.chhex.blickwinkel.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.chhex.blickwinkel.source.SourceStatusService;
import ch.chhex.blickwinkel.source.SourceStatusService.FeedStatus;
import ch.chhex.blickwinkel.source.SourceStatusService.SourceOption;
import ch.chhex.blickwinkel.story.StoryQueryService;
import ch.chhex.blickwinkel.story.StoryQueryService.Singleton;
import ch.chhex.blickwinkel.story.StoryQueryService.StoryArticle;
import ch.chhex.blickwinkel.story.StoryQueryService.StoryDetail;
import ch.chhex.blickwinkel.story.StoryQueryService.StorySummary;

/**
 * Rendert alle Seiten mit Beispieldaten. Fängt Fehler in Templates und
 * Ausdrücken ab, die sonst erst im Browser auffallen würden.
 */
@WebMvcTest(UiController.class)
@Import(Formats.class)
class UiControllerTest {

    private static final OffsetDateTime T = OffsetDateTime.of(2026, 11, 1, 15, 33, 0, 0, ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StoryQueryService stories;

    @MockitoBean
    SourceStatusService status;

    @BeforeEach
    void beispieldaten() {
        when(stories.recentStories(anyInt(), anyInt(), anyInt())).thenReturn(List.of(
                new StorySummary(505, "Bundesrat erhöht Armeebudget", T, T.plusHours(7), 4,
                        List.of("NZZ", "SRF News", "watson"))));

        when(stories.story(anyLong())).thenReturn(Optional.empty());
        when(stories.story(505)).thenReturn(Optional.of(new StoryDetail(505, "Bundesrat erhöht Armeebudget",
                T, T.plusHours(7), List.of(
                        new StoryArticle("NZZ Meinung", "OPINION", "CH", "de", "Die Armee braucht mehr",
                                "Ein Kommentar", "https://nzz.ch/a", T, 0.81f),
                        new StoryArticle("SRF News", "NEWS", "CH", "de", "Mehr Geld für die Armee",
                                null, "https://srf.ch/b", null, null)))));

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
    void statusSeite() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("560")))
                .andExpect(content().string(containsString("HTTP 403")))
                .andExpect(content().string(containsString("nie")));
    }
}
