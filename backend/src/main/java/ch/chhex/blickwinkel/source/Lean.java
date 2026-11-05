package ch.chhex.blickwinkel.source;

import java.util.Locale;

/**
 * Politische Ausrichtung auf fünf Stufen (wie AllSides), dazu {@link #KEINE} für Quellen,
 * bei denen links/rechts nicht die richtige Frage ist. Werte wie das Postgres-Enum {@code source_lean}.
 */
public enum Lean {

    LINKS(-2, "links", "L"),
    MITTE_LINKS(-1, "mitte-links", "M-L"),
    MITTE(0, "mitte", "M"),
    MITTE_RECHTS(1, "mitte-rechts", "M-R"),
    RECHTS(2, "rechts", "R"),
    KEINE(null, "keine Richtung", "–");

    private final Integer position;
    private final String label;
    private final String shortLabel;

    Lean(Integer position, String label, String shortLabel) {
        this.position = position;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    /** Kürzel für enge Stellen, z.B. «M-L»; «–» bei keiner Richtung. */
    public String shortLabel() {
        return shortLabel;
    }

    /** -2 (links) bis 2 (rechts); null bei {@link #KEINE}. */
    public Integer position() {
        return position;
    }

    public String label() {
        return label;
    }

    /** Aus sources.yml, z.B. «mitte-links»; leer heisst nicht eingeordnet (null). */
    public static Lean parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }
}
