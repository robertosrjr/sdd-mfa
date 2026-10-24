package com.mfa.stepup.infrastructure.adapter.out.policy;

import com.mfa.stepup.application.port.out.RateLimitPort;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de limitação temporária por operação (FR-015 / SC-007).
 * Mantém contagem de falhas recentes isoladas por (subject, operationType, operationId),
 * aplicando bloqueio temporário após exceder o limite sem bloquear a conta inteira do usuário.
 */
@Component
public class TemporaryLockoutAdapter implements RateLimitPort {

    private final int maxFailures;
    private final Duration lockoutDuration;
    private final Duration windowDuration;

    private final Map<String, LockoutState> states = new ConcurrentHashMap<>();

    public TemporaryLockoutAdapter(
        @Value("${mfa.stepup.rate-limit.max-failures:3}") int maxFailures,
        @Value("${mfa.stepup.rate-limit.lockout-seconds:900}") long lockoutSeconds,
        @Value("${mfa.stepup.rate-limit.window-seconds:300}") long windowSeconds
    ) {
        this.maxFailures = maxFailures;
        this.lockoutDuration = Duration.ofSeconds(lockoutSeconds);
        this.windowDuration = Duration.ofSeconds(windowSeconds);
    }

    @Override
    public boolean isLockedOut(SubjectId subjectId, OperationType operationType, OperationId operationId) {
        String key = buildKey(subjectId, operationType, operationId);
        LockoutState state = states.get(key);
        if (state == null) {
            return false;
        }

        Instant now = Instant.now();
        if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
            return true;
        }

        if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
            // Lockout expirou: limpa o estado
            states.remove(key);
            return false;
        }

        return false;
    }

    @Override
    public long getRemainingLockoutSeconds(SubjectId subjectId, OperationType operationType, OperationId operationId) {
        String key = buildKey(subjectId, operationType, operationId);
        LockoutState state = states.get(key);
        if (state == null || state.lockedUntil == null) {
            return 0;
        }

        long remaining = Duration.between(Instant.now(), state.lockedUntil).toSeconds();
        return Math.max(0, remaining);
    }

    @Override
    public void recordFailure(SubjectId subjectId, OperationType operationType, OperationId operationId) {
        String key = buildKey(subjectId, operationType, operationId);
        Instant now = Instant.now();

        states.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.firstFailureAt.plus(windowDuration))) {
                return new LockoutState(1, now, null);
            }

            int newCount = existing.failureCount + 1;
            Instant lockedUntil = (newCount >= maxFailures) ? now.plus(lockoutDuration) : null;
            return new LockoutState(newCount, existing.firstFailureAt, lockedUntil);
        });
    }

    @Override
    public void resetFailures(SubjectId subjectId, OperationType operationType, OperationId operationId) {
        String key = buildKey(subjectId, operationType, operationId);
        states.remove(key);
    }

    private String buildKey(SubjectId subjectId, OperationType operationType, OperationId operationId) {
        return subjectId.value() + ":" + operationType.value() + ":" + operationId.value();
    }

    private record LockoutState(int failureCount, Instant firstFailureAt, Instant lockedUntil) {}
}

