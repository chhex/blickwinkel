package ch.chhex.blickwinkel.source;

import java.sql.Types;
import java.util.List;
import java.util.Locale;

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
 * Synchronisiert sources.yml beim Start in die Tabelle {@code source}.
 * Die YAML-Datei ist die Wahrheit; entfernte Codes werden deaktiviert, nicht gelöscht,
 * damit ihre Artikel erhalten bleiben.
 */
@Component
public class SourceSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SourceSync.class);

    private static final String UPSERT = """
            insert into source (code, name, country, language, section, feed_url, camp, active)
            values (:code, :name, :country, :language, cast(:section as source_section), :feedUrl, :camp, true)
            on conflict (code) do update set
                name     = excluded.name,
                country  = excluded.country,
                language = excluded.language,
                section  = excluded.section,
                feed_url = excluded.feed_url,
                camp     = excluded.camp,
                active   = true
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
        for (SourceDefinition d : definitions) {
            jdbc.sql(UPSERT)
                    .param("code", d.code())
                    .param("name", d.name())
                    .param("country", d.country().toUpperCase(Locale.ROOT))
                    .param("language", d.language().toLowerCase(Locale.ROOT))
                    .param("section", d.section().toUpperCase(Locale.ROOT))
                    .param("feedUrl", d.feedUrl().toString())
                    .param("camp", d.camp(), Types.VARCHAR)
                    .update();
        }

        List<String> codes = definitions.stream().map(SourceDefinition::code).toList();
        int deactivated = codes.isEmpty()
                ? jdbc.sql("update source set active = false where active").update()
                : jdbc.sql("update source set active = false where active and code not in (:codes)")
                        .param("codes", codes)
                        .update();

        log.info("Quellen synchronisiert: {} aktiv, {} deaktiviert", definitions.size(), deactivated);
    }
}
