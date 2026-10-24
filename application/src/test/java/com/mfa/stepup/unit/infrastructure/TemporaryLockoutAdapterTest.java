package com.mfa.stepup.unit.infrastructure;

import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;
import com.mfa.stepup.infrastructure.adapter.out.policy.TemporaryLockoutAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryLockoutAdapterTest {

    @Test
    @DisplayName("Should lock out specific operation after max failures without locking other operations (FR-015)")
    void shouldLockoutOnlyTargetOperationAfterThreshold() {
        // maxFailures = 3, lockout = 60s, window = 60s
        TemporaryLockoutAdapter adapter = new TemporaryLockoutAdapter(3, 60, 60);

        SubjectId subject = new SubjectId("user_100");
        OperationType typeTransfer = new OperationType("FUNDS_TRANSFER");
        OperationId op1 = new OperationId("op_1");
        OperationId op2 = new OperationId("op_2");

        assertThat(adapter.isLockedOut(subject, typeTransfer, op1)).isFalse();

        // 2 failures: not yet locked
        adapter.recordFailure(subject, typeTransfer, op1);
        adapter.recordFailure(subject, typeTransfer, op1);
        assertThat(adapter.isLockedOut(subject, typeTransfer, op1)).isFalse();

        // 3rd failure: reaches limit -> locked out!
        adapter.recordFailure(subject, typeTransfer, op1);
        assertThat(adapter.isLockedOut(subject, typeTransfer, op1)).isTrue();
        assertThat(adapter.getRemainingLockoutSeconds(subject, typeTransfer, op1)).isGreaterThan(0);

        // Crucial FR-015 check: op2 for the SAME user is NOT locked out!
        assertThat(adapter.isLockedOut(subject, typeTransfer, op2)).isFalse();

        // Reset clears lockout
        adapter.resetFailures(subject, typeTransfer, op1);
        assertThat(adapter.isLockedOut(subject, typeTransfer, op1)).isFalse();
    }
}

