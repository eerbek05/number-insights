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

    /* ---- one-sample & paired t ---- */

    @Test
    void oneSampleTTestMatchesHandComputation() {
        // {1..10}: mean 5.5, sample std 3.0277, SE 0.9574; vs mu = 4:
        // t = 1.5/0.9574 = 1.5667, df = 9
        Inference.TTestResult t = Inference.oneSampleTTest(stats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4);
        assertEquals(1.5, t.meanDifference(), 1e-12);
        assertEquals(9.0, t.degreesOfFreedom(), 1e-12);
        assertEquals(1.566699, t.tStatistic(), 1e-5);
        assertTrue(t.pValue() > 0.14 && t.pValue() < 0.16); // known ≈ 0.1516
    }

    @Test
    void oneSampleAgainstOwnMeanGivesPOne() {
        Inference.TTestResult t = Inference.oneSampleTTest(stats(1, 2, 3, 4, 5), 3);
        assertEquals(0.0, t.tStatistic(), 1e-12);
        assertEquals(1.0, t.pValue(), 1e-12);
    }

    @Test
    void pairedTTestUsesTheDifferences() {
        Dataset before = Dataset.of(List.of(10, 12, 14, 16, 18));
        Dataset after = Dataset.of(List.of(9, 10, 13, 14, 16));
        // Differences: 1,2,1,2,2 → mean 1.6, sample std 0.5477, SE 0.2449
        Inference.TTestResult t = Inference.pairedTTest(before, after);
        assertEquals(1.6, t.meanDifference(), 1e-12);
        assertEquals(4.0, t.degreesOfFreedom(), 1e-12);
        assertEquals(6.531973, t.tStatistic(), 1e-5);
        assertTrue(t.pValue() < 0.01);
    }

    @Test
    void pairedTTestRejectsMismatchedLengths() {
        assertThrows(IllegalArgumentException.class, () -> Inference.pairedTTest(
                Dataset.of(List.of(1, 2, 3)), Dataset.of(List.of(1, 2))));
    }

    /* ---- ANOVA ---- */

    @Test
    void anovaMatchesHandComputation() {
        // Groups {1,2,3},{2,3,4},{6,7,8}: grand mean 4; SSB = 42, MSB = 21;
        // each group's sample variance is 1 → SSW = 6, MSW = 1 → F = 21, df = (2, 6)
        Inference.AnovaResult r = Inference.oneWayAnova(List.of(
                stats(1, 2, 3), stats(2, 3, 4), stats(6, 7, 8)));
        assertEquals(21.0, r.fStatistic(), 1e-9);
        assertEquals(2, r.dfBetween());
        assertEquals(6, r.dfWithin());
        assertEquals(4.0, r.grandMean(), 1e-12);
        assertEquals(List.of(2.0, 3.0, 7.0), r.groupMeans());
        assertTrue(r.pValue() < 0.01 && r.pValue() > 0);
    }

    @Test
    void anovaOfIdenticalGroupsIsNotSignificant() {
        Inference.AnovaResult r = Inference.oneWayAnova(List.of(
                stats(1, 2, 3), stats(1, 2, 3), stats(1, 2, 3)));
        assertEquals(0.0, r.fStatistic(), 1e-12);
        assertEquals(1.0, r.pValue(), 1e-9);
    }

    /* ---- chi-square ---- */

    @Test
    void chiSquareGoodnessOfFitMatchesHandComputation() {
        // Observed 10,20,30 vs uniform expected 20: χ² = (100+0+100)/20 = 10, df 2.
        // For df = 2 the survival function is exp(-x/2) → p = e^-5 = 0.006738 exactly.
        Inference.ChiSquareResult r = Inference.chiSquareGoodnessOfFit(new double[] {10, 20, 30});
        assertEquals(10.0, r.statistic(), 1e-12);
        assertEquals(2, r.df());
        assertEquals(Math.exp(-5), r.pValue(), 1e-9);
    }

    @Test
    void chiSquareIndependenceMatchesHandComputation() {
        // [[10,20],[20,10]]: all expected 15 → χ² = 4·(25/15) = 6.6667, df 1, p ≈ 0.0098
        Inference.ChiSquareResult r = Inference.chiSquareIndependence(
                new double[][] {{10, 20}, {20, 10}});
        assertEquals(20.0 / 3, r.statistic(), 1e-9);
        assertEquals(1, r.df());
        assertEquals(0.009823, r.pValue(), 1e-5);
        assertEquals(15.0, r.expected()[0][0], 1e-12);
    }

    @Test
    void chiSquareRejectsBadTables() {
        assertThrows(IllegalArgumentException.class,
                () -> Inference.chiSquareGoodnessOfFit(new double[] {5}));
        assertThrows(IllegalArgumentException.class,
                () -> Inference.chiSquareIndependence(new double[][] {{1, 2}}));
        assertThrows(IllegalArgumentException.class,
                () -> Inference.chiSquareIndependence(new double[][] {{1, -2}, {3, 4}}));
    }

    /* ---- the gamma/F numerics underneath ---- */

    @Test
    void regularizedGammaPMatchesChiSquareDf2Identity() {
        // χ²(2) CDF is 1 - exp(-x/2) → P(1, x/2) must equal it.
        for (double x : new double[] {0.5, 1, 2, 5, 10}) {
            assertEquals(1 - Math.exp(-x / 2), Inference.regularizedGammaP(1, x / 2), 1e-12);
        }
        assertEquals(0.0, Inference.regularizedGammaP(3, 0), 1e-12);
    }

    @Test
    void fUpperTailPMatchesCriticalValue() {
        // Classic F table: F(0.05; 2, 6) = 5.1433 → upper tail p = 0.05
        assertEquals(0.05, Inference.fUpperTailP(5.143253, 2, 6), 1e-4);
        assertEquals(1.0, Inference.fUpperTailP(0, 3, 10), 1e-12);
    }
}
