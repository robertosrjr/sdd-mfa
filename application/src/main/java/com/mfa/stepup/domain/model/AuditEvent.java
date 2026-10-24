package com.mfa.stepup.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoria append-only para transições de estado e decisões de segurança críticas.
 * Em estrita conformidade com a Constituição (Princípio IV), este evento não retém nem emite
 * credenciais, tokens, segredos ou dados pessoais excessivos.
 */
public record AuditEvent(
    String eventId,
    String eventType,
    SubjectId subjectId,
    ActorId actorId,
    OperationId operationId,
    OperationType operationType,
    String decision,
    String policyVersion,
    String correlationId,
    Instant timestamp,
    String details
) {
    public AuditEvent {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(operationType, "operationType cannot be null");
        Objects.requireNonNull(decision, "decision cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static AuditEvent create(
        String eventType,
        SubjectId subjectId,
        ActorId actorId,
        OperationId operationId,
        OperationType operationType,
        String decision,
        String policyVersion,
        String correlationId,
        String details
    ) {
        return new AuditEvent(
            UUID.randomUUID().toString(),
            eventType,
            subjectId,
            actorId,
            operationId,
            operationType,
            decision,
            policyVersion,
            correlationId,
            Instant.now(),
            details
        );
    }
}

