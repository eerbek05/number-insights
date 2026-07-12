package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.web.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the embedded web server: a real {@link WebServer} is
 * bound to an ephemeral port and exercised over HTTP with the JDK client.
 */
class WebServerTest {

    private static WebServer server;
    private static HttpClient client;
    private static String base;

    @BeforeAll
    static void startServer() throws Exception {
        server = new WebServer(0); // port 0 → OS picks a free one
        server.start();
        client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
        base = "http://localhost:" + server.port();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void servesTheFrontendPage() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(res.body().contains("Number Insights"));
    }

    @Test
    void unknownPathIs404() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/nope")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, res.statusCode());
    }

    @Test
    void analyzesPostedData() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze?bins=4"))
                        .POST(HttpRequest.BodyPublishers.ofString("1\n2\n3\n4\n5\n6\n7\n8"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"count\": 8"));
        assertTrue(res.body().contains("\"mean\": 4.5000"));
        assertTrue(res.body().contains("\"histogram\": ["));
        // 4 bins requested → 4 bin objects
        assertEquals(4, res.body().split("\\{\"low\"", -1).length - 1);
    }

    @Test
    void acceptsCommaSeparatedInput() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze"))
                        .POST(HttpRequest.BodyPublishers.ofString("10, 20, 30\n40; 50"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"count\": 5"));
    }

    @Test
    void invalidTokenReturns400WithErrorMessage() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze"))
                        .POST(HttpRequest.BodyPublishers.ofString("1\n2\nbanana"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("banana"));
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze"))
                        .POST(HttpRequest.BodyPublishers.ofString("  \n# only a comment\n"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("error"));
    }

    @Test
    void getOnAnalyzeEndpointIsRejected() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, res.statusCode());
    }

    @Test
    void analyzeResponseIncludesExtendedMeasures() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/analyze"))
                        .POST(HttpRequest.BodyPublishers.ofString("1\n2\n3\n4\n5"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"skewness\": 0.0000"));
        assertTrue(res.body().contains("\"excessKurtosis\": -1.2000"));
        assertTrue(res.body().contains("\"standardError\":"));
        assertTrue(res.body().contains("\"ci95Low\":"));
        assertTrue(res.body().contains("\"outlierCount\": 0"));
    }

    @Test
    void comparesTwoDatasets() throws Exception {
        String body = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n---\n11\n12\n13\n14\n15\n16\n17\n18\n19\n20";
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/compare?bins=5"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"a\": {"));
        assertTrue(res.body().contains("\"b\": {"));
        assertTrue(res.body().contains("\"meanDifference\": -10.000000"));
        assertTrue(res.body().contains("\"degreesOfFreedom\": 18.000000"));
        assertTrue(res.body().contains("\"pValue\":"));
        // Aligned bins: both histograms span the combined range 1..20,
        // so both first bins start at 1.0000
        assertEquals(2, res.body().split("\\{\"low\": 1\\.0000", -1).length - 1);
    }

    @Test
    void compareWithoutSeparatorIs400() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/compare"))
                        .POST(HttpRequest.BodyPublishers.ofString("1\n2\n3"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("---"));
    }

    @Test
    void compareWithEmptySecondDatasetIs400() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/compare"))
                        .POST(HttpRequest.BodyPublishers.ofString("1\n2\n3\n---\n# nothing"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }
}
