package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.InsufficientAuthenticationException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.exception.InvalidIntentException;
import com.mfa.stepup.domain.model.ApprovalDecision;
import com.mfa.stepup.domain.model.AuthenticationEvidence;
import com.mfa.stepup.domain.model.AuthorizationDecision;
import com.mfa.stepup.domain.model.AuthorizationState;
import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.model.IntentVersion;
import com.mfa.stepup.domain.model.OperationAuthorization;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;
import com.mfa.stepup.domain.model.TransactionApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuthorizationTest {

    private static final String VALID_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String OTHER_HASH = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";

    private OperationAuthorization auth;
    private IntentDigest digest;
    private SubjectId subjectId;

    @BeforeEach
    void setUp() {
        digest = new IntentDigest(VALID_HASH);
        subjectId = new SubjectId("user_123");
        auth = OperationAuthorization.initiate(
            new OperationId("op_999"),
            new OperationType("TRANSFER"),
            subjectId,
            null,
            new IntentVersion(1L),
            digest,
            "policy_v1",
            Duration.ofMinutes(15)
        );
    }

    @Test
    @DisplayName("Should require step-up when authentication is insufficient or stale (RFC 9470 / FR-003)")
    void shouldRequireStepUpOnWeakOrStaleAuth() {
        Instant now = Instant.now();
        // Stale evidence
        AuthenticationEvidence staleEvidence = new AuthenticationEvidence(
            "mfa_token",
            now.minusSeconds(600),
            "FIDO2"
        );

        assertThatThrownBy(() -> auth.evaluateAuthentication(staleEvidence, "mfa_token", Duration.ofSeconds(300), now))
            .isInstanceOf(InsufficientAuthenticationException.class)
            .hasMessageContaining("criteria");

        assertThat(auth.getState()).isEqualTo(AuthorizationState.AUTHENTICATION_REQUIRED);
    }

    @Test
    @DisplayName("Should deny execution when business policy returns DENY (FR-003)")
    void shouldDenyWhenPolicyRejects() {
        Instant now = Instant.now();
        AuthenticationEvidence goodAuth = new AuthenticationEvidence("mfa_token", now.minusSeconds(10), "FIDO2");
        auth.evaluateAuthentication(goodAuth, "mfa_token", Duration.ofSeconds(300), now);

        AuthorizationDecision denied = AuthorizationDecision.deny("policy_v1", "LIMIT_EXCEEDED");

        assertThatThrownBy(() -> auth.evaluatePolicy(denied))
            .isInstanceOf(AuthorizationDeniedException.class)
            .hasMessageContaining("denied");

        assertThat(auth.getState()).isEqualTo(AuthorizationState.DENIED);
    }

    @Test
    @DisplayName("Should reject execution if material intent changes after approval (FR-006 / SC-002)")
    void shouldRejectWhenIntentChangesAfterApproval() {
        Instant now = Instant.now();
        AuthenticationEvidence goodAuth = new AuthenticationEvidence("mfa_token", now, "FIDO2");
        auth.evaluateAuthentication(goodAuth, "mfa_token", Duration.ofSeconds(300), now);
        auth.evaluatePolicy(AuthorizationDecision.allow("policy_v1"));

        TransactionApproval approval = TransactionApproval.approve(
            auth.getAuthorizationId(),
            subjectId,
            digest,
            now.plusSeconds(300)
        );

        // Intenção mudou!
        IntentDigest alteredDigest = new IntentDigest(OTHER_HASH);

        assertThatThrownBy(() -> auth.recordApproval(approval, alteredDigest, now))
            .isInstanceOf(InvalidIntentException.class)
            .hasMessageContaining("digest mismatch");

        assertThat(auth.getState()).isEqualTo(AuthorizationState.CANCELLED);
    }

    @Test
    @DisplayName("Should complete single-use approval consumption and prevent second execution (FR-007 / SC-003)")
    void shouldConsumeApprovalOnceAndPreventReplay() {
        Instant now = Instant.now();
        auth.evaluateAuthentication(new AuthenticationEvidence("mfa_token", now, "FIDO2"), "mfa_token", Duration.ofSeconds(300), now);
        auth.evaluatePolicy(AuthorizationDecision.allow("policy_v1"));

        TransactionApproval approval = TransactionApproval.approve(
            auth.getAuthorizationId(),
            subjectId,
            digest,
            now.plusSeconds(300)
        );

        auth.recordApproval(approval, digest, now);
        assertThat(auth.getState()).isEqualTo(AuthorizationState.USER_APPROVED);

        // 1st consumption: succeeds
        auth.prepareExecution(digest, now);
        assertThat(auth.getState()).isEqualTo(AuthorizationState.EXECUTING);
        assertThat(auth.getTransactionApproval().isConsumed()).isTrue();

        // 2nd consumption: MUST fail
        assertThatThrownBy(() -> auth.prepareExecution(digest, now))
            .isInstanceOf(IllegalStateException.class);
    }
}

