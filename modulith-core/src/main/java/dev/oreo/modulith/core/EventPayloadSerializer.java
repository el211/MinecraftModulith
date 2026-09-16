package dev.oreo.modulith.core;

/** Converts event payloads into text persisted by an {@link EventPublicationRegistry}. */
@FunctionalInterface
public interface EventPayloadSerializer {
    String serialize(Object event);

    static EventPayloadSerializer toStringSerializer() {
        return event -> String.valueOf(event);
    }
}
