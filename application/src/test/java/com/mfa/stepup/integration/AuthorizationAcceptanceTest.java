package com.mfa.stepup.integration;

import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.out.BusinessOperationPort;
import com.mfa.stepup.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real HTTP server with controlled identity/policy/business adapters and a relational database. */
@SpringBootTest(classes=MfaStepupApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationAcceptanceTest extends IdempotencyIntegrationSupport {
    @Autowired TestRestTemplate http;
    private ResponseEntity<String> post(String key,String body) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key",key);
        return http.exchange("/api/v1/operation-authorizations",HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
    }
    @Test void authenticatesAuthorizesChecksIntentExecutesAndReplaysOverHttp() throws Exception {
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa",Instant.now().minusSeconds(600),"FIDO2"));
        var challenge = post(KEY,payload());
        assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(challenge.getHeaders().getFirst("WWW-Authenticate")).contains("insufficient_user_authentication");
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals",Timestamp.class)).isNull();
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa",Instant.now(),"FIDO2"));
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenReturn(AuthorizationDecision.deny("v1","REVOKED"));
        assertThat(post(KEY,payload()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenReturn(AuthorizationDecision.allow("v1"));
        assertThat(post(KEY,payload().replace("\"10\"","\"20\"")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Independent success criterion starts with a fresh attempt budget; lockout has its own test.
        rate.resetFailures(new SubjectId("idem-owner"),new OperationType("TRANSFER"),new OperationId("idem-op"));
        var executed = post(KEY,payload());
        assertThat(executed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(executed.getBody()).get("state").asText()).isEqualTo("EXECUTED");
        assertThat(post(KEY,payload()).getBody()).isEqualTo(executed.getBody());
        assertThat(post(KEY,payload().replace("\"10\"","\"20\"")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("new-key-cannot-reuse-approval",payload()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(business,times(1)).execute(any(),any(),any(),any());
    }
    @Test void unknownOutcomeIsReconciledWithoutASecondHttpExecution() throws Exception {
        when(business.execute(any(),any(),any(),any())).thenThrow(new IllegalStateException("downstream timeout"));
        assertThat(post(KEY,payload()).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        when(reconciliation.queryOutcome(any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.success("confirmed-http-result"));
        var recovered = post(KEY,payload());
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(recovered.getBody()).get("businessResultRef").asText()).isEqualTo("confirmed-http-result");
        verify(business,times(1)).execute(any(),any(),any(),any());
        verify(reconciliation,times(1)).queryOutcome(any(),any());
    }
    @Test void lockoutIsPerOperationAndDoesNotConsumeApproval() throws Exception {
        for(int i=0;i<5;i++) rate.recordFailure(new SubjectId("idem-owner"),new OperationType("TRANSFER"),new OperationId("idem-op"));
        var blocked = post(KEY,payload());
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(post(KEY,payload().replace("idem-op","other-op")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals",Timestamp.class)).isNull();
        verifyNoInteractions(business);
    }
}
