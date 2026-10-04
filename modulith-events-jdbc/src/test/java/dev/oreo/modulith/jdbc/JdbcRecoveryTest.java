package dev.oreo.modulith.jdbc;

import dev.oreo.modulith.core.*;
import org.sqlite.SQLiteDataSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JdbcRecoveryTest {
    @TempDir Path directory;
    @Test void boundedQueriesFilterOnServer() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("bounded.db").toAbsolutePath());
        JdbcEventPublicationRegistry registry = new JdbcEventPublicationRegistry(source);
        UUID one = registry.begin("Notice", "listener", "one").id();
        UUID two = registry.begin("Notice", "listener", "two").id();
        registry.begin("Notice", "listener", "three");
        assertEquals(0, registry.incomplete(0).size());
        assertEquals(2, registry.incomplete(2).size());
        registry.fail(one, "failure");
        registry.fail(two, "failure");
        assertEquals(1, registry.failed(1).size());
        assertTrue(registry.failed(one).isPresent());
        assertTrue(registry.failed(UUID.randomUUID()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.incomplete(-1));
    }

    @Test void transactionBoundEnqueueAndDurableRetries() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("modulith-tx.db").toAbsolutePath());
        JdbcEventPublicationRegistry registry = new JdbcEventPublicationRegistry(source);

        try (Connection tx = source.getConnection()) {
            tx.setAutoCommit(false);
            registry.begin(tx, "Notice", "notice.listener", "rollback");
            assertTrue(registry.incomplete().isEmpty());
            tx.rollback();
        }
        assertTrue(registry.incomplete().isEmpty());

        UUID id;
        try (Connection tx = source.getConnection()) {
            tx.setAutoCommit(false);
            id = registry.begin(tx, "Notice", "notice.listener", "committed").id();
            tx.commit();
        }
        assertEquals(1, registry.incomplete().size());
        registry.fail(id, "transient");
        assertEquals(1, registry.incrementRetryCount(id));
        JdbcEventPublicationRegistry reopened = new JdbcEventPublicationRegistry(source);
        assertEquals(1, reopened.retryCount(id));
        assertEquals(1, reopened.failed().size());
        reopened.deadLetter(id, "exhausted");
        assertTrue(reopened.failed().isEmpty());
        assertEquals(EventPublicationStatus.DEAD_LETTER, reopened.all().getFirst().status());
    }
}
