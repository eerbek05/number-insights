package com.eerbek.numberinsights.web;

import com.eerbek.numberinsights.io.DataLoader;
import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.report.ReportFormatter;
import com.eerbek.numberinsights.stats.DescriptiveStatistics;
import com.eerbek.numberinsights.viz.Histogram;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * A tiny embedded web server exposing the analysis pipeline to a browser UI.
 *
 * <p>Built on the JDK's own {@link com.sun.net.httpserver.HttpServer}, so the
 * project keeps its zero-dependency footprint — no servlet container, no web
 * framework. Two endpoints:</p>
 *
 * <ul>
 *   <li>{@code GET /} — serves the single-page frontend bundled on the
 *       classpath ({@code /web/index.html});</li>
 *   <li>{@code POST /api/analyze[?bins=N]} — accepts raw numeric text
 *       (the same formats {@link DataLoader} understands) and responds with
 *       descriptive statistics plus histogram bins as JSON.</li>
 * </ul>
 */
public final class WebServer {

    private static final int MIN_BINS = 2;
    private static final int MAX_BINS = 60;
    private static final int DEFAULT_BINS = 12;

    private final HttpServer server;

    /**
     * Creates (but does not start) a server bound to the given port.
     *
     * @param port the TCP port, or {@code 0} to let the OS pick a free one
     * @throws IOException if the port cannot be bound
     */
    public WebServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/analyze", this::handleAnalyze);
    }

    /** Starts serving requests on a background thread. */
    public void start() {
        server.start();
    }

    /** Stops the server, allowing in-flight exchanges up to a second to finish. */
    public void stop() {
        server.stop(1);
    }

    /** @return the actual port the server is listening on */
    public int port() {
        return server.getAddress().getPort();
    }

    // ------------------------------------------------------------------ //

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "text/plain", "Not Found");
            return;
        }
        respond(exchange, 200, "text/html; charset=utf-8", loadFrontend());
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json", errorJson("Use POST with the raw data as the request body"));
            return;
        }

        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        try {
            Dataset dataset = DataLoader.fromLines(body.lines().toList());
            if (dataset.isEmpty()) {
                respond(exchange, 400, "application/json", errorJson("No numeric data found in the input"));
                return;
            }
            int bins = clampBins(binsParam(exchange), dataset);
            respond(exchange, 200, "application/json", analysisJson(dataset, bins));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    /** Builds the full analysis payload: statistics + histogram bins. */
    static String analysisJson(Dataset dataset, int bins) {
        String stats = new ReportFormatter(ReportFormatter.Format.JSON)
                .format(new DescriptiveStatistics(dataset).summary());

        StringBuilder histogram = new StringBuilder("[");
        List<Histogram.Bin> computed = Histogram.computeBins(dataset, bins);
        for (int i = 0; i < computed.size(); i++) {
            Histogram.Bin bin = computed.get(i);
            if (i > 0) {
                histogram.append(", ");
            }
            histogram.append(String.format(Locale.ROOT,
                    "{\"low\": %.4f, \"high\": %.4f, \"count\": %d}",
                    bin.low(), bin.high(), bin.count()));
        }
        histogram.append("]");

        return "{\n\"stats\": " + stats + ",\n\"histogram\": " + histogram + "\n}";
    }

    private static int binsParam(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return DEFAULT_BINS;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "bins".equals(kv[0])) {
                try {
                    return Integer.parseInt(URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("bins must be an integer");
                }
            }
        }
        return DEFAULT_BINS;
    }

    /** Keeps the bin count sensible for the dataset and the UI. */
    private static int clampBins(int requested, Dataset dataset) {
        int upper = Math.min(MAX_BINS, Math.max(MIN_BINS, dataset.size()));
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

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
