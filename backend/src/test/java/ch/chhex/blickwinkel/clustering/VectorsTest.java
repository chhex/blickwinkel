package ch.chhex.blickwinkel.clustering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VectorsTest {

    @Test
    void laufenderMittelwertEntsprichtArithmetischemMittel() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        float[] c = {1f, 1f};
        float[] centroid = Vectors.runningMean(a, 1, b);
        centroid = Vectors.runningMean(centroid, 2, c);
        assertArrayEquals(new float[] {2f / 3, 2f / 3}, centroid, 1e-6f);
    }

    @Test
    void cosineAehnlichkeit() {
        assertEquals(1.0, Vectors.cosineSimilarity(new float[] {1, 2}, new float[] {2, 4}), 1e-9);
        assertEquals(0.0, Vectors.cosineSimilarity(new float[] {1, 0}, new float[] {0, 1}), 1e-9);
    }

    @Test
    void dimensionenMuessenPassen() {
        assertThrows(IllegalArgumentException.class,
                () -> Vectors.runningMean(new float[] {1}, 1, new float[] {1, 2}));
    }
}
