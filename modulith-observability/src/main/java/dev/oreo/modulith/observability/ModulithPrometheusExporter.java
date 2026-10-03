package dev.oreo.modulith.observability;

import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModulithMetrics;
import java.util.*;

/** Dependency-free, pull-based Prometheus text exposition of current runtime metrics. */
public final class ModulithPrometheusExporter {
    private ModulithPrometheusExporter() {}

    public static String render(ModuleRuntime runtime) {
        Objects.requireNonNull(runtime);
        ModulithMetrics.Snapshot snapshot = runtime.metrics().snapshot();
        StringBuilder text = new StringBuilder();
        line(text, "minecraft_modulith_events_published_total", snapshot.eventsPublished());
        line(text, "minecraft_modulith_listener_invocations_total", snapshot.listenerInvocations());
        line(text, "minecraft_modulith_listener_completions_total", snapshot.listenerCompletions());
        line(text, "minecraft_modulith_listener_failures_total", snapshot.listenerFailures());
        line(text, "minecraft_modulith_incomplete_publications",
                runtime.diagnostics().incompletePublications());
        snapshot.moduleStarts().forEach((module, count) ->
                text.append("minecraft_modulith_module_starts_total{module=\"")
                        .append(escape(module)).append("\"} ").append(count).append('\n'));
        snapshot.moduleStops().forEach((module, count) ->
                text.append("minecraft_modulith_module_stops_total{module=\"")
                        .append(escape(module)).append("\"} ").append(count).append('\n'));
        snapshot.moduleStartupDurations().forEach((module, duration) ->
                text.append("minecraft_modulith_module_startup_seconds{module=\"")
                        .append(escape(module)).append("\"} ").append(duration.toNanos() / 1e9).append('\n'));
        return text.toString();
    }

    private static void line(StringBuilder text, String name, long value) {
        text.append(name).append(' ').append(value).append('\n');
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
