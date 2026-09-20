package dev.oreo.modulith.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight runtime metrics with no external metrics dependency. */
public final class ModulithMetrics {
    private final ConcurrentHashMap<String, Long> moduleStartupNanos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> moduleStarts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> moduleStops = new ConcurrentHashMap<>();
    private final LongAdder eventsPublished = new LongAdder();
    private final LongAdder listenerInvocations = new LongAdder();
    private final LongAdder listenerCompletions = new LongAdder();
    private final LongAdder listenerFailures = new LongAdder();
    private final LongAdder listenerNanos = new LongAdder();

    void moduleStarted(String moduleId, long nanos) {
        moduleStartupNanos.put(moduleId, nanos);
        moduleStarts.computeIfAbsent(moduleId, ignored -> new LongAdder()).increment();
    }

    void moduleStopped(String moduleId) {
        moduleStops.computeIfAbsent(moduleId, ignored -> new LongAdder()).increment();
    }

    void eventPublished() {
        eventsPublished.increment();
    }

    void listenerInvoked() {
        listenerInvocations.increment();
    }

    void listenerCompleted(long nanos) {
        listenerCompletions.increment();
        listenerNanos.add(nanos);
    }

    void listenerFailed(long nanos) {
        listenerFailures.increment();
        listenerNanos.add(nanos);
    }

    public Snapshot snapshot() {
        Map<String, Duration> startupDurations = moduleStartupNanos.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Duration.ofNanos(entry.getValue())
                ));

        return new Snapshot(
                startupDurations,
                sums(moduleStarts),
                sums(moduleStops),
                eventsPublished.sum(),
                listenerInvocations.sum(),
                listenerCompletions.sum(),
                listenerFailures.sum(),
                Duration.ofNanos(listenerNanos.sum())
        );
    }

    private static Map<String, Long> sums(Map<String, LongAdder> source) {
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().sum()
        ));
    }

    public record Snapshot(
            Map<String, Duration> moduleStartupDurations,
            Map<String, Long> moduleStarts,
            Map<String, Long> moduleStops,
            long eventsPublished,
            long listenerInvocations,
            long listenerCompletions,
            long listenerFailures,
            Duration totalListenerTime
    ) {
    }
}
