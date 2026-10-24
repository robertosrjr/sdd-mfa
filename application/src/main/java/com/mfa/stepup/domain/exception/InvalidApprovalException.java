package com.mfa.stepup.domain.exception;

/**
 * Exceção lançada quando a aprovação transacional for inexistente, expirada,
 * cancelada, rejeitada ou já consumida.
 */
public class InvalidApprovalException extends DomainException {

    public InvalidApprovalException(String message) {
        super(message);
    }

    public InvalidApprovalException(String message, Throwable cause) {
        super(message, cause);
    }
}

