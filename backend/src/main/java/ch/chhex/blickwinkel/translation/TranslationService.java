package ch.chhex.blickwinkel.translation;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;

/**
 * Übersetzt Titel und Teaser eines Artikels auf Abruf. Jede Übersetzung wird in
 * {@code article_translation} gespeichert und danach nur noch von dort geliefert.
 */
@Service
public class TranslationService {

    /** Was das UI von einem Artikel braucht, um Titel und Teaser anzuzeigen. */
    public record ArticleText(long id, String url, String title, String teaser, String language) {
    }

    public record Translation(String lang, String title, String teaser, String sourceLang, String provider) {
    }

    private static final String SELECT_ARTICLE = """
            select id, url, title, teaser, trim(language) as language
            from article
            where id = :id
            """;

    private static final String SELECT_TRANSLATION = """
            select trim(lang) as lang, title, teaser, source_lang, provider
            from article_translation
            where article_id = :id
              and lang = :lang
            """;

    /** Zwei gleichzeitige Klicks: der zweite Insert wird still verworfen. */
    private static final String INSERT_TRANSLATION = """
            insert into article_translation (article_id, lang, title, teaser, source_lang, provider)
            values (:id, :lang, :title, :teaser, :sourceLang, :provider)
            on conflict (article_id, lang) do nothing
            """;

    private final JdbcClient jdbc;
    private final TranslationClient client;
    private final List<String> targetLanguages;

    public TranslationService(JdbcClient jdbc, TranslationClient client, BlickwinkelProperties props) {
        this.jdbc = jdbc;
        this.client = client;
        var cfg = props.translation();
        this.targetLanguages = cfg == null || cfg.targetLanguages() == null
                ? List.of()
                : cfg.targetLanguages().stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).toList();
    }

    public boolean isEnabled() {
        return client.isConfigured() && !targetLanguages.isEmpty();
    }

    public List<String> targetLanguages() {
        return targetLanguages;
    }

    public Optional<ArticleText> article(long id) {
        return jdbc.sql(SELECT_ARTICLE)
                .param("id", id)
                .query((rs, n) -> new ArticleText(
                        rs.getLong("id"),
                        rs.getString("url"),
                        rs.getString("title"),
                        rs.getString("teaser"),
                        rs.getString("language")))
                .optional();
    }

    /**
     * Liefert die Übersetzung aus dem Cache oder holt sie beim Anbieter.
     *
     * @throws TranslationException bei ungültiger Sprache, fehlender Konfiguration oder Anbieterfehler
     */
    public Translation translate(ArticleText article, String lang) {
        String target = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        if (!targetLanguages.contains(target)) {
            throw new TranslationException("Zielsprache «" + lang + "» ist nicht vorgesehen.");
        }

        Optional<Translation> cached = jdbc.sql(SELECT_TRANSLATION)
                .param("id", article.id())
                .param("lang", target)
                .query((rs, n) -> new Translation(
                        rs.getString("lang"),
                        rs.getString("title"),
                        rs.getString("teaser"),
                        rs.getString("source_lang"),
                        rs.getString("provider")))
                .optional();
        if (cached.isPresent()) {
            return cached.get();
        }

        List<String> texts = new ArrayList<>(2);
        texts.add(article.title());
        if (article.teaser() != null) {
            texts.add(article.teaser());
        }
        TranslationClient.Result result = client.translate(texts, article.language(), target);
        String title = result.texts().get(0);
        String teaser = article.teaser() == null ? null : result.texts().get(1);
        String source = result.detectedSourceLang() != null ? result.detectedSourceLang() : article.language();

        jdbc.sql(INSERT_TRANSLATION)
                .param("id", article.id())
                .param("lang", target)
                .param("title", title)
                .param("teaser", teaser, Types.VARCHAR)
                .param("sourceLang", source, Types.VARCHAR)
                .param("provider", client.provider())
                .update();

        return new Translation(target, title, teaser, source, client.provider());
    }

    public Optional<TranslationClient.Usage> usage() {
        return client.usage();
    }
}
