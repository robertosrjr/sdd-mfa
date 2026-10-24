package com.mfa.stepup.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.mfa.stepup.application.port.out.AuditEventPort;
import com.mfa.stepup.domain.exception.AuthorizationDeniedException;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.*;
import com.mfa.stepup.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.mfa.stepup.infrastructure.adapter.out.audit.LoggingAuditAdapter;
import com.mfa.stepup.infrastructure.observability.AuditPrivacyPolicy;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.SQLException;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditPrivacyComplianceTest extends IdempotencyIntegrationSupport {
    private static final String EMAIL="private-owner@example.invalid";
    private static final String ACTOR="private-agent@example.invalid";
    private static final String TOKEN="Bearer private-access-token-123";
    private static final String PASSWORD="private-password-456";
    private static final String AMOUNT="987654.321";
    @Autowired AuditEventPort audit;
    @Autowired GlobalExceptionHandler errors;
    @Autowired AuditPrivacyPolicy privacy;
    private Logger root;
    private ListAppender<ILoggingEvent> capture;

    @BeforeEach void captureLogs() {
        root=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        capture=new ListAppender<>(); capture.start(); root.addAppender(capture);
    }
    @AfterEach void detach() { root.detachAppender(capture); capture.stop(); }

    private AuditEvent sensitiveEvent() {
        return AuditEvent.create("OPERATION_EXECUTED",new SubjectId(EMAIL),new ActorId(ACTOR),
            new OperationId("private-account-123"),new OperationType("private-operation-type"),"EXECUTED",TOKEN,
            PASSWORD,"{\"amount\":\""+AMOUNT+"\",\"authorization\":\""+TOKEN+"\",\"password\":\""+PASSWORD+"\"}");
    }
    private String logs() {
        return capture.list.stream().map(e -> e.getFormattedMessage()+
            (e.getThrowableProxy()==null?"":ThrowableProxyUtil.asString(e.getThrowableProxy()))).collect(Collectors.joining("\n"));
    }
    private void assertPrivate(String text) {
        assertThat(text).doesNotContain(EMAIL,ACTOR,TOKEN,PASSWORD,AMOUNT,"private-account-123","private-operation-type");
    }

    @Test void auditUsesProtectedReferencesAndOmitsFreeText() {
        audit.record(sensitiveEvent());
        assertThat(logs()).contains("AUDIT_EVENT"); assertPrivate(logs());
        var row=jdbc.queryForMap("SELECT * FROM audit_events");
        assertPrivate(row.toString()); assertThat(row.get("DETAILS")).isNull();
    }
    @Test void errorsDoNotLogProviderMessagesOrThrowableCauses() {
        errors.handleInvalidApproval(new InvalidApprovalException(TOKEN+" "+EMAIL));
        errors.handleAuthorizationDenied(new AuthorizationDeniedException(PASSWORD,TOKEN,EMAIL));
        errors.handleUnhandled(new IllegalStateException(TOKEN,new SQLException(PASSWORD+" "+AMOUNT)));
        assertPrivate(logs());
    }
    @Test void auditPersistenceFailureDoesNotExposeSqlDiagnostics() {
        var failing=new JdbcTemplate() {
            @Override public int update(String sql,Object... args) {
                throw new DataAccessResourceFailureException(TOKEN,new SQLException(PASSWORD+" "+EMAIL));
            }
        };
        new LoggingAuditAdapter(failing,privacy).record(sensitiveEvent());
        assertThat(logs()).contains("persist"); assertPrivate(logs());
    }
}
