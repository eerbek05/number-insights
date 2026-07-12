package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.io.DataLoader;
import com.eerbek.numberinsights.model.Dataset;

import java.util.List;

import org.junit.jupiter.api.Test;

class DataLoaderTest {

    @Test
    void parsesOneValuePerLine() {
        Dataset ds = DataLoader.fromLines(List.of("1", "2", "3"));
        assertEquals(List.of(1, 2, 3), ds.values());
    }

    @Test
    void parsesCommaSeparatedValues() {
        Dataset ds = DataLoader.fromLines(List.of("1, 2, 3", "4;5", "6\t7"));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), ds.values());
    }

    @Test
    void skipsBlankAndCommentLines() {
        Dataset ds = DataLoader.fromLines(List.of("1", "", "# a comment", "2", "   "));
        assertEquals(List.of(1, 2), ds.values());
    }

    @Test
    void handlesNegativeNumbers() {
        Dataset ds = DataLoader.fromLines(List.of("-5", "10", "-3"));
        assertEquals(List.of(-5, 10, -3), ds.values());
    }

    @Test
    void reportsInvalidTokenWithLineNumber() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DataLoader.fromLines(List.of("1", "2", "oops")));
        assertTrue(ex.getMessage().contains("oops"));
        assertTrue(ex.getMessage().contains("line 3"));
    }

    @Test
    void loadsBundledSampleDataset() {
        Dataset ds = TestData.sampleDataset();
        assertEquals(1000, ds.size());
    }
}
