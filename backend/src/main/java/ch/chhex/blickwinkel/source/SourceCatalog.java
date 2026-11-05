package ch.chhex.blickwinkel.source;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Alle Quellen mit Einordnung, nach Code. Wird pro Seitenaufruf frisch gelesen: ein paar
 * Dutzend Zeilen, und so gibt es keinen Cache, der nach dem Start veralten könnte.
 */
@Service
public class SourceCatalog {

    private static final String SELECT_SOURCES = """
            select code, name, country, trim(language) as language, section::text as section,
                   ownership::text as ownership, lean::text as lean, lean_source, note
            from source
            order by name
            """;

    /** Momentaufnahme für eine Seite; {@link #of} liefert für unbekannte Codes einen Platzhalter. */
    public record Snapshot(Map<String, SourceInfo> byCode) {

        public SourceInfo of(String code) {
            SourceInfo s = code == null ? null : byCode.get(code);
            return s != null ? s : SourceInfo.unknown(code);
        }
    }

    private final JdbcClient jdbc;

    public SourceCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot snapshot() {
        Map<String, SourceInfo> result = new LinkedHashMap<>();
        jdbc.sql(SELECT_SOURCES)
                .query((rs, n) -> {
                    Lean lean = Lean.parse(rs.getString("lean"));
                    String leanSource = rs.getString("lean_source");
                    return new SourceInfo(
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("country"),
                            rs.getString("language"),
                            rs.getString("section"),
                            Ownership.parse(rs.getString("ownership")),
                            lean,
                            leanSource == null ? null : LeanSource.valueOf(leanSource),
                            rs.getString("note"));
                })
                .list()
                .forEach(s -> result.put(s.code(), s));
        return new Snapshot(Map.copyOf(result));
    }
}
