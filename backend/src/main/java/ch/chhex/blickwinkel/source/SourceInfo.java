package ch.chhex.blickwinkel.source;

import java.util.ArrayList;
import java.util.List;

/** Eine Quelle mit ihrer Einordnung, so wie das UI sie als Badge zeigt. */
public record SourceInfo(String code, String name, String country, String language, String section,
        Ownership ownership, Lean lean, LeanSource leanSource, String note) {

    public SourceInfo {
        ownership = ownership == null ? Ownership.PRIVAT : ownership;
    }

    /** Für Quellen, die (noch) nicht im Katalog stehen: nur der Code. */
    public static SourceInfo unknown(String code) {
        return new SourceInfo(code, code, null, null, null, Ownership.PRIVAT, null, null, null);
    }

    public boolean opinion() {
        return "OPINION".equals(section);
    }

    /** Position auf der Skala -2..2, null wenn ohne Richtung oder nicht eingeordnet. */
    public Integer leanPosition() {
        return lean == null ? null : lean.position();
    }

    /** Text für den Tooltip, z.B. «CH · privat · Ausrichtung: mitte-links (AllSides)». */
    public String tooltip() {
        List<String> parts = new ArrayList<>();
        parts.add(name);
        if (country != null) {
            parts.add(country);
        }
        parts.add(ownership.label());
        if (lean == null) {
            parts.add("Ausrichtung: nicht eingeordnet");
        } else {
            parts.add("Ausrichtung: " + lean.label() + (leanSource != null ? " (" + leanSource.label() + ")" : ""));
        }
        if (note != null && !note.isBlank()) {
            parts.add(note.strip());
        }
        return String.join(" · ", parts);
    }
}
