package com.mfa.stepup.infrastructure.adapter.in.web.dto;

import java.time.Instant;

/**
 * Resposta da autorização e resultado da execução da operação.
 */
public record AuthorizationResponseDto(
    String authorizationId,
    String operationId,
    String state,
    boolean executed,
    boolean outcomeUnknown,
    String businessResultRef,
    Instant executedAt,
    String message,
    String subjectId,
    String actorId
) {}

