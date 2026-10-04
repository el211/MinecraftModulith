package dev.oreo.modulith.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import dev.oreo.modulith.core.EventPublication;
import dev.oreo.modulith.core.EventPublicationRegistry;
import dev.oreo.modulith.core.EventPublicationStatus;
import dev.oreo.modulith.core.ModulithException;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB-backed publication registry. Stores one document per event delivery in the
 * supplied collection and creates an index on the {@code status} field automatically.
 *
 * <p>Example:
 *
 * <pre>
 * MongoClient client = MongoClients.create("mongodb://localhost:27017");
 * MongoCollection&lt;Document&gt; collection = client
 *     .getDatabase("myplugin")
 *     .getCollection("modulith_event_publication");
 *
 * var registry = new MongoEventPublicationRegistry(collection);
 * </pre>
 */
public final class MongoEventPublicationRegistry implements EventPublicationRegistry {

    private final MongoCollection<Document> collection;

    public MongoEventPublicationRegistry(MongoCollection<Document> collection) {
        this.collection = Objects.requireNonNull(collection, "collection");
        collection.createIndex(Indexes.ascending("status"));
    }

    @Override
    public EventPublication begin(String eventType, String listenerId, String payload) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Document document = new Document()
                .append("_id", id.toString())
                .append("event_type", eventType)
                .append("listener_id", listenerId)
                .append("payload", payload)
                .append("status", EventPublicationStatus.PENDING.name())
                .append("published_at", now.toString())
                .append("completed_at", null)
                .append("error", null)
                .append("retry_count", 0);

        try {
            collection.insertOne(document);
            return new EventPublication(id, eventType, listenerId, payload,
                    EventPublicationStatus.PENDING, now, null, null);
        } catch (Exception exception) {
            throw new ModulithException("Could not persist event publication", exception);
        }
    }

    /**
     * Enqueues inside a caller-managed MongoDB ClientSession transaction.
     * Does not commit or abort that transaction.
     */
    public EventPublication begin(ClientSession session, String eventType, String listenerId, String payload) {
        Objects.requireNonNull(session, "session");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Document document = new Document()
                .append("_id", id.toString())
                .append("event_type", eventType)
                .append("listener_id", listenerId)
                .append("payload", payload)
                .append("status", EventPublicationStatus.PENDING.name())
                .append("published_at", now.toString())
                .append("completed_at", null)
                .append("error", null);
        collection.insertOne(session, document);
        return new EventPublication(id, eventType, listenerId, payload,
                EventPublicationStatus.PENDING, now, null, null);
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
        try {
            collection.updateOne(
                    Filters.eq("_id", publicationId.toString()),
                    Updates.combine(
                            Updates.set("status", status.name()),
                            Updates.set("completed_at", Instant.now().toString()),
                            Updates.set("error", error)
                    )
            );
        } catch (Exception exception) {
            throw new ModulithException("Could not update event publication " + publicationId, exception);
        }
    }

    @Override
    public List<EventPublication> incomplete() {
        try {
            List<EventPublication> publications = new ArrayList<>();
            collection.find(Filters.eq("status", EventPublicationStatus.PENDING.name()))
                    .sort(Sorts.ascending("published_at"))
                    .forEach(doc -> publications.add(fromDocument(doc)));
            return List.copyOf(publications);
        } catch (Exception exception) {
            throw new ModulithException("Could not query incomplete event publications", exception);
        }
    }

    @Override
    public List<EventPublication> failed() {
        try {
            List<EventPublication> publications = new ArrayList<>();
            collection.find(Filters.eq("status", EventPublicationStatus.FAILED.name()))
                    .sort(Sorts.ascending("published_at"))
                    .forEach(doc -> publications.add(fromDocument(doc)));
            return List.copyOf(publications);
        } catch (Exception exception) {
            throw new ModulithException("Could not query failed event publications", exception);
        }
    }

    @Override
    public void deadLetter(UUID publicationId, String reason) {
        update(publicationId, EventPublicationStatus.DEAD_LETTER, reason);
    }

    @Override
    public int retryCount(UUID publicationId) {
        Document doc = collection.find(Filters.eq("_id", publicationId.toString())).first();
        return doc == null ? 0 : doc.getInteger("retry_count", 0);
    }

    @Override
    public int incrementRetryCount(UUID publicationId) {
        Document result = collection.findOneAndUpdate(
                Filters.and(Filters.eq("_id", publicationId.toString()),
                        Filters.eq("status", EventPublicationStatus.FAILED.name())),
                Updates.inc("retry_count", 1),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return result == null ? 0 : result.getInteger("retry_count", 0);
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
        // Mongo cursor.limit(0) means UNLIMITED, so guard it explicitly.
        if (limit == 0) return List.of();
        try {
            List<EventPublication> publications = new ArrayList<>();
            collection.find(Filters.eq("status", status.name()))
                    .sort(Sorts.ascending("published_at"))
                    .limit(limit)
                    .forEach(doc -> publications.add(fromDocument(doc)));
            return List.copyOf(publications);
        } catch (Exception exception) {
            throw new ModulithException("Could not query limited event publications", exception);
        }
    }

    @Override
    public java.util.Optional<EventPublication> failed(UUID publicationId) {
        Objects.requireNonNull(publicationId, "publicationId");
        Document result = collection.find(Filters.and(
                Filters.eq("_id", publicationId.toString()),
                Filters.eq("status", EventPublicationStatus.FAILED.name()))).first();
        return result == null ? java.util.Optional.empty()
                : java.util.Optional.of(fromDocument(result));
    }

    /** Returns all event publications ordered by publication time. */
    public List<EventPublication> all() {
        try {
            List<EventPublication> publications = new ArrayList<>();
            collection.find()
                    .sort(Sorts.ascending("published_at"))
                    .forEach(doc -> publications.add(fromDocument(doc)));
            return List.copyOf(publications);
        } catch (Exception exception) {
            throw new ModulithException("Could not query event publications", exception);
        }
    }

    /**
     * Deletes completed publications whose {@code completed_at} timestamp is before
     * {@code cutoff}. Returns the number of documents deleted.
     */
    public int deleteCompletedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        try {
            return (int) collection.deleteMany(
                    Filters.and(
                            Filters.eq("status", EventPublicationStatus.COMPLETED.name()),
                            Filters.ne("completed_at", null),
                            Filters.lt("completed_at", cutoff.toString())
                    )
            ).getDeletedCount();
        } catch (Exception exception) {
            throw new ModulithException("Could not prune completed event publications", exception);
        }
    }

    private static EventPublication fromDocument(Document doc) {
        String completedAt = doc.getString("completed_at");
        return new EventPublication(
                UUID.fromString(doc.getString("_id")),
                doc.getString("event_type"),
                doc.getString("listener_id"),
                doc.getString("payload"),
                EventPublicationStatus.valueOf(doc.getString("status")),
                Instant.parse(doc.getString("published_at")),
                completedAt == null ? null : Instant.parse(completedAt),
                doc.getString("error")
        );
    }
}
