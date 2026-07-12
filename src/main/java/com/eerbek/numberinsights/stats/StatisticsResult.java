package com.eerbek.numberinsights.stats;

import java.util.List;

/**
 * An immutable snapshot of the descriptive statistics computed for a dataset.
 *
 * <p>Implemented as a {@code record} so it is a transparent, value-based carrier
 * with automatically generated accessors, {@code equals}, {@code hashCode} and
 * {@code toString}.</p>
 *
 * @param count    number of observations
 * @param sum      sum of all observations
 * @param min      smallest observation
 * @param max      largest observation
 * @param range    {@code max - min}
 * @param mean     arithmetic mean
 * @param median   middle value (average of the two central values for even counts)
 * @param modes    the most frequently occurring value(s)
 * @param variance population variance
 * @param stdDev   population standard deviation
 * @param q1       first quartile (25th percentile)
 * @param q3       third quartile (75th percentile)
 * @param iqr      inter-quartile range ({@code q3 - q1})
 */
public record StatisticsResult(
        long count,
        long sum,
        int min,
        int max,
        int range,
        double mean,
        double median,
        List<Integer> modes,
        double variance,
        double stdDev,
        double q1,
        double q3,
        double iqr) {

    public StatisticsResult {
        // Keep the modes list immutable inside the record.
        modes = List.copyOf(modes);
    }
}
