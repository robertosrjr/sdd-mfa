package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.model.IntentVersion;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;

/**
 * Porta de saída para disparo da execução final no Serviço de Negócio proprietário da operação.
 */
public interface BusinessOperationPort {

    ExecutionResult execute(
        OperationId operationId,
        OperationType operationType,
        IntentDigest intentDigest,
        IntentVersion intentVersion
    );

    record ExecutionResult(
        boolean success,
        boolean outcomeUnknown,
        String businessResultRef,
        String errorMessage
    ) {
        public static ExecutionResult success(String businessResultRef) {
            return new ExecutionResult(true, false, businessResultRef, null);
        }

        public static ExecutionResult failed(String errorMessage) {
            return new ExecutionResult(false, false, null, errorMessage);
        }

        public static ExecutionResult unknown() {
            return new ExecutionResult(false, true, null, "Downstream response timed out or outcome unknown");
        }
    }
}

