package com.mfa.stepup.infrastructure.config;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

@Configuration(proxyBeanMethods = false)
public class TracingPrivacyConfig {
    @Bean
    ServerRequestObservationConvention privateHttpObservations() {
        return new DefaultServerRequestObservationConvention() {
            @Override public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                // No raw URL, query parameters, headers or request body.
                return KeyValues.empty();
            }
            @Override public String getContextualName(ServerRequestObservationContext context) { return "HTTP request"; }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    PropagatingReceiverTracingObservationHandler<ReceiverContext<?>> privateHttpErrors(
            ObjectProvider<Tracer> tracers, ObjectProvider<Propagator> propagators) {
        return new PropagatingReceiverTracingObservationHandler<>(tracers.getIfAvailable(() -> Tracer.NOOP),
                propagators.getIfAvailable(() -> Propagator.NOOP)) {
            @Override public void onError(ReceiverContext<?> context) {
                // Sanitize at the tracing handler itself, before recording any exception event/status.
                context.setError(new SanitizedHttpFailure());
                super.onError(context);
            }
        };
    }

    private static class SanitizedHttpFailure extends RuntimeException {
        SanitizedHttpFailure() { super("HTTP operation failed", null, false, false); }
    }
}
