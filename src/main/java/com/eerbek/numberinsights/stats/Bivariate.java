package com.eerbek.numberinsights.stats;

import com.eerbek.numberinsights.model.Dataset;

import java.util.Arrays;

/**
 * Bivariate statistics for paired observations: correlation and simple linear
 * regression.
 *
 * <p>Given two datasets of equal length interpreted as {@code (x_i, y_i)}
 * pairs, computes Pearson's r (with the exact two-sided p-value via the
 * Student-t relation {@code t = r·√((n-2)/(1-r²))}), Spearman's rank
 * correlation ρ (average ranks for ties), and the least-squares line
 * {@code y = slope·x + intercept} with its R².</p>
 */
public final class Bivariate {

    /**
     * The combined correlation/regression outcome.
     *
     * @param n           number of pairs
     * @param pearsonR    Pearson product-moment correlation
     * @param pearsonP    two-sided p-value for {@code H0: r = 0} ({@code df = n - 2})
     * @param spearmanRho Spearman rank correlation (average ranks for ties)
     * @param slope       least-squares slope
     * @param intercept   least-squares intercept
     * @param rSquared    coefficient of determination of the fitted line
     */
    public record BivariateResult(
            long n,
            double pearsonR,
            double pearsonP,
            double spearmanRho,
            double slope,
            double intercept,
            double rSquared) {
    }

    private Bivariate() {
        // Utility class; not instantiable.
    }

    /**
     * Analyses two datasets as paired {@code (x, y)} observations.
     *
     * @param x the first coordinate of each pair
     * @param y the second coordinate, same length and order as {@code x}
     * @return correlation and regression results
     * @throws IllegalArgumentException if lengths differ, fewer than three pairs,
     *                                  or either variable is constant
     */
    public static BivariateResult analyze(Dataset x, Dataset y) {
        if (x.size() != y.size()) {
            throw new IllegalArgumentException(
                    "Paired analysis needs datasets of equal length (got "
                            + x.size() + " and " + y.size() + ")");
        }
        int n = x.size();
        if (n < 3) {
            throw new IllegalArgumentException("Paired analysis needs at least 3 pairs");
        }

        double[] xs = x.doubleStream().toArray();
        double[] ys = y.doubleStream().toArray();

        double r = pearson(xs, ys);
        double rho = pearson(ranks(xs), ranks(ys));

        // Least squares from the same moments Pearson uses.
        double meanX = mean(xs);
        double meanY = mean(ys);
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (xs[i] - meanX) * (xs[i] - meanX);
            sxy += (xs[i] - meanX) * (ys[i] - meanY);
        }
        double slope = sxy / sxx;
        double intercept = meanY - slope * meanX;

        return new BivariateResult(n, r, pearsonP(r, n), rho, slope, intercept, r * r);
    }

    /** Two-sided p-value for Pearson's r via {@code t = r·√((n-2)/(1-r²))}. */
    static double pearsonP(double r, int n) {
        double denominator = 1 - r * r;
        if (denominator <= 0) {
            return 0; // |r| = 1: a perfect line
        }
        double t = r * Math.sqrt((n - 2) / denominator);
        return Inference.twoSidedP(t, n - 2.0);
    }

    private static double pearson(double[] xs, double[] ys) {
        double meanX = mean(xs);
        double meanY = mean(ys);
        double sxx = 0;
        double syy = 0;
        double sxy = 0;
        for (int i = 0; i < xs.length; i++) {
            double dx = xs[i] - meanX;
            double dy = ys[i] - meanY;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        if (!(sxx > 0) || !(syy > 0)) {
            throw new IllegalArgumentException(
                    "Correlation is undefined when a variable is constant");
        }
        return sxy / Math.sqrt(sxx * syy);
    }

    /** Ranks with ties resolved as the average of the tied positions (1-based). */
    static double[] ranks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (i, j) -> Double.compare(values[i], values[j]));

        double[] result = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[order[j + 1]] == values[order[i]]) {
                j++;
            }
            double averageRank = (i + j) / 2.0 + 1; // 1-based
            for (int k = i; k <= j; k++) {
                result[order[k]] = averageRank;
            }
            i = j + 1;
        }
        return result;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
