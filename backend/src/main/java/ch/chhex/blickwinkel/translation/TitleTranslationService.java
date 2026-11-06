package ch.chhex.blickwinkel.translation;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Übersetzt die Titel der Story-Übersicht in eine Sprache, gebündelt und mit Cache in
 * {@code title_translation}. Titel, die schon in der Zielsprache sind, bleiben unberührt.
 *
 * <p>Ein Fehler beim Anbieter bricht die Seite nicht ab: Die bereits gespeicherten
 * Übersetzungen werden gezeigt, der Rest bleibt im Original, und es gibt eine Meldung.</p>
 */
@Service
public class TitleTranslationService {

    private static final Logger log = LoggerFactory.getLogger(TitleTranslationService.class);

    /** DeepL nimmt bis 50 Texte pro Anfrage. */
    static final int BATCH = 50;

    /** Ein Titel, wie die Übersicht ihn kennt: Artikel, Text und Sprache des Originals. */
    public record TitleRef(long articleId, String title, String language) {
    }

    /** Ergebnis für eine Seite; {@link #of} fällt auf das Original zurück. */
    public record TitleTranslations(String lang, Map<Long, String> titles, String error) {

        public static final TitleTranslations NONE = new TitleTranslations(null, Map.of(), null);

        public String of(long articleId, String original) {
            String t = titles.get(articleId);
            return t != null ? t : original;
        }

        public boolean translated(long articleId) {
            return titles.containsKey(articleId);
        }
    }

    private static final String SELECT_CACHED = """
            select article_id, title
            from title_translation
            where lang = :lang
              and article_id in (:ids)
            """;

    private static final String INSERT = """
            insert into title_translation (article_id, lang, title, source_lang, provider)
            values (:id, :lang, :title, :sourceLang, :provider)
            on conflict (article_id, lang) do nothing
            """;

    private final JdbcClient jdbc;
    private final TranslationClient client;
    private final TranslationService translations;

    public TitleTranslationService(JdbcClient jdbc, TranslationClient client, TranslationService translations) {
        this.jdbc = jdbc;
        this.client = client;
        this.translations = translations;
    }

    /** Zielsprache normalisiert, oder null wenn nicht vorgesehen / Übersetzung aus. */
    public String target(String lang) {
        if (lang == null || !translations.isEnabled()) {
            return null;
        }
        String l = lang.trim().toLowerCase(Locale.ROOT);
        return translations.targetLanguages().contains(l) ? l : null;
    }

    public TitleTranslations translate(List<TitleRef> refs, String lang) {
        String target = target(lang);
        if (target == null) {
            return TitleTranslations.NONE;
        }
        List<TitleRef> foreign = refs.stream()
                .filter(r -> !target.equals(r.language()))
                .toList();
        if (foreign.isEmpty()) {
            return new TitleTranslations(target, Map.of(), null);
        }

        Map<Long, String> result = new HashMap<>();
        jdbc.sql(SELECT_CACHED)
                .param("lang", target)
                .param("ids", foreign.stream().map(TitleRef::articleId).distinct().toList())
                .query((rs, n) -> Map.entry(rs.getLong("article_id"), rs.getString("title")))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));

        List<TitleRef> missing = foreign.stream().filter(r -> !result.containsKey(r.articleId())).toList();
        String error = null;
        try {
            for (var group : batches(missing).entrySet()) {
                for (List<TitleRef> batch : group.getValue()) {
                    var texts = batch.stream().map(TitleRef::title).toList();
                    var translated = client.translate(texts, group.getKey(), target).texts();
                    for (int i = 0; i < batch.size(); i++) {
                        TitleRef r = batch.get(i);
                        result.put(r.articleId(), translated.get(i));
                        jdbc.sql(INSERT)
                                .param("id", r.articleId())
                                .param("lang", target)
                                .param("title", translated.get(i))
                                .param("sourceLang", group.getKey(), Types.VARCHAR)
                                .param("provider", client.provider())
                                .update();
                    }
                }
            }
        } catch (TranslationException e) {
            log.warn("Titel nach {} nicht vollständig übersetzt: {}", target, e.getMessage());
            error = e.getMessage();
        }
        return new TitleTranslations(target, Map.copyOf(result), error);
    }

    /**
     * Nach Ausgangssprache gruppiert (kurze Titel erkennt der Anbieter sonst manchmal falsch)
     * und in Pakete zu höchstens {@link #BATCH} geteilt; doppelte Artikel nur einmal.
     */
    static Map<String, List<List<TitleRef>>> batches(List<TitleRef> missing) {
        Map<Long, TitleRef> unique = new LinkedHashMap<>();
        missing.forEach(r -> unique.putIfAbsent(r.articleId(), r));

        Map<String, List<TitleRef>> byLang = new LinkedHashMap<>();
        unique.values().forEach(r -> byLang.computeIfAbsent(r.language(), k -> new ArrayList<>()).add(r));

        Map<String, List<List<TitleRef>>> result = new LinkedHashMap<>();
        byLang.forEach((language, list) -> {
            List<List<TitleRef>> chunks = new ArrayList<>();
            for (int i = 0; i < list.size(); i += BATCH) {
                chunks.add(List.copyOf(list.subList(i, Math.min(i + BATCH, list.size()))));
            }
            result.put(language, chunks);
        });
        return result;
    }
}
