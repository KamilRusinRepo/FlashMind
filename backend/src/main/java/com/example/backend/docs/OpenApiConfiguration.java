package com.example.backend.docs;

import java.util.List;
import java.util.Set;
import com.example.backend.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation only: authorization and CSRF enforcement remain in Spring Security. */
@Configuration
public class OpenApiConfiguration {
    private static final String SESSION = "SessionCookie";
    private static final String CSRF = "CsrfToken";
    private static final Set<String> PUBLIC = Set.of("/api/status", "/api/auth/csrf", "/api/auth/login",
            "/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification",
            "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/confirm-email-change",
            "/api/auth/logout");

    @Bean
    OpenAPI flashMindApi() {
        return new OpenAPI().info(new Info().title("FlashMind API").version("1.0")
                .description("""
                    FlashMind backend: decks, flashcards, study sessions, statistics and account administration.

                    ### Testing with a browser session
                    1. Open this page on the backend origin (normally http://localhost:8080).
                    2. Execute GET /api/auth/csrf and copy the response's token value.
                    3. Click Authorize and paste it into CsrfToken. Do not paste the cookie or a password there.
                    4. Execute POST /api/auth/login with your username/email and password.
                    5. Execute GET /api/auth/csrf again and replace CsrfToken in Authorize: login rotates it.
                    6. Execute protected endpoints. The browser sends the HttpOnly session cookie automatically.

                    SessionCookie describes browser authentication; there is no need to enter JSESSIONID manually.
                    POST, PUT, PATCH and DELETE require CsrfToken, including public registration and login.
                    After logout, password/email changes or session expiration, obtain a fresh token.
                    ADMIN endpoints require an administrator account. A USER cannot gain access through Swagger.
                    Requests modify the real application database and may enqueue real mail jobs (Mailpit locally).
                    Documentation and schemas contain no credentials. Enter your own values for requests.
                    """))
                .servers(List.of(new Server().url("/").description("Same backend origin as this documentation")))
                .components(new Components()
                        .addSecuritySchemes(SESSION, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                                .description("HttpOnly cookie set by POST /api/auth/login. Sent automatically by the browser on this origin; do not enter it manually."))
                        .addSecuritySchemes(CSRF, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")
                                .description("Copy token from GET /api/auth/csrf using the same browser session. Refresh after login/logout; not a login credential.")))
                .tags(List.of(
                        tag("Authentication", "Registration, email verification, session login/logout and password recovery. Passwords are not trimmed and are limited to 72 UTF-8 bytes (BCrypt)."),
                        tag("Account", "Current account settings. New email stays pending until confirmation; password changes invalidate sessions."),
                        tag("Decks", "Own decks CRUD and accessible library. Owner mutations do not permit changing another user's deck."),
                        tag("Flashcards", "Own flashcards CRUD; supports optional HTTP(S) image links."),
                        tag("Explore", "Public decks and following; authorless public decks are shown as Deleted user."),
                        tag("Study", "Own or followed public decks. Up to 10 cards; FREE_PRACTICE is random and does not change review dates. SCHEDULED_REVIEW uses due cards."),
                        tag("Statistics", "The current user's XP, streak, activity and mastery reports."),
                        tag("Administration", "ADMIN only. Last active administrator is protected. Deleting an account preserves public decks as Deleted user. Editing public decks preserves their public visibility."),
                        tag("Status", "Application/database health.")));
    }

