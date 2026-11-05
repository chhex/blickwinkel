package ch.chhex.blickwinkel.source;

import java.net.URI;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;
import ch.chhex.blickwinkel.config.BlickwinkelProperties.SourceDefinition;

/**
 * Synchronisiert sources.yml beim Start in die Tabellen {@code source} und {@code source_feed}.
 * Die YAML-Datei ist die Wahrheit. Entfernte Quellen und Feeds werden deaktiviert, nicht
 * gelöscht, damit ihre Artikel erhalten bleiben.
 */
@Component
public class SourceSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SourceSync.class);

    private static final String UPSERT_SOURCE = """
            insert into source (code, name, country, language, section, camp,
                                ownership, lean, lean_source, note, active)
            values (:code, :name, :country, :language, cast(:section as source_section), :camp,
                    cast(:ownership as source_ownership), cast(:lean as source_lean), :leanSource, :note, true)
            on conflict (code) do update set
                name        = excluded.name,
                country     = excluded.country,
                language    = excluded.language,
                section     = excluded.section,
                camp        = excluded.camp,
                ownership   = excluded.ownership,
                lean        = excluded.lean,
                lean_source = excluded.lean_source,
                note        = excluded.note,
                active      = true
            returning id
            """;

    /** Ein Feed, der in der YAML zu einer anderen Quelle gewandert ist, zieht mit um. */
    private static final String UPSERT_FEED = """
            insert into source_feed (source_id, url, active)
            values (:sourceId, :url, true)
            on conflict (url) do update set
                source_id = excluded.source_id,
                active    = true
            """;

    private static final String DEACTIVATE_OTHER_FEEDS = """
            update source_feed
            set active = false
            where source_id = :sourceId
              and active
              and url not in (:urls)
            """;

    private final JdbcClient jdbc;
    private final List<SourceDefinition> definitions;

    public SourceSync(JdbcClient jdbc, BlickwinkelProperties props) {
        this.jdbc = jdbc;
        this.definitions = props.sources() == null ? List.of() : props.sources();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validate(definitions);

        int feeds = 0;
        int deactivatedFeeds = 0;
        for (SourceDefinition d : definitions) {
            Lean lean = Lean.parse(d.lean());
            LeanSource leanSource = LeanSource.parse(d.leanSource(), lean);
            long sourceId = jdbc.sql(UPSERT_SOURCE)
                    .param("code", d.code())
                    .param("name", d.name())
                    .param("country", d.country().toUpperCase(Locale.ROOT))
                    .param("language", d.language().toLowerCase(Locale.ROOT))
                    .param("section", d.section().toUpperCase(Locale.ROOT))
                    .param("camp", d.camp(), Types.VARCHAR)
                    .param("ownership", Ownership.parse(d.ownership()).name())
                    .param("lean", lean == null ? null : lean.name(), Types.VARCHAR)
                    .param("leanSource", leanSource == null ? null : leanSource.name(), Types.VARCHAR)
                    .param("note", blankToNull(d.note()), Types.VARCHAR)
                    .query(Long.class)
                    .single();

            List<String> urls = d.feedUrls().stream().map(URI::toString).toList();
            for (String url : urls) {
                jdbc.sql(UPSERT_FEED)
                        .param("sourceId", sourceId)
                        .param("url", url)
                        .update();
                feeds++;
            }
            deactivatedFeeds += jdbc.sql(DEACTIVATE_OTHER_FEEDS)
                    .param("sourceId", sourceId)
                    .param("urls", urls)
                    .update();
        }

        List<String> codes = definitions.stream().map(SourceDefinition::code).toList();
        int deactivatedSources = codes.isEmpty()
                ? jdbc.sql("update source set active = false where active").update()
                : jdbc.sql("update source set active = false where active and code not in (:codes)")
                        .param("codes", codes)
                        .update();

        log.info("Quellen synchronisiert: {} Quellen mit {} Feeds aktiv, {} Quellen und {} Feeds deaktiviert",
                definitions.size(), feeds, deactivatedSources, deactivatedFeeds);
    }

    /**
     * Prüft sources.yml vor dem Schreiben. Fehler brechen den Start ab: lieber laut
     * scheitern als eine Quelle still falsch synchronisieren.
     */
    static void validate(List<SourceDefinition> definitions) {
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        Set<String> urls = new HashSet<>();

        for (SourceDefinition d : definitions) {
            String code = d.code() == null ? "(ohne code)" : d.code();
            if (d.code() == null || d.code().isBlank()) {
                errors.add("Quelle ohne code");
            } else if (!codes.add(d.code())) {
                errors.add("code doppelt: " + d.code());
            }
            checkValue(errors, code, "ownership", d.ownership(), () -> Ownership.parse(d.ownership()));
            checkValue(errors, code, "lean", d.lean(), () -> Lean.parse(d.lean()));
            checkValue(errors, code, "lean-source", d.leanSource(),
                    () -> LeanSource.parse(d.leanSource(), Lean.MITTE));
            if (isBlank(d.lean()) && !isBlank(d.leanSource())) {
                errors.add(code + ": lean-source ohne lean");
            }
            if (d.feedUrls() == null || d.feedUrls().isEmpty()) {
                errors.add(code + ": keine feed-urls");
                continue;
            }
            for (URI url : d.feedUrls()) {
                if (!urls.add(url.toString())) {
                    errors.add(code + ": feed-url mehrfach eingetragen: " + url);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("sources.yml ungültig: " + String.join("; ", errors));
        }
    }

    private static void checkValue(List<String> errors, String code, String field, String value, Runnable parse) {
        try {
            parse.run();
        } catch (IllegalArgumentException e) {
            errors.add(code + ": " + field + " unbekannt: " + value);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.strip();
    }
}
