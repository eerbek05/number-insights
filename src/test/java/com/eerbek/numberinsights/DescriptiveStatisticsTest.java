package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.stats.DescriptiveStatistics;
import com.eerbek.numberinsights.stats.StatisticsResult;

import java.util.List;

import org.junit.jupiter.api.Test;

class DescriptiveStatisticsTest {

    private static final double EPS = 1e-9;

    /** Classic textbook set with a known mean of 5 and population std dev of 2. */
    private StatisticsResult summaryOf(Integer... values) {
        return new DescriptiveStatistics(Dataset.of(List.of(values))).summary();
    }

    @Test
    void computesCoreMeasures() {
        StatisticsResult s = summaryOf(2, 4, 4, 4, 5, 5, 7, 9);
        assertEquals(8, s.count());
        assertEquals(40, s.sum());
        assertEquals(2, s.min());
        assertEquals(9, s.max());
        assertEquals(7, s.range());
        assertEquals(5.0, s.mean(), EPS);
        assertEquals(4.0, s.variance(), EPS);
        assertEquals(2.0, s.stdDev(), EPS);
    }

    @Test
    void medianOfEvenCountAveragesMiddleValues() {
        StatisticsResult s = summaryOf(2, 4, 4, 4, 5, 5, 7, 9);
        assertEquals(4.5, s.median(), EPS);
    }

    @Test
    void medianOfOddCountIsMiddleValue() {
        StatisticsResult s = summaryOf(1, 3, 7);
        assertEquals(3.0, s.median(), EPS);
    }

    @Test
    void identifiesTheMode() {
        StatisticsResult s = summaryOf(2, 4, 4, 4, 5, 5, 7, 9);
        assertEquals(List.of(4), s.modes());
    }

    @Test
    void reportsMultipleModesInAscendingOrder() {
        StatisticsResult s = summaryOf(1, 1, 2, 2, 3);
        assertEquals(List.of(1, 2), s.modes());
    }

    @Test
    void computesQuartilesWithLinearInterpolation() {
        StatisticsResult s = summaryOf(2, 4, 4, 4, 5, 5, 7, 9);
        assertEquals(4.0, s.q1(), EPS);
        assertEquals(5.5, s.q3(), EPS);
        assertEquals(1.5, s.iqr(), EPS);
    }

    @Test
    void singleValueDataset() {
        StatisticsResult s = summaryOf(42);
        assertEquals(42.0, s.mean(), EPS);
        assertEquals(42.0, s.median(), EPS);
        assertEquals(0.0, s.stdDev(), EPS);
    }

    @Test
    void emptyDatasetIsRejected() {
        Dataset empty = Dataset.of(List.of());
        assertThrows(IllegalArgumentException.class, () -> new DescriptiveStatistics(empty));
    }

    @Test
    void sampleVarianceUsesNMinusOne() {
        // {1..5}: population variance 2, sample variance 2.5
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        assertEquals(2.0, s.variance(), EPS);
        assertEquals(2.5, s.sampleVariance(), EPS);
        assertEquals(Math.sqrt(2.5), s.sampleStdDev(), EPS);
    }

    @Test
    void skewnessOfSymmetricDataIsZero() {
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        assertEquals(0.0, s.skewness(), EPS);
    }

    @Test
    void skewnessMatchesSpreadsheetConvention() {
        // Excel SKEW(1,1,1,10) = 2 exactly (adjusted Fisher–Pearson)
        StatisticsResult s = summaryOf(1, 1, 1, 10);
        assertEquals(2.0, s.skewness(), 1e-9);
    }

    @Test
    void kurtosisMatchesSpreadsheetConvention() {
        // Excel KURT(1,2,3,4,5) = -1.2 exactly (sample excess kurtosis)
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        assertEquals(-1.2, s.excessKurtosis(), 1e-9);
    }

    @Test
    void standardErrorAndConfidenceInterval() {
        // {1..5}: SE = sqrt(2.5)/sqrt(5) = 0.7071...; CI = 3 ± 1.95996·SE
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        double se = Math.sqrt(2.5 / 5);
        assertEquals(se, s.standardError(), EPS);
        assertEquals(3 - 1.959963984540054 * se, s.ci95Low(), 1e-9);
        assertEquals(3 + 1.959963984540054 * se, s.ci95High(), 1e-9);
    }

    @Test
    void coefficientOfVariation() {
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        assertEquals(Math.sqrt(2.5) / 3.0, s.coefficientOfVariation(), EPS);
    }

    @Test
    void outerPercentiles() {
        // {1..5}: p5 → rank 0.2 → 1.2; p95 → rank 3.8 → 4.8
        StatisticsResult s = summaryOf(1, 2, 3, 4, 5);
        assertEquals(1.2, s.p5(), EPS);
        assertEquals(4.8, s.p95(), EPS);
    }

    @Test
    void tukeyFencesAndOutlierCount() {
        // {1,2,3,4,100}: q1=2, q3=4, iqr=2 → fences [-1, 7] → one outlier (100)
        StatisticsResult s = summaryOf(1, 2, 3, 4, 100);
        assertEquals(-1.0, s.lowerFence(), EPS);
        assertEquals(7.0, s.upperFence(), EPS);
        assertEquals(1, s.outlierCount());
    }

    @Test
    void measuresUndefinedForTinySamplesAreNaN() {
        StatisticsResult one = summaryOf(42);
        assertTrue(Double.isNaN(one.sampleVariance()));
        assertTrue(Double.isNaN(one.skewness()));

        StatisticsResult three = summaryOf(1, 2, 3);
        assertFalse(Double.isNaN(three.skewness()));
        assertTrue(Double.isNaN(three.excessKurtosis())); // kurtosis needs n >= 4

        StatisticsResult zeroMean = summaryOf(-1, 0, 1);
        assertTrue(Double.isNaN(zeroMean.coefficientOfVariation())); // mean is 0
    }
}
