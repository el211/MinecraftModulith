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

    /** Returns at most {@code limit} pending publications in publication order.
     * Storage adapters should override this method with server-side limits.
     */
    default List<EventPublication> incomplete(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        if (limit == 0) return List.of();
        return incomplete().stream().limit(limit).toList();
    }

    /** Explicit retry candidates; FAILED is kept separate from crash-pending PENDING. */
    default List<EventPublication> failed() { return List.of(); }

    /** Returns at most {@code limit} failed publications in publication order.
     * Storage adapters should override this method with server-side limits.
     */
    default List<EventPublication> failed(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        if (limit == 0) return List.of();
        return failed().stream().limit(limit).toList();
    }

    /** Fetches one failed publication for idempotent replay without loading all failed rows. */
    default java.util.Optional<EventPublication> failed(UUID publicationId) {
        return failed().stream().filter(p -> p.id().equals(publicationId)).findFirst();
    }

    /** Returns the number of retries already attempted, as recorded by a supporting adapter. */
    default int retryCount(UUID publicationId) {
        throw new UnsupportedOperationException("Retry counters are unsupported");
    }

    /**
     * Increment only if publication status is still FAILED. Return 0 when no longer eligible.
     * Single-worker operation is required until distributed claims/leases are supported.
     */
    default int incrementRetryCount(UUID publicationId) {
        throw new UnsupportedOperationException("Retry counters are unsupported");
    }

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
