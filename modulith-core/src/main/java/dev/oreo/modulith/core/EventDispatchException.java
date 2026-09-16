package dev.oreo.modulith.core;

import java.util.List;

/** Raised when one or more event listeners fail under a waiting completion policy. */
public final class EventDispatchException extends ModulithException {
    private final List<Throwable> failures;

    public EventDispatchException(String message, List<Throwable> failures) {
        super(message, failures.isEmpty() ? null : failures.getFirst());
        this.failures = List.copyOf(failures);
        for (int i = 1; i < failures.size(); i++) {
            addSuppressed(failures.get(i));
        }
    }

    public List<Throwable> failures() {
        return failures;
    }
}
