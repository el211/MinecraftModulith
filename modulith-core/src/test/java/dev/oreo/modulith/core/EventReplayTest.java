package dev.oreo.modulith.core;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EventReplayTest {
    record Notice(String text) {}
    @Test void replaysWithoutCreatingAnotherPublication() {
        MemoryRegistry registry = new MemoryRegistry();
        EventPayloadCodec codec = new EventPayloadCodec() {
            public String serialize(Object event) { return ((Notice)event).text(); }
            public Object deserialize(String type, String payload) {
                if (!type.equals(Notice.class.getName())) throw new IllegalArgumentException();
                return new Notice(payload);
            }
        };
        EventBus bus = new EventBus(Runnable::run, registry, codec, new ModulithMetrics());
        AtomicInteger deliveries = new AtomicInteger();
        bus.subscribe(Notice.class, "test.notice", EventDelivery.SYNC, notice -> {
            assertEquals("hello", notice.text()); deliveries.incrementAndGet();
        });
        registry.begin(Notice.class.getName(), "test.notice", "hello");
        EventRecoveryReport result = bus.replayIncomplete(10);
        assertEquals(1, result.recovered());
        assertEquals(1, deliveries.get());
        assertEquals(1, registry.created);
        assertTrue(registry.incomplete().isEmpty());
    }

    @Test void acceptsTypeSafeCodecAndLimitsBacklog() {
        MemoryRegistry registry = new MemoryRegistry();
        EventPayloadCodec codec = new EventPayloadCodec() {
            public String serialize(Object event) { return ((Notice) event).text(); }

            @Override
            public <T> T deserialize(String eventType, String payload, Class<T> expectedType) {
                assertEquals(Notice.class.getName(), eventType);
                return expectedType.cast(new Notice(payload));
            }
        };
        EventBus bus = new EventBus(Runnable::run, registry, codec, new ModulithMetrics());
        AtomicInteger deliveries = new AtomicInteger();
        bus.subscribe(Notice.class, "test.typed", EventDelivery.SYNC, event -> deliveries.incrementAndGet());
        for (int i = 0; i < 5; i++) registry.begin(Notice.class.getName(), "test.typed", "" + i);
        assertEquals(2, bus.replayIncomplete(2).recovered());
        assertEquals(2, deliveries.get());
        assertEquals(3, registry.incomplete().size());
        assertEquals(0, bus.replayIncomplete(0).recovered());
        assertThrows(IllegalArgumentException.class, () -> registry.incomplete(-1));
    }

    static class MemoryRegistry implements EventPublicationRegistry {
        Map<UUID, EventPublication> entries = new LinkedHashMap<>();
        int created;
        public EventPublication begin(String type, String listener, String payload) {
            created++;
            EventPublication entry = new EventPublication(UUID.randomUUID(), type, listener,
                    payload, EventPublicationStatus.PENDING, Instant.now(), null, null);
            entries.put(entry.id(), entry);
            return entry;
        }
        public void complete(UUID id) {
            EventPublication old = entries.get(id);
            entries.put(id, new EventPublication(id, old.eventType(), old.listenerId(),
                    old.payload(), EventPublicationStatus.COMPLETED, old.publishedAt(), Instant.now(), null));
        }
        public void fail(UUID id, String error) {
            EventPublication old = entries.get(id);
            entries.put(id, new EventPublication(id, old.eventType(), old.listenerId(),
                    old.payload(), EventPublicationStatus.FAILED, old.publishedAt(), Instant.now(), error));
        }
        public List<EventPublication> incomplete() {
            return entries.values().stream().filter(e -> e.status()==EventPublicationStatus.PENDING).toList();
        }
    }
}
