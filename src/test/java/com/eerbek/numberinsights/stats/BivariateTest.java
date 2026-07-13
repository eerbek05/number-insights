package com.eerbek.numberinsights.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.model.Dataset;

import java.util.List;

import org.junit.jupiter.api.Test;

class BivariateTest {

    private static Dataset ds(double... values) {
        return Dataset.of(java.util.Arrays.stream(values).boxed().toList());
    }

    @Test
    void perfectLineHasCorrelationOneAndExactFit() {
        // y = 2x + 1, exactly
        Bivariate.BivariateResult r = Bivariate.analyze(
                ds(1, 2, 3, 4, 5), ds(3, 5, 7, 9, 11));
        assertEquals(1.0, r.pearsonR(), 1e-12);
        assertEquals(0.0, r.pearsonP(), 1e-12);
        assertEquals(1.0, r.spearmanRho(), 1e-12);
        assertEquals(2.0, r.slope(), 1e-12);
        assertEquals(1.0, r.intercept(), 1e-12);
        assertEquals(1.0, r.rSquared(), 1e-12);
    }

    @Test
    void perfectInverseLineHasCorrelationMinusOne() {
        Bivariate.BivariateResult r = Bivariate.analyze(
                ds(1, 2, 3, 4), ds(8, 6, 4, 2));
        assertEquals(-1.0, r.pearsonR(), 1e-12);
        assertEquals(-2.0, r.slope(), 1e-12);
    }

    @Test
    void spearmanCapturesMonotoneNonlinearRelations() {
        // y = x³ is monotone but not linear: ρ = 1 while r < 1
        Bivariate.BivariateResult r = Bivariate.analyze(
                ds(1, 2, 3, 4, 5), ds(1, 8, 27, 64, 125));
        assertEquals(1.0, r.spearmanRho(), 1e-12);
        assertTrue(r.pearsonR() < 1.0);
    }

    @Test
    void uncorrelatedDataIsNotSignificant() {
        // Symmetric cloud engineered so Σ(x-x̄)(y-ȳ) = 0
        Bivariate.BivariateResult r = Bivariate.analyze(
                ds(-2, -1, 0, 1, 2), ds(4, 1, 0, 1, 4));
        assertEquals(0.0, r.pearsonR(), 1e-12);
        assertEquals(1.0, r.pearsonP(), 1e-9);
        assertEquals(0.0, r.slope(), 1e-12);
    }

    @Test
    void ranksAverageTies() {
        assertArrayEquals(new double[] {1.5, 1.5, 3.0}, Bivariate.ranks(new double[] {5, 5, 9}), 1e-12);
        assertArrayEquals(new double[] {3, 1, 2}, Bivariate.ranks(new double[] {30, 10, 20}), 1e-12);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Bivariate.analyze(ds(1, 2, 3), ds(1, 2)));         // length mismatch
        assertThrows(IllegalArgumentException.class,
                () -> Bivariate.analyze(ds(1, 2), ds(1, 2)));            // too few pairs
        assertThrows(IllegalArgumentException.class,
                () -> Bivariate.analyze(ds(7, 7, 7), ds(1, 2, 3)));      // constant x
    }
}
