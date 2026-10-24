package com.mfa.stepup.domain.model;

import java.util.Objects;

/**
 * Identificador do agente, representante ou sistema que iniciou uma solicitação delegada.
 */
public record ActorId(String value) {
    public ActorId {
        Objects.requireNonNull(value, "actorId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("actorId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

