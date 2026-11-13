package edu.cuj.pulse.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** Real server only. Missing managed loopback replica set is a failure, never a skipped acceptance. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class MongoFoundationTest {
    static MongoClient client;
    static String prefix;
    MongoDatabase db;
    MongoSeatAllocator allocator;

    @BeforeAll static void connectOwnedSyntheticReplicaSet() throws Exception {
        int port = Integer.parseInt(System.getProperty("pulse.mongo.test.port"));
        prefix = System.getProperty("pulse.mongo.test.db");
        assertTrue(port >= 20000 && port <= 60000, "Dedicated test port required");
        assertTrue(prefix.matches("pulse_foundation_[a-f0-9]{16}"), "Synthetic database prefix required");
        String base = "mongodb://127.0.0.1:" + port + "/?directConnection=true&serverSelectionTimeoutMS=3000";
        try (MongoClient bootstrap = MongoClients.create(base)) {
            Document hello = bootstrap.getDatabase("admin").runCommand(new Document("hello", 1));
            String setName = hello.getString("setName");
            if (setName == null) {
                var config = new Document("_id", "pulseFoundation").append("members", List.of(new Document("_id", 0).append("host", "127.0.0.1:" + port)));
                bootstrap.getDatabase("admin").runCommand(new Document("replSetInitiate", config));
            } else if (!"pulseFoundation".equals(setName)) {
                throw new IllegalStateException("Refusing test server with unexpected replica set: " + setName);
            }
            long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!Boolean.TRUE.equals(bootstrap.getDatabase("admin").runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary"))) {
                if (System.nanoTime() > end) throw new IllegalStateException("Replica primary election timed out");
                Thread.sleep(100);
            }
        }
        client = MongoClients.create(MongoClientSettings.builder().applicationName("pulse-mongo-foundation")
                .applyConnectionString(new ConnectionString(base + "&replicaSet=pulseFoundation"))
                .timeout(15, TimeUnit.SECONDS).build());
        assertEquals("pulseFoundation", client.getDatabase("admin").runCommand(new Document("hello", 1)).getString("setName"));
    }
    @AfterAll static void close() {
        if (client == null) return;
        // The runner supplies a fresh random prefix; remove only databases owned by this test run.
        try {
            for (String name : client.listDatabaseNames().into(new ArrayList<>())) {
                if (name.startsWith(prefix + "_")) client.getDatabase(name).drop();
            }
        } finally {
            client.close();
        }
    }
    @BeforeEach void freshDatabase() {
        db = client.getDatabase(prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        FoundationSchema.installFresh(db);
        allocator = new MongoSeatAllocator(client, db.getName(), Clock.systemUTC());
        allocator.provisionInventory("institutionA", "eventA", 1);
    }

    @Test void aConcurrentLastSeatAndQueueHaveAtomicEvidence() throws Exception {
        List<MongoSeatAllocator.Receipt> results = race(16, i -> allocator.register(actor("p" + i), "eventA", key(), 0));
        assertEquals(1, results.stream().filter(r -> r.status().equals("REGISTERED")).count());
        var sequences = new HashSet<Long>();
        results.stream().filter(r -> r.status().equals("WAITLISTED")).forEach(r -> sequences.add(r.queueSequence()));
        assertEquals(15, sequences.size());
        for (long i = 1; i <= 15; i++) assertTrue(sequences.contains(i));
        assertEquals(0, number(inventory(), "remaining"));
        assertEquals(16, number(inventory(), "version"));
        assertEvidenceCount(16);
    }

    @Test void bDuplicateRequestReturnsImmutableReceiptWithoutSideEffects() throws Exception {
        String request = key();
        List<MongoSeatAllocator.Receipt> results = race(12, i -> allocator.register(actor("same"), "eventA", request, 0));
        assertEquals(1, new HashSet<>(results).size());
        Document before = db.getCollection("idempotencyRecords").find().first();
        assertEquals(results.getFirst(), allocator.register(actor("same"), "eventA", request, 0));
        assertEquals(before, db.getCollection("idempotencyRecords").find().first());
        assertEquals(1, number(inventory(), "version"));
        assertEvidenceCount(1);
    }

    @Test void cPayloadConflictAndNewKeyDuplicateDoNotConsumeAnotherSeat() {
        String request = key();
        allocator.register(actor("same"), "eventA", request, 0);
        assertThrows(IllegalStateException.class, () -> allocator.register(actor("same"), "eventA", request, 1));
        assertThrows(IllegalStateException.class, () -> allocator.register(actor("same"), "eventA", key(), 0));
        assertEvidenceCount(1);
        assertEquals(1, number(inventory(), "version"));
    }

    @Test void dInstitutionIsolationAndStaleRevisionAreEnforced() {
        assertThrows(IllegalStateException.class, () -> allocator.register(new MongoSeatAllocator.AuthorizedActor("other", "p"), "eventA", key(), 0));
        assertThrows(IllegalStateException.class, () -> allocator.register(actor("p"), "eventA", key(), 1));
        assertEvidenceCount(0);
        assertEquals(1, number(inventory(), "remaining"));
        assertEquals(0, number(inventory(), "version"));
    }

    @Test void eCancellationContendsWithAdmissionAndPreventsLaterAdmission() throws Exception {
        race(12, i -> {
            if (i == 0) { allocator.cancel(actor("canceller"), "eventA", key(), 0); return "cancel"; }
            try { return allocator.register(actor("p" + i), "eventA", key(), 0).status(); }
            catch (IllegalStateException expected) { assertEquals("Event is not open", expected.getMessage()); return "closed"; }
        });
        assertEquals("CANCELLED", inventory().getString("lifecycle"));
        assertEquals(1, number(inventory(), "lifecycleRevision"));
        long count = db.getCollection("registrations").countDocuments();
        assertThrows(IllegalStateException.class, () -> allocator.register(actor("late"), "eventA", key(), 1));
        assertEquals(count, db.getCollection("registrations").countDocuments());
        assertEquals(count + 1, db.getCollection("idempotencyRecords").countDocuments());
        assertEquals(count + 1, db.getCollection("auditEvents").countDocuments());
        assertEquals(count + 1, db.getCollection("outbox").countDocuments());
        assertEquals(count, db.getCollection("registrations").countDocuments());
        assertTrue(number(inventory(), "remaining") >= 0);
    }

    @Test void eCancellationRetryIsImmutableAndRecordsAuditAndOutboxAtomically() {
        var actor = actor("canceller"); String request = key();
        List<MongoSeatAllocator.Receipt> receipts;
        try { receipts = race(12, i -> allocator.cancel(actor, "eventA", request, 0)); }
        catch (Exception failure) { throw new AssertionError(failure); }
        assertEquals(1, new HashSet<>(receipts).size());
        assertEquals(new MongoSeatAllocator.Receipt("CANCELLED", 0), receipts.getFirst());
        Document inventory = inventory();
        assertEquals(new MongoSeatAllocator.Receipt("CANCELLED", 0), allocator.cancel(actor, "eventA", request, 0));
        assertEquals(inventory, inventory());
        assertEquals(0, db.getCollection("registrations").countDocuments());
        assertEquals(1, db.getCollection("idempotencyRecords").countDocuments());
        assertEquals(1, db.getCollection("auditEvents").countDocuments(eq("kind", "EVENT_CANCELLED")));
        assertEquals(1, db.getCollection("outbox").countDocuments(eq("kind", "EVENT_CANCELLED")));
        assertThrows(IllegalStateException.class, () -> allocator.cancel(actor, "eventA", request, 1));
        assertThrows(IllegalStateException.class, () -> allocator.cancel(actor("other"), "eventA", request, 0));
        assertEquals(1, db.getCollection("auditEvents").countDocuments());
        assertEquals(1, number(inventory(), "version"));
        assertEquals(1, number(inventory(), "lifecycleRevision"));
    }

    @Test void eCancellationKeyCannotReplayARegistrationReceipt() {
        var actor = actor("same-person"); String request = key();
        assertEquals("REGISTERED", allocator.register(actor, "eventA", request, 0).status());
        assertThrows(IllegalStateException.class, () -> allocator.cancel(actor, "eventA", request, 0));
        assertEquals("OPEN", inventory().getString("lifecycle"));
        assertEquals(1, db.getCollection("registrations").countDocuments());
        assertEquals(1, db.getCollection("idempotencyRecords").countDocuments());
        assertEquals(0, db.getCollection("auditEvents").countDocuments(eq("kind", "EVENT_CANCELLED")));
        assertEquals(1, number(inventory(), "version"));
    }

    @Test void eRegistrationKeyCannotReplayACancellationReceipt() {
        var actor = actor("same-person"); String request = key();
        assertEquals(new MongoSeatAllocator.Receipt("CANCELLED", 0), allocator.cancel(actor, "eventA", request, 0));
        assertThrows(IllegalStateException.class, () -> allocator.register(actor, "eventA", request, 0));
        assertEquals("CANCELLED", inventory().getString("lifecycle"));
        assertEquals(0, db.getCollection("registrations").countDocuments());
        assertEquals(1, db.getCollection("idempotencyRecords").countDocuments());
        assertEquals(1, db.getCollection("auditEvents").countDocuments(eq("kind", "EVENT_CANCELLED")));
        assertEquals(1, db.getCollection("outbox").countDocuments(eq("kind", "EVENT_CANCELLED")));
        assertEquals(1, number(inventory(), "version"));
    }

    @Test void eCancellationOutboxFailureRollsBackLifecycleAndEvidence() {
        db.runCommand(new Document("collMod", "outbox").append("validator", new Document("kind", "neverAllowed")));
        assertThrows(MongoException.class, () -> allocator.cancel(actor("canceller"), "eventA", key(), 0));
        assertEquals("OPEN", inventory().getString("lifecycle"));
        assertEquals(0, number(inventory(), "lifecycleRevision"));
        assertEquals(0, number(inventory(), "version"));
        assertEquals(1, number(inventory(), "remaining"));
        for (String collection : List.of("registrations", "idempotencyRecords", "auditEvents", "outbox"))
            assertEquals(0, db.getCollection(collection).countDocuments(), collection);
    }

    @Test void fFailureAfterOutboxWriteRollsBackAllDocuments() {
        db.runCommand(new Document("collMod", "auditEvents").append("validator", new Document("personId", "neverAllowed")));
        assertThrows(MongoException.class, () -> allocator.register(actor("p"), "eventA", key(), 0));
        assertEvidenceCount(0);
        assertEquals(1, number(inventory(), "remaining"));
        assertEquals(0, number(inventory(), "version"));
    }

    @Test void gValidatorsAndUniqueIndexesRejectInvalidWrites() {
        assertThrows(MongoException.class, () -> db.getCollection("inventories").updateOne(eq("eventId", "eventA"), new Document("$set", new Document("remaining", -1L))));
        assertThrows(MongoException.class, () -> db.getCollection("inventories").updateOne(eq("eventId", "eventA"), new Document("$set", new Document("unboundedPayload", "unexpected"))));
        assertThrows(MongoException.class, () -> allocator.provisionInventory("institutionA", "eventA", 1));
        assertThrows(IllegalStateException.class, () -> FoundationSchema.installFresh(db));
        assertEquals(1, db.getCollection("inventories").countDocuments());
        assertEquals(1, number(inventory(), "remaining"));
    }

    @Test void hTransientTransactionRetryDoesNotDuplicateEffects() {
        failNextCommand("insert", 112, "TransientTransactionError");
        try { assertEquals("REGISTERED", allocator.register(actor("retry"), "eventA", key(), 0).status()); }
        finally { disableFailPoint(); }
        assertEvidenceCount(1);
        assertEquals(1, number(inventory(), "version"));
    }

    @Test void iUnknownCommitResultRetriesCommitWithoutDuplicatingEffects() {
        // ShutdownInProgress takes the single-node set into recovery, while
        // MaxTimeMSExpired is translated to a terminal driver timeout even with an
        // unknown-result label. A one-shot NotWritablePrimary response preserves the
        // server and exercises the driver's unknown-commit retry path.
        failNextCommand("commitTransaction", 10107, "UnknownTransactionCommitResult");
        try { assertEquals("REGISTERED", allocator.register(actor("commit"), "eventA", key(), 0).status()); }
        finally { disableFailPoint(); }
        assertEvidenceCount(1);
        assertEquals(1, number(inventory(), "version"));
    }

    private static void failNextCommand(String command, int code, String label) {
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1)).append("data", new Document("failCommands", List.of(command))
                        .append("appName", "pulse-mongo-foundation").append("errorCode", code).append("errorLabels", List.of(label))));
    }
    private static void disableFailPoint() { client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off")); }
    private MongoSeatAllocator.AuthorizedActor actor(String person) { return new MongoSeatAllocator.AuthorizedActor("institutionA", person); }
    private static String key() { return UUID.randomUUID().toString(); }
    private Document inventory() { return db.getCollection("inventories").find(eq("eventId", "eventA")).first(); }
    private static long number(Document document, String field) { return ((Number) document.get(field)).longValue(); }
    private void assertEvidenceCount(long count) {
        for (String collection : List.of("registrations", "idempotencyRecords", "auditEvents", "outbox")) assertEquals(count, db.getCollection(collection).countDocuments(), collection);
    }
    private static <T> List<T> race(int count, java.util.function.IntFunction<T> operation) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
            List<Future<T>> work = new ArrayList<>();
            for (int i = 0; i < count; i++) { int actor = i; work.add(pool.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return operation.apply(actor); })); }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<T> result = new ArrayList<>();
            for (Future<T> task : work) result.add(task.get(30, TimeUnit.SECONDS));
            return result;
        }
    }
}
