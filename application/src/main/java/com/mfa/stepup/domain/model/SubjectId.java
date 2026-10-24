package com.mfa.stepup.domain.model;

import java.util.Objects;

/**
 * Identificador do titular ou responsável pelo recurso em cujo nome a operação é executada.
 */
public record SubjectId(String value) {
    public SubjectId {
        Objects.requireNonNull(value, "subjectId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("subjectId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

