package ch.chhex.blickwinkel.source;

import java.util.Locale;

/**
 * Politische Ausrichtung auf fünf Stufen (wie AllSides), dazu {@link #KEINE} für Quellen,
 * bei denen links/rechts nicht die richtige Frage ist. Werte wie das Postgres-Enum {@code source_lean}.
 */
public enum Lean {

    LINKS(-2, "links"),
    MITTE_LINKS(-1, "mitte-links"),
    MITTE(0, "mitte"),
    MITTE_RECHTS(1, "mitte-rechts"),
    RECHTS(2, "rechts"),
    KEINE(null, "keine Richtung");

    private final Integer position;
    private final String label;

    Lean(Integer position, String label) {
        this.position = position;
        this.label = label;
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
