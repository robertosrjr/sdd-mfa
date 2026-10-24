package com.mfa.stepup.domain.model;

import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.exception.InvalidIntentException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Raiz que governa o ciclo de vida da autorização de uma operação sensível.
 * Garante a separação estrita entre autenticação, autorização de negócio e consentimento humano,
 * e a vinculação inegociável à intenção canônica aprovada.
 */
public class OperationAuthorization {

    private final String authorizationId;
    private final OperationId operationId;
    private final OperationType operationType;
    private final SubjectId subjectId;
    private final ActorId actorId;
    private final DelegationAttribution delegationAttribution;
    private final IntentVersion intentVersion;
    private final IntentDigest intentDigest;
    private String policyVersion;
    private AuthorizationState state;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant completedAt;

    private AuthenticationEvidence authenticationEvidence;
    private AuthorizationDecision authorizationDecision;
    private TransactionApproval transactionApproval;

    public OperationAuthorization(
        String authorizationId,
        OperationId operationId,
        OperationType operationType,
        SubjectId subjectId,
        ActorId actorId,
        IntentVersion intentVersion,
        IntentDigest intentDigest,
        String policyVersion,
        AuthorizationState state,
        Instant createdAt,
        Instant expiresAt,
        Instant completedAt,
        AuthenticationEvidence authenticationEvidence,
        AuthorizationDecision authorizationDecision,
        TransactionApproval transactionApproval
    ) {
        this.authorizationId = Objects.requireNonNull(authorizationId, "authorizationId cannot be null");
        this.operationId = Objects.requireNonNull(operationId, "operationId cannot be null");
        this.operationType = Objects.requireNonNull(operationType, "operationType cannot be null");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId cannot be null");
        this.actorId = actorId;
        this.delegationAttribution = new DelegationAttribution(subjectId, actorId);
        this.intentVersion = Objects.requireNonNull(intentVersion, "intentVersion cannot be null");
        this.intentDigest = Objects.requireNonNull(intentDigest, "intentDigest cannot be null");
        this.policyVersion = policyVersion != null ? policyVersion : "1.0.0";
        this.state = state != null ? state : AuthorizationState.REQUESTED;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.completedAt = completedAt;
        this.authenticationEvidence = authenticationEvidence;
        this.authorizationDecision = authorizationDecision;
        this.transactionApproval = transactionApproval;
    }

    public static OperationAuthorization initiate(
        OperationId operationId,
        OperationType operationType,
        SubjectId subjectId,
        ActorId actorId,
        IntentVersion intentVersion,
        IntentDigest intentDigest,
        String policyVersion,
        Duration validity
    ) {
        Instant now = Instant.now();
        return new OperationAuthorization(
            UUID.randomUUID().toString(),
            operationId,
            operationType,
            subjectId,
            actorId,
            intentVersion,
            intentDigest,
            policyVersion,
            AuthorizationState.REQUESTED,
            now,
            now.plus(validity),
            null,
            null,
            null,
            null
        );
    }

    /**
     * Valida força e recência da autenticação conforme a política e RFC 9470 (FR-002 / FR-003).
     */
    public void evaluateAuthentication(AuthenticationEvidence evidence, String requiredAcr, Duration maxAge, Instant now) {
        this.authenticationEvidence = Objects.requireNonNull(evidence, "evidence cannot be null");

        boolean strongEnough = evidence.satisfiesStrength(requiredAcr);
        boolean freshEnough = evidence.isFresh(maxAge, now);

        if (!strongEnough || !freshEnough) {
            this.state = AuthorizationState.AUTHENTICATION_REQUIRED;
            Integer maxAgeSeconds = maxAge != null ? (int) maxAge.toSeconds() : null;
            throw new InsufficientAuthenticationException(
                "Authentication does not meet required strength or freshness criteria",
                requiredAcr,
                maxAgeSeconds
            );
        }

        this.state = AuthorizationState.AUTHENTICATED;
    }

