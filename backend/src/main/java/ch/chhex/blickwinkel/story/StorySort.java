package ch.chhex.blickwinkel.story;

import java.util.Arrays;
import java.util.Locale;

/**
 * Sortierungen der Story-Übersicht. Die ORDER-BY-Ausdrücke sind fest hinterlegt;
 * aus der URL kommt nur der Code, nie SQL.
 */
public enum StorySort {

    NEU("neu", "Neueste zuerst", "last_seen_at desc, id desc"),
    QUELLEN("quellen", "Meiste Quellen", "source_count desc, last_seen_at desc, id desc"),
    ARTIKEL("artikel", "Meiste Artikel", "article_count desc, last_seen_at desc, id desc"),
    ALT("alt", "Älteste zuerst", "first_seen_at asc, id asc");

    private final String code;
    private final String label;
    private final String orderBy;

    StorySort(String code, String label, String orderBy) {
        this.code = code;
        this.label = label;
        this.orderBy = orderBy;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    String orderBy() {
        return orderBy;
    }

    /** Unbekannte oder fehlende Werte ergeben die Standardsortierung. */
    public static StorySort fromCode(String code) {
        if (code == null) {
            return NEU;
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.code.equals(c)).findFirst().orElse(NEU);
    }
}
