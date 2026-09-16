package dev.oreo.modulith.core;

import java.time.Instant;
import java.util.UUID;

/** Persistent metadata for delivery of one event to one listener. */
public record EventPublication(
        UUID id,
        String eventType,
        String listenerId,
        String payload,
        EventPublicationStatus status,
        Instant publishedAt,
        Instant completedAt,
        String error
) {
}
