package dev.oreo.modulith.sqlite;

import dev.oreo.modulith.core.EventPublication;
import dev.oreo.modulith.core.EventPublicationRegistry;
import dev.oreo.modulith.core.EventPublicationStatus;
import dev.oreo.modulith.core.ModulithException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite-backed publication registry that records delivery before listener invocation and marks
 * completion/failure afterwards.
 */
public final class SqliteEventPublicationRegistry implements EventPublicationRegistry, AutoCloseable {
    private final Connection connection;
    private final ReentrantLock lock = new ReentrantLock();

    public SqliteEventPublicationRegistry(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        try {
            Path absolute = databaseFile.toAbsolutePath();
            if (absolute.getParent() != null) {
                java.nio.file.Files.createDirectories(absolute.getParent());
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            initializeSchema();
        } catch (Exception exception) {
            throw new ModulithException("Could not open MinecraftModulith SQLite registry", exception);
        }
    }

    private void initializeSchema() throws SQLException {
        lock.lock();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS modulith_event_publication (
                        id TEXT PRIMARY KEY,
                        event_type TEXT NOT NULL,
                        listener_id TEXT NOT NULL,
                        payload TEXT,
                        status TEXT NOT NULL,
                        published_at TEXT NOT NULL,
                        completed_at TEXT,
                        error TEXT
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_modulith_event_publication_status
                    ON modulith_event_publication(status)
                    """);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public EventPublication begin(String eventType, String listenerId, String payload) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        lock.lock();
