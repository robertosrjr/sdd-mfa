package com.mfa.stepup.integration;

import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.out.*;
import com.mfa.stepup.domain.exception.DomainException;
import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import com.mfa.stepup.infrastructure.observability.AuditPrivacyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = MfaStepupApplication.class)
class DelegatedOperationIntegrationTest {
    @Autowired AuditPrivacyPolicy privacy;
    @Autowired AuthorizeSensitiveOperationUseCase useCase;
    @Autowired OperationAuthorizationRepositoryPort repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimitPort rateLimit;
    @MockitoBean IdentityProviderPort identity;
    @MockitoBean PolicyAuthorizationPort policy;
    @MockitoBean BusinessOperationPort business;

    private final Map<String, Object> material = Map.of("amount", "10", "currency", "BRL");

    @BeforeEach
    void seedTrustedHumanConsent() {
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM transaction_approvals");
        jdbc.update("DELETE FROM operation_authorizations");
        rateLimit.resetFailures(new SubjectId("owner"), new OperationType("TRANSFER"), new OperationId("op"));
        var now = Instant.now();
        String digest = new IntentCanonicalizer().calculateDigest(material).value();
        jdbc.update("""
            INSERT INTO operation_authorizations
                (authorization_id, operation_id, operation_type, subject_id, actor_id, intent_version,
                 intent_digest, policy_version, state, created_at, expires_at)
            VALUES ('auth', 'op', 'TRANSFER', 'owner', 'agent', 1, ?, 'v1', 'REQUESTED', ?, ?)
            """, digest, Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
        jdbc.update("""
            INSERT INTO transaction_approvals
                (approval_id, authorization_id, subject_id, actor_id, intent_digest, decision,
                 decided_at, expires_at, approver_id, human_confirmed)
            VALUES ('approval', 'auth', 'owner', 'agent', ?, 'APPROVED', ?, ?, 'owner', TRUE)
            """, digest, Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
        when(identity.getRequiredAcr(any())).thenReturn("mfa");
        when(identity.getMaxAge(any())).thenReturn(Duration.ofMinutes(5));
        when(identity.getAuthenticationEvidence(any())).thenReturn(new AuthenticationEvidence("mfa", now, "FIDO2"));
        when(policy.evaluatePolicy(any(), any(), any(), any())).thenReturn(AuthorizationDecision.allow("v1"));
        when(business.execute(any(), any(), any(), any())).thenReturn(BusinessOperationPort.ExecutionResult.success("ref"));
    }

    @Test
    void executesOnceWithHumanOwnerConsentAndAuditsBothIdentities() {
        var result = useCase.execute(command("owner", "agent", "op", "TRANSFER", 1, material, "approval"));
        assertThat(result.executed()).isTrue();
        assertThat(result.authorizationId()).isEqualTo("auth");
        assertThat(result.delegationAttribution()).isEqualTo(new DelegationAttribution(new SubjectId("owner"), new ActorId("agent")));
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals WHERE approval_id='approval'", Timestamp.class)).isNotNull();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM audit_events WHERE event_type='OPERATION_EXECUTED'
              AND subject_id=? AND actor_id=? AND operation_id=?
            """, Integer.class,privacy.reference("owner"),privacy.reference("agent"),privacy.reference("op"))).isEqualTo(1);
        assertThat(useCase.execute(command("owner", "agent", "op", "TRANSFER", 1, material, "approval"))).isEqualTo(result);
        verify(business, times(1)).execute(any(), any(), any(), any());
        verify(policy, atLeastOnce()).evaluatePolicy(new SubjectId("owner"), new ActorId("agent"), new OperationType("TRANSFER"), new OperationId("op"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"actorMismatch", "missingActor", "subjectMismatch", "agentApproval", "notHuman",
        "missingApprover", "approvalAttribution", "wrongOperation", "wrongType", "changedIntent", "changedVersion",
        "rejected", "cancelled", "expired", "authorizationExpired", "missingApproval"})
    void rejectsInvalidDelegationWithoutConsumptionOrExecution(String scenario) {
        String subject = "owner", actor = "agent", operation = "op", type = "TRANSFER", approval = "approval";
        long version = 1;
        Map<String, Object> data = material;
        switch (scenario) {
            case "actorMismatch" -> actor = "other-agent";
            case "missingActor" -> actor = null;
            case "subjectMismatch" -> subject = "other-owner";
            case "agentApproval" -> jdbc.update("UPDATE transaction_approvals SET approver_id='agent'");
            case "notHuman" -> jdbc.update("UPDATE transaction_approvals SET human_confirmed=FALSE");
            case "missingApprover" -> jdbc.update("UPDATE transaction_approvals SET approver_id=NULL");
            case "approvalAttribution" -> jdbc.update("UPDATE transaction_approvals SET actor_id=NULL");
            case "wrongOperation" -> operation = "other-op";
            case "wrongType" -> type = "OTHER";
            case "changedIntent" -> data = Map.of("amount", "999");
            case "changedVersion" -> version = 2;
            case "rejected" -> jdbc.update("UPDATE transaction_approvals SET decision='REJECTED'");
            case "cancelled" -> jdbc.update("UPDATE transaction_approvals SET decision='CANCELLED'");
            case "expired" -> jdbc.update("UPDATE transaction_approvals SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(60)));
            case "authorizationExpired" -> jdbc.update("UPDATE operation_authorizations SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(60)));
            case "missingApproval" -> approval = "absent";
            default -> throw new AssertionError(scenario);
        }
        var request = command(subject, actor, operation, type, version, data, approval);
        assertThatThrownBy(() -> useCase.execute(request)).isInstanceOf(DomainException.class);
        verifyNoInteractions(business);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals WHERE approval_id='approval'", Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_type='AUTHORIZATION_REJECTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void policyDenialCannotBeOverriddenByHumanConsent() {
        when(policy.evaluatePolicy(any(), any(), any(), any())).thenReturn(AuthorizationDecision.deny("v1", "NO_DELEGATION"));
        assertThatThrownBy(() -> useCase.execute(command("owner", "agent", "op", "TRANSFER", 1, material, "approval")))
            .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(business);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals WHERE approval_id='approval'", Timestamp.class)).isNull();
    }

    @Test
    void atomicConsumptionRejectsConsentChangedAfterRead() {
        var snapshot = repository.findApprovalById("approval").orElseThrow();
        jdbc.update("UPDATE transaction_approvals SET approver_id='agent'");
        assertThat(repository.consumeApprovalAtomic(snapshot, Instant.now())).isFalse();
    }

    private AuthorizeOperationCommand command(String subject, String actor, String operation, String type,
            long version, Map<String, Object> data, String approval) {
        return new AuthorizeOperationCommand(subject, actor, operation, type, version, data, approval, "corr-delegation", "key-delegation-0001");
    }
}
