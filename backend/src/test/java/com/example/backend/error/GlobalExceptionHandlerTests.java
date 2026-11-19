package com.example.backend.error;

import com.example.backend.auth.token.InvalidTokenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsValidationErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/errors/validate"))
                .andExpect(jsonPath("$.fieldErrors.username").value("Username is required."))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required."))
                .andExpect(jsonPath("$.rejectedValue").doesNotExist());
    }

    @Test
    void preservesBodyFieldErrorsWhenPathConstraintsEnableMethodValidation() throws Exception {
        mvc.perform(post("/test/errors/validate/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"private-submitted-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("Username is required."))
                .andExpect(content().string(not(containsString("private-submitted-value"))));
        mvc.perform(post("/test/errors/validate/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ValidName\",\"password\":\"private-submitted-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(content().string(not(containsString("private-submitted-value"))));
    }

    @Test
    void returnsBadRequestForMalformedJsonWithoutEchoingContent() throws Exception {
        mvc.perform(post("/test/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"private-submitted-value\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(content().string(not(containsString("private-submitted-value"))));
    }

    @Test
    void preservesMethodNotAllowedStatusAndAllowHeader() throws Exception {
        mvc.perform(get("/test/errors/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void returnsBadRequestForInvalidParameterType() throws Exception {
        mvc.perform(get("/test/errors/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/test/errors/validate").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void preservesExplicitHttpStatusWithoutExposingInternalReason() throws Exception {
        mvc.perform(get("/test/errors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("The requested resource was not found."))
                .andExpect(content().string(not(containsString("private-resource-details"))));
    }

    @Test
    void returnsNotFoundForUnknownEndpoint() throws Exception {
        mvc.perform(get("/test/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/test/unknown"));
    }

    @Test
    void returnsConflictWithoutExposingDatabaseDetails() throws Exception {
        mvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(content().string(not(containsString("private-database-details"))));
    }

    @Test
    void returnsGenericServerErrorWithoutStackTraceOrQueryString() throws Exception {
        mvc.perform(get("/test/errors/unexpected").param("token", "private-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred."))
                .andExpect(jsonPath("$.path").value("/test/errors/unexpected"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("private-exception-details"))))
                .andExpect(content().string(not(containsString("private-token"))));
    }

    @Test
    void returnsBadRequestForInvalidToken() throws Exception {
        mvc.perform(get("/test/errors/token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.token").value("The token is invalid, expired or has already been used."));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        @GetMapping("/token")
        void invalidToken() {
            throw new InvalidTokenException();
        }

        @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping(value = "/validate/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validateWithPath(@PathVariable("id") @Min(1) Long id, @Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/number")
        int number(@RequestParam int value) {
            return value;
        }

        @GetMapping("/missing")
        void missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "private-resource-details");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("private-database-details");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("private-exception-details");
        }
    }

    record TestRequest(
            @NotBlank(message = "Username is required.") String username,
            @NotBlank(message = "Password is required.") String password
    ) {
    }
}
