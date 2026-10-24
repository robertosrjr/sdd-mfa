package com.mfa.stepup.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.domain.exception.InvalidApprovalException;
import com.mfa.stepup.domain.model.AuthorizationDecision;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Map;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureObservability
@Import(TelemetryTestConfiguration.class)
class AuthorizationObservabilityIntegrationTest extends IdempotencyIntegrationSupport {
    private static final String EMAIL = "telemetry-private@example.invalid";
    private static final String ACTOR = "telemetry-agent@example.invalid";
    private static final String SECRET = "private-credential-123456";
    private static final String TOKEN = "Bearer private-token-123456";
    private static final String ACCOUNT = "private-bank-account-123";
    private static final String AMOUNT = "987654.321";
    @Autowired MeterRegistry meters;
    @Autowired ObservationRegistry observations;
    @Autowired TelemetryTestConfiguration.CapturedSpans spans;
    private Logger root;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void capture() {
        spans.clear(); root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>(); logs.start(); root.addAppender(logs);
    }
    @AfterEach void detach() { root.detachAppender(logs); logs.stop(); }
    private String emittedLogs() {
        return logs.list.stream().map(e -> e.getFormattedMessage() +
            (e.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(e.getThrowableProxy()))).collect(Collectors.joining("\n"));
    }
    private String emittedSpans() {
        return spans.values().stream().map(s -> s.getName() + s.getAttributes() + s.getEvents() + s.getStatus()
            + s.getLinks() + s.getResource().getAttributes()).collect(Collectors.joining("\n"));
    }
    private void assertPrivate(String output) {
        assertThat(output).doesNotContain(EMAIL, ACTOR, SECRET, TOKEN, ACCOUNT, AMOUNT, KEY);
    }

    @Test void realSpansLogsAndPrometheusExcludeHeadersPayloadIdentityAndQueryValues() throws Exception {
        var data = Map.<String,Object>of("amount", AMOUNT, "destination", ACCOUNT, "email", EMAIL,
            "credentials", Map.of("password", SECRET, "token", TOKEN));
        var digest = new IntentCanonicalizer().calculateDigest(data).value();
        jdbc.update("UPDATE operation_authorizations SET subject_id=?,actor_id=?,operation_id=?,intent_digest=?", EMAIL, ACTOR, ACCOUNT, digest);
        jdbc.update("UPDATE transaction_approvals SET subject_id=?,actor_id=?,approver_id=?,intent_digest=?", EMAIL, ACTOR, EMAIL, digest);
        String body = mapper.writeValueAsString(Map.of("subjectId",EMAIL,"actorId",ACTOR,"delegated",true,
            "operationId",ACCOUNT,"operationType","TRANSFER","intentVersion",1,"materialData",data,"approvalId","idem-approval"));
        mvc.perform(post("/api/v1/operation-authorizations").queryParam("password",SECRET)
                .header("Authorization",TOKEN).header("X-Correlation-Id",EMAIL).header("Idempotency-Key",KEY)
                .header("baggage","email="+EMAIL).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        assertThat(spans.values()).anyMatch(s -> s.getName().equals("operation.authorization"));
        assertThat(spans.values()).anyMatch(s -> s.getName().equalsIgnoreCase("HTTP request"));
        assertPrivate(emittedLogs()); assertPrivate(emittedSpans());
        String prometheus = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(prometheus).contains("authorization_decision_total", "authorization_duration_seconds");
        assertPrivate(prometheus);
    }

    @Test void decisionsAndLatencyCountReplayWithoutAdditionalExecution() {
        var counter = meters.find("authorization.decision").tags("decision","executed","operation","TRANSFER").counter();
        double before = counter == null ? 0 : counter.count();
        var first = useCase.execute(command(KEY));
        assertThat(useCase.execute(command(KEY))).isEqualTo(first);
        assertThat(meters.get("authorization.decision").tags("decision","executed","operation","TRANSFER").counter().count()).isEqualTo(before + 2);
        var timer = meters.get("authorization.duration").tags("decision","executed","operation","TRANSFER").timer();
        assertThat(timer.count()).isGreaterThanOrEqualTo(2);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();
        verify(business,times(1)).execute(any(),any(),any(),any());
    }

    @Test void labelsRemainBoundedWhenClientSuppliesManyOperationTypes() {
        for (int i = 0; i < 12; i++) {
            var command = new AuthorizeOperationCommand("idem-owner",null,"idem-op","client-type-"+i,1,material,
                "idem-approval","client-correlation-"+i,KEY);
            assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidApprovalException.class);
        }
        assertThat(meters.find("authorization.decision").tags("decision","invalid_approval","operation","OTHER").meters()).hasSize(1);
        meters.counter("authorization.decision","decision",EMAIL,"operation","TRANSFER").increment();
        meters.counter("authorization.decision","decision","executed","operation","TRANSFER","subject",EMAIL).increment();
        var ids = meters.getMeters().stream().filter(m -> m.getId().getName().startsWith("authorization.")).map(m -> m.getId().toString()).toList();
        assertThat(ids).noneMatch(id -> id.contains(EMAIL) || id.contains("client-type-") || id.contains("subject"));
    }

    @Test void providerDenialAndHttpErrorEventsDoNotExportRawExceptions() throws Exception {
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenReturn(AuthorizationDecision.deny("v1",SECRET));
        String denied = mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",KEY)
                .contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        assertThat(denied).doesNotContain(SECRET);
        when(policy.evaluatePolicy(any(),any(),any(),any())).thenThrow(new IllegalStateException(TOKEN,new RuntimeException(SECRET)));
        String failure = mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",KEY)
                .contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();
        assertPrivate(failure);
        var request = new MockHttpServletRequest("GET","/private/"+EMAIL);
        var context = new ServerRequestObservationContext(request,new MockHttpServletResponse());
        Observation observation = Observation.createNotStarted("http.server.requests", () -> context, observations).start();
        observation.error(new IllegalStateException(TOKEN,new RuntimeException(SECRET))).stop();
        assertThat(spans.values()).anyMatch(s -> s.getName().equals("operation.authorization"));
        assertPrivate(emittedLogs()); assertPrivate(emittedSpans());
        assertThat(meters.get("authorization.decision").tags("decision","denied","operation","TRANSFER").counter().count()).isPositive();
    }
}
