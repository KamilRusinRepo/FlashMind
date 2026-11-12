package com.example.backend.support;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.ObjectMapper;

/** Exercise the real session/CSRF handshake instead of bypassing security in REST tests. */
public final class CsrfHttpClient {
    private CsrfHttpClient() { }

    public static HttpResponse<String> post(URI endpoint, String body, ObjectMapper mapper) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
            var csrf = client.send(HttpRequest.newBuilder(endpoint.resolve("/api/auth/csrf"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (csrf.statusCode() != 200) throw new AssertionError("Unable to obtain the test CSRF token.");
            var token = mapper.readTree(csrf.body());
            return client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
