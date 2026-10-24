package com.mfa.stepup.integration;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@TestConfiguration(proxyBeanMethods = false)
class TelemetryTestConfiguration {
    @Bean CapturedSpans capturedSpans() { return new CapturedSpans(); }
    @Bean SdkTracerProvider sdkTracerProvider(CapturedSpans spans) {
        return SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(spans)).build();
    }
    static class CapturedSpans implements SpanExporter {
        private final ConcurrentLinkedQueue<SpanData> spans = new ConcurrentLinkedQueue<>();
        List<SpanData> values() { return List.copyOf(spans); }
        void clear() { spans.clear(); }
        @Override public CompletableResultCode export(Collection<SpanData> values) { spans.addAll(values); return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    }
}
