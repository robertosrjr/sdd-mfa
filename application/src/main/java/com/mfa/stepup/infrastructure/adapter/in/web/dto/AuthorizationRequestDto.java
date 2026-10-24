package com.mfa.stepup.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Payload da solicitação de autorização e execução de operação sensível.
 */
public record AuthorizationRequestDto(
    @NotBlank(message = "subjectId is required")
    String subjectId,

    @Pattern(regexp = "(?s).*\\S.*", message = "actorId must not be blank")
    String actorId,

    @NotBlank(message = "operationId is required")
    String operationId,

    @NotBlank(message = "operationType is required")
    String operationType,

    @NotNull(message = "intentVersion is required")
    @PositiveOrZero(message = "intentVersion must be non-negative")
    Long intentVersion,

    @NotEmpty(message = "materialData must contain operation material fields")
    Map<String, Object> materialData,

    @NotBlank(message = "approvalId is required")
    String approvalId,

    Boolean delegated
) {
    public AuthorizationRequestDto(String subjectId, String actorId, String operationId, String operationType,
            Long intentVersion, Map<String, Object> materialData, String approvalId) {
        this(subjectId, actorId, operationId, operationType, intentVersion, materialData, approvalId, null);
    }

    @AssertTrue(message = "Delegated requests require an actorId and consistent delegation context")
    @JsonIgnore
    public boolean isDelegationContextValid() {
        return delegated == null || delegated == (actorId != null && !actorId.isBlank());
    }
}

