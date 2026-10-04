package dev.oreo.modulith.core;

/**
 * Bidirectional, version-aware event encoding. Unlike toString serialization, this allows
 * events recorded before shutdown to be reconstituted after restart.
 */
public interface EventPayloadCodec extends EventPayloadSerializer {
    /**
     * Legacy decoder retained for source compatibility with existing codec implementations.
     * New codecs may override the typed overload instead.
     */
    @Deprecated
    default Object deserialize(String eventType, String payload) {
        throw new UnsupportedOperationException("Implement deserialize(eventType, payload, type)");
    }

    /** Decodes the event and verifies it is assignable to the listener's declared event type. */
    default <T> T deserialize(String eventType, String payload, Class<T> type) {
        return type.cast(deserialize(eventType, payload));
    }
}
