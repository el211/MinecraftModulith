package dev.oreo.modulith.core;

/** One retry pass; tick on a context suitable for the registered listeners. */
public record EventRetryTickReport(int retried, int deadLettered, int deferred, int unavailable) {}
