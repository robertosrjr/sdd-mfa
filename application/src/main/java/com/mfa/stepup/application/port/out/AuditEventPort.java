package com.mfa.stepup.application.port.out;

import com.mfa.stepup.domain.model.AuditEvent;

/**
 * Porta de saída para publicação e persistência de eventos de auditoria de segurança.
 */
public interface AuditEventPort {

    void record(AuditEvent event);
}

