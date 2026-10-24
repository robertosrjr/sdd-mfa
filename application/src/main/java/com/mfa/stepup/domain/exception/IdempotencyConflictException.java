package com.mfa.stepup.domain.exception;

/**
 * Exceção lançada quando uma chave de idempotência é reutilizada com um fingerprint de requisição diferente
 * ou quando há tentativa concorrente em andamento.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

