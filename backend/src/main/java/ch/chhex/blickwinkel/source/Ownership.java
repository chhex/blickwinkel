package ch.chhex.blickwinkel.source;

import java.util.Locale;

/** Wer eine Quelle finanziert bzw. kontrolliert. Werte wie das Postgres-Enum {@code source_ownership}. */
public enum Ownership {

    PRIVAT("privat", ""),
    /** Gebühren oder Staat, redaktionell unabhängig per Gesetz oder Statut (SRF, BBC). */
    OEFFENTLICH("öffentlich finanziert", "◐"),
    /** Die Regierung bestimmt die Linie (teleSUR, Sputnik). */
    STAATLICH("Staatsmedium", "🏛"),
    /** Nimmt Geld von Parteien. */
    PARTEIEN("Parteigeld", "🗳");

    private final String label;
    private final String icon;

    Ownership(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    /** Leer bei privat: Das ist der Normalfall und braucht kein Zeichen. */
    public String icon() {
        return icon;
    }

    /** Aus sources.yml, z.B. «oeffentlich»; leer heisst privat. Unbekannt: IllegalArgumentException. */
    public static Ownership parse(String value) {
        if (value == null || value.isBlank()) {
            return PRIVAT;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
