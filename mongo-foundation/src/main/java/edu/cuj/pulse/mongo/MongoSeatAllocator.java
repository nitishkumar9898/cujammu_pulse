package edu.cuj.pulse.mongo;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** Persistence primitive only. No HTTP route, role grant, or JPA dual-write integration. */
public final class MongoSeatAllocator {
    private final MongoClient client;
    private final MongoDatabase db;
    private final Clock clock;
    private static final TransactionOptions TX = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary()).maxCommitTime(3L, TimeUnit.SECONDS).build();

    /** Values must originate in an authenticated, server-authorized caller. Not a registration DTO. */
    public record AuthorizedActor(String institutionId, String personId) {
        public AuthorizedActor { token(institutionId); token(personId); }
    }
    public record Receipt(String status, long queueSequence) {}

    public MongoSeatAllocator(MongoClient client, String database, Clock clock) {
        this.client = client; this.db = client.getDatabase(database); this.clock = clock;
        if (client.getClusterDescription() == null) throw new IllegalArgumentException("Client required");
    }

    /** Provisioning/migration operation; not exposed to end users. */
    public void provisionInventory(String institutionId, String eventId, long capacity) {
        token(institutionId); token(eventId);
        if (capacity < 0 || capacity > 1_000_000) throw new IllegalArgumentException("Capacity out of bounds");
        Date now = Date.from(clock.instant());
        db.getCollection("inventories").insertOne(new Document("institutionId", institutionId).append("eventId", eventId)
                .append("capacity", capacity).append("remaining", capacity).append("version", 0L).append("lifecycleRevision", 0L).append("queueSequence", 0L)
                .append("schemaVersion", FoundationSchema.VERSION).append("createdAt", now).append("updatedAt", now).append("lifecycle", "OPEN"));
    }

    public Receipt register(AuthorizedActor actor, String eventId, String requestKey, long expectedVersion) {
        token(eventId); UUID.fromString(requestKey);
        if (expectedVersion < 0) throw new IllegalArgumentException("Version out of bounds");
        // Values exclude delimiters by construction. Hash does not confer authorization.
        String hash = digest("REGISTER|" + actor.institutionId() + "|" + actor.personId() + "|" + eventId + "|" + expectedVersion);
        String operationId = digest("REGISTER|" + actor.institutionId() + "|" + actor.personId() + "|" + eventId + "|" + requestKey);
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                var receiptScope = and(eq("institutionId", actor.institutionId()), eq("eventId", eventId), eq("personId", actor.personId()), eq("requestKey", requestKey));
                Document existing = db.getCollection("idempotencyRecords").find(session, receiptScope).first();
                if (existing != null) {
                    if (!hash.equals(existing.getString("payloadHash"))) throw new IllegalStateException("Idempotency payload conflict");
                    // Read-only replay: avoid changing the inventory version or manually aborting
                    // the driver's withTransaction lifecycle. A concurrent first attempt conflicts
                    // on the shared inventory write, then the driver retry observes this receipt.
                    return new Receipt(existing.getString("status"), number(existing, "queueSequence"));
                }
                // Every admission/cancellation writes this document. This prevents snapshot write skew.
                Document inventory = inventories().findOneAndUpdate(session,
                        and(eq("institutionId", actor.institutionId()), eq("eventId", eventId)),
                        inc("version", 1L), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE));
                if (inventory == null) throw new IllegalStateException("Inventory unavailable");
                if (!"OPEN".equals(inventory.getString("lifecycle"))) throw new IllegalStateException("Event is not open");
                // expectedVersion is the lifecycle epoch; ordinary allocations use a separate contention version.
                if (expectedVersion != number(inventory, "lifecycleRevision")) throw new IllegalStateException("Lifecycle revision conflict");
                if (db.getCollection("registrations").find(session, and(eq("institutionId", actor.institutionId()), eq("eventId", eventId), eq("personId", actor.personId()))).first() != null)
                    throw new IllegalStateException("Already registered");
                boolean seat = number(inventory, "remaining") > 0;
                long queue = seat ? 0 : Math.addExact(number(inventory, "queueSequence"), 1);
                Date now = Date.from(clock.instant());
                inventories().updateOne(session, eq("_id", inventory.getObjectId("_id")), seat
                        ? combine(inc("remaining", -1L), set("updatedAt", now)) : combine(set("queueSequence", queue), set("updatedAt", now)));
                String status = seat ? "REGISTERED" : "WAITLISTED";
                Document record = base(actor, eventId, now).append("status", status).append("queueSequence", queue);
                db.getCollection("registrations").insertOne(session, record);
                db.getCollection("idempotencyRecords").insertOne(session, base(actor, eventId, now).append("requestKey", requestKey)
                        .append("payloadHash", hash).append("status", status).append("queueSequence", queue));
                db.getCollection("outbox").insertOne(session, base(actor, eventId, now).append("kind", "REGISTRATION_ACCEPTED").append("operationId", operationId));
                db.getCollection("auditEvents").insertOne(session, base(actor, eventId, now).append("kind", "REGISTRATION_ACCEPTED").append("operationId", operationId));
                return new Receipt(status, queue);
            }, TX);
        }
    }

    /** Actor authorization is performed by the caller; this transaction records the actor and expected lifecycle epoch. */
    public Receipt cancel(AuthorizedActor actor, String eventId, String requestKey, long expectedLifecycleRevision) {
        token(eventId); UUID.fromString(requestKey);
        if (expectedLifecycleRevision < 0) throw new IllegalArgumentException("Lifecycle revision out of bounds");
        String hash = digest("CANCEL|" + actor.institutionId() + "|" + actor.personId() + "|" + eventId + "|" + expectedLifecycleRevision);
        String operationId = digest("CANCEL|" + actor.institutionId() + "|" + actor.personId() + "|" + eventId + "|" + requestKey);
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                var receiptScope = and(eq("institutionId", actor.institutionId()), eq("eventId", eventId),
                        eq("personId", actor.personId()), eq("requestKey", requestKey));
                Document existing = db.getCollection("idempotencyRecords").find(session, receiptScope).first();
                if (existing != null) {
                    if (!hash.equals(existing.getString("payloadHash"))) throw new IllegalStateException("Idempotency payload conflict");
                    return new Receipt(existing.getString("status"), number(existing, "queueSequence"));
                }
                // Cancellation and admission share the same contention write and lifecycle epoch.
                Document previous = inventories().findOneAndUpdate(session,
                        and(eq("institutionId", actor.institutionId()), eq("eventId", eventId)), inc("version", 1L),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE));
                if (previous == null) throw new IllegalStateException("Inventory unavailable");
                if (!"OPEN".equals(previous.getString("lifecycle"))) throw new IllegalStateException("Event is not open");
                if (expectedLifecycleRevision != number(previous, "lifecycleRevision")) throw new IllegalStateException("Lifecycle revision conflict");
                Date now = Date.from(clock.instant());
                inventories().updateOne(session, eq("_id", previous.getObjectId("_id")),
                        combine(inc("lifecycleRevision", 1L), set("lifecycle", "CANCELLED"), set("updatedAt", now)));
                db.getCollection("idempotencyRecords").insertOne(session, base(actor, eventId, now).append("requestKey", requestKey)
                        .append("payloadHash", hash).append("status", "CANCELLED").append("queueSequence", 0L));
                db.getCollection("auditEvents").insertOne(session, base(actor, eventId, now).append("kind", "EVENT_CANCELLED").append("operationId", operationId));
                db.getCollection("outbox").insertOne(session, base(actor, eventId, now).append("kind", "EVENT_CANCELLED").append("operationId", operationId));
                return new Receipt("CANCELLED", 0L);
            }, TX);
        }
    }

    private MongoCollection<Document> inventories() { return db.getCollection("inventories"); }
    private static long number(Document doc, String field) { return ((Number) doc.get(field)).longValue(); }
    private static Document base(AuthorizedActor actor, String event, Date now) {
        return new Document("institutionId", actor.institutionId()).append("eventId", event).append("personId", actor.personId())
                .append("schemaVersion", FoundationSchema.VERSION).append("createdAt", now);
    }
    private static void token(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid scoped identifier"); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
