package dev.oreo.modulith.core;

/** Completion behavior for event dispatch. */
public enum EventCompletionPolicy {
    /** Wait for every matching listener and fail after all listeners complete if any failed. */
    WAIT_FOR_ALL,

    /** Complete exceptionally on the first observed listener failure. */
    FAIL_FAST,

    /** Schedule matching listeners and return immediately. */
    FIRE_AND_FORGET
}
