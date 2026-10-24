package com.mfa.stepup.domain.model;

import com.mfa.stepup.domain.exception.InvalidApprovalException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro durável do consentimento e decisão explícita do titular sobre uma intenção exata.
 * O consumo atômico (consumedAt) garante que a aprovação seja utilizada no máximo uma única vez.
 */
public record TransactionApproval(
    String approvalId,
    String authorizationId,
    SubjectId subjectId,
    IntentDigest intentDigest,
    ApprovalDecision decision,
    Instant decidedAt,
    Instant expiresAt,
    Instant consumedAt,
    DelegationAttribution delegationAttribution,
    SubjectId approverId,
    boolean humanConfirmed
) {
    // Compatibility for direct approvals produced by a trusted human interaction.
    public TransactionApproval(String approvalId, String authorizationId, SubjectId subjectId,
            IntentDigest intentDigest, ApprovalDecision decision, Instant decidedAt, Instant expiresAt, Instant consumedAt) {
        this(approvalId, authorizationId, subjectId, intentDigest, decision, decidedAt, expiresAt, consumedAt,
            new DelegationAttribution(subjectId, null), subjectId, true);
    }

    public TransactionApproval {
        Objects.requireNonNull(authorizationId, "authorizationId cannot be null");
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(intentDigest, "intentDigest cannot be null");
        Objects.requireNonNull(decision, "decision cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        if (approvalId == null || approvalId.isBlank()) {
            approvalId = UUID.randomUUID().toString();
        }
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }

    public static TransactionApproval approve(
        String authorizationId,
        SubjectId subjectId,
        IntentDigest intentDigest,
        Instant expiresAt
    ) {
        return new TransactionApproval(
            UUID.randomUUID().toString(),
            authorizationId,
            subjectId,
            intentDigest,
            ApprovalDecision.APPROVED,
            Instant.now(),
            expiresAt,
            null
        );
    }

    public static TransactionApproval reject(
        String authorizationId,
        SubjectId subjectId,
        IntentDigest intentDigest
    ) {
        return new TransactionApproval(
            UUID.randomUUID().toString(),
            authorizationId,
            subjectId,
            intentDigest,
            ApprovalDecision.REJECTED,
            Instant.now(),
            Instant.now(),
            null
        );
    }

    public static TransactionApproval cancel(
        String authorizationId,
        SubjectId subjectId,
        IntentDigest intentDigest
    ) {
        return new TransactionApproval(
            UUID.randomUUID().toString(),
            authorizationId,
            subjectId,
            intentDigest,
            ApprovalDecision.CANCELLED,
            Instant.now(),
            Instant.now(),
            null
        );
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsable(IntentDigest currentDigest, Instant now) {
        if (decision != ApprovalDecision.APPROVED) {
            return false;
        }
        if (isConsumed()) {
            return false;
        }
        if (isExpired(now)) {
            return false;
        }
        return this.intentDigest.equals(currentDigest);
    }

    public TransactionApproval consume(Instant now) {
        if (decision != ApprovalDecision.APPROVED) {
            throw new InvalidApprovalException("Cannot consume an approval with decision: " + decision);
        }
        if (isConsumed()) {
            throw new InvalidApprovalException("Transaction approval has already been consumed at " + consumedAt);
        }
        if (isExpired(now)) {
            throw new InvalidApprovalException("Transaction approval expired at " + expiresAt);
        }
        return new TransactionApproval(
            approvalId,
            authorizationId,
            subjectId,
            intentDigest,
            decision,
            decidedAt,
            expiresAt,
            now,
            delegationAttribution,
            approverId,
            humanConfirmed
        );
    }
}

