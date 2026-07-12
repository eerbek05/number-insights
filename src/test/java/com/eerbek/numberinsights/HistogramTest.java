package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.viz.Histogram;

import java.util.List;

import org.junit.jupiter.api.Test;

class HistogramTest {

    @Test
    void rendersOneLinePerBin() {
        Dataset ds = Dataset.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        String output = new Histogram(5, 20).render(ds);
        assertEquals(5, output.lines().count());
    }

    @Test
    void countsAcrossBinsSumToDatasetSize() {
        Dataset ds = Dataset.of(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        String output = new Histogram(5, 20).render(ds);
        int total = output.lines()
                .mapToInt(line -> Integer.parseInt(line.substring(line.lastIndexOf(' ') + 1).strip()))
                .sum();
        assertEquals(ds.size(), total);
    }

    @Test
    void emptyDatasetIsRejected() {
        Dataset empty = Dataset.of(List.of());
        assertThrows(IllegalArgumentException.class, () -> new Histogram().render(empty));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Histogram(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Histogram(10, 0));
    }

    @Test
    void handlesUniformValues() {
        Dataset ds = Dataset.of(List.of(7, 7, 7, 7));
        String output = new Histogram(4, 10).render(ds);
        assertTrue(output.contains("4"));
    }

    @Test
    void computeBinsCoversTheFullRangeAndSumsToSize() {
        Dataset ds = Dataset.of(List.of(0, 5, 10, 15, 20));
        List<Histogram.Bin> bins = Histogram.computeBins(ds, 4);
        assertEquals(4, bins.size());
        assertEquals(0.0, bins.get(0).low());
        assertEquals(20.0, bins.get(3).high());
        assertEquals(ds.size(), bins.stream().mapToInt(Histogram.Bin::count).sum());
    }

    @Test
    void computeBinsRejectsBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> Histogram.computeBins(Dataset.of(List.of()), 4));
        assertThrows(IllegalArgumentException.class,
                () -> Histogram.computeBins(Dataset.of(List.of(1)), 0));
    }
}
