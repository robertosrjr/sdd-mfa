package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.AuthenticationEvidence;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;

import java.time.Duration;

/**
 * Porta de saída para o Provedor de Identidade (IdP).
 * Permite obter a evidência de autenticação da sessão e os requisitos de step-up da operação.
 */
public interface IdentityProviderPort {

    AuthenticationEvidence getAuthenticationEvidence(SubjectId subjectId);

    String getRequiredAcr(OperationType operationType);

    Duration getMaxAge(OperationType operationType);
}

