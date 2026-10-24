package com.mfa.stepup.domain.exception;

/**
 * Exceção base de domínio para o fluxo de autorização sensível com step-up.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

