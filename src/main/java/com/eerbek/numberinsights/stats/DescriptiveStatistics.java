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
 * {@link StatisticsResult} is produced in {@code O(n log n)} time. Conventions
 * chosen to match what students meet in an introductory statistics course (and
 * what spreadsheets/pandas report):</p>
 *
 * <ul>
 *   <li>quartiles/percentiles use linear interpolation between closest ranks
 *       (NumPy default, spreadsheet {@code QUARTILE.INC});</li>
 *   <li>{@code variance}/{@code stdDev} are the <em>population</em> forms
 *       (divide by {@code n}); {@code sampleVariance}/{@code sampleStdDev} are
 *       the <em>sample</em> forms (divide by {@code n - 1});</li>
 *   <li>skewness is the adjusted Fisher–Pearson coefficient (spreadsheet
 *       {@code SKEW}); kurtosis is sample <em>excess</em> kurtosis (spreadsheet
 *       {@code KURT} — 0 for a normal distribution);</li>
 *   <li>the 95% confidence interval of the mean uses the normal approximation
 *       ({@code mean ± 1.96 · SE});</li>
 *   <li>outliers are counted with the Tukey 1.5·IQR fence rule.</li>
 * </ul>
 */
public final class DescriptiveStatistics {

    /** Two-sided 95% critical value of the standard normal distribution. */
    private static final double Z_95 = 1.959963984540054;

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
        int n = sorted.length;
        long sum = sum();
        int min = sorted[0];
        int max = sorted[n - 1];
        double mean = (double) sum / n;

        double squaredDiffs = 0;
        for (int v : sorted) {
            double diff = v - mean;
            squaredDiffs += diff * diff;
        }
        double variance = squaredDiffs / n;
        double sampleVariance = (n >= 2) ? squaredDiffs / (n - 1) : Double.NaN;
        double sampleStdDev = Math.sqrt(sampleVariance);

        double standardError = sampleStdDev / Math.sqrt(n);
        double q1 = percentile(25);
        double q3 = percentile(75);
        double iqr = q3 - q1;
        double lowerFence = q1 - 1.5 * iqr;
        double upperFence = q3 + 1.5 * iqr;

        return new StatisticsResult(
                n,
                sum,
                min,
                max,
                max - min,
                mean,
                median(),
                modes(),
                variance,
                Math.sqrt(variance),
                sampleVariance,
                sampleStdDev,
                q1,
                q3,
                iqr,
                percentile(5),
                percentile(95),
                skewness(mean, sampleStdDev),
                excessKurtosis(mean, sampleStdDev),
                (mean != 0) ? sampleStdDev / mean : Double.NaN,
                standardError,
                mean - Z_95 * standardError,
                mean + Z_95 * standardError,
                lowerFence,
                upperFence,
                countOutside(lowerFence, upperFence));
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

    /** Adjusted Fisher–Pearson skewness: {@code n/((n-1)(n-2)) · Σz³} with sample std. */
    private double skewness(double mean, double sampleStdDev) {
        int n = sorted.length;
        if (n < 3 || !(sampleStdDev > 0)) {
            return Double.NaN;
        }
        double sumZ3 = 0;
        for (int v : sorted) {
            double z = (v - mean) / sampleStdDev;
            sumZ3 += z * z * z;
        }
        return (double) n / ((n - 1.0) * (n - 2.0)) * sumZ3;
    }

    /** Sample excess kurtosis (spreadsheet {@code KURT}): 0 for a normal distribution. */
    private double excessKurtosis(double mean, double sampleStdDev) {
        int n = sorted.length;
        if (n < 4 || !(sampleStdDev > 0)) {
            return Double.NaN;
        }
        double sumZ4 = 0;
        for (int v : sorted) {
            double z = (v - mean) / sampleStdDev;
            sumZ4 += z * z * z * z;
        }
        double term1 = (double) n * (n + 1) / ((n - 1.0) * (n - 2.0) * (n - 3.0)) * sumZ4;
        double term2 = 3.0 * (n - 1.0) * (n - 1.0) / ((n - 2.0) * (n - 3.0));
        return term1 - term2;
    }

    private long countOutside(double lowerFence, double upperFence) {
        long outliers = 0;
        for (int v : sorted) {
            if (v < lowerFence || v > upperFence) {
                outliers++;
            }
        }
        return outliers;
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
