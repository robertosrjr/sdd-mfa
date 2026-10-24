package com.mfa.stepup.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Evidência de autenticação associada à sessão do titular.
 * Modela a força (acr) e o instante de autenticação (authTime) para avaliação de step-up (RFC 9470).
 * Não armazena nem transporta credenciais, tokens ou segredos de sessão.
 */
public record AuthenticationEvidence(
    String acr,
    Instant authTime,
    String method
) {
    public AuthenticationEvidence {
        Objects.requireNonNull(acr, "acr cannot be null");
        Objects.requireNonNull(authTime, "authTime cannot be null");
    }

    public boolean isFresh(Duration maxAge, Instant now) {
        if (maxAge == null) {
            return true;
        }
        Objects.requireNonNull(now, "now cannot be null");
        Duration age = Duration.between(authTime, now);
        return !age.isNegative() && age.compareTo(maxAge) <= 0;
    }

    public boolean satisfiesStrength(String requiredAcr) {
        if (requiredAcr == null || requiredAcr.isBlank()) {
            return true;
        }
        // Avaliação de força: igualdade direta ou hierarquia de autenticação
        return this.acr.equalsIgnoreCase(requiredAcr.trim());
    }
}

