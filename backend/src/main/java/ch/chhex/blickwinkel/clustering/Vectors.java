package ch.chhex.blickwinkel.clustering;

/** Reine Vektor-Mathematik für das Clustering, ohne Abhängigkeiten. */
public final class Vectors {

    private Vectors() {
    }

    /**
     * Neuer Centroid nach Aufnahme eines Vektors: laufender Mittelwert
     * {@code c + (v - c) / (n + 1)}, wobei {@code n} die bisherige Anzahl ist.
     */
    public static float[] runningMean(float[] centroid, int count, float[] vector) {
        requireSameLength(centroid, vector);
        if (count < 1) {
            throw new IllegalArgumentException("count muss >= 1 sein, war " + count);
        }
        float[] result = new float[centroid.length];
        float n1 = count + 1;
        for (int i = 0; i < centroid.length; i++) {
            result[i] = centroid[i] + (vector[i] - centroid[i]) / n1;
        }
        return result;
    }

    /** Cosine-Ähnlichkeit, nur für Tests und Plausibilitätschecks (die DB rechnet selbst). */
    public static double cosineSimilarity(float[] a, float[] b) {
        requireSameLength(a, b);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static void requireSameLength(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Dimensionen passen nicht: " + a.length + " vs. " + b.length);
        }
    }
}