    /**
     * Avalia a política independente de autorização (FR-002 / FR-003).
     */
    public void evaluatePolicy(AuthorizationDecision decision) {
        this.authorizationDecision = Objects.requireNonNull(decision, "decision cannot be null");
        this.policyVersion = decision.policyVersion();

        if (!decision.allowed()) {
            this.state = AuthorizationState.DENIED;
            throw new AuthorizationDeniedException(
                "Business authorization denied by policy",
                decision.policyVersion(),
                decision.reasonCode()
            );
        }

        this.state = AuthorizationState.POLICY_AUTHORIZED;
    }

    /**
     * Vincula a aprovação do titular e valida a correspondência exata com o digest atual (FR-005 / FR-006).
     */
    public void recordApproval(TransactionApproval approval, IntentDigest currentDigest, Instant now) {
        this.transactionApproval = Objects.requireNonNull(approval, "approval cannot be null");

        if (!authorizationId.equals(approval.authorizationId()) || !subjectId.equals(approval.subjectId())
                || !delegationAttribution.equals(approval.delegationAttribution())) {
            throw new InvalidApprovalException("Approval attribution does not match the authorization");
        }
        delegationAttribution.validateApprover(approval.approverId(), approval.humanConfirmed());

        if (approval.decision() == ApprovalDecision.CANCELLED) {
            this.state = AuthorizationState.CANCELLED;
            this.completedAt = now;
            throw new InvalidApprovalException("Operation authorization was cancelled by the user");
        }

        if (approval.decision() == ApprovalDecision.REJECTED) {
            this.state = AuthorizationState.REJECTED;
            this.completedAt = now;
            throw new InvalidApprovalException("Operation authorization was rejected by the user");
        }

        if (!this.intentDigest.equals(currentDigest) || !approval.intentDigest().equals(currentDigest)) {
            this.state = AuthorizationState.CANCELLED;
            this.completedAt = now;
            throw new InvalidIntentException("Operation material data changed after approval (intent digest mismatch)");
        }

        if (!approval.isUsable(currentDigest, now)) {
            throw new InvalidApprovalException("Transaction approval is expired, has already been consumed, or is invalid");
        }

        this.state = AuthorizationState.USER_APPROVED;
    }

    /**
     * Consome a aprovação de forma atômica e avança para EXECUTING.
     */
    public void prepareExecution(IntentDigest currentDigest, Instant now) {
        if (state != AuthorizationState.USER_APPROVED) {
            throw new IllegalStateException("Cannot execute operation in state: " + state);
        }
        if (transactionApproval == null || !transactionApproval.isUsable(currentDigest, now)) {
            throw new InvalidApprovalException("No valid or unconsumed approval available for execution");
        }

        this.transactionApproval = this.transactionApproval.consume(now);
        this.state = AuthorizationState.EXECUTING;
    }

    public void markExecuted(Instant now) {
        this.state = AuthorizationState.EXECUTED;
        this.completedAt = now;
    }

    public void markExecutionFailed(Instant now) {
        this.state = AuthorizationState.EXECUTION_FAILED;
        this.completedAt = now;
    }

    public void markOutcomeUnknown(Instant now) {
        this.state = AuthorizationState.OUTCOME_UNKNOWN;
    }

    // Getters
    public String getAuthorizationId() { return authorizationId; }
    public OperationId getOperationId() { return operationId; }
    public OperationType getOperationType() { return operationType; }
    public SubjectId getSubjectId() { return subjectId; }
    public ActorId getActorId() { return actorId; }
    public DelegationAttribution getDelegationAttribution() { return delegationAttribution; }
    public IntentVersion getIntentVersion() { return intentVersion; }
    public IntentDigest getIntentDigest() { return intentDigest; }
    public String getPolicyVersion() { return policyVersion; }
    public AuthorizationState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public AuthenticationEvidence getAuthenticationEvidence() { return authenticationEvidence; }
    public AuthorizationDecision getAuthorizationDecision() { return authorizationDecision; }
    public TransactionApproval getTransactionApproval() { return transactionApproval; }
}

