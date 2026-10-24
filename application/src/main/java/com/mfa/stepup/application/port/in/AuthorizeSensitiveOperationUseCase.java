package com.mfa.stepup.application.port.in;

import com.mfa.stepup.domain.model.AuthorizationState;
import com.mfa.stepup.domain.model.DelegationAttribution;

import java.time.Instant;
import java.util.Map;

/**
 * Caso de uso primário de entrada (Inbound Port) para a aprovação e execução de operações sensíveis com step-up.
 */
public interface AuthorizeSensitiveOperationUseCase {

    AuthorizationResult execute(AuthorizeOperationCommand command);

    record AuthorizeOperationCommand(
        String subjectId,
        String actorId,
        String operationId,
        String operationType,
        long intentVersion,
        Map<String, Object> materialData,
        String approvalId,
        String correlationId,
        String idempotencyKey,
        boolean delegated
    ) {
        public AuthorizeOperationCommand(String subjectId, String actorId, String operationId, String operationType,
                long intentVersion, Map<String, Object> materialData, String approvalId, String correlationId,
                String idempotencyKey) {
            this(subjectId, actorId, operationId, operationType, intentVersion, materialData, approvalId,
                correlationId, idempotencyKey, actorId != null);
        }
    }

    record AuthorizationResult(
        String authorizationId,
        String operationId,
        AuthorizationState state,
        boolean executed,
        boolean outcomeUnknown,
        String businessResultRef,
        Instant executedAt,
        String message,
        DelegationAttribution delegationAttribution
    ) {
        public AuthorizationResult(String authorizationId, String operationId, AuthorizationState state,
                boolean executed, boolean outcomeUnknown, String businessResultRef, Instant executedAt, String message) {
            this(authorizationId, operationId, state, executed, outcomeUnknown, businessResultRef, executedAt, message, null);
        }
    }
}

