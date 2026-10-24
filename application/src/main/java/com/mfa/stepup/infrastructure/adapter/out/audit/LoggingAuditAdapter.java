package com.mfa.stepup.infrastructure.adapter.out.audit;

import com.mfa.stepup.application.port.out.AuditEventPort;
import com.mfa.stepup.domain.model.AuditEvent;
import com.mfa.stepup.infrastructure.observability.AuditPrivacyPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.Objects;

/** Independent append-only audit transaction; no free text, raw identity or exception diagnostics. */
@Component
public class LoggingAuditAdapter implements AuditEventPort {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private final JdbcTemplate jdbc;
    private final AuditPrivacyPolicy privacy;

    public LoggingAuditAdapter(JdbcTemplate jdbc, AuditPrivacyPolicy privacy) {
        this.jdbc = jdbc; this.privacy = privacy;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "audit event cannot be null");
        String eventRef = privacy.reference(event.eventId());
        String subjectRef = privacy.reference(event.subjectId().value());
        String actorRef = privacy.reference(event.actorId() == null ? null : event.actorId().value());
        String operationRef = privacy.reference(event.operationId().value());
        String correlationRef = privacy.reference(event.correlationId());
        String policyRef = privacy.reference(event.policyVersion());
        String type = privacy.eventType(event.eventType());
        String decision = privacy.decision(event.decision());
        String operation = AuditPrivacyPolicy.operation(event.operationType().value());
        log.info("AUDIT_EVENT eventRef={} type={} subjectRef={} actorRef={} operationRef={} operation={} decision={} policyRef={} correlationRef={}",
            eventRef, type, subjectRef, actorRef, operationRef, operation, decision, policyRef, correlationRef);
        try {
            jdbc.update("""
                INSERT INTO audit_events(event_id,event_type,subject_id,actor_id,operation_id,operation_type,
                    decision,policy_version,correlation_id,timestamp,details) VALUES(?,?,?,?,?,?,?,?,?,?,NULL)
                """, eventRef,type,subjectRef,event.actorId() == null ? null : actorRef,operationRef,operation,
                decision,policyRef,correlationRef,Timestamp.from(event.timestamp()));
        } catch (Exception ex) {
            // JDBC exception messages and causes can include query parameters and credentials.
            log.error("Audit persistence failed eventRef={} type={}", eventRef, type);
        }
    }
}
