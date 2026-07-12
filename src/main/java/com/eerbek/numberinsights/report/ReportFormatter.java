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
 * third-party dependencies.</p>
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
        appendRow(sb, "Q1 (25%)", num(s.q1()));
        appendRow(sb, "Q3 (75%)", num(s.q3()));
        appendRow(sb, "IQR", num(s.iqr()));
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
                + "  \"mean\": " + num(s.mean()) + ",\n"
                + "  \"median\": " + num(s.median()) + ",\n"
                + "  \"modes\": " + modes + ",\n"
                + "  \"variance\": " + num(s.variance()) + ",\n"
                + "  \"stdDev\": " + num(s.stdDev()) + ",\n"
                + "  \"q1\": " + num(s.q1()) + ",\n"
                + "  \"q3\": " + num(s.q3()) + ",\n"
                + "  \"iqr\": " + num(s.iqr()) + "\n"
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
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
