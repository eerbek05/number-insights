package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
