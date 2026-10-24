package com.mfa.stepup.integration;

import com.mfa.stepup.application.port.out.BusinessOperationPort;
import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.infrastructure.adapter.out.persistence.JpaIdempotencyRepository;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConcurrentExecutionTest extends IdempotencyIntegrationSupport {
    @Test
    void concurrentSameKeyExecutesOnceWithApprovalCommittedBeforeDispatch() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(business.execute(any(),any(),any(),any())).thenAnswer(call -> {
            entered.countDown(); if (!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("Test dispatch timed out");
            return BusinessOperationPort.ExecutionResult.success("concurrent-result");
        });
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> useCase.execute(command(KEY)));
            try {
                assertThat(entered.await(15,TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("SELECT consumed_at FROM transaction_approvals WHERE approval_id='idem-approval'",Timestamp.class)).isNotNull();
                assertThat(entry().record().state().name()).isEqualTo("IN_PROGRESS");
                var second=executor.submit(() -> useCase.execute(command(KEY)));
                assertThatThrownBy(() -> second.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(IdempotencyConflictException.class);
            } finally { release.countDown(); }
            var result=first.get(15,TimeUnit.SECONDS);
            assertThat(result.executed()).isTrue();
            assertThat(useCase.execute(command(KEY))).isEqualTo(result);
            var restartedRepository=new JpaIdempotencyRepository(jdbc,mapper);
            assertThat(restartedRepository.find(entry().record().scope(),entry().record().keyHash()).orElseThrow().result()).isEqualTo(result);
        }
        verify(business,times(1)).execute(any(),any(),any(),any());
    }

    @Test
    void anotherKeyCannotReuseConsumedApproval() {
        useCase.execute(command(KEY));
        assertThatThrownBy(() -> useCase.execute(command("different-idempotency-key"))).isInstanceOf(InvalidApprovalException.class);
        verify(business,times(1)).execute(any(),any(),any(),any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records",Integer.class)).isEqualTo(1);
    }
}
