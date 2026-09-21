package dev.oreo.modulith.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    @Test
    void tracksAsyncCompletion() {
        RecordingRegistry registry = new RecordingRegistry();
        ModulithMetrics metrics = new ModulithMetrics();
        EventBus bus = new EventBus(
                Runnable::run,
                registry,
                EventPayloadSerializer.toStringSerializer(),
                metrics
        );

        bus.register(new Object() {
            @ModuleListener(delivery = EventDelivery.ASYNC)
            public CompletableFuture<Void> on(String value) {
                return CompletableFuture.completedFuture(null);
            }
        });

        EventDispatchResult result = bus.publish("hello", EventCompletionPolicy.WAIT_FOR_ALL);
        assertTrue(result.successful());
        assertEquals(1, registry.completed);
        assertEquals(1, metrics.snapshot().eventsPublished());
    }

    @Test
    void waitForAllPropagatesFailures() {
        EventBus bus = new EventBus();
        bus.subscribe(String.class, value -> {
            throw new IllegalStateException("boom");
        });

        assertThrows(EventDispatchException.class,
                () -> bus.publish("hello", EventCompletionPolicy.WAIT_FOR_ALL));
    }

    private static final class RecordingRegistry implements EventPublicationRegistry {
        private int completed;
        private final List<EventPublication> pending = new ArrayList<>();

        @Override
        public EventPublication begin(String eventType, String listenerId, String payload) {
            EventPublication publication = new EventPublication(
                    UUID.randomUUID(), eventType, listenerId, payload,
                    EventPublicationStatus.PENDING, Instant.now(), null, null
            );
            pending.add(publication);
            return publication;
        }

        @Override
        public void complete(UUID publicationId) {
            completed++;
            pending.removeIf(publication -> publication.id().equals(publicationId));
        }

        @Override
        public void fail(UUID publicationId, String error) {
            pending.removeIf(publication -> publication.id().equals(publicationId));
        }

        @Override
        public List<EventPublication> incomplete() {
            return List.copyOf(pending);
        }
    }
}
