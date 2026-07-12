package com.eerbek.numberinsights.report;

import com.eerbek.numberinsights.stats.StatisticsResult;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Turns a {@link StatisticsResult} into a human- or machine-readable report.
 *
 * <p>Two formats are supported, selected via {@link Format}: an aligned
 * {@code TABLE} for reading in a terminal, and compact {@code JSON} for piping
 * into other tools. The JSON is emitted by hand to keep the project free of
 * third-party dependencies. Measures that are undefined for the sample
 * (see {@link StatisticsResult}) print as {@code n/a} in tables and
 * {@code null} in JSON.</p>
 */
public final class ReportFormatter {

    /** Output styles supported by {@link #format}. */
    public enum Format {
        TABLE,
        JSON
    }

    private final Format format;

    public ReportFormatter(Format format) {
        this.format = format;
    }

    /**
     * Formats the given statistics in the configured style.
     *
     * @param stats the statistics to render
     * @return a printable report string
     */
    public String format(StatisticsResult stats) {
        return switch (format) {
            case TABLE -> table(stats);
            case JSON -> json(stats);
        };
    }

    private String table(StatisticsResult s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Descriptive Statistics\n");
        sb.append("======================\n");
        appendRow(sb, "Count", String.valueOf(s.count()));
        appendRow(sb, "Sum", String.valueOf(s.sum()));
        appendRow(sb, "Min", String.valueOf(s.min()));
        appendRow(sb, "Max", String.valueOf(s.max()));
        appendRow(sb, "Range", String.valueOf(s.range()));
        appendRow(sb, "Mean", num(s.mean()));
        appendRow(sb, "Median", num(s.median()));
        appendRow(sb, "Mode", modeString(s));
        appendRow(sb, "Variance", num(s.variance()));
        appendRow(sb, "Std Dev", num(s.stdDev()));
        appendRow(sb, "Sample Var", num(s.sampleVariance()));
        appendRow(sb, "Sample Std", num(s.sampleStdDev()));
        appendRow(sb, "Skewness", num(s.skewness()));
        appendRow(sb, "Kurtosis*", num(s.excessKurtosis()));
        appendRow(sb, "CV", num(s.coefficientOfVariation()));
        appendRow(sb, "Std Error", num(s.standardError()));
        appendRow(sb, "95% CI", "[" + num(s.ci95Low()) + ", " + num(s.ci95High()) + "]");
        appendRow(sb, "P5", num(s.p5()));
        appendRow(sb, "Q1 (25%)", num(s.q1()));
        appendRow(sb, "Q3 (75%)", num(s.q3()));
        appendRow(sb, "P95", num(s.p95()));
        appendRow(sb, "IQR", num(s.iqr()));
        appendRow(sb, "Fences", "[" + num(s.lowerFence()) + ", " + num(s.upperFence()) + "]");
        appendRow(sb, "Outliers", String.valueOf(s.outlierCount()));
        sb.append("(*) excess kurtosis: 0 = normal distribution\n");
        return sb.toString();
    }

    private String json(StatisticsResult s) {
        StringJoiner modes = new StringJoiner(", ", "[", "]");
        s.modes().forEach(m -> modes.add(String.valueOf(m)));
        return "{\n"
                + "  \"count\": " + s.count() + ",\n"
                + "  \"sum\": " + s.sum() + ",\n"
                + "  \"min\": " + s.min() + ",\n"
                + "  \"max\": " + s.max() + ",\n"
                + "  \"range\": " + s.range() + ",\n"
                + "  \"mean\": " + jsonNum(s.mean()) + ",\n"
                + "  \"median\": " + jsonNum(s.median()) + ",\n"
                + "  \"modes\": " + modes + ",\n"
                + "  \"variance\": " + jsonNum(s.variance()) + ",\n"
                + "  \"stdDev\": " + jsonNum(s.stdDev()) + ",\n"
                + "  \"sampleVariance\": " + jsonNum(s.sampleVariance()) + ",\n"
                + "  \"sampleStdDev\": " + jsonNum(s.sampleStdDev()) + ",\n"
                + "  \"skewness\": " + jsonNum(s.skewness()) + ",\n"
                + "  \"excessKurtosis\": " + jsonNum(s.excessKurtosis()) + ",\n"
                + "  \"coefficientOfVariation\": " + jsonNum(s.coefficientOfVariation()) + ",\n"
                + "  \"standardError\": " + jsonNum(s.standardError()) + ",\n"
                + "  \"ci95Low\": " + jsonNum(s.ci95Low()) + ",\n"
                + "  \"ci95High\": " + jsonNum(s.ci95High()) + ",\n"
                + "  \"p5\": " + jsonNum(s.p5()) + ",\n"
                + "  \"q1\": " + jsonNum(s.q1()) + ",\n"
                + "  \"q3\": " + jsonNum(s.q3()) + ",\n"
                + "  \"p95\": " + jsonNum(s.p95()) + ",\n"
                + "  \"iqr\": " + jsonNum(s.iqr()) + ",\n"
                + "  \"lowerFence\": " + jsonNum(s.lowerFence()) + ",\n"
                + "  \"upperFence\": " + jsonNum(s.upperFence()) + ",\n"
                + "  \"outlierCount\": " + s.outlierCount() + "\n"
                + "}";
    }

    private static void appendRow(StringBuilder sb, String label, String value) {
        sb.append(String.format(Locale.ROOT, "%-12s : %s%n", label, value));
    }

    private static String modeString(StatisticsResult s) {
        StringJoiner joiner = new StringJoiner(", ");
        s.modes().forEach(m -> joiner.add(String.valueOf(m)));
        return joiner.toString();
    }

    private static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** JSON has no NaN/Infinity literals — undefined measures become {@code null}. */
    private static String jsonNum(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
