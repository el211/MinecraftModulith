package dev.oreo.modulith.core;

import java.util.List;

/** Summary of one event publication. */
public record EventDispatchResult(
        String eventType,
        int listeners,
        int completed,
        List<Throwable> failures,
        boolean detached
) {
    public EventDispatchResult {
        failures = List.copyOf(failures);
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}
