package com.eerbek.numberinsights.stats;

import java.util.List;

/**
 * An immutable snapshot of the descriptive statistics computed for a dataset.
 *
 * <p>Implemented as a {@code record} so it is a transparent, value-based carrier
 * with automatically generated accessors, {@code equals}, {@code hashCode} and
 * {@code toString}.</p>
 *
 * <p>Measures that need a minimum sample size (sample variance needs n ≥ 2,
 * skewness n ≥ 3, kurtosis and the Jarque–Bera test n ≥ 4) or a non-zero
 * denominator (coefficient of variation needs a non-zero mean) are
 * {@link Double#NaN} when undefined; formatters render NaN as {@code n/a} /
 * {@code null}.</p>
 *
 * @param count          number of observations
 * @param sum            sum of all observations
 * @param min            smallest observation
 * @param max            largest observation
 * @param range          {@code max - min}
 * @param mean           arithmetic mean
 * @param median         middle value (average of the two central values for even counts)
 * @param modes          the most frequently occurring value(s)
 * @param variance       population variance (divide by {@code n})
 * @param stdDev         population standard deviation
 * @param sampleVariance sample variance (divide by {@code n - 1}; NaN when n &lt; 2)
 * @param sampleStdDev   sample standard deviation (NaN when n &lt; 2)
 * @param q1             first quartile (25th percentile)
 * @param q3             third quartile (75th percentile)
 * @param iqr            inter-quartile range ({@code q3 - q1})
 * @param p5             5th percentile
 * @param p95            95th percentile
 * @param skewness       adjusted Fisher–Pearson skewness (the SKEW convention used
 *                       by spreadsheets and pandas; NaN when n &lt; 3 or the data is constant)
 * @param excessKurtosis sample excess kurtosis (spreadsheet KURT convention — 0 for a
 *                       normal distribution; NaN when n &lt; 4 or the data is constant)
 * @param coefficientOfVariation {@code sampleStdDev / mean} (NaN when the mean is 0 or n &lt; 2)
 * @param standardError  standard error of the mean, {@code sampleStdDev / sqrt(n)}
 * @param ci95Low        lower bound of the 95% confidence interval of the mean (normal approximation)
 * @param ci95High       upper bound of the 95% confidence interval of the mean (normal approximation)
 * @param lowerFence     Tukey lower outlier fence, {@code q1 - 1.5 * iqr}
 * @param upperFence     Tukey upper outlier fence, {@code q3 + 1.5 * iqr}
 * @param outlierCount   number of observations outside the Tukey fences
 * @param jarqueBera     Jarque–Bera normality statistic (from the population-moment
 *                       skewness and kurtosis; NaN when n &lt; 4 or the data is constant)
 * @param jarqueBeraP    asymptotic p-value of the Jarque–Bera test (χ² with 2 df);
 *                       small values are evidence against normality
 */
public record StatisticsResult(
        long count,
        double sum,
        double min,
        double max,
        double range,
        double mean,
        double median,
        List<Double> modes,
        double variance,
        double stdDev,
        double sampleVariance,
        double sampleStdDev,
        double q1,
        double q3,
        double iqr,
        double p5,
        double p95,
        double skewness,
        double excessKurtosis,
        double coefficientOfVariation,
        double standardError,
        double ci95Low,
        double ci95High,
        double lowerFence,
        double upperFence,
        long outlierCount,
        double jarqueBera,
        double jarqueBeraP) {

    public StatisticsResult {
        // Keep the modes list immutable inside the record.
        modes = List.copyOf(modes);
    }
}
