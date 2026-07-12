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
}
