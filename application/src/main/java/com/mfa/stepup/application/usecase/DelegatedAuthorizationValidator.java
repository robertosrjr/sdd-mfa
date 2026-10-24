package com.mfa.stepup.application.usecase;

import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.exception.InvalidIntentException;
import com.mfa.stepup.domain.model.*;

/** Checks request claims against durable authorization and consent evidence. */
public class DelegatedAuthorizationValidator {
    public void validate(OperationAuthorization stored, DelegationAttribution requested,
            boolean delegated, OperationId operationId, OperationType operationType,
            IntentVersion version, IntentDigest digest) {
        if (delegated != requested.delegated() || !stored.getDelegationAttribution().equals(requested)) {
            throw new InvalidApprovalException("Missing or mismatched delegation attribution");
        }
        if (!stored.getOperationId().equals(operationId) || !stored.getOperationType().equals(operationType)) {
            throw new InvalidApprovalException("Approval belongs to another operation");
        }
        if (!stored.getIntentVersion().equals(version) || !stored.getIntentDigest().equals(digest)) {
            throw new InvalidIntentException("Approved operation intent changed");
        }
    }
}
