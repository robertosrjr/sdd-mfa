package com.mfa.stepup.domain.model;

/**
 * Estados do ciclo de vida da jornada de autorização de operação.
 * Não inclui estados de espera assíncrona humana durável (o fluxo é interativo e síncrono).
 */
public enum AuthorizationState {
    REQUESTED,
    AUTHENTICATION_REQUIRED,
    AUTHENTICATED,
    POLICY_AUTHORIZED,
    DENIED,
    USER_APPROVED,
    EXECUTING,
    EXECUTED,
    EXECUTION_FAILED,
    OUTCOME_UNKNOWN,
    RECONCILING,
    CANCELLED,
    REJECTED,
    EXPIRED
}

