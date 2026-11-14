package com.example.backend.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.backend.auth.RegistrationConflictException;
import com.example.backend.user.UsernameConflictException;
import com.example.backend.user.EmailChangeException;
import com.example.backend.auth.EmailNotVerifiedException;
import com.example.backend.auth.dto.response.EmailVerificationRequiredResponse;
import org.springframework.security.core.AuthenticationException;
import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.study.StudyStartException;
import com.example.backend.study.StudyActionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailChangeException.class)
    public ResponseEntity<ApiError> handleEmailChange(EmailChangeException exception, HttpServletRequest request) {
        var response = ResponseEntity.status(exception.getStatus()).header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS) response.header(HttpHeaders.RETRY_AFTER, "60");
        return response.body(createError(exception.getStatus(), exception.getMessage(), request.getRequestURI(),
                exception.getField() == null ? Map.of() : Map.of(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(UsernameConflictException.class)
    public ResponseEntity<ApiError> handleUsernameConflict(UsernameConflictException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(createError(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI(), Map.of("username", exception.getMessage())));
    }

    @ExceptionHandler({StudyStartException.class, StudyActionException.class})
    public ResponseEntity<ApiError> handleStudyConflict(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(createError(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<EmailVerificationRequiredResponse> handleUnverifiedEmail(
            EmailNotVerifiedException exception, HttpServletRequest request) {
        ApiError error = createError(HttpStatus.FORBIDDEN, exception.getMessage(), request.getRequestURI(), Map.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(EmailVerificationRequiredResponse.from(error, exception.getEmail()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createError(HttpStatus.UNAUTHORIZED,
                "Invalid email/username or password.", request.getRequestURI(), Map.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(),
                        error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()));

        ApiError body = createError(status, "Please check the submitted fields.", path(request), fieldErrors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ApiError error = body instanceof ApiError apiError
                ? apiError
                : createError(status, messageFor(status), path(request), Map.of());
        // Keep Spring's status and protocol headers, e.g. Allow for HTTP 405.
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(createError(
                HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI(), Map.of("token", exception.getMessage())
        ));
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiError> handleRegistrationConflict(
            RegistrationConflictException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(createError(
                HttpStatus.CONFLICT,
                "An account with these details already exists.",
                request.getRequestURI(),
                Map.of(exception.getField(), exception.getMessage())
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(createError(
                HttpStatus.CONFLICT,
                "The submitted data conflicts with existing data.",
                request.getRequestURI(),
                Map.of()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.",
                request.getRequestURI(),
                Map.of()
        ));
    }

    private ApiError createError(HttpStatusCode status, String message, String path, Map<String, String> fieldErrors) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        return new ApiError(
                Instant.now(),
                status.value(),
                httpStatus == null ? "HTTP Error" : httpStatus.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request body or parameters are invalid.";
            case 401 -> "Authentication is required.";
            case 403 -> "You do not have permission to access this resource.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "This HTTP method is not supported for this resource.";
            case 406 -> "The requested response format is not supported.";
            case 409 -> "The request conflicts with the current state of the resource.";
            case 413 -> "The uploaded content is too large.";
            case 415 -> "The request content type is not supported.";
            case 429 -> "Too many requests. Please try again later.";
            case 503 -> "The service is temporarily unavailable.";
            default -> status.is5xxServerError()
                    ? "An unexpected server error occurred."
                    : "The request could not be completed.";
        };
    }
}
