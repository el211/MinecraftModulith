package dev.oreo.modulith.jdbc;

import dev.oreo.modulith.core.EventPublication;
import dev.oreo.modulith.core.EventPublicationRegistry;
import dev.oreo.modulith.core.EventPublicationStatus;
import dev.oreo.modulith.core.ModulithException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC-backed publication registry compatible with any JDBC data source (PostgreSQL, MySQL,
 * MariaDB, H2, SQL Server, etc.). The caller is responsible for providing and closing the
 * {@link DataSource}; this class does not manage connection lifecycle.
 *
 * <p>The JDBC driver must be present on the runtime classpath. Example with PostgreSQL:
 *
 * <pre>
 * DataSource dataSource = ...; // HikariCP, PGSimpleDataSource, etc.
 * var registry = new JdbcEventPublicationRegistry(dataSource);
 * </pre>
 */
public final class JdbcEventPublicationRegistry implements EventPublicationRegistry {

    private final DataSource dataSource;

    public JdbcEventPublicationRegistry(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        initializeSchema();
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS modulith_event_publication (
                        id VARCHAR(36) PRIMARY KEY,
                        event_type TEXT NOT NULL,
                        listener_id TEXT NOT NULL,
                        payload TEXT,
                        status VARCHAR(16) NOT NULL,
                        published_at VARCHAR(32) NOT NULL,
                        completed_at VARCHAR(32),
                        error TEXT
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_modulith_event_publication_status
                    ON modulith_event_publication(status)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS modulith_event_retry (
                        publication_id VARCHAR(36) PRIMARY KEY,
                        retry_count INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new ModulithException("Could not initialize JDBC event publication registry", exception);
        }
    }

    @Override
    public EventPublication begin(String eventType, String listenerId, String payload) {
        try (Connection connection = dataSource.getConnection()) {
            return begin(connection, eventType, listenerId, payload);
        } catch (SQLException exception) {
            throw new ModulithException("Could not persist event publication", exception);
        }
    }

    /**
     * Writes into the caller's existing SQL transaction. Does not commit, roll back or
     * close the connection. Replay the publication after the surrounding transaction commits.
     */
    public EventPublication begin(Connection connection, String eventType, String listenerId, String payload) {
        Objects.requireNonNull(connection, "connection");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

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
            return new EventPublication(id, eventType, listenerId, payload,
                    EventPublicationStatus.PENDING, now, null, null);
        } catch (SQLException exception) {
            throw new ModulithException("Could not persist event publication", exception);
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
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
        }
    }

    @Override
    public List<EventPublication> incomplete() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                     FROM modulith_event_publication
                     WHERE status = ?
                     ORDER BY published_at ASC
                     """)) {
            statement.setString(1, EventPublicationStatus.PENDING.name());
            try (ResultSet result = statement.executeQuery()) {
                List<EventPublication> publications = new ArrayList<>();
                while (result.next()) {
                    publications.add(fromRow(result));
                }
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not query incomplete event publications", exception);
        }
    }

    @Override
    public List<EventPublication> failed() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                 FROM modulith_event_publication WHERE status = ? ORDER BY published_at ASC
                 """)) {
            statement.setString(1, EventPublicationStatus.FAILED.name());
            try (ResultSet result = statement.executeQuery()) {
                List<EventPublication> publications = new ArrayList<>();
                while (result.next()) publications.add(fromRow(result));
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not query failed event publications", exception);
        }
    }

    @Override
    public void deadLetter(UUID publicationId, String reason) {
        update(publicationId, EventPublicationStatus.DEAD_LETTER, reason);
    }

    @Override
    public int retryCount(UUID publicationId) {
        try (Connection connection = dataSource.getConnection()) {
            return readRetryCount(connection, publicationId);
        } catch (SQLException exception) {
            throw new ModulithException("Could not read retry count", exception);
        }
    }

    private static int readRetryCount(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT retry_count FROM modulith_event_retry WHERE publication_id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public int incrementRetryCount(UUID publicationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT status FROM modulith_event_publication WHERE id = ?")) {
                    check.setString(1, publicationId.toString());
                    try (ResultSet result = check.executeQuery()) {
                        if (!result.next() || !EventPublicationStatus.FAILED.name().equals(result.getString(1))) {
                            connection.rollback();
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
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO modulith_event_retry (publication_id, retry_count) VALUES (?, 1)")) {
                        insert.setString(1, publicationId.toString());
                        insert.executeUpdate();
                    }
                }
                int count = readRetryCount(connection, publicationId);
                connection.commit();
                return count;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new ModulithException("Could not increment retry count", exception);
        }
    }

    /** Returns all event publications ordered by publication time. */
    public List<EventPublication> all() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, event_type, listener_id, payload, status, published_at, completed_at, error
                     FROM modulith_event_publication
                     ORDER BY published_at ASC
                     """);
             ResultSet result = statement.executeQuery()) {
            List<EventPublication> publications = new ArrayList<>();
            while (result.next()) {
                publications.add(fromRow(result));
            }
            return List.copyOf(publications);
        } catch (SQLException exception) {
            throw new ModulithException("Could not query event publications", exception);
        }
    }

    /**
     * Deletes completed publications whose {@code completed_at} timestamp is before
     * {@code cutoff}. Returns the number of rows deleted.
     */
    public int deleteCompletedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM modulith_event_publication
                     WHERE status = ? AND completed_at IS NOT NULL AND completed_at < ?
                     """)) {
            statement.setString(1, EventPublicationStatus.COMPLETED.name());
            statement.setString(2, cutoff.toString());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModulithException("Could not prune completed event publications", exception);
        }
    }

    private static EventPublication fromRow(ResultSet result) throws SQLException {
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
}
