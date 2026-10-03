package dev.oreo.modulith.sqlite;

import dev.oreo.modulith.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteRecoveryTest {
    @TempDir Path directory;

    @Test void retryCountSurvivesRestartAndDeadLetters() {
        Path file = directory.resolve("events.sqlite");
        UUID id;
        try (SqliteEventPublicationRegistry registry = new SqliteEventPublicationRegistry(file)) {
            id = registry.begin("Notice", "notice.listener", "{}").id();
            registry.fail(id, "network");
            assertEquals(1, registry.failed().size());
            assertEquals(1, registry.incrementRetryCount(id));
            assertEquals(2, registry.incrementRetryCount(id));
        }
        try (SqliteEventPublicationRegistry registry = new SqliteEventPublicationRegistry(file)) {
            assertEquals(2, registry.retryCount(id));
            assertEquals(1, registry.failed().size());
            registry.deadLetter(id, "exhausted");
            assertTrue(registry.failed().isEmpty());
            assertEquals(EventPublicationStatus.DEAD_LETTER, registry.all().getFirst().status());
            assertEquals(0, registry.incrementRetryCount(id));
        }
    }
}
