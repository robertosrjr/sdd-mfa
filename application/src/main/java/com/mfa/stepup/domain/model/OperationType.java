package com.mfa.stepup.domain.model;

import java.util.Objects;

/**
 * Tipo estável da operação (ex: FUNDS_TRANSFER, SENSITIVE_DATA_EXPORT, CREDENTIAL_CHANGE).
 */
public record OperationType(String value) {
    public OperationType {
        Objects.requireNonNull(value, "operationType cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("operationType cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

