package ch.chhex.blickwinkel.story;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ch.chhex.blickwinkel.story.StoryQueryService.StoryDetail;
import ch.chhex.blickwinkel.story.StoryQueryService.StoryView;

/**
 * REST-API, z.B. für curl/jq und ein späteres Frontend:
 * {@code GET /api/stories?hours=24&minSources=2} und {@code GET /api/stories/{id}}
 */
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryQueryService stories;

    public StoryController(StoryQueryService stories) {
        this.stories = stories;
    }

    @GetMapping
    public List<StoryView> recent(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "2") int minSources,
            @RequestParam(defaultValue = "100") int limit) {
        return stories.recentStoriesWithArticles(hours, minSources, limit);
    }

    @GetMapping("/{id}")
    public StoryDetail one(@PathVariable long id) {
        return stories.story(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
