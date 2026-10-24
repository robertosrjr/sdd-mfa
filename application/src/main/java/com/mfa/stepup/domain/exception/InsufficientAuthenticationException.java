package com.mfa.stepup.domain.exception;

/**
 * Exceção lançada quando a força ou recência da autenticação não satisfaz os requisitos da política.
 * Modela os parâmetros necessários para o desafio RFC 9470 (OAuth Step-Up).
 */
public class InsufficientAuthenticationException extends DomainException {

    private final String requiredAcrValues;
    private final Integer maxAgeSeconds;

    public InsufficientAuthenticationException(String message, String requiredAcrValues, Integer maxAgeSeconds) {
        super(message);
        this.requiredAcrValues = requiredAcrValues;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public InsufficientAuthenticationException(String message) {
        this(message, null, null);
    }

    public String getRequiredAcrValues() {
        return requiredAcrValues;
    }

    public Integer getMaxAgeSeconds() {
        return maxAgeSeconds;
    }
}

