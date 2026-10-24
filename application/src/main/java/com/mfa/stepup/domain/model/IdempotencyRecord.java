package com.mfa.stepup.domain.model;

import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable execution ownership. Expiry permits retention cleanup, never unsafe re-execution. */
public record IdempotencyRecord(String scope, String keyHash, String fingerprint, State state,
        OperationId operationId, Instant createdAt, Instant updatedAt, Instant expiresAt,
        Instant leaseUntil, String ownerToken) {
    public enum State { IN_PROGRESS, SUCCEEDED, FAILED, OUTCOME_UNKNOWN, RECONCILING }

    public IdempotencyRecord {
        if (scope == null || scope.isBlank() || scope.length() > 128) throw new IllegalArgumentException("Invalid idempotency scope");
        if (keyHash == null || !keyHash.matches("[a-f0-9]{64}") || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid request hashes");
        Objects.requireNonNull(state); Objects.requireNonNull(operationId); Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt); Objects.requireNonNull(expiresAt); Objects.requireNonNull(leaseUntil);
        if (ownerToken == null || ownerToken.isBlank()) throw new IllegalArgumentException("Missing execution owner");
    }

    public static IdempotencyRecord start(String scope, String keyHash, String fingerprint, OperationId operation,
            Instant now, Duration retention, Duration lease) {
        positive(retention); positive(lease);
        return new IdempotencyRecord(scope, keyHash, fingerprint, State.IN_PROGRESS, operation, now, now,
            now.plus(retention), now.plus(lease), UUID.randomUUID().toString());
    }

    public boolean terminal() { return state == State.SUCCEEDED || state == State.FAILED; }

    public void assertFingerprint(String requested) {
        if (!fingerprint.equals(requested)) throw new IdempotencyConflictException("Idempotency key was used with another request");
    }

    public IdempotencyRecord transition(State next, Instant now) {
        boolean allowed = switch (state) {
            case IN_PROGRESS -> next == State.SUCCEEDED || next == State.FAILED || next == State.OUTCOME_UNKNOWN;
            case OUTCOME_UNKNOWN -> next == State.RECONCILING;
            case RECONCILING -> next == State.SUCCEEDED || next == State.FAILED || next == State.OUTCOME_UNKNOWN;
            default -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid idempotency state transition");
        return new IdempotencyRecord(scope, keyHash, fingerprint, next, operationId, createdAt, now, expiresAt, now, ownerToken);
    }

    public IdempotencyRecord claimReconciliation(Instant now, Duration lease) {
        positive(lease);
        if (terminal()) throw new IllegalStateException("Terminal result cannot be reconciled");
        if (state != State.OUTCOME_UNKNOWN && now.isBefore(leaseUntil))
            throw new IdempotencyConflictException("Matching request is in progress");
        return new IdempotencyRecord(scope, keyHash, fingerprint, State.RECONCILING, operationId, createdAt,
            now, expiresAt, now.plus(lease), UUID.randomUUID().toString());
    }

    private static void positive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("Duration must be positive");
    }
}
