package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.OperationAuthorization;
import com.mfa.stepup.domain.model.TransactionApproval;

import java.time.Instant;
import java.util.Optional;

/**
 * Porta de persistência para o agregado OperationAuthorization e controle atômico de aprovações.
 */
public interface OperationAuthorizationRepositoryPort {

    OperationAuthorization save(OperationAuthorization authorization);

    Optional<OperationAuthorization> findById(String authorizationId);

    Optional<TransactionApproval> findApprovalById(String approvalId);

    /**
     * Consumo atômico Compare-And-Swap (CAS):
     * Compares the validated consent snapshot, including attribution, decision and expiry,
     * and requires consumed_at IS NULL before setting consumed_at.
     * Retorna true se exatamente 1 registro foi atualizado (garantindo consumo exclusivo único).
     */
    boolean consumeApprovalAtomic(TransactionApproval approval, Instant now);
}

