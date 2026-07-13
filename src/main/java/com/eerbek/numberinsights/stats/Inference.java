package com.eerbek.numberinsights.stats;

import com.eerbek.numberinsights.model.Dataset;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferential statistics: hypothesis tests over one, two or many samples.
 *
 * <p>Implements the tests of an introductory statistics course — one-sample,
 * paired and Welch's two-sample t-tests, one-way ANOVA and the χ² tests for
 * goodness of fit and independence — with exact p-values computed in-house:
 * the Student-t and F distributions via the regularized incomplete beta
 * function, χ² via the regularized incomplete gamma function (both built on a
 * Lanczos log-gamma), keeping the project free of third-party math libraries.</p>
 */
public final class Inference {

    /**
     * The outcome of a t-test (one-sample, paired or two-sample).
     *
     * @param meanDifference   the tested difference ({@code meanA - meanB}, mean of
     *                         the pairwise differences, or {@code mean - mu0})
     * @param cohensD          effect size using the relevant sample standard deviation
     *                         (conventional read: 0.2 small, 0.5 medium, 0.8 large)
     * @param tStatistic       the t statistic
     * @param degreesOfFreedom the test's degrees of freedom
     * @param pValue           two-sided p-value from the Student t distribution
     */
    public record TTestResult(
            double meanDifference,
            double cohensD,
            double tStatistic,
            double degreesOfFreedom,
            double pValue) {
    }

    /**
     * The outcome of a one-way ANOVA.
     *
     * @param fStatistic F ratio ({@code MS_between / MS_within})
     * @param dfBetween  numerator degrees of freedom ({@code k - 1})
     * @param dfWithin   denominator degrees of freedom ({@code N - k})
     * @param pValue     upper-tail p-value from the F distribution
     * @param grandMean  mean of all observations pooled
     * @param groupMeans the group means, in input order
     */
    public record AnovaResult(
            double fStatistic,
            long dfBetween,
            long dfWithin,
            double pValue,
            double grandMean,
            List<Double> groupMeans) {

        public AnovaResult {
            groupMeans = List.copyOf(groupMeans);
        }
    }

    /**
     * The outcome of a χ² test.
     *
     * @param statistic the χ² statistic
     * @param df        degrees of freedom
     * @param pValue    upper-tail p-value from the χ² distribution
     * @param expected  the expected counts under the null hypothesis, same shape
     *                  as the observed table
     */
    public record ChiSquareResult(
            double statistic,
            long df,
            double pValue,
            double[][] expected) {
    }

    private Inference() {
        // Utility class; not instantiable.
    }

    /**
     * One-sample t-test: is the population mean different from {@code mu0}?
     *
     * @param s   summary of the sample (needs {@code count >= 2} and non-zero variance)
     * @param mu0 the hypothesised mean
     * @return the test outcome ({@code df = n - 1})
     * @throws IllegalArgumentException on a sample of fewer than two values or zero variance
     */
    public static TTestResult oneSampleTTest(StatisticsResult s, double mu0) {
        if (s.count() < 2) {
            throw new IllegalArgumentException("The one-sample t-test needs at least 2 values");
        }
        if (!(s.sampleStdDev() > 0)) {
            throw new IllegalArgumentException("The one-sample t-test is undefined for constant data");
        }
        double diff = s.mean() - mu0;
        double t = diff / s.standardError();
        double df = s.count() - 1;
        return new TTestResult(diff, diff / s.sampleStdDev(), t, df, twoSidedP(t, df));
    }

