package dev.oreo.modulith.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Owns module resources and releases them in reverse registration order. */
public final class LifecycleScope implements AutoCloseable {
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    public synchronized void onClose(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed) {
            action.run();
            return;
        }
        cleanup.push(action);
    }

    @Override
    public void close() {
        RuntimeException first = null;
        while (true) {
            Runnable action;
            synchronized (this) {
                action = cleanup.pollFirst();
                if (action == null) {
                    closed = true;
                    break;
                }
            }
            try {
                action.run();
            } catch (RuntimeException exception) {
                if (first == null) {
                    first = exception;
                } else {
                    first.addSuppressed(exception);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
