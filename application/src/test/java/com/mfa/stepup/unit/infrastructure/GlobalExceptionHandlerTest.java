package com.mfa.stepup.unit.infrastructure;

import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.TemporaryLockoutException;
import com.mfa.stepup.infrastructure.adapter.in.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should return 401 with RFC 9470 WWW-Authenticate challenge on InsufficientAuthenticationException")
    void shouldReturn401WithRfc9470Challenge() {
        InsufficientAuthenticationException ex = new InsufficientAuthenticationException(
            "MFA step-up required for high value transfer",
            "gold_hardware_token",
            300
        );

        ResponseEntity<ProblemDetail> response = handler.handleInsufficientAuthentication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
            .contains("error=\"insufficient_user_authentication\"")
            .contains("acr_values=\"gold_hardware_token\"")
            .contains("max_age=300");

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Insufficient User Authentication");
        assertThat(body.getProperties()).containsEntry("required_acr_values", "gold_hardware_token");
        assertThat(body.getProperties()).containsEntry("max_age", 300);
    }

    @Test
    @DisplayName("Should return 403 Forbidden with policy details on AuthorizationDeniedException")
    void shouldReturn403OnAuthorizationDenied() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException(
            "User lacks TRANSFER_EXECUTE permission",
            "policy_v2.1",
            "INSUFFICIENT_RESOURCE_PRIVILEGE"
        );

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Authorization Denied");
        assertThat(body.getProperties()).containsEntry("reason_code", "INSUFFICIENT_RESOURCE_PRIVILEGE");
        assertThat(body.getProperties()).containsEntry("policy_version", "policy_v2.1");
    }

    @Test
    @DisplayName("Should return 429 Too Many Requests with Retry-After header on TemporaryLockoutException")
    void shouldReturn429OnTemporaryLockout() {
        TemporaryLockoutException ex = new TemporaryLockoutException(
            "Too many failed confirmation attempts",
            Instant.now().plusSeconds(900),
            900
        );

        ResponseEntity<ProblemDetail> response = handler.handleTemporaryLockout(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Temporarily Locked");
    }

    @Test
    @DisplayName("Should return 409 Conflict on IdempotencyConflictException")
    void shouldReturn409OnIdempotencyConflict() {
        IdempotencyConflictException ex = new IdempotencyConflictException("Key reused with different payload");

        ResponseEntity<ProblemDetail> response = handler.handleIdempotencyConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Idempotency Conflict");
    }
}

