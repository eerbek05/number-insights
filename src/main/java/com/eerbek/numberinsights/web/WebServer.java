package com.eerbek.numberinsights.web;

import com.eerbek.numberinsights.io.DataLoader;
import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.report.ReportFormatter;
import com.eerbek.numberinsights.stats.Bivariate;
import com.eerbek.numberinsights.stats.DescriptiveStatistics;
import com.eerbek.numberinsights.stats.Inference;
import com.eerbek.numberinsights.stats.StatisticsResult;
import com.eerbek.numberinsights.viz.Histogram;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * The embedded web server exposing the analysis pipeline to a browser UI.
 *
 * <p>Built on the JDK's own {@link com.sun.net.httpserver.HttpServer} with a
 * virtual-thread-per-request executor, so the project keeps its zero-dependency
 * footprint — no servlet container, no web framework. Hardened for real use:
 * request bodies are size-capped (413 beyond {@value #MAX_BODY_BYTES} bytes),
 * datasets are value-count-capped, request/response times are bounded, every
 * response carries {@code X-Content-Type-Options} (plus a strict CSP on the
 * page itself), and each request is access-logged through
 * {@link System.Logger}.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /} — the single-page frontend bundled on the classpath;</li>
 *   <li>{@code GET /health} — liveness probe with the application version;</li>
 *   <li>{@code POST /api/analyze[?bins=N&amp;mu=M]} — raw numeric text in, full
 *       analysis out (statistics, histogram bins, percentile grid, outlier
 *       values; a one-sample t-test against {@code mu} when given);</li>
 *   <li>{@code POST /api/compare[?bins=N&amp;paired=1]} — two to four datasets
 *       separated by {@code ---} lines; two get Welch's t-test (plus paired
 *       t-test, correlation and regression when {@code paired}), three or four
 *       get one-way ANOVA — histogram bins aligned over the combined range;</li>
 *   <li>{@code POST /api/chisquare} — rows of category counts; one row runs a
 *       goodness-of-fit test against uniform, two or more run the independence
 *       test on the contingency table.</li>
 * </ul>
 */
public final class WebServer {

    /** Hard cap on request body size; larger requests get 413. */
    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    /** Hard cap on the number of values in one dataset. */
    public static final int MAX_VALUES = 200_000;

    private static final int MIN_BINS = 2;
    private static final int MAX_BINS = 60;
    private static final int DEFAULT_BINS = 12;
    private static final int MAX_DATASETS = 4;
    private static final int MAX_LISTED_OUTLIERS = 100;
    private static final int MAX_LISTED_PAIRS = 2000;

    private static final System.Logger LOG = System.getLogger("numberinsights.web");

    /** Line of three or more dashes separating datasets in a multi-set body. */
    private static final Pattern DATASET_SEPARATOR = Pattern.compile("(?m)^\\s*-{3,}\\s*$");

    static {
        // Bound slow clients; these are read when the HttpServer implementation loads.
        System.setProperty("sun.net.httpserver.maxReqTime", "30");
        System.setProperty("sun.net.httpserver.maxRspTime", "120");
    }

    private final HttpServer server;
    private final ExecutorService executor;

