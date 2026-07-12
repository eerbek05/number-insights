package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eerbek.numberinsights.analysis.StreamAnalyzer;
import com.eerbek.numberinsights.model.Dataset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests carried over from the original "Lab 13" coursework.
 *
 * <p>They pin the exact results of the stream pipelines against the 1000-value
 * reference dataset, proving that refactoring the exercise into a layered
 * application did not change any of its behaviour.</p>
 */
class StreamAnalyzerTest {

    private StreamAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        Dataset dataset = TestData.sampleDataset();
        analyzer = new StreamAnalyzer(dataset);
    }

    @Test
    @DisplayName("total count is 1000")
    void totalCount() {
        assertEquals(1000, analyzer.totalCount());
    }

    @Test
    @DisplayName("odd count is 507")
    void oddCount() {
        assertEquals(507, analyzer.oddCount());
    }

    @Test
    @DisplayName("even count is 493")
    void evenCount() {
        assertEquals(493, analyzer.evenCount());
    }

    @Test
    @DisplayName("distinct values greater than five is 94")
    void distinctGreaterThanFive() {
        assertEquals(94, analyzer.distinctGreaterThanFiveCount());
    }

    @Test
    @DisplayName("result1: even values in (5, 50) sorted")
    void result1() {
        assertArrayEquals(TestData.expectedResult(0), analyzer.evenBetweenFiveAndFiftySorted());
    }

    @Test
    @DisplayName("result2: first fifty values squared times three")
    void result2() {
        assertArrayEquals(TestData.expectedResult(1), analyzer.firstFiftySquaredTimesThree());
    }

    @Test
    @DisplayName("result3: odd values doubled, sorted, skipped, distinct")
    void result3() {
        assertArrayEquals(TestData.expectedResult(2), analyzer.oddDoubledSortedSkippedDistinct());
    }
}
