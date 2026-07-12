package com.eerbek.numberinsights.cli;

import com.eerbek.numberinsights.report.ReportFormatter.Format;

import java.nio.file.Path;

/**
 * Parsed command-line options for the application.
 *
 * <p>Parsing is deliberately hand-rolled (no CLI framework) so the project stays
 * dependency-free and the argument handling is easy to follow and unit-test.</p>
 */
public final class CliOptions {

    private final Path inputFile;
    private final boolean showStats;
    private final boolean showHistogram;
    private final Format format;
    private final boolean help;

    private CliOptions(Path inputFile, boolean showStats, boolean showHistogram,
                       Format format, boolean help) {
        this.inputFile = inputFile;
        this.showStats = showStats;
        this.showHistogram = showHistogram;
        this.format = format;
        this.help = help;
    }

    public Path inputFile() {
        return inputFile;
    }

    public boolean showStats() {
        return showStats;
    }

    public boolean showHistogram() {
        return showHistogram;
    }

    public Format format() {
        return format;
    }

    public boolean isHelpRequested() {
        return help;
    }

    /**
     * Parses raw command-line arguments.
     *
     * <p>Supported flags:</p>
     * <pre>
     *   &lt;file&gt;            path to the data file (required unless --help)
     *   --stats           print descriptive statistics (default when no view flag given)
     *   --histogram       print an ASCII histogram
     *   --format &lt;t&gt;       output format for stats: table (default) or json
     *   -h, --help        show usage
     * </pre>
     *
     * @param args the raw arguments
     * @return the parsed options
     * @throws IllegalArgumentException if an argument is unknown or malformed
     */
    public static CliOptions parse(String[] args) {
        Path inputFile = null;
        boolean stats = false;
        boolean histogram = false;
        boolean help = false;
        Format format = Format.TABLE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> help = true;
                case "--stats" -> stats = true;
                case "--histogram" -> histogram = true;
                case "--format" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--format requires a value (table|json)");
                    }
                    format = parseFormat(args[++i]);
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    if (inputFile != null) {
                        throw new IllegalArgumentException("Unexpected extra argument: " + arg);
                    }
                    inputFile = Path.of(arg);
                }
            }
        }

        // If the user asked for neither view explicitly, default to stats.
        if (!stats && !histogram && !help) {
            stats = true;
        }

        return new CliOptions(inputFile, stats, histogram, format, help);
    }

    private static Format parseFormat(String value) {
        try {
            return Format.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format: " + value + " (expected table or json)");
        }
    }

    /** @return the multi-line usage/help text */
    public static String usage() {
        return """
                Number Insights - statistical analysis for numeric datasets

                USAGE:
                  number-insights <file> [options]

                OPTIONS:
                  --stats           Print descriptive statistics (default)
                  --histogram       Print an ASCII histogram of the distribution
                  --format <type>   Statistics output format: table (default) or json
                  -h, --help        Show this help message

                EXAMPLES:
                  number-insights data.txt
                  number-insights data.csv --stats --histogram
                  number-insights data.txt --format json
                """;
    }
}
