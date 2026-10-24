package com.mfa.stepup.application.usecase;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.application.port.out.*;
import com.mfa.stepup.application.port.out.IdempotencyPort.Entry;
import com.mfa.stepup.domain.model.*;
import org.springframework.stereotype.Service;

@Service
public class ReconcileOperationOutcomeService {
    private final IdempotentExecutionTransactions transactions;
    private final BusinessOperationReconciliationPort downstream;
    private final OperationAuthorizationRepositoryPort repository;
    private final AuditEventPort audit;
    public ReconcileOperationOutcomeService(IdempotentExecutionTransactions transactions,BusinessOperationReconciliationPort downstream,
            OperationAuthorizationRepositoryPort repository,AuditEventPort audit) {
        this.transactions=transactions; this.downstream=downstream; this.repository=repository; this.audit=audit;
    }

    public AuthorizationResult reconcile(Entry entry,String correlationId) {
        var claimed=transactions.claimReconciliation(entry);
        if (claimed.record().terminal()) return claimed.result();
        var auth=repository.findById(claimed.authorizationId()).orElseThrow();
        BusinessOperationPort.ExecutionResult outcome;
        try {
            outcome=downstream.queryOutcome(auth.getOperationId(),auth.getOperationType());
            if (outcome==null) outcome=BusinessOperationPort.ExecutionResult.unknown();
        } catch (RuntimeException ex) { outcome=BusinessOperationPort.ExecutionResult.unknown(); }
        var result=transactions.finish(claimed,outcome);
        audit.record(AuditEvent.create("OPERATION_RECONCILED",auth.getSubjectId(),auth.getActorId(),auth.getOperationId(),
            auth.getOperationType(),result.state().name(),auth.getPolicyVersion(),correlationId,"Downstream outcome queried without a new business write"));
        return result;
    }
}
