package dev.oreo.modulith.core;

/**
 * Bidirectional, version-aware event encoding. Unlike toString serialization, this allows
 * events recorded before shutdown to be reconstituted after restart.
 */
public interface EventPayloadCodec extends EventPayloadSerializer {
    Object deserialize(String eventType, String payload);
}
