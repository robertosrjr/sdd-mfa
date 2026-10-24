package com.mfa.stepup.domain.model;

/**
 * Versão de concorrência dos dados materiais da operação no sistema de origem.
 */
public record IntentVersion(long value) {
    public IntentVersion {
        if (value < 0) {
            throw new IllegalArgumentException("intentVersion cannot be negative");
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

