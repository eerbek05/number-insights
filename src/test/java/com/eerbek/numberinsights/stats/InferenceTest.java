package com.eerbek.numberinsights.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.model.Dataset;
import java.util.List;

import org.junit.jupiter.api.Test;

class InferenceTest {

    private static StatisticsResult stats(Integer... values) {
        return new DescriptiveStatistics(Dataset.of(List.of(values))).summary();
    }

    /* ---- the numerics underneath the test ---- */

    @Test
    void logGammaMatchesKnownValues() {
        // Γ(5) = 24, Γ(0.5) = √π
        assertEquals(Math.log(24), Inference.logGamma(5), 1e-10);
        assertEquals(0.5 * Math.log(Math.PI), Inference.logGamma(0.5), 1e-10);
    }

    @Test
    void incompleteBetaEdgeCases() {
        assertEquals(0.0, Inference.regularizedIncompleteBeta(2, 3, 0), 1e-12);
        assertEquals(1.0, Inference.regularizedIncompleteBeta(2, 3, 1), 1e-12);
        // I_x(1,1) is the uniform CDF: I_0.3(1,1) = 0.3
        assertEquals(0.3, Inference.regularizedIncompleteBeta(1, 1, 0.3), 1e-10);
    }

    @Test
    void studentTPValueMatchesCriticalValues() {
        // Classic t-table entries: t(0.025, df=10) = 2.2281 → two-sided p = 0.05
        assertEquals(0.05, Inference.twoSidedP(2.228138852, 10), 1e-4);
        // t(0.025, df=∞) ≈ 1.960 → p ≈ 0.05 (large df approaches the normal)
        assertEquals(0.05, Inference.twoSidedP(1.959963985, 100000), 1e-4);
        // t = 0 → p = 1
        assertEquals(1.0, Inference.twoSidedP(0, 10), 1e-12);
    }

    /* ---- the test itself ---- */

    @Test
    void identicalDatasetsShowNoDifference() {
        StatisticsResult a = stats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Inference.TTestResult t = Inference.welchTTest(a, a);
        assertEquals(0.0, t.meanDifference(), 1e-12);
        assertEquals(0.0, t.tStatistic(), 1e-12);
        assertEquals(1.0, t.pValue(), 1e-12);
    }

    @Test
    void clearlySeparatedDatasetsAreSignificant() {
        StatisticsResult a = stats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        StatisticsResult b = stats(11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        Inference.TTestResult t = Inference.welchTTest(a, b);

        assertEquals(-10.0, t.meanDifference(), 1e-12);
        // Equal sizes and variances → Welch df = na + nb - 2 = 18
        assertEquals(18.0, t.degreesOfFreedom(), 1e-9);
        // t = -10 / sqrt(2 · 9.1667/10) = -7.385...
        assertEquals(-7.385489, t.tStatistic(), 1e-5);
        assertTrue(t.pValue() < 0.001);
        // Cohen's d = -10 / sqrt(9.1667) = -3.3029...
        assertEquals(-3.302891, t.cohensD(), 1e-5);
    }

    @Test
    void welchHandlesUnequalVariances() {
        StatisticsResult a = stats(10, 10, 10, 10, 11); // tiny variance
        StatisticsResult b = stats(1, 5, 9, 13, 22);    // large variance
        Inference.TTestResult t = Inference.welchTTest(a, b);
        // Sanity: df < na + nb - 2 when variances differ (Welch correction)
        assertTrue(t.degreesOfFreedom() < 8);
        assertTrue(t.pValue() > 0 && t.pValue() < 1);
    }

    @Test
    void tinySamplesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Inference.welchTTest(stats(1), stats(1, 2, 3)));
    }

    @Test
    void zeroVarianceInBothDatasetsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Inference.welchTTest(stats(5, 5, 5), stats(7, 7, 7)));
    }
}
