package com.mfa.stepup.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.out.*;
import com.mfa.stepup.application.usecase.IdempotentExecutionTransactions;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes=MfaStepupApplication.class)
@AutoConfigureMockMvc
abstract class IdempotencyIntegrationSupport {
    @Autowired protected AuthorizeSensitiveOperationUseCase useCase;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected IdempotencyPort idempotency;
    @Autowired protected RequestFingerprintPort fingerprints;
    @Autowired protected RateLimitPort rate;
    @Autowired protected IdempotentExecutionTransactions transactions;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected MockMvc mvc;
    @MockitoBean protected IdentityProviderPort identity;
    @MockitoBean protected PolicyAuthorizationPort policy;
    @MockitoBean protected BusinessOperationPort business;
    @MockitoBean protected BusinessOperationReconciliationPort reconciliation;
    protected static final String KEY="idempotency-test-key-0001";
    protected final Map<String,Object> material=Map.of("amount","10","currency","BRL");

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM idempotency_records"); jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM transaction_approvals"); jdbc.update("DELETE FROM operation_authorizations");
        rate.resetFailures(new SubjectId("idem-owner"),new OperationType("TRANSFER"),new OperationId("idem-op"));
        var now=Instant.now(); var digest=new IntentCanonicalizer().calculateDigest(material).value();
        jdbc.update("""
            INSERT INTO operation_authorizations(authorization_id,operation_id,operation_type,subject_id,
                intent_version,intent_digest,policy_version,state,created_at,expires_at)
            VALUES('idem-auth','idem-op','TRANSFER','idem-owner',1,?,'v1','REQUESTED',?,?)
            """,digest,Timestamp.from(now),Timestamp.from(now.plusSeconds(300)));
        jdbc.update("""
            INSERT INTO transaction_approvals(approval_id,authorization_id,subject_id,intent_digest,decision,
                decided_at,expires_at,approver_id,human_confirmed)
            VALUES('idem-approval','idem-auth','idem-owner',?,'APPROVED',?,?,'idem-owner',TRUE)
            """,digest,Timestamp.from(now),Timestamp.from(now.plusSeconds(300)));
        when(identity.getRequiredAcr(any())).thenReturn("mfa"); when(identity.getMaxAge(any())).thenReturn(Duration.ofMinutes(5));
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa",now,"FIDO2"));
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenReturn(AuthorizationDecision.allow("v1"));
        when(business.execute(any(),any(),any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.success("result-ref"));
        when(reconciliation.queryOutcome(any(),any())).thenReturn(BusinessOperationPort.ExecutionResult.unknown());
    }
    protected AuthorizeOperationCommand command(String key) {
        return new AuthorizeOperationCommand("idem-owner",null,"idem-op","TRANSFER",1,material,"idem-approval","idem-correlation",key);
    }
    protected IdempotencyPort.Entry entry() {
        return idempotency.find(fingerprints.scope(command(KEY)),IdempotencyKey.from(KEY).hash()).orElseThrow();
    }
    protected String payload() throws Exception {
        return mapper.writeValueAsString(Map.of("subjectId","idem-owner","operationId","idem-op","operationType","TRANSFER",
            "intentVersion",1,"materialData",material,"approvalId","idem-approval"));
    }
}
