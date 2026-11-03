package ch.chhex.blickwinkel.story;

import java.time.OffsetDateTime;

/**
 * Filter der Story-Übersicht.
 *
 * @param from       Beginn des Zeitraums; eine Story zählt, wenn sich ihr Zeitraum mit [from, to] überschneidet
 * @param to         Ende des Zeitraums
 * @param minSources Mindestanzahl verschiedener Quellen
 * @param query      Suchbegriffe (durch Leerzeichen getrennt, alle müssen vorkommen) oder null
 * @param sort       Sortierung
 * @param limit      Höchstzahl Stories
 */
public record StoryFilter(OffsetDateTime from, OffsetDateTime to, int minSources, String query,
        StorySort sort, int limit) {

    public StoryFilter {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Zeitraum unvollständig");
        }
        query = normalizeQuery(query);
        sort = sort == null ? StorySort.NEU : sort;
    }

    /** Leer wird null; mehrfache Leerzeichen werden eines. */
    static String normalizeQuery(String q) {
        if (q == null) {
            return null;
        }
        String t = q.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /**
     * Für LIKE: Die Platzhalter {@code %} und {@code _} und der Escape-Backslash aus
     * der Benutzereingabe werden wörtlich genommen.
     */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    String likeQuery() {
        return query == null ? null : escapeLike(query);
    }
}
