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
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO modulith_event_publication
                    (id, event_type, listener_id, payload, status, published_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, id.toString());
            statement.setString(2, eventType);
            statement.setString(3, listenerId);
            statement.setString(4, payload);
            statement.setString(5, EventPublicationStatus.PENDING.name());
            statement.setString(6, now.toString());
            statement.executeUpdate();
            return new EventPublication(
                    id,
                    eventType,
                    listenerId,
                    payload,
                    EventPublicationStatus.PENDING,
                    now,
                    null,
                    null
            );
        } catch (SQLException exception) {
            throw new ModulithException("Could not persist event publication", exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void complete(UUID publicationId) {
        update(publicationId, EventPublicationStatus.COMPLETED, null);
    }

    @Override
    public void fail(UUID publicationId, String error) {
        update(publicationId, EventPublicationStatus.FAILED, error);
    }

    private void update(UUID publicationId, EventPublicationStatus status, String error) {
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE modulith_event_publication
                SET status = ?, completed_at = ?, error = ?
                WHERE id = ?
                """)) {
            statement.setString(1, status.name());
            statement.setString(2, Instant.now().toString());
            statement.setString(3, error);
            statement.setString(4, publicationId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModulithException("Could not update event publication " + publicationId, exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<EventPublication> incomplete() {
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                FROM modulith_event_publication
                WHERE status = ?
                ORDER BY published_at ASC
                """)) {
            statement.setString(1, EventPublicationStatus.PENDING.name());
            try (ResultSet result = statement.executeQuery()) {
                List<EventPublication> publications = new ArrayList<>();
                while (result.next()) {
                    publications.add(read(result));
                }
