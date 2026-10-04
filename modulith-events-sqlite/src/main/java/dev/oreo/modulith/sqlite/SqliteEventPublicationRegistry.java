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
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS modulith_event_retry (
                        publication_id TEXT PRIMARY KEY,
                        retry_count INTEGER NOT NULL
                    )
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
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not query incomplete event publications", exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<EventPublication> failed() {
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                FROM modulith_event_publication WHERE status = ? ORDER BY published_at ASC
                """)) {
            statement.setString(1, EventPublicationStatus.FAILED.name());
            try (ResultSet result = statement.executeQuery()) {
                List<EventPublication> publications = new ArrayList<>();
                while (result.next()) publications.add(read(result));
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not query failed event publications", exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deadLetter(UUID publicationId, String reason) {
        update(publicationId, EventPublicationStatus.DEAD_LETTER, reason);
    }

    @Override
    public int retryCount(UUID publicationId) {
        lock.lock();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT retry_count FROM modulith_event_retry WHERE publication_id = ?")) {
            query.setString(1, publicationId.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not read retry count", exception);
        } finally { lock.unlock(); }
    }

    @Override
    public int incrementRetryCount(UUID publicationId) {
        lock.lock();
        try {
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT status FROM modulith_event_publication WHERE id = ?")) {
                check.setString(1, publicationId.toString());
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next() || !EventPublicationStatus.FAILED.name().equals(result.getString(1))) {
                        return 0;
                    }
                }
            }
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE modulith_event_retry SET retry_count = retry_count + 1 WHERE publication_id = ?")) {
                statement.setString(1, publicationId.toString());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO modulith_event_retry (publication_id, retry_count) VALUES (?, 1)")) {
                    statement.setString(1, publicationId.toString());
                    statement.executeUpdate();
                }
            }
            return retryCount(publicationId);
        } catch (SQLException exception) {
            throw new ModulithException("Could not increment retry count", exception);
        } finally { lock.unlock(); }
    }


    @Override
    public List<EventPublication> incomplete(int limit) {
        return findByStatus(EventPublicationStatus.PENDING, limit);
    }

    @Override
    public List<EventPublication> failed(int limit) {
        return findByStatus(EventPublicationStatus.FAILED, limit);
    }

    private List<EventPublication> findByStatus(EventPublicationStatus status, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        if (limit == 0) return List.of();
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                FROM modulith_event_publication WHERE status = ?
                ORDER BY published_at ASC LIMIT ?
                """)) {
            statement.setString(1, status.name());
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<EventPublication> publications = new ArrayList<>();
                while (result.next()) publications.add(read(result));
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not query limited event publications", exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public java.util.Optional<EventPublication> failed(UUID publicationId) {
        Objects.requireNonNull(publicationId, "publicationId");
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                FROM modulith_event_publication WHERE id = ? AND status = ?
                """)) {
            statement.setString(1, publicationId.toString());
            statement.setString(2, EventPublicationStatus.FAILED.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? java.util.Optional.of(read(result)) : java.util.Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not find failed event publication", exception);
        } finally {
            lock.unlock();
        }
    }

    public List<EventPublication> all() {
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                FROM modulith_event_publication
                ORDER BY published_at ASC
                """);
             ResultSet result = statement.executeQuery()) {
            List<EventPublication> publications = new ArrayList<>();
            while (result.next()) {
                publications.add(read(result));
            }
            return List.copyOf(publications);
        } catch (SQLException exception) {
            throw new ModulithException("Could not query event publications", exception);
        } finally {
            lock.unlock();
        }
    }

    public int deleteCompletedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        lock.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM modulith_event_publication
                WHERE status = ? AND completed_at IS NOT NULL AND completed_at < ?
                """)) {
            statement.setString(1, EventPublicationStatus.COMPLETED.name());
            statement.setString(2, cutoff.toString());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModulithException("Could not prune completed event publications", exception);
        } finally {
            lock.unlock();
        }
    }

    private static EventPublication read(ResultSet result) throws SQLException {
        String completedAt = result.getString("completed_at");
        return new EventPublication(
                UUID.fromString(result.getString("id")),
                result.getString("event_type"),
                result.getString("listener_id"),
                result.getString("payload"),
                EventPublicationStatus.valueOf(result.getString("status")),
                Instant.parse(result.getString("published_at")),
                completedAt == null ? null : Instant.parse(completedAt),
                result.getString("error")
        );
    }

    @Override
    public void close() {
        lock.lock();
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new ModulithException("Could not close SQLite event publication registry", exception);
        } finally {
            lock.unlock();
        }
    }
}
