package com.example.backend.auth;

import com.example.backend.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.backend.role.RoleName;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import com.example.backend.mail.VerificationMailPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationApiTests extends PostgresIntegrationTest {
    @MockitoBean
    private VerificationMailPublisher mailPublisher;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Test
    void registrationCreatesAccountWithoutExposingPasswordAndIgnoresClientRole() throws Exception {
        String username = uniqueUsername();
        String password = "  ExamplePassword123!  ";
        var body = new HashMap<String, Object>();
        body.put("username", "  " + username + "  ");
        body.put("email", "  " + username.toUpperCase() + "@EXAMPLE.COM  ");
        body.put("password", password);
        body.put("roles", new String[]{"ADMIN"});
        body.put("emailVerified", true);

        var response = post(body);
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(201, response.statusCode(), response.body());
        assertEquals(username, json.get("username").asText());
        assertEquals(username + "@example.com", json.get("email").asText());
        assertFalse(json.get("emailVerified").asBoolean());
        assertTrue(json.hasNonNull("id"));
        assertTrue(json.hasNonNull("createdAt"));
        assertFalse(json.has("password"));
        assertFalse(json.has("passwordHash"));
        assertFalse(response.body().contains(password));

        User stored = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertNotEquals(password, stored.getPasswordHash());
        assertTrue(passwordEncoder.matches(password, stored.getPasswordHash()));
        assertNull(stored.getEmailVerifiedAt());
        assertNotNull(stored.getCreatedAt());
        assertEquals(1, stored.getRoles().size());
        assertEquals(RoleName.USER, stored.getRoles().iterator().next().getName());
    }

    @Test
    void invalidFieldsReturn400AndDoNotCreateUser() throws Exception {
        long count = users.count();

        var response = post(Map.of("username", "ab", "email", "invalid", "password", "short"));
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(json.get("fieldErrors").has("username"));
        assertTrue(json.get("fieldErrors").has("email"));
        assertTrue(json.get("fieldErrors").has("password"));
        assertFalse(response.body().contains("short"));
        assertEquals(count, users.count());
    }

    @Test
    void missingFieldsReturn400InsteadOfServerError() throws Exception {
        var response = post(Map.of());
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(json.get("fieldErrors").has("username"));
        assertTrue(json.get("fieldErrors").has("email"));
        assertTrue(json.get("fieldErrors").has("password"));
    }

    @Test
    void takenUsernameIgnoringCaseReturns409WithoutAnotherAccount() throws Exception {
        String username = uniqueUsername();
        assertEquals(201, post(validRequest(username, username + "@example.com")).statusCode());
        long count = users.count();

        var response = post(validRequest(username.toUpperCase(), "other-" + username + "@example.com"));
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals("This username is already taken.", json.get("fieldErrors").get("username").asText());
        assertEquals(count, users.count());
    }

    @Test
    void takenEmailIgnoringCaseReturns409WithoutAnotherAccount() throws Exception {
        String username = uniqueUsername();
        assertEquals(201, post(validRequest(username, username + "@example.com")).statusCode());
        long count = users.count();

        var response = post(validRequest(uniqueUsername(), username.toUpperCase() + "@EXAMPLE.COM"));
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals("This email address is already registered.", json.get("fieldErrors").get("email").asText());
        assertEquals(count, users.count());
    }

    @Test
    void passwordExceedingBcryptByteLimitReturns400() throws Exception {
        String username = uniqueUsername();
        var response = post(Map.of("username", username, "email", username + "@example.com",
                "password", "\u0105".repeat(40)));
        JsonNode json = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("Password must not exceed 72 UTF-8 bytes.", json.get("fieldErrors").get("password").asText());
        assertFalse(users.existsByUsernameIgnoreCase(username));
    }

    private Map<String, String> validRequest(String username, String email) {
        return Map.of("username", username, "email", email, "password", "ExamplePassword123!");
    }

    private String uniqueUsername() {
        return "learner_" + UUID.randomUUID().toString().replace("-", "");
    }

    private HttpResponse<String> post(Map<String, ?> body) throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        return com.example.backend.support.CsrfHttpClient.post(
                URI.create("http://localhost:" + port + "/api/auth/register"), objectMapper.writeValueAsString(body), objectMapper);
    }
}
