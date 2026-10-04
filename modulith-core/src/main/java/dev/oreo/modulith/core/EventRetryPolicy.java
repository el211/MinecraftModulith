package dev.oreo.modulith.core;

import java.time.Duration;
import java.util.Objects;

/** Persistent-retry policy. maxRetries excludes the initial listener invocation. */
public record EventRetryPolicy(
        int maxRetries, Duration initialDelay, Duration maximumDelay, double multiplier
) {
    public EventRetryPolicy {
        Objects.requireNonNull(initialDelay);
        Objects.requireNonNull(maximumDelay);
        if (maxRetries < 0 || initialDelay.isNegative() || initialDelay.isZero() ||
                maximumDelay.compareTo(initialDelay) < 0 || !Double.isFinite(multiplier) ||
                multiplier < 1.0) {
            throw new IllegalArgumentException("Invalid event retry policy");
        }
    }

    public Duration backoff(int previousRetries) {
        double milliseconds = initialDelay.toMillis() * Math.pow(multiplier, previousRetries);
        return Duration.ofMillis((long) Math.min(maximumDelay.toMillis(), milliseconds));
    }
}
