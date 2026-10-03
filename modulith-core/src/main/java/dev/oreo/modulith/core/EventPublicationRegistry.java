package dev.oreo.modulith.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence SPI used to track event listener delivery and completion. */
public interface EventPublicationRegistry {
    EventPublication begin(String eventType, String listenerId, String payload);

    void complete(UUID publicationId);

    void fail(UUID publicationId, String error);

    List<EventPublication> incomplete();

    /** Explicit retry candidates; FAILED is kept separate from crash-pending PENDING. */
    default List<EventPublication> failed() { return List.of(); }

    /** Removes a poison publication from retry eligibility while retaining its audit trail. */
    default void deadLetter(UUID publicationId, String reason) {
        throw new UnsupportedOperationException("This registry has no dead-letter support");
    }

    default long incompleteCount() {
        return incomplete().size();
    }

    static EventPublicationRegistry noop() {
        return NoopRegistry.INSTANCE;
    }

    final class NoopRegistry implements EventPublicationRegistry {
        private static final NoopRegistry INSTANCE = new NoopRegistry();

        private NoopRegistry() {
        }

        @Override
        public EventPublication begin(String eventType, String listenerId, String payload) {
            return new EventPublication(
                    UUID.randomUUID(),
                    eventType,
                    listenerId,
                    payload,
                    EventPublicationStatus.PENDING,
                    Instant.now(),
                    null,
                    null
            );
        }

        @Override public void complete(UUID publicationId) { }
        @Override public void fail(UUID publicationId, String error) { }
        @Override public List<EventPublication> incomplete() { return List.of(); }
    }
}
