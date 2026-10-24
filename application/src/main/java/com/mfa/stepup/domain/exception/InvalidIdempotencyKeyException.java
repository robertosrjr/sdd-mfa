package com.mfa.stepup.domain.exception;

public class InvalidIdempotencyKeyException extends DomainException {
    public InvalidIdempotencyKeyException(String message) { super(message); }
}
