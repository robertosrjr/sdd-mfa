package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;

/** Query only: implementations must never submit a new business write. */
public interface BusinessOperationReconciliationPort {
    BusinessOperationPort.ExecutionResult queryOutcome(OperationId operationId, OperationType operationType);
}
