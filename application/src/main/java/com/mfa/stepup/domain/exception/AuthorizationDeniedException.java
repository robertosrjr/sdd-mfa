package com.mfa.stepup.domain.exception;

/**
 * Exceção lançada quando a política independente de autorização nega a operação ao titular.
 */
public class AuthorizationDeniedException extends DomainException {

    private final String policyVersion;
    private final String reasonCode;

    public AuthorizationDeniedException(String message, String policyVersion, String reasonCode) {
        super(message);
        this.policyVersion = policyVersion;
        this.reasonCode = reasonCode;
    }

    public AuthorizationDeniedException(String message) {
        this(message, null, null);
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}

