package com.mfa.stepup.domain.exception;

import java.time.Instant;

/**
 * Exceção lançada quando o limite de falhas da operação é excedido, bloqueando temporariamente
 * novas tentativas apenas daquela operação sem bloquear a conta inteira do titular (FR-015).
 */
public class TemporaryLockoutException extends DomainException {

    private final Instant lockedUntil;
    private final long retryAfterSeconds;

    public TemporaryLockoutException(String message, Instant lockedUntil, long retryAfterSeconds) {
        super(message);
        this.lockedUntil = lockedUntil;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

