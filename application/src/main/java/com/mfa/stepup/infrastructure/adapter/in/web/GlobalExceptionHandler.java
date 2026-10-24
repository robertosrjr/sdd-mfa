package com.mfa.stepup.infrastructure.adapter.in.web;

import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.DomainException;
import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.exception.InvalidIntentException;
import com.mfa.stepup.domain.exception.TemporaryLockoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tratador global de exceções conforme RFC 9457 (Problem Details for HTTP APIs)
 * e RFC 9470 (OAuth 2.0 Step Up Authentication Challenge).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BASE_URI = "https://api.example.invalid/errors/";
    private static final Set<String> POLICY_REASONS = Set.of("LIMIT_EXCEEDED", "INSUFFICIENT_RESOURCE_PRIVILEGE",
        "NO_DELEGATION", "REVOKED", "DENIED");

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientAuthentication(InsufficientAuthenticationException ex) {
        log.warn("Step-up authentication required");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Additional authentication is required");
        problem.setType(URI.create(BASE_URI + "insufficient-authentication"));
        problem.setTitle("Insufficient User Authentication");
        problem.setProperty("timestamp", Instant.now());

        HttpHeaders headers = new HttpHeaders();
        StringBuilder challenge = new StringBuilder("Bearer error=\"insufficient_user_authentication\"");
        challenge.append(", error_description=\"Additional authentication is required\"");
        if (ex.getRequiredAcrValues() != null && !ex.getRequiredAcrValues().isBlank()) {
            challenge.append(", acr_values=\"").append(ex.getRequiredAcrValues()).append("\"");
            problem.setProperty("required_acr_values", ex.getRequiredAcrValues());
        }
        if (ex.getMaxAgeSeconds() != null) {
            challenge.append(", max_age=").append(ex.getMaxAgeSeconds());
            problem.setProperty("max_age", ex.getMaxAgeSeconds());
        }
        headers.set(HttpHeaders.WWW_AUTHENTICATE, challenge.toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .headers(headers)
            .body(problem);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.warn("Business authorization denied");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Business authorization was denied");
        problem.setType(URI.create(BASE_URI + "authorization-denied"));
        problem.setTitle("Authorization Denied");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("reason_code", ex.getReasonCode() != null && POLICY_REASONS.contains(ex.getReasonCode())
            ? ex.getReasonCode() : "POLICY_DENIED");
        if (ex.getPolicyVersion() != null && ex.getPolicyVersion().matches("(?:policy_)?v?\\d+(?:\\.\\d+){0,3}")) {
            problem.setProperty("policy_version", ex.getPolicyVersion());
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(InvalidIntentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidIntent(InvalidIntentException ex) {
        log.warn("Invalid operation intent");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Operation intent does not match the approved intent");
        problem.setType(URI.create(BASE_URI + "invalid-intent"));
        problem.setTitle("Invalid Operation Intent");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidApprovalException.class)
    public ResponseEntity<ProblemDetail> handleInvalidApproval(InvalidApprovalException ex) {
        log.warn("Invalid transaction approval");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Transaction approval is invalid or unavailable");
        problem.setType(URI.create(BASE_URI + "invalid-approval"));
        problem.setTitle("Invalid Transaction Approval");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The key conflicts with an existing or active request");
        problem.setType(URI.create(BASE_URI + "idempotency-conflict"));
        problem.setTitle("Idempotency Conflict");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(TemporaryLockoutException.class)
    public ResponseEntity<ProblemDetail> handleTemporaryLockout(TemporaryLockoutException ex) {
        log.warn("Operation temporarily locked");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Operation is temporarily locked");
        problem.setType(URI.create(BASE_URI + "temporarily-locked"));
        problem.setTitle("Temporarily Locked");
        problem.setProperty("locked_until", ex.getLockedUntil());
        problem.setProperty("retry_after_seconds", ex.getRetryAfterSeconds());
        problem.setProperty("timestamp", Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request payload");
        problem.setType(URI.create(BASE_URI + "validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("field_errors", errors);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleGenericDomain(DomainException ex) {
        log.warn("Domain validation failed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request does not meet operation requirements");
        problem.setType(URI.create(BASE_URI + "domain-error"));
        problem.setTitle("Domain Error");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandled(Exception ex) {
        log.error("Internal system error occurred");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected internal error occurred. Please refer to technical support."
        );
        problem.setType(URI.create(BASE_URI + "internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}

