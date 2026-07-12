package com.eerbek.numberinsights.stats;

/**
 * Inferential statistics for comparing two datasets.
 *
 * <p>Implements Welch's unequal-variances t-test — the standard tool for "do
 * these two samples have different means?" when nothing guarantees equal
 * variances — plus Cohen's d as the effect size. The two-sided p-value is
 * computed exactly from the Student t distribution via the regularized
 * incomplete beta function (Lanczos log-gamma + Lentz continued fraction),
 * keeping the project free of third-party math libraries.</p>
 */
public final class Inference {

    /**
     * The outcome of a two-sample comparison.
     *
     * @param meanDifference   {@code meanA - meanB}
     * @param cohensD          effect size using the pooled sample standard deviation
     *                         (conventional read: 0.2 small, 0.5 medium, 0.8 large)
     * @param tStatistic       Welch's t statistic
     * @param degreesOfFreedom Welch–Satterthwaite degrees of freedom
     * @param pValue           two-sided p-value from the Student t distribution
     */
    public record TTestResult(
            double meanDifference,
            double cohensD,
            double tStatistic,
            double degreesOfFreedom,
            double pValue) {
    }

    private Inference() {
        // Utility class; not instantiable.
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