    @Bean
    OpenApiCustomizer flashMindOperations() {
        return api -> {
            ModelConverters.getInstance().readAll(ApiError.class).forEach(api.getComponents()::addSchemas);
            // Logout is a Security filter, so Spring MVC cannot discover it as a controller.
            api.path("/api/auth/logout", new PathItem().post(new Operation().operationId("logout")
                    .summary("Sign out and invalidate the current session")
                    .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("Signed out; session cookie cleared.")))));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean writes = !Set.of(PathItem.HttpMethod.GET, PathItem.HttpMethod.HEAD, PathItem.HttpMethod.OPTIONS).contains(method);
                boolean anonymous = PUBLIC.contains(path);
                operation.setTags(List.of(group(path)));
                if (operation.getSummary() == null) operation.setSummary(summary(method, path));
                var security = new SecurityRequirement();
                if (!anonymous) security.addList(SESSION);
                if (writes) security.addList(CSRF);
                operation.setSecurity(security.isEmpty() ? List.of() : List.of(security));
                // Springdoc sees ResponseEntity<T>, but cannot infer its runtime success status.
                String success = successStatus(method, path);
                var responses = operation.getResponses();
                if (!success.equals("200")) {
                    ApiResponse inferred = responses.get(success);
                    if (inferred == null) inferred = responses.remove("200");
                    if (inferred == null) inferred = new ApiResponse();
                    inferred.setDescription(success.equals("201") ? "Created" : success.equals("202") ? "Request accepted" : "Completed without a response body");
                    if (success.equals("204")) inferred.setContent(null);
                    responses.addApiResponse(success, inferred);
                }
                if (writes || path.contains("{") || operation.getParameters() != null && !operation.getParameters().isEmpty())
                    responses.addApiResponse("400", error("Invalid request or expired/invalid token; fieldErrors contains validation details when applicable."));
                if (!anonymous || path.equals("/api/auth/login")) responses.addApiResponse("401", error("Missing/expired session or invalid credentials."));
                if (writes || path.startsWith("/api/admin")) responses.addApiResponse("403", error("Missing/invalid CSRF token, insufficient permission or email verification required after a valid password."));
                if (path.contains("{")) responses.addApiResponse("404", error("Resource not found or not accessible to this account."));
                if (writes && (path.startsWith("/api/admin/") || path.startsWith("/api/users/")
                        || path.equals("/api/auth/register") || path.equals("/api/auth/confirm-email-change")
                        || path.contains("study-sessions") || path.endsWith("/follow")))
                    responses.addApiResponse("409", error("Operation conflicts with the current state or a business rule; see message/fieldErrors."));
                responses.addApiResponse("500", error("Unexpected server error; internal details are not exposed."));
                if (path.equals("/api/status")) responses.addApiResponse("503", new ApiResponse().description("Database is unavailable; status and database are DOWN.")
                        .content(responses.get("200").getContent()));
            }));
            for (Schema<?> schema : api.getComponents().getSchemas().values()) {
                if (schema.getProperties() != null) schema.getProperties().forEach((name, property) -> {
                    if (Set.of("password", "currentPassword", "newPassword", "confirmPassword").contains(name)) {
                        property.setWriteOnly(true);
                        property.setFormat("password");
                    }
                });
            }
        };
    }

    private static Tag tag(String name, String description) { return new Tag().name(name).description(description); }
    private static ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
    private static String group(String path) {
        if (path.startsWith("/api/admin")) return "Administration";
        if (path.startsWith("/api/auth")) return "Authentication";
        if (path.startsWith("/api/users")) return "Account";
        if (path.startsWith("/api/statistics")) return "Statistics";
        if (path.contains("study-sessions")) return "Study";
        if (path.startsWith("/api/explore")) return "Explore";
        if (path.contains("flashcards")) return "Flashcards";
        return path.equals("/api/status") ? "Status" : "Decks";
    }
    private static String summary(PathItem.HttpMethod method, String path) {
        String name = switch (path) {
            case "/api/auth/csrf" -> "Get a CSRF token for this browser session";
            case "/api/auth/register" -> "Register a USER account and request verification email";
            case "/api/auth/login" -> "Sign in with username or email and rotate the session";
            case "/api/auth/me" -> "Get the currently signed-in user";
            case "/api/auth/verify-email" -> "Confirm registration using a one-time email token";
            case "/api/auth/resend-verification" -> "Request a new verification link (60-second limit)";
            case "/api/auth/forgot-password" -> "Request a password reset email (neutral response)";
            case "/api/auth/reset-password" -> "Set a new password with a one-time reset token";
            case "/api/auth/confirm-email-change" -> "Confirm a new email address and invalidate sessions";
            case "/api/users/me" -> "Delete own account; retain public decks as Deleted user";
            case "/api/users/me/password" -> "Change password after checking the current password";
            case "/api/users/me/username" -> "Change own username";
            case "/api/users/me/email-change" -> method == PathItem.HttpMethod.GET ? "Get the pending email change" : "Request confirmation of a new email address";
            case "/api/users/me/reminders" -> method == PathItem.HttpMethod.GET ? "Get daily reminder settings" : "Update daily reminder settings";
            case "/api/admin/access" -> "Check current administrator access";
            case "/api/admin/users" -> "Search, filter and paginate users";
            case "/api/admin/users/{userId}/enabled" -> "Block or unblock a user and invalidate their sessions";
            case "/api/admin/users/{userId}" -> method == PathItem.HttpMethod.DELETE ? "Delete a user; retain public decks as Deleted user" : "Edit username, pending email and roles; protect the last administrator";
            case "/api/admin/decks" -> "Search, filter and paginate public decks";
            case "/api/admin/decks/{deckId}" -> switch (method) {
                case GET -> "Get public deck details for administrative editing";
                case PUT -> "Edit public deck details, including decks without an author";
                default -> "Permanently delete a public deck and its study data";
            };
            case "/api/decks/{deckId}/study-sessions" -> "Start Scheduled Review or Free Practice (up to 10 cards)";
            case "/api/study-sessions/{sessionId}" -> "Get own study session and current card";
            case "/api/study-sessions/{sessionId}/answers/{answerId}" -> "Submit an answer or skip; save personal progress and XP";
            case "/api/study-sessions/{sessionId}/answers/{answerId}/hint" -> "Reveal a hint; reduce XP for a correct answer";
            case "/api/study-sessions/{sessionId}/complete" -> "Complete a session after all answers are recorded";
            case "/api/study-sessions/{sessionId}/summary" -> "Get a completed session summary";
            case "/api/statistics/me" -> "Get own activity, XP, level, streak and daily goal";
            case "/api/statistics/me/decks" -> "Get mastery and due-card counts for accessible decks";
            case "/api/statistics/me/decks/{deckId}" -> "Get the mastery report for an accessible deck";
            case "/api/status" -> "Check application and database health";
            default -> null;
        };
        if (name != null) return name;
        return switch (method) {
            case GET -> "Read " + path;
            case POST -> "Execute " + path;
            case PUT -> "Update " + path;
            case DELETE -> "Delete " + path;
            default -> method + " " + path;
        };
    }
    private static String successStatus(PathItem.HttpMethod method, String path) {
        if (method == PathItem.HttpMethod.DELETE || path.equals("/api/admin/access")
                || path.equals("/api/auth/logout") || path.equals("/api/users/me/password")) return "204";
        if (method == PathItem.HttpMethod.POST) {
            if (Set.of("/api/auth/register", "/api/decks", "/api/decks/{deckId}/flashcards", "/api/decks/{deckId}/study-sessions").contains(path)) return "201";
            if (Set.of("/api/auth/forgot-password", "/api/auth/resend-verification", "/api/users/me/email-change").contains(path)) return "202";
        }
        return "200";
    }
}
