package com.mfa.stepup.contract;

import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.infrastructure.adapter.in.web.OperationAuthorizationController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OperationAuthorizationController.class)
@ContextConfiguration(classes = MfaStepupApplication.class)
class DelegatedOperationContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthorizeSensitiveOperationUseCase useCase;

    @Test
    void mapsDelegationAndPresentsVerifiedActorAndSubject() throws Exception {
        when(useCase.execute(any())).thenReturn(new AuthorizeSensitiveOperationUseCase.AuthorizationResult(
            "auth", "op", AuthorizationState.EXECUTED, true, false, "ref", Instant.now(), "Executed",
            new DelegationAttribution(new SubjectId("owner"), new ActorId("agent"))));
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key", "delegation-contract-key-0001").contentType(MediaType.APPLICATION_JSON)
                .content(payload("\"actorId\":\"agent\",\"delegated\":true,")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectId").value("owner"))
            .andExpect(jsonPath("$.actorId").value("agent"));
        verify(useCase).execute(argThat(c -> c.delegated() && c.actorId().equals("agent") && c.subjectId().equals("owner")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"delegated\":true,", "\"actorId\":\"  \",", "\"delegated\":false,\"actorId\":\"agent\","})
    void rejectsMissingOrInconsistentActorBeforeInvokingUseCase(String context) throws Exception {
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key", "delegation-contract-key-0001").contentType(MediaType.APPLICATION_JSON).content(payload(context)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(useCase);
    }

    @Test
    void returnsProblemDetailsWhenAgentConsentIsRejected() throws Exception {
        when(useCase.execute(any())).thenThrow(new InvalidApprovalException("Human approval by the subject is required"));
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key", "delegation-contract-key-0001").contentType(MediaType.APPLICATION_JSON)
                .content(payload("\"actorId\":\"agent\",\"delegated\":true,")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid Transaction Approval"));
    }

    private String payload(String context) {
        return "{\"subjectId\":\"owner\"," + context + "\"operationId\":\"op\",\"operationType\":\"TRANSFER\","
            + "\"intentVersion\":1,\"materialData\":{\"amount\":\"10\"},\"approvalId\":\"approval\"}";
    }
}
