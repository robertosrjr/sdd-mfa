package com.mfa.stepup.infrastructure.config;

import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.application.usecase.AuthorizeSensitiveOperationService;
import com.mfa.stepup.domain.exception.*;
import com.mfa.stepup.infrastructure.observability.AuditPrivacyPolicy;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.Set;

/** The domain and use case remain independent of metrics/tracing libraries. */
@Configuration(proxyBeanMethods = false)
public class MetricsConfig {
    private static final Set<String> DECISIONS = Set.of("executed", "unknown", "failed", "step_up", "denied",
        "invalid_approval", "invalid_intent", "conflict", "locked", "invalid_request", "error");
    private static final Set<String> OPERATIONS = Set.of("TRANSFER", "PIX_TRANSFER", "FUNDS_TRANSFER", "OTHER");

    @Bean
    MeterFilter authorizationMetricCardinality() {
        return new MeterFilter() {
            @Override public MeterFilterReply accept(Meter.Id id) {
                if (!id.getName().equals("authorization.decision") && !id.getName().equals("authorization.duration"))
                    return MeterFilterReply.NEUTRAL;
                boolean valid = id.getTag("decision") != null && id.getTag("operation") != null
                    && DECISIONS.contains(id.getTag("decision")) && OPERATIONS.contains(id.getTag("operation"))
                    && id.getTags().stream().allMatch(tag -> Set.of("application", "decision", "operation").contains(tag.getKey()));
                return valid ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
            }
        };
    }

    @Bean
    @Primary
    AuthorizeSensitiveOperationUseCase observedAuthorizationUseCase(AuthorizeSensitiveOperationService delegate,
            MeterRegistry meters, ObjectProvider<Tracer> tracers) {
        Tracer tracer = tracers.getIfAvailable(() -> Tracer.NOOP);
        return command -> {
            String operation = AuditPrivacyPolicy.operation(command.operationType());
            String decision = "error";
            Timer.Sample timer = Timer.start(meters);
            var span = tracer.nextSpan().name("operation.authorization").tag("operation", operation).start();
            try (var scope = tracer.withSpan(span)) {
                var result = delegate.execute(command);
                decision = result.outcomeUnknown() ? "unknown" : result.executed() ? "executed" : "failed";
                return result;
            } catch (RuntimeException ex) {
                decision = classify(ex);
                throw ex;
            } finally {
                // Never attach the request, IDs, key, throwable or provider text to a span.
                span.tag("decision", decision).end();
                Counter.builder("authorization.decision").description("Authorization response decisions, including replay")
                    .tags("decision", decision, "operation", operation).register(meters).increment();
                timer.stop(Timer.builder("authorization.duration").description("Authorization response latency")
                    .tags("decision", decision, "operation", operation).register(meters));
            }
        };
    }

    private static String classify(RuntimeException error) {
        if (error instanceof InsufficientAuthenticationException) return "step_up";
        if (error instanceof AuthorizationDeniedException) return "denied";
        if (error instanceof InvalidApprovalException) return "invalid_approval";
        if (error instanceof InvalidIntentException) return "invalid_intent";
        if (error instanceof IdempotencyConflictException) return "conflict";
        if (error instanceof TemporaryLockoutException) return "locked";
        if (error instanceof DomainException || error instanceof IllegalArgumentException) return "invalid_request";
        return "error";
    }
}
