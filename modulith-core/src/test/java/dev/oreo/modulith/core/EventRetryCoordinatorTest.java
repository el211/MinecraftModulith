package dev.oreo.modulith.core;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EventRetryCoordinatorTest {
    record Notice(String value) {}

    static class Registry extends EventReplayTest.MemoryRegistry {
        final Map<UUID, Integer> attempts = new HashMap<>();
        @Override public List<EventPublication> failed() {
            return entries.values().stream()
                    .filter(e -> e.status() == EventPublicationStatus.FAILED).toList();
        }
        @Override public int retryCount(UUID id) { return attempts.getOrDefault(id, 0); }
        @Override public int incrementRetryCount(UUID id) {
            if (entries.get(id).status() != EventPublicationStatus.FAILED) return 0;
            return attempts.merge(id, 1, Integer::sum);
        }
        @Override public void deadLetter(UUID id, String reason) {
            EventPublication old = entries.get(id);
            entries.put(id, new EventPublication(id, old.eventType(), old.listenerId(),
                    old.payload(), EventPublicationStatus.DEAD_LETTER, old.publishedAt(),
                    Instant.now(), reason));
        }
    }

    private static EventBus bus(Registry registry) {
        EventPayloadCodec codec = new EventPayloadCodec() {
            public String serialize(Object event) { return ((Notice) event).value(); }
            public Object deserialize(String type, String payload) {
                if (!type.equals(Notice.class.getName())) throw new IllegalArgumentException();
                return new Notice(payload);
            }
        };
        return new EventBus(Runnable::run, registry, codec, new ModulithMetrics());
    }

    @Test void persistsAttemptAndRetriesAfterDelay() {
        Registry registry = new Registry();
        EventBus bus = bus(registry);
        AtomicInteger invokes = new AtomicInteger();
        bus.subscribe(Notice.class, "test.retry", EventDelivery.SYNC, value -> {
            if (invokes.getAndIncrement() == 0) throw new IllegalStateException("transient");
        });
        assertThrows(EventDispatchException.class, () -> bus.publish(new Notice("test")));
        EventRetryCoordinator coordinator = new EventRetryCoordinator(bus, registry,
                new EventRetryPolicy(2, Duration.ofSeconds(5), Duration.ofSeconds(30), 2.0));
        assertEquals(1, coordinator.tick(Instant.now(), 100).deferred());
        EventRetryTickReport report = coordinator.tick(Instant.now().plusSeconds(10), 100);
        assertEquals(1, report.retried());
        UUID id = registry.entries.keySet().iterator().next();
        assertEquals(1, registry.retryCount(id));
        assertEquals(EventPublicationStatus.COMPLETED, registry.entries.get(id).status());
        assertEquals(1, registry.created); // No second publication on retry.
    }

    @Test void deadLettersAfterMaxRetry() {
        Registry registry = new Registry();
        EventBus bus = bus(registry);
        bus.subscribe(Notice.class, "test.poison", EventDelivery.SYNC, value -> {
            throw new IllegalStateException("poison");
        });
        assertThrows(EventDispatchException.class, () -> bus.publish(new Notice("x")));
        EventRetryCoordinator coordinator = new EventRetryCoordinator(bus, registry,
                new EventRetryPolicy(1, Duration.ofSeconds(1), Duration.ofSeconds(2), 2));
        coordinator.tick(Instant.now().plusSeconds(4), 100);
        EventRetryTickReport result = coordinator.tick(Instant.now().plusSeconds(8), 100);
        assertEquals(1, result.deadLettered());
        assertEquals(EventPublicationStatus.DEAD_LETTER,
                registry.entries.values().iterator().next().status());
    }
}
