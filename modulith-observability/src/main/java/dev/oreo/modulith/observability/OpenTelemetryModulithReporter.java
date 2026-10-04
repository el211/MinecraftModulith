package dev.oreo.modulith.observability;

import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModulithMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Objects;

/**
 * Optional pull-to-push adapter. Retain one reporter per runtime: counters use deltas
 * to avoid double counting when collect() is invoked repeatedly.
 */
public final class OpenTelemetryModulithReporter {
    private final LongCounter events, invocations, completions, failures;
    private long lastEvents, lastInvocations, lastCompletions, lastFailures;

    public OpenTelemetryModulithReporter(Meter meter) {
        Objects.requireNonNull(meter);
        events = meter.counterBuilder("minecraft.modulith.events.published").build();
        invocations = meter.counterBuilder("minecraft.modulith.listeners.invoked").build();
        completions = meter.counterBuilder("minecraft.modulith.listeners.completed").build();
        failures = meter.counterBuilder("minecraft.modulith.listeners.failed").build();
    }

    public synchronized void collect(ModuleRuntime runtime) {
        ModulithMetrics.Snapshot current = runtime.metrics().snapshot();
        events.add(Math.max(0, current.eventsPublished() - lastEvents));
        invocations.add(Math.max(0, current.listenerInvocations() - lastInvocations));
        completions.add(Math.max(0, current.listenerCompletions() - lastCompletions));
        failures.add(Math.max(0, current.listenerFailures() - lastFailures));
        lastEvents = current.eventsPublished();
        lastInvocations = current.listenerInvocations();
        lastCompletions = current.listenerCompletions();
        lastFailures = current.listenerFailures();
    }
}
