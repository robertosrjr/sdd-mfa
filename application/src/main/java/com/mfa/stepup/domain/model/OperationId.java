package com.mfa.stepup.domain.model;

import java.util.Objects;

/**
 * Identificador exclusivo da operação sensível no domínio de negócio proprietário.
 */
public record OperationId(String value) {
    public OperationId {
        Objects.requireNonNull(value, "operationId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("operationId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

