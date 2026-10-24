package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DelegationAttributionTest {
    private final SubjectId subject = new SubjectId("owner");
    private final ActorId actor = new ActorId("agent");
    private final IntentDigest digest = new IntentDigest("a".repeat(64));

    @Test
    void preservesDistinctActorAndSubject() {
        var attribution = new DelegationAttribution(subject, actor);
        assertThat(attribution.subjectId()).isEqualTo(subject);
        assertThat(attribution.actorId()).isEqualTo(actor);
        assertThat(attribution.delegated()).isTrue();
        assertThat(new DelegationAttribution(subject, null).delegated()).isFalse();
    }

    @Test
    void rejectsActorImpersonatingSubject() {
        assertThatThrownBy(() -> new DelegationAttribution(subject, new ActorId("owner")))
            .isInstanceOf(InvalidApprovalException.class);
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> new DelegationAttribution(null, actor))
            .isInstanceOf(InvalidApprovalException.class);
    }

    @Test
    void requiresHumanOwnerApprovalForDelegation() {
        var attribution = new DelegationAttribution(subject, actor);
        assertThatCode(() -> attribution.validateApprover(subject, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> attribution.validateApprover(new SubjectId("agent"), true))
            .isInstanceOf(InvalidApprovalException.class);
        assertThatThrownBy(() -> attribution.validateApprover(subject, false))
            .isInstanceOf(InvalidApprovalException.class);
        assertThatThrownBy(() -> attribution.validateApprover(null, true))
            .isInstanceOf(InvalidApprovalException.class);
    }

    @Test
    void aggregateRejectsApprovalWithoutDelegatedAttribution() {
        var auth = authorization();
        var approval = TransactionApproval.approve(auth.getAuthorizationId(), subject, digest, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> auth.recordApproval(approval, digest, Instant.now()))
            .isInstanceOf(InvalidApprovalException.class);
    }

    @Test
    void aggregateAcceptsHumanOwnerConsentAndPreservesAttributionOnConsumption() {
        var auth = authorization();
        var approval = approval(auth, subject, true);
        auth.recordApproval(approval, digest, Instant.now());
        auth.prepareExecution(digest, Instant.now());
        assertThat(auth.getTransactionApproval().delegationAttribution()).isEqualTo(auth.getDelegationAttribution());
        assertThat(auth.getTransactionApproval().approverId()).isEqualTo(subject);
        assertThat(auth.getTransactionApproval().humanConfirmed()).isTrue();
    }

    @Test
    void aggregateRejectsAgentSelfApproval() {
        var auth = authorization();
        assertThatThrownBy(() -> auth.recordApproval(approval(auth, new SubjectId("agent"), true), digest, Instant.now()))
            .isInstanceOf(InvalidApprovalException.class);
    }

    @Test
    void aggregateRejectsApprovalBoundToAnotherAuthorization() {
        var auth = authorization();
        var other = authorization();
        assertThatThrownBy(() -> auth.recordApproval(approval(other, subject, true), digest, Instant.now()))
            .isInstanceOf(InvalidApprovalException.class);
    }

    private OperationAuthorization authorization() {
        return OperationAuthorization.initiate(new OperationId("op"), new OperationType("TRANSFER"), subject,
            actor, new IntentVersion(1), digest, "v1", Duration.ofMinutes(5));
    }

    private TransactionApproval approval(OperationAuthorization auth, SubjectId approver, boolean human) {
        return new TransactionApproval("approval", auth.getAuthorizationId(), subject, digest, ApprovalDecision.APPROVED,
            Instant.now(), Instant.now().plusSeconds(60), null, auth.getDelegationAttribution(), approver, human);
    }
}
