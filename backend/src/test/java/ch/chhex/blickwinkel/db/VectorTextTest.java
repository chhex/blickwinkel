package ch.chhex.blickwinkel.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VectorTextTest {

    @Test
    void roundTrip() {
        float[] v = {0.25f, -1.5f, 3.0E-5f};
        assertArrayEquals(v, VectorText.parse(VectorText.format(v)));
    }

    @Test
    void formatEntsprichtPgvector() {
        assertEquals("[1.0,-2.5]", VectorText.format(new float[] {1f, -2.5f}));
    }

    @Test
    void parstPgvectorAusgabeMitLeerzeichen() {
        assertArrayEquals(new float[] {1f, 2f}, VectorText.parse(" [1, 2] "));
    }

    @Test
    void lehntNaNAb() {
        assertThrows(IllegalArgumentException.class, () -> VectorText.format(new float[] {Float.NaN}));
    }

    @Test
    void lehntUngueltigenTextAb() {
        assertThrows(IllegalArgumentException.class, () -> VectorText.parse("1,2"));
    }
}
