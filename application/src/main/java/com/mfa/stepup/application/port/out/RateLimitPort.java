package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;

/**
 * Porta para verificação e controle de taxa de falhas por operação (FR-015).
 * Limita temporariamente tentativas para a operação específica sem bloquear
 * a conta inteira do titular.
 */
public interface RateLimitPort {

    boolean isLockedOut(SubjectId subjectId, OperationType operationType, OperationId operationId);

    long getRemainingLockoutSeconds(SubjectId subjectId, OperationType operationType, OperationId operationId);

    void recordFailure(SubjectId subjectId, OperationType operationType, OperationId operationId);

    void resetFailures(SubjectId subjectId, OperationType operationType, OperationId operationId);
}

