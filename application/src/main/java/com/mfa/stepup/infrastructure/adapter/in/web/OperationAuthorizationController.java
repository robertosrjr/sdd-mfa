package com.mfa.stepup.infrastructure.adapter.in.web;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.infrastructure.adapter.in.web.dto.AuthorizationRequestDto;
import com.mfa.stepup.infrastructure.adapter.in.web.dto.AuthorizationResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de entrada REST para autorização e execução de operações sensíveis.
 * Conforme o contrato OpenAPI em `contracts/operation-authorization.openapi.yaml`.
 */
@RestController
@RequestMapping("/api/v1/operation-authorizations")
public class OperationAuthorizationController {

    private final AuthorizeSensitiveOperationUseCase useCase;

    public OperationAuthorizationController(AuthorizeSensitiveOperationUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase);
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponseDto> authorizeOperation(
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
        @Valid @RequestBody AuthorizationRequestDto request
    ) {
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
            ? correlationId
            : UUID.randomUUID().toString();

        AuthorizeOperationCommand command = new AuthorizeOperationCommand(
            request.subjectId(),
            request.actorId(),
            request.operationId(),
            request.operationType(),
            request.intentVersion(),
            request.materialData(),
            request.approvalId(),
            effectiveCorrelationId,
            idempotencyKey,
            request.delegated() != null ? request.delegated() : request.actorId() != null
        );

        AuthorizationResult result = useCase.execute(command);

        AuthorizationResponseDto response = new AuthorizationResponseDto(
            result.authorizationId(),
            result.operationId(),
            result.state().name(),
            result.executed(),
            result.outcomeUnknown(),
            result.businessResultRef(),
            result.executedAt(),
            result.message(),
            result.delegationAttribution() == null ? null : result.delegationAttribution().subjectId().value(),
            result.delegationAttribution() == null || result.delegationAttribution().actorId() == null
                ? null : result.delegationAttribution().actorId().value()
        );

        if (result.outcomeUnknown()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(response);
    }
}

