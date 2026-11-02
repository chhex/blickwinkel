package ch.chhex.blickwinkel.ui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import ch.chhex.blickwinkel.source.SourceStatusService;
import ch.chhex.blickwinkel.story.StoryQueryService;

/**
 * Serverseitiges UI mit Thymeleaf. Filter laufen über htmx: Kommt eine Anfrage von
 * htmx (Header {@code HX-Request}), wird nur das Listen-Fragment gerendert, sonst
 * die ganze Seite. Die Logik bleibt so vollständig auf dem Server.
 */
@Controller
public class UiController {

    private static final String HX_REQUEST = "HX-Request";

    private final StoryQueryService stories;
    private final SourceStatusService status;
    private final double threshold;

    public UiController(StoryQueryService stories, SourceStatusService status,
            @Value("${blickwinkel.clustering.similarity-threshold}") double threshold) {
        this.stories = stories;
        this.status = status;
        this.threshold = threshold;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/stories";
    }

    @GetMapping("/stories")
    public String stories(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "2") int minSources,
            @RequestHeader(value = HX_REQUEST, required = false) String htmx,
            Model model) {
        model.addAttribute("hours", hours);
        model.addAttribute("minSources", minSources);
        model.addAttribute("stories", stories.recentStories(hours, minSources, 200));
        return htmx != null ? "stories :: list" : "stories";
    }

    @GetMapping("/stories/{id}")
    public String story(@PathVariable long id, Model model) {
        model.addAttribute("story", stories.story(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story " + id + " gibt es nicht")));
        return "story";
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
        return "status";
    }
}
