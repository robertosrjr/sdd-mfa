package com.mfa.stepup.application.usecase;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.out.*;
import com.mfa.stepup.application.port.out.IdempotencyPort.Entry;
import com.mfa.stepup.domain.exception.*;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

/** Authentication and policy precede replay/reservation; remote execution has no open database transaction. */
@Service
public class AuthorizeSensitiveOperationService implements AuthorizeSensitiveOperationUseCase {
    private final IdentityProviderPort identity;
    private final PolicyAuthorizationPort policy;
    private final BusinessOperationPort business;
    private final RateLimitPort rateLimit;
    private final AuditEventPort audit;
    private final IdempotencyPort idempotency;
    private final RequestFingerprintPort fingerprinter;
    private final IdempotentExecutionTransactions transactions;
    private final ReconcileOperationOutcomeService reconciliation;

    public AuthorizeSensitiveOperationService(IdentityProviderPort identity,PolicyAuthorizationPort policy,
            BusinessOperationPort business,RateLimitPort rateLimit,AuditEventPort audit,IdempotencyPort idempotency,
            RequestFingerprintPort fingerprinter,IdempotentExecutionTransactions transactions,ReconcileOperationOutcomeService reconciliation) {
        this.identity=identity; this.policy=policy; this.business=business; this.rateLimit=rateLimit; this.audit=audit;
        this.idempotency=idempotency; this.fingerprinter=fingerprinter; this.transactions=transactions; this.reconciliation=reconciliation;
    }

    public AuthorizationResult execute(AuthorizeOperationCommand command) {
        String keyHash=IdempotencyKey.from(command.idempotencyKey()).hash();
        String scope=fingerprinter.scope(command);
        String fingerprint=fingerprinter.fingerprint(command);
        SubjectId subject=new SubjectId(command.subjectId());
        ActorId actor=command.actorId()==null?null:new ActorId(command.actorId());
        var operation=new OperationId(command.operationId());
        var type=new OperationType(command.operationType());
        String correlation=command.correlationId()==null || command.correlationId().isBlank()?"corr-"+operation.value():command.correlationId();
        if (rateLimit.isLockedOut(subject,type,operation)) {
            long remaining=rateLimit.getRemainingLockoutSeconds(subject,type,operation);
            audit.record(AuditEvent.create("TEMPORARY_LOCKOUT_HIT",subject,actor,operation,type,"BLOCKED","N/A",
                correlation,"Operation is temporarily locked due to repeated failures"));
            throw new TemporaryLockoutException("Operation is temporarily locked",Instant.now().plusSeconds(remaining),remaining);
        }
        var auth=OperationAuthorization.initiate(operation,type,subject,actor,new IntentVersion(command.intentVersion()),
            new IntentCanonicalizer().calculateDigest(command.materialData()),"1.0.0",Duration.ofMinutes(15));
        IdempotentExecutionTransactions.Preparation prepared;
        try {
            String acr=identity.getRequiredAcr(type);
            Duration maxAge=identity.getMaxAge(type);
            var evidence=identity.getAuthenticationEvidence(subject);
            auth.evaluateAuthentication(evidence,acr,maxAge,Instant.now());
            event("AUTHENTICATION_VERIFIED",auth,correlation,"Authentication meets required strength and freshness");
            var decision=policy.evaluatePolicy(subject,actor,type,operation);
            auth.evaluatePolicy(decision);
            event("POLICY_VERIFIED",auth,correlation,"Business authorization granted");
            var existing=idempotency.find(scope,keyHash);
            if (existing.isPresent()) return resume(existing.get(),fingerprint,correlation);
            prepared=transactions.prepare(command,evidence,acr,maxAge,decision,scope,keyHash,fingerprint);
            if (!prepared.created()) return resume(prepared.entry(),fingerprint,correlation);
        } catch (IdempotencyConflictException ex) {
            throw ex;
        } catch (DomainException ex) {
            rateLimit.recordFailure(subject,type,operation);
            event("AUTHORIZATION_REJECTED",auth,correlation,"Authorization evidence was rejected; no business execution authorized");
            throw ex;
        }

        // Approval consumption and reservation have committed. Any exception after dispatch is uncertain.
        BusinessOperationPort.ExecutionResult outcome;
        try {
            outcome=business.execute(operation,type,auth.getIntentDigest(),auth.getIntentVersion());
            if (outcome==null) outcome=BusinessOperationPort.ExecutionResult.unknown();
        } catch (RuntimeException ex) {
            outcome=BusinessOperationPort.ExecutionResult.unknown();
        }
        var result=transactions.finish(prepared.entry(),outcome);
        if (result.executed()) rateLimit.resetFailures(subject,type,operation);
        else if (!result.outcomeUnknown()) rateLimit.recordFailure(subject,type,operation);
        audit.record(AuditEvent.create(result.executed()?"OPERATION_EXECUTED":result.state().name(),subject,actor,operation,type,result.state().name(),
            auth.getPolicyVersion(),correlation,"Business execution result recorded without sensitive payload"));
        return result;
    }

    private AuthorizationResult resume(Entry entry,String fingerprint,String correlation) {
        entry.record().assertFingerprint(fingerprint);
        if (entry.record().terminal()) return entry.result();
        // claimReconciliation refuses active leases and fences stale owners before querying.
        return reconciliation.reconcile(entry,correlation);
    }

    private void event(String name,OperationAuthorization auth,String correlation,String details) {
        audit.record(AuditEvent.create(name,auth.getSubjectId(),auth.getActorId(),auth.getOperationId(),auth.getOperationType(),
            auth.getState().name(),auth.getPolicyVersion(),correlation,details));
    }
}
