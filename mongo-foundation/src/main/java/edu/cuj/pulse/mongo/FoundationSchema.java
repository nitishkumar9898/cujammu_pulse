package edu.cuj.pulse.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;

import java.util.List;

/** Explicit migration step, never an implicit application startup operation. */
public final class FoundationSchema {
    public static final int VERSION = 2;
    private FoundationSchema() {}

    public static void installFresh(MongoDatabase db) {
        // Fresh-only avoids quietly changing an existing deployment's validators/indexes.
        if (db.listCollectionNames().iterator().hasNext()) throw new IllegalStateException("Fresh synthetic database required");
        create(db, "inventories", List.of("institutionId", "eventId", "schemaVersion", "createdAt", "updatedAt", "remaining", "capacity", "version", "lifecycleRevision", "queueSequence", "lifecycle"),
                "institutionId", text(), "eventId", text(), "schemaVersion", integer(), "createdAt", date(), "updatedAt", date(),
                "remaining", nonnegative(), "capacity", nonnegative(), "version", nonnegative(), "lifecycleRevision", nonnegative(), "queueSequence", nonnegative(),
                "lifecycle", new Document("enum", List.of("OPEN", "CANCELLED")));
        create(db, "registrations", List.of("institutionId", "eventId", "personId", "schemaVersion", "createdAt", "status", "queueSequence"),
                "institutionId", text(), "eventId", text(), "personId", text(), "schemaVersion", integer(), "createdAt", date(),
                "status", new Document("enum", List.of("REGISTERED", "WAITLISTED")), "queueSequence", nonnegative());
        create(db, "idempotencyRecords", List.of("institutionId", "eventId", "personId", "requestKey", "payloadHash", "schemaVersion", "createdAt", "status", "queueSequence"),
                "institutionId", text(), "eventId", text(), "personId", text(), "requestKey", text(), "payloadHash", new Document("bsonType", "string").append("pattern", "^[a-f0-9]{64}$"),
                "schemaVersion", integer(), "createdAt", date(), "status", new Document("enum", List.of("REGISTERED", "WAITLISTED", "CANCELLED")), "queueSequence", nonnegative());
        for (String name : List.of("auditEvents", "outbox")) {
            create(db, name, List.of("institutionId", "eventId", "personId", "schemaVersion", "createdAt", "kind", "operationId"),
                    "institutionId", text(), "eventId", text(), "personId", text(), "schemaVersion", integer(), "createdAt", date(),
                    "kind", new Document("enum", List.of("REGISTRATION_ACCEPTED", "EVENT_CANCELLED")), "operationId", text());
        }
        unique(db, "inventories", "uk_inventory", "institutionId", "eventId");
        unique(db, "registrations", "uk_enrollment", "institutionId", "eventId", "personId");
        unique(db, "idempotencyRecords", "uk_request", "institutionId", "eventId", "personId", "requestKey");
        unique(db, "auditEvents", "uk_audit_operation", "institutionId", "operationId");
        unique(db, "outbox", "uk_outbox_operation", "institutionId", "operationId");
    }

    private static void create(MongoDatabase db, String name, List<String> required, Object... properties) {
        Document fields = new Document("_id", new Document("bsonType", "objectId"));
        for (int i = 0; i < properties.length; i += 2) fields.append((String) properties[i], properties[i + 1]);
        Document jsonSchema = new Document("bsonType", "object").append("required", required).append("additionalProperties", false).append("properties", fields);
        db.createCollection(name, new CreateCollectionOptions().validationOptions(new ValidationOptions().validator(new Document("$jsonSchema", jsonSchema))));
    }
    private static Document text() { return new Document("bsonType", "string").append("minLength", 1).append("maxLength", 128); }
    private static Document integer() { return new Document("bsonType", List.of("int", "long")).append("enum", List.of(VERSION)); }
    private static Document nonnegative() { return new Document("bsonType", List.of("int", "long")).append("minimum", 0); }
    private static Document date() { return new Document("bsonType", "date"); }
    private static void unique(MongoDatabase db, String collection, String name, String... fields) {
        db.getCollection(collection).createIndex(Indexes.ascending(fields), new IndexOptions().unique(true).name(name));
    }
}
