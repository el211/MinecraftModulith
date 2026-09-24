package dev.oreo.modulith.paper;

/** Platform-neutral handle for Paper or Folia scheduled work. */
@FunctionalInterface
public interface ScheduledTaskHandle extends AutoCloseable {
    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