    /**
     * Creates (but does not start) a server bound to the given port.
     *
     * @param port the TCP port, or {@code 0} to let the OS pick a free one
     * @throws IOException if the port cannot be bound
     */
    public WebServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", logged(this::handleIndex));
        server.createContext("/health", logged(this::handleHealth));
        server.createContext("/api/analyze", logged(this::handleAnalyze));
        server.createContext("/api/compare", logged(this::handleCompare));
        server.createContext("/api/chisquare", logged(this::handleChiSquare));
    }

    /** Starts serving requests; each request runs on its own virtual thread. */
    public void start() {
        server.start();
        LOG.log(Level.INFO, "Listening on port {0}", String.valueOf(port()));
    }

    /** Stops the server, allowing in-flight exchanges up to a second to finish. */
    public void stop() {
        server.stop(1);
        executor.shutdown();
        LOG.log(Level.INFO, "Server stopped");
    }

    /** @return the actual port the server is listening on */
    public int port() {
        return server.getAddress().getPort();
    }

    /** @return the application version from the JAR manifest, or {@code dev} */
    public static String version() {
        String v = WebServer.class.getPackage().getImplementationVersion();
        return (v != null) ? v : "dev";
    }

    // ------------------------------------------------------------------ //

    /** Wraps a handler with access logging and a last-resort error boundary. */
    private static HttpHandler logged(HttpHandler handler) {
        return exchange -> {
            long start = System.nanoTime();
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                LOG.log(Level.ERROR, "Unhandled error for "
                        + exchange.getRequestURI().getPath(), e);
                if (exchange.getResponseCode() == -1) {
                    respond(exchange, 500, "application/json",
                            errorJson("Internal server error"));
                }
            } finally {
                long millis = (System.nanoTime() - start) / 1_000_000;
                LOG.log(Level.INFO, "{0} {1} -> {2} ({3} ms)",
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        String.valueOf(exchange.getResponseCode()),
                        String.valueOf(millis));
            }
        };
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "text/plain", "Not Found");
            return;
        }
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                        + "connect-src 'self'; img-src data:");
        respond(exchange, 200, "text/html; charset=utf-8", loadFrontend());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "application/json",
                "{\"status\": \"ok\", \"version\": \"" + version() + "\"}");
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        String body = readBodyOrRespond(exchange);
        if (body == null) {
            return;
        }
        try {
            Dataset dataset = parseDataset(body);
            int bins = clampBins(intParam(exchange, "bins", DEFAULT_BINS), dataset.size());
            Double mu = doubleParam(exchange, "mu");
            respond(exchange, 200, "application/json", analysisJson(dataset, bins, mu));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    private void handleCompare(HttpExchange exchange) throws IOException {
        String body = readBodyOrRespond(exchange);
        if (body == null) {
            return;
        }
        try {
            String[] parts = DATASET_SEPARATOR.split(body);
            List<Dataset> datasets = new ArrayList<>();
            for (String part : parts) {
                if (!part.isBlank()) {
                    datasets.add(parseDataset(part));
                }
            }
            if (datasets.size() < 2) {
                respond(exchange, 400, "application/json",
                        errorJson("Provide at least two datasets separated by a line of dashes (---)"));
                return;
            }
            if (datasets.size() > MAX_DATASETS) {
                respond(exchange, 400, "application/json",
                        errorJson("At most " + MAX_DATASETS + " datasets can be compared"));
                return;
            }
            int largest = datasets.stream().mapToInt(Dataset::size).max().orElse(2);
            int bins = clampBins(intParam(exchange, "bins", DEFAULT_BINS), largest);
            boolean paired = flagParam(exchange, "paired");

            if (datasets.size() == 2) {
                respond(exchange, 200, "application/json",
                        comparisonJson(datasets.get(0), datasets.get(1), bins, paired));
            } else {
                respond(exchange, 200, "application/json", anovaJson(datasets, bins));
            }
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    private void handleChiSquare(HttpExchange exchange) throws IOException {
        String body = readBodyOrRespond(exchange);
        if (body == null) {
            return;
        }
        try {
            List<double[]> rows = parseCountRows(body);
            Inference.ChiSquareResult result;
            String mode;
            if (rows.size() == 1) {
                result = Inference.chiSquareGoodnessOfFit(rows.get(0));
                mode = "goodness-of-fit";
            } else {
                result = Inference.chiSquareIndependence(rows.toArray(double[][]::new));
                mode = "independence";
            }
            StringBuilder expected = new StringBuilder("[");
            for (int r = 0; r < result.expected().length; r++) {
                if (r > 0) {
                    expected.append(", ");
                }
                expected.append(doubleArrayJson(result.expected()[r]));
            }
            expected.append("]");
            respond(exchange, 200, "application/json", String.format(Locale.ROOT,
                    "{\"mode\": \"%s\", \"statistic\": %s, \"df\": %d, \"pValue\": %s, \"expected\": %s}",
                    mode, jsonNum(result.statistic()), result.df(), jsonNum(result.pValue()),
                    expected));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ //

    /** Builds the single-dataset payload; visible for tests. */
    static String analysisJson(Dataset dataset, int bins, Double mu) {
        DescriptiveStatistics ds = new DescriptiveStatistics(dataset);
        StatisticsResult stats = ds.summary();
        String base = datasetJson(dataset, ds, stats, Histogram.computeBins(dataset, bins));
        if (mu == null) {
            return base;
        }
        String tTest = "null";
        try {
            tTest = tTestJson(Inference.oneSampleTTest(stats, mu));
        } catch (IllegalArgumentException e) {
            // Constant data → no test; the UI shows stats without it.
        }
        // Splice the extra keys into the dataset object before its closing brace.
        return base.substring(0, base.length() - 1)
                + ",\n\"oneSampleT\": " + tTest
                + ",\n\"mu\": " + jsonNum(mu) + "}";
    }

    /** Two-dataset payload: aligned bins, Welch, and paired extras on request. */
    static String comparisonJson(Dataset a, Dataset b, int bins, boolean paired) {
        DescriptiveStatistics dsA = new DescriptiveStatistics(a);
        DescriptiveStatistics dsB = new DescriptiveStatistics(b);
        StatisticsResult statsA = dsA.summary();
        StatisticsResult statsB = dsB.summary();

        // A shared range gives both histograms identical bin edges.
        double low = Math.min(statsA.min(), statsB.min());
        double high = Math.max(statsA.max(), statsB.max());

        String welch = "null";
        if (statsA.count() >= 2 && statsB.count() >= 2) {
            try {
                welch = tTestJson(Inference.welchTTest(statsA, statsB));
            } catch (IllegalArgumentException e) {
                // Both variances zero → no test; the UI shows stats without it.
            }
        }

        StringBuilder sb = new StringBuilder("{\n");
        sb.append("\"a\": ").append(datasetJson(a, dsA, statsA,
                Histogram.computeBins(a, bins, low, high))).append(",\n");
        sb.append("\"b\": ").append(datasetJson(b, dsB, statsB,
                Histogram.computeBins(b, bins, low, high))).append(",\n");
        sb.append("\"comparison\": ").append(welch);

        if (paired) {
            sb.append(",\n\"paired\": ").append(pairedJson(a, b));
        }
        return sb.append("\n}").toString();
    }

    /** Paired-mode block: paired t-test, correlation/regression, raw pairs. */
    private static String pairedJson(Dataset a, Dataset b) {
        if (a.size() != b.size()) {
            return "{\"error\": \"Paired analysis needs datasets of equal length (got "
                    + a.size() + " and " + b.size() + ")\"}";
        }
        StringBuilder sb = new StringBuilder("{");

        String tTest = "null";
        try {
            tTest = tTestJson(Inference.pairedTTest(a, b));
        } catch (IllegalArgumentException e) {
            // Constant differences → no test.
        }
        sb.append("\"tTest\": ").append(tTest);

        String bivariate = "null";
        try {
            Bivariate.BivariateResult r = Bivariate.analyze(a, b);
            bivariate = String.format(Locale.ROOT,
                    "{\"n\": %d, \"pearsonR\": %s, \"pearsonP\": %s, \"spearmanRho\": %s, "
                            + "\"slope\": %s, \"intercept\": %s, \"rSquared\": %s}",
                    r.n(), jsonNum(r.pearsonR()), jsonNum(r.pearsonP()), jsonNum(r.spearmanRho()),
                    jsonNum(r.slope()), jsonNum(r.intercept()), jsonNum(r.rSquared()));
        } catch (IllegalArgumentException e) {
            // Constant variable → no correlation.
        }
        sb.append(", \"bivariate\": ").append(bivariate);

        sb.append(", \"pairs\": [");
        int limit = Math.min(a.size(), MAX_LISTED_PAIRS);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('[').append(jsonNum(a.values().get(i))).append(", ")
                    .append(jsonNum(b.values().get(i))).append(']');
        }
        sb.append("], \"pairsTruncated\": ").append(a.size() > MAX_LISTED_PAIRS);
        return sb.append("}").toString();
    }

    /** Three-or-more-dataset payload: per-set analyses plus one-way ANOVA. */
    private static String anovaJson(List<Dataset> datasets, int bins) {
        List<DescriptiveStatistics> descriptives = new ArrayList<>();
        List<StatisticsResult> summaries = new ArrayList<>();
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (Dataset d : datasets) {
            DescriptiveStatistics ds = new DescriptiveStatistics(d);
            StatisticsResult s = ds.summary();
            descriptives.add(ds);
            summaries.add(s);
            low = Math.min(low, s.min());
            high = Math.max(high, s.max());
        }

        StringBuilder sb = new StringBuilder("{\n\"sets\": [");
        for (int i = 0; i < datasets.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append(datasetJson(datasets.get(i), descriptives.get(i), summaries.get(i),
                    Histogram.computeBins(datasets.get(i), bins, low, high)));
        }
        sb.append("],\n\"anova\": ");

        try {
            Inference.AnovaResult r = Inference.oneWayAnova(summaries);
            StringBuilder means = new StringBuilder("[");
            for (int i = 0; i < r.groupMeans().size(); i++) {
                if (i > 0) {
                    means.append(", ");
                }
                means.append(jsonNum(r.groupMeans().get(i)));
            }
            means.append("]");
            sb.append(String.format(Locale.ROOT,
                    "{\"fStatistic\": %s, \"dfBetween\": %d, \"dfWithin\": %d, \"pValue\": %s, "
                            + "\"grandMean\": %s, \"groupMeans\": %s}",
                    jsonNum(r.fStatistic()), r.dfBetween(), r.dfWithin(), jsonNum(r.pValue()),
                    jsonNum(r.grandMean()), means));
        } catch (IllegalArgumentException e) {
            sb.append("null");
        }
        return sb.append("\n}").toString();
    }

    /** One dataset's full block: stats, bins, percentile grid, outlier values. */
    private static String datasetJson(Dataset dataset, DescriptiveStatistics ds,
                                      StatisticsResult stats, List<Histogram.Bin> bins) {
        String statsJson = new ReportFormatter(ReportFormatter.Format.JSON).format(stats);

        double[] percentiles = new double[101];
        for (int p = 0; p <= 100; p++) {
            percentiles[p] = ds.percentile(p);
        }

        List<Double> outliers = ds.outliers();
        StringBuilder outlierJson = new StringBuilder("[");
        int limit = Math.min(outliers.size(), MAX_LISTED_OUTLIERS);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                outlierJson.append(", ");
            }
            outlierJson.append(jsonNum(outliers.get(i)));
        }
        outlierJson.append("]");

        return "{\"stats\": " + statsJson
                + ",\n\"histogram\": " + binsJson(bins)
                + ",\n\"percentiles\": " + doubleArrayJson(percentiles)
                + ",\n\"outliers\": " + outlierJson
                + ", \"outliersTruncated\": " + (outliers.size() > MAX_LISTED_OUTLIERS) + "}";
    }

    private static String tTestJson(Inference.TTestResult t) {
        return String.format(Locale.ROOT,
                "{\"meanDifference\": %s, \"cohensD\": %s, \"tStatistic\": %s, "
                        + "\"degreesOfFreedom\": %s, \"pValue\": %s}",
                jsonNum(t.meanDifference()), jsonNum(t.cohensD()),
                jsonNum(t.tStatistic()), jsonNum(t.degreesOfFreedom()), jsonNum(t.pValue()));
    }

    private static String binsJson(List<Histogram.Bin> computed) {
        StringBuilder histogram = new StringBuilder("[");
        for (int i = 0; i < computed.size(); i++) {
            Histogram.Bin bin = computed.get(i);
            if (i > 0) {
                histogram.append(", ");
            }
            histogram.append(String.format(Locale.ROOT,
                    "{\"low\": %s, \"high\": %s, \"count\": %d}",
                    jsonNum(bin.low()), jsonNum(bin.high()), bin.count()));
        }
        return histogram.append("]").toString();
    }

    private static String doubleArrayJson(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(jsonNum(values[i]));
        }
        return sb.append("]").toString();
    }

    // ------------------------------------------------------------------ //

    /**
     * Reads a POST body up to {@link #MAX_BODY_BYTES}. Responds itself (405/413)
     * and returns {@code null} when the caller should stop.
     */
    private static String readBodyOrRespond(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json",
                    errorJson("Use POST with the raw data as the request body"));
            return null;
        }
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > MAX_BODY_BYTES) {
                    respond(exchange, 413, "application/json", errorJson(
                            "Request body exceeds the " + (MAX_BODY_BYTES / (1024 * 1024))
                                    + " MB limit"));
                    return null;
                }
                buffer.write(chunk, 0, n);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static Dataset parseDataset(String text) {
        Dataset dataset = DataLoader.fromLines(text.lines().toList());
        if (dataset.isEmpty()) {
            throw new IllegalArgumentException("No numeric data found in the input");
        }
        if (dataset.size() > MAX_VALUES) {
            throw new IllegalArgumentException(
                    "Dataset exceeds the " + MAX_VALUES + "-value limit");
        }
        return dataset;
    }

    /** Parses rows of whitespace/comma-separated non-negative counts. */
    private static List<double[]> parseCountRows(String body) {
        List<double[]> rows = new ArrayList<>();
        int lineNumber = 0;
        for (String line : body.lines().toList()) {
            lineNumber++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("[,;\\t ]+");
            double[] row = new double[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                try {
                    row[i] = Double.parseDouble(tokens[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid count '" + tokens[i] + "' on line " + lineNumber, e);
                }
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No counts found in the input");
        }
        return rows;
    }

    private static int intParam(HttpExchange exchange, String name, int fallback) {
        String raw = queryParam(exchange, name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static Double doubleParam(HttpExchange exchange, String name) {
        String raw = queryParam(exchange, name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException("not finite");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private static boolean flagParam(HttpExchange exchange, String name) {
        String raw = queryParam(exchange, name);
        return "1".equals(raw) || "true".equalsIgnoreCase(raw);
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** Keeps the bin count sensible for the dataset and the UI. */
    private static int clampBins(int requested, int datasetSize) {
        int upper = Math.min(MAX_BINS, Math.max(MIN_BINS, datasetSize));
        return Math.max(MIN_BINS, Math.min(upper, requested));
    }

    private static String loadFrontend() {
        try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                throw new IllegalStateException("Frontend resource /web/index.html is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private static String jsonNum(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        if (contentType.startsWith("application/json")) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
