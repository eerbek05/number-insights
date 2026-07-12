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

    /** Default TCP port for {@code --serve} mode. */
    public static final int DEFAULT_PORT = 8080;

    private final Path inputFile;
    private final boolean showStats;
    private final boolean showHistogram;
    private final Format format;
    private final boolean help;
    private final boolean serve;
    private final int port;

    private CliOptions(Path inputFile, boolean showStats, boolean showHistogram,
                       Format format, boolean help, boolean serve, int port) {
        this.inputFile = inputFile;
        this.showStats = showStats;
        this.showHistogram = showHistogram;
        this.format = format;
        this.help = help;
        this.serve = serve;
        this.port = port;
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

    /** @return {@code true} when the embedded web UI should be started */
    public boolean serve() {
        return serve;
    }

    /** @return the TCP port for {@code --serve} mode */
    public int port() {
        return port;
    }

    /**
     * Parses raw command-line arguments.
     *
     * <p>Supported flags:</p>
     * <pre>
     *   &lt;file&gt;            path to the data file (required unless --help or --serve)
     *   --stats           print descriptive statistics (default when no view flag given)
     *   --histogram       print an ASCII histogram
     *   --format &lt;t&gt;       output format for stats: table (default) or json
     *   --serve           start the embedded web UI instead of printing to the terminal
     *   --port &lt;n&gt;         port for --serve mode (default 8080)
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
        boolean serve = false;
        int port = DEFAULT_PORT;
        Format format = Format.TABLE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> help = true;
                case "--stats" -> stats = true;
                case "--histogram" -> histogram = true;
                case "--serve" -> serve = true;
                case "--port" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--port requires a number");
                    }
                    port = parsePort(args[++i]);
                }
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
        if (!stats && !histogram && !help && !serve) {
            stats = true;
        }

        return new CliOptions(inputFile, stats, histogram, format, help, serve, port);
    }

    private static Format parseFormat(String value) {
        try {
            return Format.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format: " + value + " (expected table or json)");
        }
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + value);
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
                  --serve           Start the web UI (paste or upload data in the browser)
                  --port <n>        Port for --serve mode (default 8080)
                  -h, --help        Show this help message

                EXAMPLES:
                  number-insights data.txt
                  number-insights data.csv --stats --histogram
                  number-insights data.txt --format json
                  number-insights --serve --port 9000
                """;
    }
}
