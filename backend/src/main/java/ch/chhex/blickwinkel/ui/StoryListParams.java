package ch.chhex.blickwinkel.ui;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import ch.chhex.blickwinkel.story.StoryFilter;
import ch.chhex.blickwinkel.story.StorySort;

/**
 * Übersetzt die URL-Parameter der Story-Übersicht in einen {@link StoryFilter}.
 *
 * <p>Regeln: Ist «von» oder «bis» gesetzt, gilt der Datumsbereich (Schweizer Zeit, «bis»
 * einschliesslich) und der feste Zeitraum wird ignoriert ({@code hours = 0}). Sonst gilt
 * «die letzten N Stunden». Vertauschte Daten werden getauscht.</p>
 */
record StoryListParams(int hours, LocalDate von, LocalDate bis, int minSources, String q, StorySort sort) {

    static final int DEFAULT_HOURS = 24;
    static final int LIMIT = 200;

    /** Weit genug zurück für «von» ohne Angabe; älter sind die Daten nicht. */
    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static StoryListParams of(int hours, LocalDate von, LocalDate bis, int minSources, String q, String sort) {
        if (von != null && bis != null && von.isAfter(bis)) {
            LocalDate tmp = von;
            von = bis;
            bis = tmp;
        }
        boolean range = von != null || bis != null;
        int h = range ? 0 : (hours > 0 ? hours : DEFAULT_HOURS);
        String query = q == null || q.isBlank() ? null : q.trim();
        return new StoryListParams(h, von, bis, Math.max(minSources, 1), query, StorySort.fromCode(sort));
    }

    boolean isRange() {
        return von != null || bis != null;
    }

    StoryFilter toFilter(OffsetDateTime now) {
        OffsetDateTime from;
        OffsetDateTime to;
        if (isRange()) {
            from = von == null ? EARLIEST : von.atStartOfDay(Formats.ZONE).toOffsetDateTime();
            to = bis == null ? now : bis.plusDays(1).atStartOfDay(Formats.ZONE).toOffsetDateTime();
        } else {
            from = now.minusHours(hours);
            to = now;
        }
        return new StoryFilter(from, to, minSources, q, sort, LIMIT);
    }
}
