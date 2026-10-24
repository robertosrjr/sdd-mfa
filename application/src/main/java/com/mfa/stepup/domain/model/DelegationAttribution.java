package com.mfa.stepup.domain.model;

import com.mfa.stepup.domain.exception.InvalidApprovalException;

/** Technical identities presented in the trusted approval interaction. */
public record DelegationAttribution(SubjectId subjectId, ActorId actorId) {
    public DelegationAttribution {
        if (subjectId == null) {
            throw new InvalidApprovalException("Subject attribution is required");
        }
        if (actorId != null && actorId.value().equals(subjectId.value())) {
            throw new InvalidApprovalException("Delegated actor must be distinct from the subject");
        }
    }

    public boolean delegated() {
        return actorId != null;
    }

    /** Values must come from stored trusted consent evidence, never the execution payload. */
    public void validateApprover(SubjectId approverId, boolean humanConfirmed) {
        if (!humanConfirmed || !subjectId.equals(approverId)) {
            throw new InvalidApprovalException("Human approval by the subject is required");
        }
    }
}
