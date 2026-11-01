package ch.chhex.blickwinkel.db;

/** Textformat von pgvector: {@code [0.1,-0.2,0.3]}. Ohne Abhängigkeiten, gut testbar. */
public final class VectorText {

    private VectorText() {
    }

    public static String format(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vektor ist leer");
        }
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            float f = vector[i];
            if (!Float.isFinite(f)) {
                throw new IllegalArgumentException("Ungültiger Wert an Position " + i + ": " + f);
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(f);
        }
        return sb.append(']').toString();
    }

    public static float[] parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Vektortext ist null");
        }
        String s = text.trim();
        if (s.length() < 2 || s.charAt(0) != '[' || s.charAt(s.length() - 1) != ']') {
            throw new IllegalArgumentException("Kein pgvector-Text: " + abbreviate(s));
        }
        String body = s.substring(1, s.length() - 1).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Vektor ist leer");
        }
        String[] parts = body.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i].trim());
        }
        return v;
    }

    private static String abbreviate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}
