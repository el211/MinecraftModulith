package dev.oreo.modulith.jdbc;

import dev.oreo.modulith.core.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JdbcRecoveryTest {
    @Test void transactionBoundEnqueueAndDurableRetries() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:modulith_tx;DB_CLOSE_DELAY=-1");
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
