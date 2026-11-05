package ch.chhex.blickwinkel.source;

import java.util.Locale;

/** Woher eine Einordnung stammt. */
public enum LeanSource {

    ALLSIDES("AllSides"),
    EIGENE("eigene Einschätzung");

    private final String label;

    LeanSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Leer: «eigene», sobald eine Ausrichtung gesetzt ist, sonst null. */
    public static LeanSource parse(String value, Lean lean) {
        if (value == null || value.isBlank()) {
            return lean == null ? null : EIGENE;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
