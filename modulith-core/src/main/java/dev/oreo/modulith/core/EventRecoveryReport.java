package dev.oreo.modulith.core;

/** Outcome of manually replaying incomplete persistent publications. */
public record EventRecoveryReport(int recovered, int failed, int unavailableListeners) {}
