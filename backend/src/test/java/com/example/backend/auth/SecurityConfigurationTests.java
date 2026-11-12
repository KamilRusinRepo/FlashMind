package com.example.backend.auth;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.example.backend.error.SecurityErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
@Import(SecurityConfigurationTests.ProbeConfiguration.class)
class SecurityConfigurationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @Autowired SecurityErrorHandler errors;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void statusRemainsPublicWithoutCreatingASession() throws Exception {
        var result = mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP")).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void protectedRequestReturnsJson401WithoutRedirectOrBasicChallenge() throws Exception {
        mvc.perform(get("/api/security-test/protected?token=private-query").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Location")).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.path").value("/api/security-test/protected"))
                .andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(content().string(not(containsString("private-query"))));
    }

    @Test
    void everyPublicMutationStillRequiresCsrf() throws Exception {
        for (String path : new String[]{"register", "verify-email", "resend-verification"}) {
            mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(containsString("CSRF")))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void realCsrfEndpointAllowsAnonymousPostToReachDtoValidation() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
        var token = mapper.readTree(result.getResponse().getContentAsString());
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(post("/api/auth/register").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void tokenCannotBeUsedWithAnotherSessionAndIsNeverEchoedOnFailure() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andReturn();
        String token = mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/register").session(new MockHttpSession()).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(content().string(not(containsString(token))));
        mvc.perform(post("/api/auth/register").session((MockHttpSession) result.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", "wrong-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(content().string(not(containsString("wrong-token"))));
    }

    @Test
    void authenticatedTestUserCanAccessProtectedResourceButStillNeedsCsrfForPost() throws Exception {
        mvc.perform(get("/api/security-test/protected").with(user("learner").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/security-test/protected").with(user("learner").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/security-test/protected").with(user("learner").roles("USER")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void validCsrfDoesNotAuthenticateAnonymousUser() throws Exception {
        mvc.perform(post("/api/security-test/protected").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultLoginAndLogoutEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/logout").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void accessDeniedHandlerUsesApiErrorWithoutLeakingExceptionDetails() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/private");
        var response = new MockHttpServletResponse();
        errors.handle(request, response, new AccessDeniedException("internal-sensitive-detail"));
        assertEquals(403, response.getStatus());
        var body = mapper.readTree(response.getContentAsString());
        assertEquals("You do not have permission to access this resource.", body.get("message").asText());
        assertFalse(response.getContentAsString().contains("internal-sensitive-detail"));
    }

    @Test
    void actualSessionCookieIsHttpOnlyAndSameSiteLaxButDoesNotGrantAuthentication() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        try (var http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
            var csrfResponse = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrfResponse.statusCode());
            assertTrue(csrfResponse.headers().allValues("Set-Cookie").stream().anyMatch(cookie ->
                    cookie.startsWith("JSESSIONID=") && cookie.toLowerCase().contains("httponly") && cookie.contains("SameSite=Lax")));
            var protectedResponse = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/security-test/protected"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, protectedResponse.statusCode());
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/security-test/protected")
        Map<String, Boolean> getProtected() { return Map.of("accessible", true); }
        @PostMapping("/api/security-test/protected")
        Map<String, Boolean> postProtected() { return Map.of("accessible", true); }
    }
}
