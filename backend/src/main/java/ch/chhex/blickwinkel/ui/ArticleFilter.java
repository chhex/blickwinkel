package ch.chhex.blickwinkel.ui;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import ch.chhex.blickwinkel.story.StoryQueryService.StoryArticle;

/**
 * Filter für die Artikel im Story-Detail: Quelle, Sprache, Land.
 *
 * <p>Innerhalb eines Merkmals gilt ODER (NZZ oder SRF), zwischen den Merkmalen UND
 * (Sprache de und Land CH). Ein leeres Merkmal schränkt nicht ein. Gefiltert wird in
 * Java: Eine Story hat selten mehr als ein paar Dutzend Artikel, und für die Auswahl
 * (welche Quellen kommen überhaupt vor) braucht es ohnehin die ungefilterte Liste.</p>
 */
public record ArticleFilter(Set<String> quellen, Set<String> sprachen, Set<String> laender) {

    public static final ArticleFilter NONE = new ArticleFilter(Set.of(), Set.of(), Set.of());

    /** Eine wählbare Ausprägung mit Anzahl Artikel, z.B. NZZ (2). */
    public record Option(String code, String label, long count) {
    }

    /** Was in einer Story vorkommt. Ein Merkmal mit nur einer Ausprägung bietet das UI nicht an. */
    public record Facets(List<Option> quellen, List<Option> sprachen, List<Option> laender) {
    }

    public ArticleFilter {
        quellen = Set.copyOf(quellen);
        sprachen = Set.copyOf(sprachen);
        laender = Set.copyOf(laender);
    }

    /** Aus URL-Parametern; leere Werte fallen weg, Schreibweise wie in der Datenbank. */
    public static ArticleFilter of(Collection<String> quellen, Collection<String> sprachen,
            Collection<String> laender) {
        return new ArticleFilter(
                clean(quellen, false),
                clean(sprachen, false),
                clean(laender, true));
    }

    public boolean isEmpty() {
        return quellen.isEmpty() && sprachen.isEmpty() && laender.isEmpty();
    }

    public boolean matches(StoryArticle a) {
        return accepts(quellen, a.sourceCode())
                && accepts(sprachen, a.language())
                && accepts(laender, a.country());
    }

    /** Behält die Reihenfolge der Eingabe. */
    public List<StoryArticle> apply(List<StoryArticle> articles) {
        return isEmpty() ? articles : articles.stream().filter(this::matches).toList();
    }

    public static Facets facets(List<StoryArticle> articles) {
        return new Facets(
                options(articles, StoryArticle::sourceCode, StoryArticle::source),
                options(articles, StoryArticle::language, a -> a.language().toUpperCase(Locale.ROOT)),
                options(articles, StoryArticle::country, StoryArticle::country));
    }

    private static List<Option> options(List<StoryArticle> articles, Function<StoryArticle, String> code,
            Function<StoryArticle, String> label) {
        Map<String, Option> byCode = new LinkedHashMap<>();
        for (StoryArticle a : articles) {
            String c = code.apply(a);
            if (c != null) {
                byCode.merge(c, new Option(c, label.apply(a), 1),
                        (x, y) -> new Option(x.code(), x.label(), x.count() + 1));
            }
        }
        return byCode.values().stream()
                .sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean accepts(Set<String> wanted, String value) {
        return wanted.isEmpty() || (value != null && wanted.contains(value));
    }

    private static Set<String> clean(Collection<String> values, boolean upper) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::strip)
                .map(v -> upper ? v.toUpperCase(Locale.ROOT) : v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
