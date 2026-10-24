package com.mfa.stepup.application.usecase;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.application.port.out.*;
import com.mfa.stepup.application.port.out.IdempotencyPort.Entry;
import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;

/** Short database transactions. The remote business call must run after prepare has committed. */
@Service
public class IdempotentExecutionTransactions {
    private final IdempotencyPort idempotency;
    private final OperationAuthorizationRepositoryPort repository;
    private final Duration retention;
    private final Duration lease;
    public record Preparation(Entry entry, boolean created) {}

    public IdempotentExecutionTransactions(IdempotencyPort idempotency, OperationAuthorizationRepositoryPort repository,
            @Value("${mfa.idempotency.retention:PT24H}") String retention,
            @Value("${mfa.idempotency.lease:PT30S}") String lease) {
        this.idempotency=idempotency; this.repository=repository;
        this.retention=Duration.parse(retention); this.lease=Duration.parse(lease);
        if (this.retention.isNegative() || this.retention.isZero() || this.lease.isNegative() || this.lease.isZero())
            throw new IllegalArgumentException("Idempotency durations must be positive");
    }

    @Transactional
    public Preparation prepare(AuthorizeOperationCommand command, AuthenticationEvidence evidence, String requiredAcr,
            Duration maxAge, AuthorizationDecision decision, String scope, String keyHash, String fingerprint) {
        var existing=idempotency.find(scope,keyHash);
        if (existing.isPresent()) return existing(existing.get(),fingerprint);
        var approval=repository.findApprovalById(command.approvalId())
            .orElseThrow(() -> new InvalidApprovalException("Transaction approval was not found"));
        var auth=repository.findById(approval.authorizationId())
            .orElseThrow(() -> new InvalidApprovalException("Approval authorization was not found"));
        // Requests sharing approval serialize on the authorization row, even with different keys.
        existing=idempotency.find(scope,keyHash);
        if (existing.isPresent()) return existing(existing.get(),fingerprint);
        var now=Instant.now();
        var digest=new IntentCanonicalizer().calculateDigest(command.materialData());
        new DelegatedAuthorizationValidator().validate(auth,
            new DelegationAttribution(new SubjectId(command.subjectId()),command.actorId()==null?null:new ActorId(command.actorId())),
            command.delegated(),new OperationId(command.operationId()),new OperationType(command.operationType()),
            new IntentVersion(command.intentVersion()),digest);
        auth.evaluateAuthentication(evidence,requiredAcr,maxAge,now); auth.evaluatePolicy(decision);
        if (now.isAfter(auth.getExpiresAt())) throw new InvalidApprovalException("Operation authorization expired");
        auth.recordApproval(approval,digest,now);
        var entry=new Entry(IdempotencyRecord.start(scope,keyHash,fingerprint,auth.getOperationId(),now,retention,lease),auth.getAuthorizationId(),null);
        idempotency.create(entry);
        if (!repository.consumeApprovalAtomic(approval,now))
            throw new InvalidApprovalException("Approval is invalid, expired, or has already been consumed");
        auth.prepareExecution(digest,now); repository.save(auth);
        return new Preparation(entry,true);
    }

    private Preparation existing(Entry entry,String fingerprint) {
        entry.record().assertFingerprint(fingerprint); return new Preparation(entry,false);
    }

    @Transactional
    public Entry claimReconciliation(Entry requested) {
        var current=idempotency.find(requested.record().scope(),requested.record().keyHash()).orElseThrow();
        current.record().assertFingerprint(requested.record().fingerprint());
        if (current.record().terminal()) return current;
        var now=Instant.now();
        var nextRecord=current.record().claimReconciliation(now,lease);
        var auth=repository.findById(current.authorizationId()).orElseThrow();
        var unknown=result(auth,BusinessOperationPort.ExecutionResult.unknown(),now);
        var next=new Entry(nextRecord,current.authorizationId(),unknown);
        if (!idempotency.replaceIfOwned(current,next)) throw new IdempotencyConflictException("Reconciliation is already in progress");
        auth.markOutcomeUnknown(now); repository.save(auth);
        return next;
    }

    @Transactional
    public AuthorizationResult finish(Entry owned,BusinessOperationPort.ExecutionResult outcome) {
        var auth=repository.findById(owned.authorizationId()).orElseThrow();
        var now=Instant.now();
        var result=result(auth,outcome,now);
        var state=outcome.outcomeUnknown()?IdempotencyRecord.State.OUTCOME_UNKNOWN:
            outcome.success()?IdempotencyRecord.State.SUCCEEDED:IdempotencyRecord.State.FAILED;
        var next=new Entry(owned.record().transition(state,now),owned.authorizationId(),result);
        if (!idempotency.replaceIfOwned(owned,next)) {
            var current=idempotency.find(owned.record().scope(),owned.record().keyHash()).orElseThrow();
            if (current.result()==null) throw new IdempotencyConflictException("Execution ownership changed");
            return current.result();
        }
        if (outcome.outcomeUnknown()) auth.markOutcomeUnknown(now);
        else if (outcome.success()) auth.markExecuted(now);
        else auth.markExecutionFailed(now);
        repository.save(auth);
        return result;
    }

    private AuthorizationResult result(OperationAuthorization auth,BusinessOperationPort.ExecutionResult outcome,Instant now) {
        boolean unknown=outcome.outcomeUnknown();
        boolean success=!unknown && outcome.success();
        return new AuthorizationResult(auth.getAuthorizationId(),auth.getOperationId().value(),
            unknown?AuthorizationState.OUTCOME_UNKNOWN:success?AuthorizationState.EXECUTED:AuthorizationState.EXECUTION_FAILED,
            success,unknown,success?outcome.businessResultRef():null,now,
            unknown?"Business outcome is not yet confirmed; retry the same key to reconcile":
                success?"Operation successfully authorized and executed":"Business operation failed",
            auth.getDelegationAttribution());
    }
}
