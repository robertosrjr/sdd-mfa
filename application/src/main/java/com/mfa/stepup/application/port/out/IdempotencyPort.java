package com.mfa.stepup.application.port.out;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.domain.model.IdempotencyRecord;
import java.util.Optional;

public interface IdempotencyPort {
    record Entry(IdempotencyRecord record, String authorizationId, AuthorizationResult result) {}
    Optional<Entry> find(String scope, String keyHash);
    void create(Entry entry);
    /** Fences stale executors/reconcilers by owner token and state. */
    boolean replaceIfOwned(Entry previous, Entry next);
}
