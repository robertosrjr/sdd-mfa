package com.mfa.stepup.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.AuthorizationState;
import com.mfa.stepup.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.mfa.stepup.infrastructure.adapter.in.web.OperationAuthorizationController;
import com.mfa.stepup.infrastructure.adapter.in.web.dto.AuthorizationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OperationAuthorizationController.class)
@ContextConfiguration(classes = MfaStepupApplication.class)
@Import(GlobalExceptionHandler.class)
class OperationAuthorizationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthorizeSensitiveOperationUseCase useCase;

    @Test
    @DisplayName("POST /api/v1/operation-authorizations should return 200 OK on valid execution")
    void shouldReturn200OnSuccessfulExecution() throws Exception {
        AuthorizationResult mockResult = new AuthorizationResult(
            "auth_123",
            "op_999",
            AuthorizationState.EXECUTED,
            true,
            false,
            "tx_confirmed_abc",
            Instant.now(),
            "Operation successfully authorized and executed"
        );

        when(useCase.execute(any())).thenReturn(mockResult);

        AuthorizationRequestDto request = new AuthorizationRequestDto(
            "usr_100",
            null,
            "op_999",
            "FUNDS_TRANSFER",
            1L,
            Map.of("amount", "5000.00", "currency", "BRL"),
            "appr_456"
        );

        mockMvc.perform(post("/api/v1/operation-authorizations")
                .header("Idempotency-Key", "idemp-key-test-123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorizationId").value("auth_123"))
            .andExpect(jsonPath("$.operationId").value("op_999"))
            .andExpect(jsonPath("$.state").value("EXECUTED"))
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.businessResultRef").value("tx_confirmed_abc"));
    }

    @Test
    @DisplayName("POST /api/v1/operation-authorizations should return 401 with RFC 9470 WWW-Authenticate header")
    void shouldReturn401WithRfc9470HeaderWhenStepUpRequired() throws Exception {
        when(useCase.execute(any())).thenThrow(new InsufficientAuthenticationException(
            "MFA step-up required for high value transaction",
            "urn:mfa:hardware_token",
            300
        ));

        AuthorizationRequestDto request = new AuthorizationRequestDto(
            "usr_100",
            null,
            "op_999",
            "FUNDS_TRANSFER",
            1L,
            Map.of("amount", "5000.00"),
            "appr_456"
        );

        mockMvc.perform(post("/api/v1/operation-authorizations")
                .header("Idempotency-Key", "idemp-key-test-123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("insufficient_user_authentication")))
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("acr_values=\"urn:mfa:hardware_token\"")))
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("max_age=300")))
            .andExpect(jsonPath("$.title").value("Insufficient User Authentication"));
    }

    @Test
    @DisplayName("POST /api/v1/operation-authorizations should return 403 when business policy denies operation")
    void shouldReturn403WhenPolicyDenies() throws Exception {
        when(useCase.execute(any())).thenThrow(new AuthorizationDeniedException(
            "Transfer limit exceeded for account tier",
            "policy_v1.0",
            "LIMIT_EXCEEDED"
        ));

        AuthorizationRequestDto request = new AuthorizationRequestDto(
            "usr_100",
            null,
            "op_999",
            "FUNDS_TRANSFER",
            1L,
            Map.of("amount", "5000.00"),
            "appr_456"
        );

        mockMvc.perform(post("/api/v1/operation-authorizations")
                .header("Idempotency-Key", "idemp-key-test-123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Authorization Denied"))
            .andExpect(jsonPath("$.reason_code").value("LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("POST /api/v1/operation-authorizations should return 400 when approval is invalid or consumed")
    void shouldReturn400WhenApprovalInvalid() throws Exception {
        when(useCase.execute(any())).thenThrow(new InvalidApprovalException(
            "Approval has already been consumed"
        ));

        AuthorizationRequestDto request = new AuthorizationRequestDto(
            "usr_100",
            null,
            "op_999",
            "FUNDS_TRANSFER",
            1L,
            Map.of("amount", "5000.00"),
            "appr_456"
        );

        mockMvc.perform(post("/api/v1/operation-authorizations")
                .header("Idempotency-Key", "idemp-key-test-123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid Transaction Approval"));
    }
}

