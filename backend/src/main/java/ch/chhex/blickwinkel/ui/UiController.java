package ch.chhex.blickwinkel.ui;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import ch.chhex.blickwinkel.source.SourceCatalog;
import ch.chhex.blickwinkel.source.SourceStatusService;
import ch.chhex.blickwinkel.story.StoryQueryService;
import ch.chhex.blickwinkel.story.StorySort;
import ch.chhex.blickwinkel.translation.TranslationException;
import ch.chhex.blickwinkel.translation.TranslationService;
import ch.chhex.blickwinkel.translation.TranslationService.Translation;

/**
 * Serverseitiges UI mit Thymeleaf. Filter laufen über htmx: Kommt eine Anfrage von
 * htmx (Header {@code HX-Request}), wird nur das Listen-Fragment gerendert, sonst
 * die ganze Seite. Die Logik bleibt so vollständig auf dem Server.
 */
@Controller
public class UiController {

    private static final Logger log = LoggerFactory.getLogger(UiController.class);
    private static final String HX_REQUEST = "HX-Request";

    /** Für die Templates: Übersetzung verfügbar, und in welche Sprachen. */
    public record TranslationUi(boolean enabled, List<String> targets) {
    }

    private final StoryQueryService stories;
    private final SourceStatusService status;
    private final SourceCatalog catalog;
    private final TranslationService translations;
    private final double threshold;

    public UiController(StoryQueryService stories, SourceStatusService status, SourceCatalog catalog,
            TranslationService translations,
            @Value("${blickwinkel.clustering.similarity-threshold}") double threshold) {
        this.stories = stories;
        this.status = status;
        this.catalog = catalog;
        this.translations = translations;
        this.threshold = threshold;
    }

    @ModelAttribute("translation")
    public TranslationUi translation() {
        return new TranslationUi(translations.isEnabled(), translations.targetLanguages());
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/stories";
    }

    @GetMapping("/stories")
    public String stories(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis,
            @RequestParam(defaultValue = "2") int minSources,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestHeader(value = HX_REQUEST, required = false) String htmx,
            Model model) {
        var p = StoryListParams.of(hours, von, bis, minSources, q, sort);
        var list = stories.search(p.toFilter(OffsetDateTime.now(ZoneOffset.UTC)));

        model.addAttribute("hours", p.hours());
        model.addAttribute("von", p.von());
        model.addAttribute("bis", p.bis());
        model.addAttribute("minSources", p.minSources());
        model.addAttribute("q", p.q());
        model.addAttribute("sort", p.sort().code());
        model.addAttribute("sorts", StorySort.values());
        model.addAttribute("limit", StoryListParams.LIMIT);
        model.addAttribute("stories", list);
        model.addAttribute("catalog", catalog.snapshot());
        return htmx != null ? "stories :: list" : "stories";
    }

    /**
     * Story-Detail. Die Artikel kommen in der Reihenfolge ihres Erscheinens; der älteste
     * ist als «zuerst» markiert. Die Filter wirken nur auf die Tabelle: Die Auswahl der
     * Quellen, Sprachen und Länder zeigt immer alles, was in der Story vorkommt.
     */
    @GetMapping("/stories/{id}")
    public String story(@PathVariable long id,
            @RequestParam(required = false) List<String> quelle,
            @RequestParam(required = false) List<String> sprache,
            @RequestParam(required = false) List<String> land,
            @RequestHeader(value = HX_REQUEST, required = false) String htmx,
            Model model) {
        var story = stories.story(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story " + id + " gibt es nicht"));
        var all = story.articles();
        var filter = ArticleFilter.of(quelle, sprache, land);

        model.addAttribute("story", story);
        model.addAttribute("filter", filter);
        model.addAttribute("facets", ArticleFilter.facets(all));
        model.addAttribute("articles", filter.apply(all));
        model.addAttribute("total", all.size());
        model.addAttribute("firstId", all.size() > 1 ? all.get(0).id() : null);
        model.addAttribute("catalog", catalog.snapshot());
        return htmx != null ? "story :: articles" : "story";
    }

    /**
     * Titel und Teaser eines Artikels als Fragment: ohne {@code lang} im Original,
     * mit {@code lang} übersetzt (aus dem Cache oder frisch beim Anbieter).
     * Fehler erscheinen im Fragment selbst, die Seite bleibt bedienbar.
     */
    @GetMapping("/articles/{id}/text")
    public String articleText(@PathVariable long id, @RequestParam(required = false) String lang, Model model) {
        var article = translations.article(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikel " + id + " gibt es nicht"));
        Translation tr = null;
        String err = null;
        if (lang != null && !lang.isBlank()) {
            try {
                tr = translations.translate(article, lang);
            } catch (TranslationException e) {
                log.warn("Übersetzung von Artikel {} nach {} fehlgeschlagen: {}", id, lang, e.getMessage());
                err = e.getMessage();
            }
        }
        model.addAttribute("t", article);
        model.addAttribute("tr", tr);
        model.addAttribute("err", err);
        return "article-text :: text";
    }

    @GetMapping("/einzelmeldungen")
    public String singletons(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String source,
            @RequestHeader(value = HX_REQUEST, required = false) String htmx,
            Model model) {
        model.addAttribute("hours", hours);
        model.addAttribute("source", source);
        model.addAttribute("threshold", threshold);
        model.addAttribute("singletons", stories.singletons(hours, source, 200));
        if (htmx != null) {
            return "singletons :: list";
        }
        model.addAttribute("sources", status.activeSources());
        return "singletons";
    }

    @GetMapping("/status")
    public String status(Model model) {
        model.addAttribute("pipeline", status.pipeline());
        model.addAttribute("feeds", status.feeds());
        model.addAttribute("translationUsage",
                translations.isEnabled() ? translations.usage().orElse(null) : null);
        return "status";
    }
}
