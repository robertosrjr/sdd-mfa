package com.mfa.stepup.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Resultado da avaliação de política corporativa de autorização de negócio.
 * Mantido como decisão independente de autenticação e consentimento.
 */
public record AuthorizationDecision(
    boolean allowed,
    String policyVersion,
    String reasonCode,
    Instant evaluatedAt
) {
    public AuthorizationDecision {
        Objects.requireNonNull(policyVersion, "policyVersion cannot be null");
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }

    public static AuthorizationDecision allow(String policyVersion) {
        return new AuthorizationDecision(true, policyVersion, "ALLOWED", Instant.now());
    }

    public static AuthorizationDecision deny(String policyVersion, String reasonCode) {
        return new AuthorizationDecision(false, policyVersion, reasonCode, Instant.now());
    }
}

