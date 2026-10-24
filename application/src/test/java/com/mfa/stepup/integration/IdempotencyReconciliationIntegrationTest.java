package com.mfa.stepup.integration;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.out.BusinessOperationPort;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdempotencyReconciliationIntegrationTest extends IdempotencyIntegrationSupport {
    @Test
    void timeoutReturns202ThenQueriesAndReplaysConfirmedResultWithoutAnotherWrite() throws Exception {
        when(business.execute(any(),any(),any(),any())).thenThrow(new RuntimeException("private downstream timeout details"));
        when(reconciliation.queryOutcome(any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.success("confirmed-ref"));
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(payload()))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("OUTCOME_UNKNOWN"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals WHERE approval_id='idem-approval'",Timestamp.class)).isNotNull();
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(payload()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.businessResultRef").value("confirmed-ref"));
        var confirmed=useCase.execute(command(KEY));
        assertThat(confirmed).isEqualTo(entry().result());
        assertThat(entry().record().state().name()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT state FROM operation_authorizations WHERE authorization_id='idem-auth'",String.class)).isEqualTo("EXECUTED");
        verify(business,times(1)).execute(any(),any(),any(),any());
        verify(reconciliation,times(1)).queryOutcome(any(),any());
    }

    @Test
    void queryFailureKeepsUncertainStateAndDoesNotReexecute() {
        when(business.execute(any(),any(),any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.unknown());
        useCase.execute(command(KEY));
        when(reconciliation.queryOutcome(any(),any())).thenThrow(new RuntimeException("unavailable"));
        assertThat(useCase.execute(command(KEY)).outcomeUnknown()).isTrue();
        assertThat(entry().record().state().name()).isEqualTo("OUTCOME_UNKNOWN");
        verify(business,times(1)).execute(any(),any(),any(),any());
    }

    @Test
    void failedTerminalResultIsReplayed() {
        when(business.execute(any(),any(),any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.failed("private rejection"));
        var first=useCase.execute(command(KEY));
        assertThat(first.state()).isEqualTo(AuthorizationState.EXECUTION_FAILED);
        assertThat(useCase.execute(command(KEY))).isEqualTo(first);
        assertThat(entry().record().state().name()).isEqualTo("FAILED");
        verify(business,times(1)).execute(any(),any(),any(),any()); verifyNoInteractions(reconciliation);
    }

    @Test
    void changedPayloadWithSameKeyReturns409() throws Exception {
        useCase.execute(command(KEY));
        String altered=payload().replace("\"10\"","\"20\"");
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(altered))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Idempotency Conflict"));
        verify(business,times(1)).execute(any(),any(),any(),any());
        // Only hashes and the minimal response are persisted, never raw keys or transaction payloads.
        var row=jdbc.queryForMap("SELECT key_hash,request_fingerprint,response_body FROM idempotency_records");
        assertThat(row.toString()).doesNotContain(KEY,"\"amount\"");
    }

    @Test
    void staleExecutionAfterProcessInterruptionQueriesWithoutAnotherWrite() {
        when(business.execute(any(),any(),any(),any())).thenAnswer(call -> { throw new AssertionError("Simulated process termination"); });
        assertThatThrownBy(() -> useCase.execute(command(KEY))).isInstanceOf(AssertionError.class);
        assertThat(entry().record().state().name()).isEqualTo("IN_PROGRESS");
        jdbc.update("UPDATE idempotency_records SET lease_until=?",Timestamp.from(Instant.now().minusSeconds(60)));
        when(reconciliation.queryOutcome(any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.success("recovered"));
        assertThat(useCase.execute(command(KEY)).businessResultRef()).isEqualTo("recovered");
        verify(business,times(1)).execute(any(),any(),any(),any());
    }

    @Test
    void expiredRetentionCannotDiscardUncertainOutcome() {
        when(business.execute(any(),any(),any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.unknown());
        useCase.execute(command(KEY));
        jdbc.update("UPDATE idempotency_records SET expires_at=?",Timestamp.from(Instant.now().minusSeconds(86400)));
        assertThat(useCase.execute(command(KEY)).outcomeUnknown()).isTrue();
        verify(business,times(1)).execute(any(),any(),any(),any());
        verify(reconciliation,times(1)).queryOutcome(any(),any());
    }

    @Test
    void stepUpFailureDoesNotReserveKeyOrConsumeApproval() {
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa",Instant.now().minusSeconds(600),"FIDO2"));
        assertThatThrownBy(() -> useCase.execute(command(KEY))).isInstanceOf(InsufficientAuthenticationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals",Timestamp.class)).isNull();
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa",Instant.now(),"FIDO2"));
        assertThat(useCase.execute(command(KEY)).executed()).isTrue();
    }

    @Test
    void terminalReplayStillRequiresCurrentPolicy() {
        useCase.execute(command(KEY));
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenReturn(AuthorizationDecision.deny("v2","REVOKED"));
        assertThatThrownBy(() -> useCase.execute(command(KEY))).isInstanceOf(com.mfa.stepup.domain.exception.AuthorizationDeniedException.class);
        verify(business,times(1)).execute(any(),any(),any(),any());
    }

    @Test
    void staleOwnerCannotOverwriteReconciledSuccess() {
        when(business.execute(any(),any(),any(),any())).thenAnswer(call -> { throw new AssertionError("crash"); });
        assertThatThrownBy(() -> useCase.execute(command(KEY))).isInstanceOf(AssertionError.class);
        var stale=entry();
        jdbc.update("UPDATE idempotency_records SET lease_until=?",Timestamp.from(Instant.now().minusSeconds(60)));
        when(reconciliation.queryOutcome(any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.success("confirmed"));
        var confirmed=useCase.execute(command(KEY));
        assertThat(transactions.finish(stale,BusinessOperationPort.ExecutionResult.failed("late failure"))).isEqualTo(confirmed);
        assertThat(jdbc.queryForObject("SELECT state FROM operation_authorizations",String.class)).isEqualTo("EXECUTED");
    }

    @Test
    void concurrentReconciliationHasOneQueryAndNoNewWrite() throws Exception {
        when(business.execute(any(),any(),any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.unknown());
        useCase.execute(command(KEY));
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(reconciliation.queryOutcome(any(),any())).thenAnswer(call -> {
            entered.countDown(); if (!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("Query timed out");
            return BusinessOperationPort.ExecutionResult.success("confirmed");
        });
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> useCase.execute(command(KEY)));
            try {
                assertThat(entered.await(15,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> useCase.execute(command(KEY)))
                    .isInstanceOf(com.mfa.stepup.domain.exception.IdempotencyConflictException.class);
            } finally { release.countDown(); }
            assertThat(first.get(15,TimeUnit.SECONDS).executed()).isTrue();
        }
        verify(reconciliation,times(1)).queryOutcome(any(),any());
        verify(business,times(1)).execute(any(),any(),any(),any());
    }
}
