package com.eerbek.numberinsights;

import com.eerbek.numberinsights.cli.CliOptions;
import com.eerbek.numberinsights.io.DataLoader;
import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.report.ReportFormatter;
import com.eerbek.numberinsights.stats.DescriptiveStatistics;
import com.eerbek.numberinsights.viz.Histogram;

/**
 * Command-line entry point for Number Insights.
 *
 * <p>Wires the pipeline together: parse options → load the {@link Dataset} →
 * run the requested views (statistics and/or histogram) → print the report.
 * Keeping {@code main} thin (only orchestration and error handling) means every
 * piece of real logic lives in an independently testable class.</p>
 */
public final class Main {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println(CliOptions.usage());
            System.exit(2);
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) {
        CliOptions options = CliOptions.parse(args);

        if (options.isHelpRequested()) {
            System.out.println(CliOptions.usage());
            return;
        }
        if (options.inputFile() == null) {
            throw new IllegalArgumentException("No input file provided");
        }

        Dataset dataset = DataLoader.fromFile(options.inputFile());
        if (dataset.isEmpty()) {
            throw new IllegalArgumentException("The input file contains no numeric data");
        }

        System.out.println("Loaded " + dataset.size() + " values from " + options.inputFile());
        System.out.println();

        if (options.showStats()) {
            var stats = new DescriptiveStatistics(dataset).summary();
            System.out.println(new ReportFormatter(options.format()).format(stats));
            System.out.println();
        }

        if (options.showHistogram()) {
            System.out.println("Distribution");
            System.out.println("============");
            System.out.print(new Histogram().render(dataset));
        }
    }

    private Main() {
        // Entry-point holder; not instantiable.
    }
}
