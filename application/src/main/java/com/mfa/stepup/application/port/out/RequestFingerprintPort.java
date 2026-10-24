package com.mfa.stepup.application.port.out;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;

public interface RequestFingerprintPort {
    String fingerprint(AuthorizeOperationCommand command);
    String scope(AuthorizeOperationCommand command);
}
