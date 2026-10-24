package com.mfa.stepup.integration;

import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.application.port.out.BusinessOperationPort;
import com.mfa.stepup.application.port.out.IdentityProviderPort;
import com.mfa.stepup.application.port.out.PolicyAuthorizationPort;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.AuthenticationEvidence;
import com.mfa.stepup.domain.model.AuthorizationDecision;
import com.mfa.stepup.domain.model.AuthorizationState;
import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import com.mfa.stepup.infrastructure.observability.AuditPrivacyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MfaStepupApplication.class)
class AuthorizeSensitiveOperationIntegrationTest {

    @Autowired
    private AuditPrivacyPolicy privacy;

    @Autowired
    private AuthorizeSensitiveOperationUseCase useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private IdentityProviderPort identityProviderPort;

    @MockitoBean
    private PolicyAuthorizationPort policyAuthorizationPort;

    @MockitoBean
    private BusinessOperationPort businessOperationPort;

    private final IntentCanonicalizer canonicalizer = new IntentCanonicalizer();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM idempotency_records");
        jdbcTemplate.execute("DELETE FROM audit_events");
        jdbcTemplate.execute("DELETE FROM transaction_approvals");
        jdbcTemplate.execute("DELETE FROM operation_authorizations");
    }

    @Test
    @DisplayName("End-to-End: Should successfully authorize and execute operation once, consuming approval atomically")
    void shouldAuthorizeAndExecuteOperationSuccessfully() {
        String authId = "auth-int-001";
        String approvalId = "appr-int-001";
        Map<String, Object> materialData = Map.of(
            "amount", "1000.00",
            "currency", "BRL",
            "destination", "PIX_KEY_123"
        );
        IntentDigest digest = canonicalizer.calculateDigest(materialData);

        // Prepara registro inicial de autorização e aprovação no banco
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO operation_authorizations (
                authorization_id, operation_id, operation_type, subject_id,
                intent_version, intent_digest, policy_version, state, created_at, expires_at
            ) VALUES (?, 'op-100', 'PIX_TRANSFER', 'usr-alice', 1, ?, 'v1', 'REQUESTED', ?, ?)
            """,
            authId, digest.value(), Timestamp.from(now), Timestamp.from(now.plusSeconds(900))
        );

        jdbcTemplate.update(
            """
            INSERT INTO transaction_approvals (
                approval_id, authorization_id, subject_id, intent_digest, decision, decided_at, expires_at,
                approver_id, human_confirmed
            ) VALUES (?, ?, 'usr-alice', ?, 'APPROVED', ?, ?, 'usr-alice', TRUE)
            """,
            approvalId, authId, digest.value(), Timestamp.from(now), Timestamp.from(now.plusSeconds(900))
        );

        // Configura mocks dos serviços externos
        when(identityProviderPort.getRequiredAcr(any())).thenReturn("urn:mfa:token");
        when(identityProviderPort.getMaxAge(any())).thenReturn(Duration.ofMinutes(5));
        when(identityProviderPort.getAuthenticationEvidence(any())).thenReturn(
            new AuthenticationEvidence("urn:mfa:token", now.minusSeconds(30), "FIDO2")
        );
        when(policyAuthorizationPort.evaluatePolicy(any(), any(), any(), any())).thenReturn(
            AuthorizationDecision.allow("policy_v1")
        );
        when(businessOperationPort.execute(any(), any(), any(), any())).thenReturn(
            BusinessOperationPort.ExecutionResult.success("bank_tx_ref_99999")
        );

        AuthorizeOperationCommand command = new AuthorizeOperationCommand(
            "usr-alice",
            null,
            "op-100",
            "PIX_TRANSFER",
            1L,
            materialData,
            approvalId,
            "corr-12345",
            "idemp-key-int-01"
        );

        // Executa 1ª tentativa
        AuthorizationResult result = useCase.execute(command);

        assertThat(result.executed()).isTrue();
        assertThat(result.state()).isEqualTo(AuthorizationState.EXECUTED);
        assertThat(result.businessResultRef()).isEqualTo("bank_tx_ref_99999");

        // Valida no banco que approval foi consumida (consumed_at IS NOT NULL)
        Timestamp consumedAt = jdbcTemplate.queryForObject(
            "SELECT consumed_at FROM transaction_approvals WHERE approval_id = ?",
            Timestamp.class,
            approvalId
        );
        assertThat(consumedAt).isNotNull();

        // Valida que evento de auditoria foi persistido na tabela append-only
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE correlation_id = ?",
            Integer.class, privacy.reference("corr-12345")
        );
        assertThat(auditCount).isGreaterThan(0);

        // Same key replays the durable result without another execution.
        assertThat(useCase.execute(command)).isEqualTo(result);
        org.mockito.Mockito.verify(businessOperationPort, org.mockito.Mockito.times(1)).execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("End-to-End: Should fail with InsufficientAuthenticationException when step-up criteria is not met")
    void shouldFailWhenStepUpFails() {
        String authId = "auth-int-002";
        String approvalId = "appr-int-002";
        Map<String, Object> materialData = Map.of("amount", "2000.00");
        IntentDigest digest = canonicalizer.calculateDigest(materialData);
        Instant now = Instant.now();

        // Autenticação desatualizada (stale: 10 minutos atrás, maxAge = 5 minutos)
        when(identityProviderPort.getRequiredAcr(any())).thenReturn("urn:mfa:token");
        when(identityProviderPort.getMaxAge(any())).thenReturn(Duration.ofMinutes(5));
        when(identityProviderPort.getAuthenticationEvidence(any())).thenReturn(
            new AuthenticationEvidence("urn:mfa:token", now.minusSeconds(600), "FIDO2")
        );

        AuthorizeOperationCommand command = new AuthorizeOperationCommand(
            "usr-bob",
            null,
            "op-200",
            "PIX_TRANSFER",
            1L,
            materialData,
            approvalId,
            "corr-stale",
            "idemp-stale-key-0001"
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(InsufficientAuthenticationException.class)
            .hasMessageContaining("criteria");
    }
}