    /**
     * Paired t-test: a one-sample t-test on the pairwise differences
     * {@code a_i - b_i} against zero. Use for before/after measurements on the
     * same subjects.
     *
     * @param a first measurement per subject
     * @param b second measurement per subject, same length and order as {@code a}
     * @return the test outcome ({@code df = n - 1})
     * @throws IllegalArgumentException if lengths differ, fewer than two pairs,
     *                                  or all differences are identical
     */
    public static TTestResult pairedTTest(Dataset a, Dataset b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "The paired t-test needs datasets of equal length (got "
                            + a.size() + " and " + b.size() + ")");
        }
        List<Double> diffs = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            diffs.add(a.values().get(i) - b.values().get(i));
        }
        StatisticsResult d = new DescriptiveStatistics(Dataset.of(diffs)).summary();
        if (!(d.sampleStdDev() > 0)) {
            throw new IllegalArgumentException(
                    "The paired t-test is undefined when every difference is identical");
        }
        return oneSampleTTest(d, 0);
    }

    /**
     * One-way ANOVA: do {@code k} independent groups share one mean? Computable
     * entirely from the groups' summaries (counts, means, sample variances).
     *
     * @param groups summaries of at least two groups, each with {@code count >= 2}
     * @return the test outcome
     * @throws IllegalArgumentException on fewer than two groups, a group with
     *                                  fewer than two values, or zero within-group variance
     */
    public static AnovaResult oneWayAnova(List<StatisticsResult> groups) {
        if (groups.size() < 2) {
            throw new IllegalArgumentException("ANOVA needs at least two groups");
        }
        long totalN = 0;
        double weightedSum = 0;
        for (StatisticsResult g : groups) {
            if (g.count() < 2) {
                throw new IllegalArgumentException("Every ANOVA group needs at least 2 values");
            }
            totalN += g.count();
            weightedSum += g.mean() * g.count();
        }
        double grandMean = weightedSum / totalN;

        double ssBetween = 0;
        double ssWithin = 0;
        List<Double> means = new ArrayList<>(groups.size());
        for (StatisticsResult g : groups) {
            double diff = g.mean() - grandMean;
            ssBetween += g.count() * diff * diff;
            ssWithin += (g.count() - 1) * g.sampleVariance();
            means.add(g.mean());
        }

        long dfBetween = groups.size() - 1;
        long dfWithin = totalN - groups.size();
        double msWithin = ssWithin / dfWithin;
        if (!(msWithin > 0)) {
            throw new IllegalArgumentException("ANOVA is undefined with zero within-group variance");
        }
        double f = (ssBetween / dfBetween) / msWithin;
        return new AnovaResult(f, dfBetween, dfWithin, fUpperTailP(f, dfBetween, dfWithin),
                grandMean, means);
    }

    /**
     * χ² goodness-of-fit test of observed counts against expected counts.
     *
     * @param observed observed category counts (all non-negative, at least two categories)
     * @param expected expected counts, same length, all positive; pass equal values
     *                 (or use {@link #chiSquareGoodnessOfFit(double[])}) for a uniform null
     * @return the test outcome ({@code df = k - 1})
     */
    public static ChiSquareResult chiSquareGoodnessOfFit(double[] observed, double[] expected) {
        if (observed.length < 2 || observed.length != expected.length) {
            throw new IllegalArgumentException(
                    "Goodness-of-fit needs >= 2 categories and matching observed/expected lengths");
        }
        double statistic = 0;
        for (int i = 0; i < observed.length; i++) {
            if (observed[i] < 0 || !(expected[i] > 0)) {
                throw new IllegalArgumentException(
                        "Counts must be non-negative and expected counts positive");
            }
            double diff = observed[i] - expected[i];
            statistic += diff * diff / expected[i];
        }
        long df = observed.length - 1;
        return new ChiSquareResult(statistic, df, chiSquareUpperTailP(statistic, df),
                new double[][] {expected.clone()});
    }

    /** χ² goodness-of-fit against a uniform distribution over the categories. */
    public static ChiSquareResult chiSquareGoodnessOfFit(double[] observed) {
        double total = 0;
        for (double o : observed) {
            total += o;
        }
        double[] expected = new double[observed.length];
        java.util.Arrays.fill(expected, total / observed.length);
        return chiSquareGoodnessOfFit(observed, expected);
    }

    /**
     * χ² test of independence on an r×c contingency table.
     *
     * @param observed the observed counts; at least 2×2, non-negative, no empty row/column
     * @return the test outcome ({@code df = (r-1)(c-1)}), including the expected table
     */
    public static ChiSquareResult chiSquareIndependence(double[][] observed) {
        int rows = observed.length;
        if (rows < 2 || observed[0].length < 2) {
            throw new IllegalArgumentException("Independence test needs at least a 2x2 table");
        }
        int cols = observed[0].length;
        double[] rowTotals = new double[rows];
        double[] colTotals = new double[cols];
        double total = 0;
        for (int r = 0; r < rows; r++) {
            if (observed[r].length != cols) {
                throw new IllegalArgumentException("Every row must have the same number of columns");
            }
            for (int c = 0; c < cols; c++) {
                double v = observed[r][c];
                if (v < 0) {
                    throw new IllegalArgumentException("Counts must be non-negative");
                }
                rowTotals[r] += v;
                colTotals[c] += v;
                total += v;
            }
        }

        double[][] expected = new double[rows][cols];
        double statistic = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                expected[r][c] = rowTotals[r] * colTotals[c] / total;
                if (!(expected[r][c] > 0)) {
                    throw new IllegalArgumentException(
                            "The table has an empty row or column - the test is undefined");
                }
                double diff = observed[r][c] - expected[r][c];
                statistic += diff * diff / expected[r][c];
            }
        }
        long df = (long) (rows - 1) * (cols - 1);
        return new ChiSquareResult(statistic, df, chiSquareUpperTailP(statistic, df), expected);
    }

    /**
     * Runs Welch's two-sample t-test on two summarised datasets.
     *
     * @param a summary of the first sample (needs {@code count >= 2})
     * @param b summary of the second sample (needs {@code count >= 2})
     * @return the test outcome
     * @throws IllegalArgumentException if either sample has fewer than two values
     *                                  or both samples have zero variance
     */
    public static TTestResult welchTTest(StatisticsResult a, StatisticsResult b) {
        if (a.count() < 2 || b.count() < 2) {
            throw new IllegalArgumentException("Welch's t-test needs at least 2 values in each dataset");
        }
        double na = a.count();
        double nb = b.count();
        double va = a.sampleVariance();
        double vb = b.sampleVariance();

        double meanDiff = a.mean() - b.mean();
        double seSquared = va / na + vb / nb;
        if (seSquared == 0) {
            throw new IllegalArgumentException(
                    "Welch's t-test is undefined when both datasets have zero variance");
        }

        double t = meanDiff / Math.sqrt(seSquared);
        double df = seSquared * seSquared
                / ((va / na) * (va / na) / (na - 1) + (vb / nb) * (vb / nb) / (nb - 1));

        double pooledVariance = ((na - 1) * va + (nb - 1) * vb) / (na + nb - 2);
        double cohensD = (pooledVariance > 0) ? meanDiff / Math.sqrt(pooledVariance) : Double.NaN;

        return new TTestResult(meanDiff, cohensD, t, df, twoSidedP(t, df));
    }

    /**
     * Two-sided p-value of the Student t distribution:
     * {@code P(|T| >= |t|) = I_x(df/2, 1/2)} with {@code x = df / (df + t²)}.
     */
    static double twoSidedP(double t, double df) {
        return regularizedIncompleteBeta(df / 2.0, 0.5, df / (df + t * t));
    }

    /**
     * Upper-tail p-value of the F distribution:
     * {@code P(F' >= f) = I_x(df2/2, df1/2)} with {@code x = df2 / (df2 + df1·f)}.
     */
    static double fUpperTailP(double f, double df1, double df2) {
        if (f <= 0) {
            return 1;
        }
        return regularizedIncompleteBeta(df2 / 2.0, df1 / 2.0, df2 / (df2 + df1 * f));
    }

    /** Upper-tail p-value of the χ² distribution: {@code 1 - P(df/2, x/2)}. */
    static double chiSquareUpperTailP(double x, double df) {
        if (x <= 0) {
            return 1;
        }
        return 1 - regularizedGammaP(df / 2.0, x / 2.0);
    }

    /**
     * Regularized lower incomplete gamma function {@code P(a, x)}, via the series
     * expansion for {@code x < a + 1} and the Lentz continued fraction otherwise
     * (the classic split — each converges fast on its own side).
     */
    static double regularizedGammaP(double a, double x) {
        if (x <= 0) {
            return 0;
        }
        if (x < a + 1) {
            // Series: P(a,x) = e^{-x} x^a / Γ(a) · Σ x^n / (a(a+1)...(a+n))
            double sum = 1.0 / a;
            double term = sum;
            double ap = a;
            for (int n = 1; n <= 500; n++) {
                ap += 1;
                term *= x / ap;
                sum += term;
                if (Math.abs(term) < Math.abs(sum) * 1e-15) {
                    break;
                }
            }
            return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
        }
        // Continued fraction for Q(a,x); P = 1 - Q.
        final double tiny = 1e-300;
        double b = x + 1 - a;
        double c = 1 / tiny;
        double d = 1 / b;
        double h = d;
        for (int i = 1; i <= 500; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = b + an / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < 1e-15) {
                break;
            }
        }
        double q = h * Math.exp(-x + a * Math.log(x) - logGamma(a));
        return 1 - q;
    }

    /**
     * Regularized incomplete beta function {@code I_x(a, b)}, evaluated with the
     * continued-fraction expansion (converges fast on the side chosen below).
     */
    static double regularizedIncompleteBeta(double a, double b, double x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaContinuedFraction(a, b, x) / a;
        }
        return 1 - front * betaContinuedFraction(b, a, 1 - x) / b;
    }

    /** Modified Lentz evaluation of the incomplete-beta continued fraction. */
    private static double betaContinuedFraction(double a, double b, double x) {
        final double tiny = 1e-30;
        final double eps = 1e-14;

        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < tiny) {
            d = tiny;
        }
        d = 1 / d;
        double h = d;

        for (int m = 1; m <= 300; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1 / d;
            h *= d * c;

            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < eps) {
                break;
            }
        }
        return h;
    }

    /** Lanczos approximation of {@code ln Γ(x)} (g = 7, 9 coefficients). */
    static double logGamma(double x) {
        final double[] g = {
                0.99999999999980993, 676.5203681218851, -1259.1392167224028,
                771.32342877765313, -176.61502916214059, 12.507343278686905,
                -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (x < 0.5) {
            // Reflection formula keeps the approximation accurate for small x.
            return Math.log(Math.PI / Math.sin(Math.PI * x)) - logGamma(1 - x);
        }
        x -= 1;
        double sum = g[0];
        for (int i = 1; i < g.length; i++) {
            sum += g[i] / (x + i);
        }
        double t = x + 7.5;
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(sum);
    }
}
