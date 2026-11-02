package ch.chhex.blickwinkel.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Prüft, dass alle Dateien, die das Layout einbindet (htmx, Pico, eigenes CSS),
 * auch wirklich unter static/ liegen. Schützt vor Tippfehlern beim Versionswechsel.
 */
class StaticResourcesTest {

    @Test
    void layoutVerweistNurAufVorhandeneDateien() throws IOException {
        String layout = new ClassPathResource("templates/fragments/layout.html")
                .getContentAsString(StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("@\\{/([^}]+\\.(?:js|css))\\}").matcher(layout);
        List<String> paths = new ArrayList<>();
        while (m.find()) {
            paths.add(m.group(1));
        }

        assertFalse(paths.isEmpty(), "keine Verweise im Layout gefunden");
        for (String path : paths) {
            assertTrue(new ClassPathResource("static/" + path).exists(), "fehlt unter static/: " + path);
        }
    }
}
