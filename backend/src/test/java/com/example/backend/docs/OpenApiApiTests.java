package com.example.backend.docs;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import com.example.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiApiTests extends PostgresIntegrationTest {
    @Autowired Environment environment;
    @Autowired ObjectMapper json;

    @Test void anonymousCanReadDocsWithoutWeakeningAuthenticationOrCsrf() throws Exception {
        try (var client = client()) {
            var response = get(client, "/v3/api-docs");
            assertEquals(200, response.statusCode(), response.body());
            var spec = json.readTree(response.body());
            assertEquals("FlashMind API", spec.get("info").get("title").asText());
            assertEquals("/", spec.get("servers").get(0).get("url").asText());
            var paths = spec.get("paths");
            for (var path : new String[]{"/api/auth/login", "/api/auth/logout", "/api/decks", "/api/decks/{deckId}/flashcards",
                    "/api/decks/{deckId}/study-sessions", "/api/statistics/me", "/api/admin/users/{userId}", "/api/admin/decks/{deckId}"}) {
                assertTrue(paths.has(path), path);
            }
            assertFalse(paths.has("/error"));
            assertEquals(200, get(client, "/swagger-ui/index.html").statusCode());
            assertEquals(200, get(client, "/swagger-ui/swagger-ui-bundle.js").statusCode());
            assertEquals(200, get(client, "/v3/api-docs/swagger-config").statusCode());
            assertEquals(401, get(client, "/api/auth/me").statusCode());
            assertEquals(401, get(client, "/api/admin/access").statusCode());
            var login = client.send(HttpRequest.newBuilder(endpoint("/api/auth/login"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", "unknown", "password", "ExampleOnly123!"))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, login.statusCode());
        }
    }

    @Test void documentsRealStatusesSchemasSessionAndCsrfRequirements() throws Exception {
        try (var client = client()) {
            var spec = json.readTree(get(client, "/v3/api-docs").body());
            var paths = spec.get("paths");
            var schemes = spec.get("components").get("securitySchemes");
            assertEquals("cookie", schemes.get("SessionCookie").get("in").asText());
            assertEquals("JSESSIONID", schemes.get("SessionCookie").get("name").asText());
            assertEquals("X-CSRF-TOKEN", schemes.get("CsrfToken").get("name").asText());
            assertEquals(0, paths.get("/api/auth/csrf").get("get").get("security").size());
            var login = paths.get("/api/auth/login").get("post");
            assertTrue(login.get("security").get(0).has("CsrfToken"));
            assertFalse(login.get("security").get(0).has("SessionCookie"));
            assertTrue(paths.get("/api/admin/decks/{deckId}").get("put").get("security").get(0).has("SessionCookie"));
            assertTrue(paths.get("/api/admin/decks/{deckId}").get("put").get("security").get(0).has("CsrfToken"));
            assertTrue(paths.get("/api/auth/register").get("post").get("responses").get("201").has("content"));
            assertTrue(paths.get("/api/decks").get("post").get("responses").has("201"));
            assertTrue(paths.get("/api/auth/forgot-password").get("post").get("responses").has("202"));
            var logout = paths.get("/api/auth/logout").get("post");
            assertTrue(logout.get("responses").has("204"));
            assertFalse(logout.get("responses").get("204").has("content"));
            assertTrue(logout.get("security").get(0).has("CsrfToken"));
            assertEquals("#/components/schemas/ApiError", login.get("responses").get("403").get("content").get("application/json").get("schema").get("$ref").asText());
            var schemas = spec.get("components").get("schemas");
            assertTrue(schemas.has("ApiError"));
            assertFalse(schemas.has("SessionUser"));
            assertFalse(schemas.has("CsrfToken"));
            assertFalse(spec.toString().contains("passwordHash"));
        }
    }

    private HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
