package com.eerbek.numberinsights.stats;

import com.eerbek.numberinsights.model.Dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes descriptive statistics for a {@link Dataset}.
 *
 * <p>All measures are derived from a single sorted copy of the data, so a full
 * {@link StatisticsResult} is produced in {@code O(n log n)} time. Quartiles use
 * linear interpolation between the closest ranks (the same method as NumPy's
 * default and spreadsheet {@code QUARTILE.INC}).</p>
 */
public final class DescriptiveStatistics {

    private final int[] sorted;

    /**
     * @param dataset the data to analyse; must contain at least one value
     * @throws IllegalArgumentException if the dataset is empty
     */
    public DescriptiveStatistics(Dataset dataset) {
        if (dataset.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute statistics for an empty dataset");
        }
        this.sorted = dataset.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /** @return every descriptive measure bundled into a single result object */
    public StatisticsResult summary() {
        long count = sorted.length;
        long sum = sum();
        int min = sorted[0];
        int max = sorted[sorted.length - 1];
        double mean = (double) sum / count;
        double variance = variance(mean);

        return new StatisticsResult(
                count,
                sum,
                min,
                max,
                max - min,
                mean,
                median(),
                modes(),
                variance,
                Math.sqrt(variance),
                percentile(25),
                percentile(75),
                percentile(75) - percentile(25));
    }

    long sum() {
        long total = 0;
        for (int v : sorted) {
            total += v;
        }
        return total;
    }

    double mean() {
        return (double) sum() / sorted.length;
    }

    /** @return the median (50th percentile) */
    double median() {
        return percentile(50);
    }

    private double variance(double mean) {
        double squaredDiffs = 0;
        for (int v : sorted) {
            double diff = v - mean;
            squaredDiffs += diff * diff;
        }
        return squaredDiffs / sorted.length;
    }

    /**
     * Returns the value at the given percentile using linear interpolation
     * between closest ranks.
     *
     * @param p the percentile in the range {@code [0, 100]}
     * @return the interpolated percentile value
     */
    double percentile(double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        double fraction = rank - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }

    /**
     * @return the value(s) that occur most often, in ascending order. When every
     *         value is unique, all values qualify and are returned.
     */
    List<Integer> modes() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int v : sorted) {
            counts.merge(v, 1, Integer::sum);
        }
        int highest = counts.values().stream().max(Integer::compareTo).orElse(0);
        List<Integer> modes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == highest) {
                modes.add(entry.getKey());
            }
        }
        return modes; // sorted[] was ascending, so insertion order is ascending
    }
}
