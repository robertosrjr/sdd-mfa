package com.mfa.stepup.domain.exception;

/**
 * Exceção lançada quando os dados materiais da operação divergirem da intenção aprovada
 * ou quando a versão/digest for inválido.
 */
public class InvalidIntentException extends DomainException {

    public InvalidIntentException(String message) {
        super(message);
    }

    public InvalidIntentException(String message, Throwable cause) {
        super(message, cause);
    }
}

