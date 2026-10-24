package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.model.IdempotencyRecord;
import com.mfa.stepup.domain.model.OperationId;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class IdempotencyRecordTest {
    private final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    private IdempotencyRecord start() {
        return IdempotencyRecord.start("scope", "a".repeat(64), "b".repeat(64), new OperationId("op"),
            now, Duration.ofHours(24), Duration.ofSeconds(30));
    }
    @Test void onlyTerminalStatesCanBeReplayed() {
        assertThat(start().terminal()).isFalse();
        assertThat(start().transition(IdempotencyRecord.State.SUCCEEDED, now).terminal()).isTrue();
        assertThat(start().transition(IdempotencyRecord.State.FAILED, now).terminal()).isTrue();
    }
    @Test void uncertainOutcomeMustReconcileBeforeCompletion() {
        var unknown = start().transition(IdempotencyRecord.State.OUTCOME_UNKNOWN, now);
        assertThatThrownBy(() -> unknown.transition(IdempotencyRecord.State.SUCCEEDED, now)).isInstanceOf(IllegalStateException.class);
        var reconciling = unknown.claimReconciliation(now, Duration.ofSeconds(30));
        assertThat(reconciling.state()).isEqualTo(IdempotencyRecord.State.RECONCILING);
        assertThat(reconciling.ownerToken()).isNotEqualTo(unknown.ownerToken());
        assertThat(reconciling.transition(IdempotencyRecord.State.SUCCEEDED, now).terminal()).isTrue();
    }
    @Test void rejectsChangesToTerminalStateAndPayload() {
        assertThatThrownBy(() -> start().transition(IdempotencyRecord.State.SUCCEEDED, now)
            .transition(IdempotencyRecord.State.IN_PROGRESS, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> start().assertFingerprint("c".repeat(64))).isInstanceOf(IdempotencyConflictException.class);
    }
    @Test void activeLeaseCannotBeStolenButStaleExecutionCanBeReconciled() {
        assertThatThrownBy(() -> start().claimReconciliation(now.plusSeconds(1), Duration.ofSeconds(30)))
            .isInstanceOf(IdempotencyConflictException.class);
        assertThat(start().claimReconciliation(now.plusSeconds(31), Duration.ofSeconds(30)).state())
            .isEqualTo(IdempotencyRecord.State.RECONCILING);
    }
    @Test void rejectsMissingScopeAndInvalidRetention() {
        assertThatThrownBy(() -> IdempotencyRecord.start("", "a".repeat(64), "b".repeat(64), new OperationId("op"),
            now, Duration.ofHours(24), Duration.ofSeconds(30))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyRecord.start("scope", "a".repeat(64), "b".repeat(64), new OperationId("op"),
            now, Duration.ZERO, Duration.ofSeconds(30))).isInstanceOf(IllegalArgumentException.class);
    }
}
