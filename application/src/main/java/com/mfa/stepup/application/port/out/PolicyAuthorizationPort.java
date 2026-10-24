package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.ActorId;
import com.mfa.stepup.domain.model.AuthorizationDecision;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;

/**
 * Porta de saída para o Serviço de Política de Autorização de Negócio.
 */
public interface PolicyAuthorizationPort {

    AuthorizationDecision evaluatePolicy(
        SubjectId subjectId,
        ActorId actorId,
        OperationType operationType,
        OperationId operationId
    );
}

