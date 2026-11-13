package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.service.AuditService;
import com.college.events.service.EventService;
import com.college.events.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Independent assignment boundary tests; H2/MVC evidence does not certify deployed Mongo or devices. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:event-proposal-assignment-http;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "app.default-admin-password=",
    "app.upload.dir=target/test-uploads/event-proposal-assignment-http", "spring.cache.type=none",
    "logging.file.name=target/test-logs/event-proposal-assignment-http.log", "app.media.cleanup-cron=-",
    "app.identity.enabled=false", "app.email.backend=console"
})
@AutoConfigureMockMvc
@DirtiesContext
class EventProposalAssignmentHttpTest {
    private static final String BASE = "/api/v1/admin/event-proposals";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EventService service;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired AuditLogRepository audits;
    @Autowired NotificationRepository notifications;
    @Autowired MailDeliveryRepository mail;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired FeatureFlagRepository flags;
    @MockitoSpyBean AuditService audit;
    @MockitoSpyBean NotificationService notificationService;
    User dispatcher, reviewer, alternative, owner, student;
    Category category;

    @BeforeEach void setup() {
        dispatcher = account(Role.ADMIN); reviewer = account(Role.ADMIN); alternative = account(Role.ADMIN);
        owner = account(Role.ORGANIZER); student = account(Role.STUDENT);
        category = categories.saveAndFlush(Category.builder().name("Assignment " + UUID.randomUUID()).build());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); reset(audit, notificationService); }

    @Test void anonymousStudentAndMissingCsrfCannotAssignOrDiscoverReviewQueue() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count(), beforeInbox = notifications.count();
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(user(student))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/reviewers").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(put(assignmentUrl(proposal)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer)))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(assignmentUrl(proposal)).with(user(student)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer)))
            .andExpect(status().isForbidden());
        mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).contentType("application/json").content(assignment(proposal, reviewer)))
            .andExpect(status().isForbidden());
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeInbox, notifications.count());
    }

    @Test void assignmentPersistsProvenanceAndOnePrivateNoticeWithoutPublicAnnouncement() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count(), beforeInbox = notifications.count(), beforeMail = mail.count();
        Event assigned = assign(dispatcher, proposal, reviewer);
        assertEquals(reviewer.getId(), assigned.getAssignedReviewerId()); assertEquals(dispatcher.getId(), assigned.getReviewerAssignedByUserId());
        assertNotNull(assigned.getReviewerAssignedAt()); assertEquals(proposal.getLifecycleVersion(), assigned.getLastReviewerAssignmentRevision());
        assertTrue(assigned.getLifecycleVersion() > proposal.getLifecycleVersion()); assertEquals(EventStatus.PENDING, assigned.getStatus());
        assertEquals(beforeAudit + 1, audits.count()); assertEquals(beforeInbox + 1, notifications.count()); assertEquals(beforeMail, mail.count());
        assertTrue(operations.findByEventId(proposal.getId()).isEmpty());
        var notice = notifications.findAll().stream().max(Comparator.comparing(Notification::getId)).orElseThrow();
        assertEquals(reviewer.getId(), notice.getUser().getId());
        assertFalse(notice.getMessage().contains(proposal.getDescription()));
        var assignmentAudit = audits.findAll().stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
        assertEquals("EVENT_REVIEWER_ASSIGNED", assignmentAudit.getAction());
        assertTrue(assignmentAudit.getDetails().contains("reviewerId=" + reviewer.getId()));
        assertTrue(assignmentAudit.getDetails().contains("inputRevision=" + proposal.getLifecycleVersion()));
    }

    @Test void legacyUnassignedProposalCannotBeReviewedByAnyAdministrator() throws Exception {
        Event legacy = proposal(owner); long beforeAudit = audits.count(), beforeMail = mail.count();
        approve(legacy, reviewer, 403); reject(legacy, dispatcher, 403);
        unchanged(legacy); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void unassignedLegacyApprovalCannotBypassAssignmentThroughDecisionReplay() throws Exception {
        Event legacy = proposal(owner); legacy.setStatus(EventStatus.APPROVED); legacy.setApprovedAt(LocalDateTime.now().withNano(0));
        legacy.setLastProposalDecisionRevision(0L); legacy = events.saveAndFlush(legacy);
        mvc.perform(post(BASE + "/" + legacy.getId() + "/approve").with(user(reviewer)).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision", 0))))
            .andExpect(status().isForbidden());
        unchanged(legacy);
    }

    @Test void ownerCannotDispatchOwnProposalAndOwnerCannotBeItsReviewer() throws Exception {
        Event dispatcherOwned = proposal(dispatcher), reviewerOwned = proposal(reviewer);
        mvc.perform(put(assignmentUrl(dispatcherOwned)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(dispatcherOwned, reviewer)))
            .andExpect(status().isForbidden());
        mvc.perform(put(assignmentUrl(reviewerOwned)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(reviewerOwned, reviewer)))
            .andExpect(status().isForbidden());
        unchanged(dispatcherOwned); unchanged(reviewerOwned);
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "wrong-role", "missing"})
    void assignmentRequiresAnExistingEnabledAdministrator(String targetState) throws Exception {
        Event proposal = proposal(owner);
        if (targetState.equals("disabled")) { reviewer.setEnabled(false); reviewer = users.saveAndFlush(reviewer); }
        if (targetState.equals("wrong-role")) { reviewer.setRole(Role.FACULTY); reviewer = users.saveAndFlush(reviewer); }
        long targetId = targetState.equals("missing") ? Long.MAX_VALUE : reviewer.getId();
        mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedRevision", proposal.getLifecycleVersion(), "reviewerId", targetId))))
            .andExpect(status().isForbidden());
        unchanged(proposal);
    }

    @Test void revokedDispatchAndReviewerPrincipalsCannotMutateWithStaleRoles() throws Exception {
        Event proposal = proposal(owner); Event assigned = assign(dispatcher, proposal, reviewer);
        as(dispatcher); User revokedDispatcher = users.findById(dispatcher.getId()).orElseThrow(); revokedDispatcher.setRole(Role.STUDENT); users.saveAndFlush(revokedDispatcher);
        assertThrows(AccessDeniedException.class, () -> service.assignReviewer(assigned.getId(), alternative.getId(), assigned.getLifecycleVersion()));
        as(reviewer); User disabledReviewer = users.findById(reviewer.getId()).orElseThrow(); disabledReviewer.setEnabled(false); users.saveAndFlush(disabledReviewer);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(assigned.getId(), assigned.getLifecycleVersion()));
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(assigned.getId(), "Synthetic review", assigned.getLifecycleVersion()));
        unchanged(assigned);
    }

    @Test void exactRetryAndSameTargetAtCurrentRevisionDoNotRepeatAssignmentEffects() throws Exception {
        Event proposal = proposal(owner); Event assigned = assign(dispatcher, proposal, reviewer);
        long beforeAudit = audits.count(), beforeInbox = notifications.count(), beforeMail = mail.count();
        assign(dispatcher, proposal, reviewer); unchanged(assigned);
        assign(alternative, assigned, reviewer); unchanged(assigned);
        mvc.perform(put(assignmentUrl(proposal)).with(user(alternative)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer)))
            .andExpect(status().isConflict());
        assertEquals(beforeAudit, audits.count()); assertEquals(beforeInbox, notifications.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void reassignmentRejectsStaleInputAndRevokesThePreviousReviewer() throws Exception {
        Event proposal = proposal(owner); Event assigned = assign(dispatcher, proposal, reviewer);
        mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(proposal, alternative)))
            .andExpect(status().isConflict());
        unchanged(assigned);
        Event reassigned = assign(dispatcher, assigned, alternative);
        assertEquals(alternative.getId(), reassigned.getAssignedReviewerId());
        var assignmentAudit = audits.findAll().stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
        assertTrue(assignmentAudit.getDetails().contains("previousReviewerId=" + reviewer.getId()));
        assertTrue(assignmentAudit.getDetails().contains("reviewerId=" + alternative.getId()));
        approve(reassigned, reviewer, 403); reject(reassigned, reviewer, 403); unchanged(reassigned);
        approve(reassigned, alternative, 204); assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, reload(reassigned).getStatus());
    }

    @Test void onlyAssignedAdministratorCanApproveOrRejectAndDecisionRetryStaysNoWrite() throws Exception {
        Event assigned = assign(dispatcher, proposal(owner), reviewer);
        approve(assigned, dispatcher, 403); reject(assigned, alternative, 403); unchanged(assigned);
        approve(assigned, reviewer, 204); Event approved = reload(assigned);
        long beforeAudit = audits.count(), beforeMail = mail.count(); approve(assigned, reviewer, 204);
        unchanged(approved); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
        approve(assigned, dispatcher, 403);
        Event second = assign(dispatcher, proposal(owner), reviewer); reject(second, reviewer, 204);
        assertEquals(EventStatus.REJECTED, reload(second).getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"reviewer", "assigner", "time", "input-revision"})
    void partialAssignmentProvenanceCannotAuthorizeDecisions(String missing) throws Exception {
        Event assigned = assign(dispatcher, proposal(owner), reviewer);
        switch (missing) {
            case "reviewer" -> assigned.setAssignedReviewerId(null);
            case "assigner" -> assigned.setReviewerAssignedByUserId(null);
            case "time" -> assigned.setReviewerAssignedAt(null);
            case "input-revision" -> assigned.setLastReviewerAssignmentRevision(null);
            default -> throw new AssertionError("Unexpected fixture");
        }
        assigned = events.saveAndFlush(assigned); approve(assigned, reviewer, 403); reject(assigned, reviewer, 403); unchanged(assigned);
    }

    @ParameterizedTest @EnumSource(value = EventStatus.class, names = {"REJECTED", "APPROVED", "CANCELLED", "COMPLETED"})
    void assignmentCannotRewriteNonPendingEventStates(EventStatus status) throws Exception {
        Event original = proposal(owner); original.setStatus(status); original = events.saveAndFlush(original);
        mvc.perform(put(assignmentUrl(original)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(original, reviewer)))
            .andExpect(status().isConflict());
        unchanged(original);
    }

    @ParameterizedTest @ValueSource(strings = {"operations", "registration", "approval"})
    void pendingLabelDoesNotPermitAssignmentOverHistoricalEvidence(String history) throws Exception {
        Event original = proposal(owner);
        if (history.equals("operations")) operations.saveAndFlush(EventOperations.builder().event(original).build());
        if (history.equals("registration")) registrations.saveAndFlush(Registration.builder().event(original).user(student).build());
        if (history.equals("approval")) { original.setApprovedAt(LocalDateTime.now().withNano(0).minusDays(1)); original = events.saveAndFlush(original); }
        mvc.perform(put(assignmentUrl(original)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(original, reviewer)))
            .andExpect(status().isConflict());
        unchanged(original);
    }

    @Test void failedAuditRollsBackAssignmentAndPrivateNotice() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count(), beforeInbox = notifications.count(), beforeMail = mail.count();
        doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(), anyString(), anyString(), anyString());
        var result = mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer))).andReturn();
        assertTrue(result.getResponse().getStatus() >= 400, "A failed durable audit must not report success");
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeInbox, notifications.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void failedPrivateNoticeRollsBackAssignmentAndItsAudit() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count(), beforeInbox = notifications.count();
        doThrow(new IllegalStateException("Synthetic inbox outage")).when(notificationService).createNotification(any(User.class), anyString(), anyString());
        var result = mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer))).andReturn();
        assertTrue(result.getResponse().getStatus() >= 400);
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeInbox, notifications.count());
    }

    @Test void queueCapabilitiesReflectAssignmentAndFreshReviewerAvailability() throws Exception {
        Event assigned = assign(dispatcher, proposal(owner), reviewer);
        JsonNode assignedView = find(page(BASE, reviewer, 0), assigned.getId());
        assertTrue(assignedView.get("canDecide").asBoolean()); assertTrue(assignedView.get("assignedReviewerActive").asBoolean());
        JsonNode dispatcherView = find(page(BASE, dispatcher, 0), assigned.getId());
        assertFalse(dispatcherView.get("canDecide").asBoolean()); assertTrue(dispatcherView.get("canAssign").asBoolean());
        reviewer.setEnabled(false); reviewer = users.saveAndFlush(reviewer);
        JsonNode unavailable = find(page(BASE, dispatcher, 0), assigned.getId());
        assertFalse(unavailable.get("assignedReviewerActive").asBoolean()); assertFalse(unavailable.get("canDecide").asBoolean());
        Event own = proposal(dispatcher); JsonNode ownView = find(page(BASE, dispatcher, 0), own.getId());
        assertFalse(ownView.get("canAssign").asBoolean()); assertFalse(ownView.get("canDecide").asBoolean());
    }

    @Test void opaqueProposalMediaRoutesAllowOnlyOwnerAndCurrentAssignedReviewer() throws Exception {
        Event proposal = proposal(owner);
        String bannerKey = "events/" + UUID.randomUUID() + ".png";
        String templateKey = "events/" + UUID.randomUUID() + ".png";
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        proposal.setBannerImage(bannerKey); proposal.setCertificateTemplatePath(templateKey); proposal = events.saveAndFlush(proposal);
        Instant accepted = Instant.now();
        mediaAssets.saveAndFlush(MediaAsset.builder().ownerUserId(owner.getId()).resourceType("EVENT_BANNER").resourceId(proposal.getId())
                .objectKey(bannerKey).mediaType("image/png").sizeBytes(bytes.length).uploadAcceptedAt(accepted)
                .expiresAt(accepted.plusSeconds(86400)).status("ACTIVE").sha256("a".repeat(64)).build());
        mediaAssets.saveAndFlush(MediaAsset.builder().ownerUserId(owner.getId()).resourceType("EVENT_CERT_TEMPLATE").resourceId(proposal.getId())
                .objectKey(templateKey).mediaType("image/png").sizeBytes(bytes.length).uploadAcceptedAt(accepted)
                .expiresAt(accepted.plusSeconds(86400)).status("ACTIVE").sha256("b".repeat(64)).build());
        Path root = Path.of("target/test-uploads/event-proposal-assignment-http");
        for (String key : List.of(bannerKey, templateKey)) { Path file = root.resolve(key); Files.createDirectories(file.getParent()); Files.write(file, bytes); }

        Event assigned = assign(dispatcher, proposal, reviewer);
        for (String kind : List.of("banner", "certificate-template")) {
            mvc.perform(get(BASE + "/" + assigned.getId() + "/media/" + kind).with(user(reviewer)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(content().bytes(bytes));
            mvc.perform(get("/api/v1/organizer/event-proposals/" + assigned.getId() + "/media/" + kind).with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
            mvc.perform(get(BASE + "/" + assigned.getId() + "/media/" + kind).with(user(alternative)))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/organizer/event-proposals/" + assigned.getId() + "/media/" + kind).with(user(alternative)))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get("/uploads/" + bannerKey).with(user(alternative))).andExpect(status().isNotFound());
        String queueJson = mvc.perform(get(BASE).with(user(reviewer))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(queueJson.contains(bannerKey)); assertFalse(queueJson.contains(templateKey));

        FeatureFlag eventsFlag = flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); boolean wasEnabled = eventsFlag.isEnabled();
        eventsFlag.setEnabled(false); flags.saveAndFlush(eventsFlag);
        try {
            mvc.perform(get(BASE + "/" + assigned.getId() + "/media/banner").with(user(reviewer))).andExpect(status().isConflict());
            mvc.perform(get("/api/v1/organizer/event-proposals/" + assigned.getId() + "/media/banner").with(user(owner))).andExpect(status().isConflict());
        } finally { FeatureFlag restored = flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); restored.setEnabled(wasEnabled); flags.saveAndFlush(restored); }

        MediaAsset banner = mediaAssets.findByObjectKey(bannerKey).orElseThrow(); Instant originalExpiry = banner.getExpiresAt();
        banner.setExpiresAt(Instant.now().minusSeconds(1)); banner = mediaAssets.saveAndFlush(banner);
        mvc.perform(get(BASE + "/" + assigned.getId() + "/media/banner").with(user(reviewer))).andExpect(status().isGone());
        banner.setExpiresAt(originalExpiry); banner.setLegalHold(true); banner = mediaAssets.saveAndFlush(banner);
        mvc.perform(get("/api/v1/organizer/event-proposals/" + assigned.getId() + "/media/banner").with(user(owner))).andExpect(status().isGone());
        banner.setLegalHold(false); mediaAssets.saveAndFlush(banner);

        Event reassigned = assign(dispatcher, assigned, alternative);
        mvc.perform(get(BASE + "/" + reassigned.getId() + "/media/banner").with(user(reviewer))).andExpect(status().isForbidden());
    }

    @Test void disabledEventModuleBlocksAssignmentAndReviewWithoutWrites() throws Exception {
        Event proposal = proposal(owner), assigned = assign(dispatcher, proposal(owner), reviewer);
        long beforeAudit = audits.count(), beforeInbox = notifications.count();
        FeatureFlag flag = flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); boolean enabled = flag.isEnabled();
        flag.setEnabled(false); flags.saveAndFlush(flag);
        try {
            mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(assignment(proposal, reviewer)))
                .andExpect(status().is4xxClientError());
            mvc.perform(post(BASE + "/" + assigned.getId() + "/approve").with(user(reviewer)).with(csrf()).contentType("application/json").content(decision(assigned)))
                .andExpect(status().is4xxClientError());
            unchanged(proposal); unchanged(assigned); assertEquals(beforeAudit, audits.count()); assertEquals(beforeInbox, notifications.count());
        } finally { FeatureFlag current = flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); current.setEnabled(enabled); flags.saveAndFlush(current); }
    }

    @Test void staleConcurrentReassignmentAndApprovalHaveExactlyOneWinner() throws Exception {
        Event assigned = assign(dispatcher, proposal(owner), reviewer); long beforeAudit = audits.count();
        var ready = new CyclicBarrier(2); var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> reassign = () -> {
                try { as(dispatcher); ready.await(5, TimeUnit.SECONDS); service.assignReviewer(assigned.getId(), alternative.getId(), assigned.getLifecycleVersion()); return true; }
                catch (AccessDeniedException | IllegalStateException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            Callable<Boolean> decide = () -> {
                try { as(reviewer); ready.await(5, TimeUnit.SECONDS); service.approveEvent(assigned.getId(), assigned.getLifecycleVersion()); return true; }
                catch (AccessDeniedException | IllegalStateException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var assignment = workers.submit(reassign); var approval = workers.submit(decide);
            boolean reassigned = assignment.get(20, TimeUnit.SECONDS), approved = approval.get(20, TimeUnit.SECONDS);
            assertNotEquals(reassigned, approved); Event winner = reload(assigned); assertEquals(beforeAudit + 1, audits.count());
            assertEquals(approved ? EventStatus.APPROVED_PENDING_PUBLICATION : EventStatus.PENDING, winner.getStatus());
            assertEquals(approved ? reviewer.getId() : alternative.getId(), winner.getAssignedReviewerId());
            assertFalse(operations.findByEventId(assigned.getId()).isPresent());
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void reviewQueueAndReviewerDirectoryAreBoundedPrivateDtoPages() throws Exception {
        for (int i = 0; i < 21; i++) { proposal(owner); account(Role.ADMIN); }
        JsonNode first = page(BASE, dispatcher, 0), next = page(BASE, dispatcher, 1);
        assertEquals(Set.of("items", "page", "hasNext"), fields(first)); assertEquals(20, first.get("items").size()); assertTrue(first.get("hasNext").asBoolean());
        Set<Long> ids = new HashSet<>();
        for (JsonNode item : first.get("items")) {
            assertEquals(Set.of("id", "title", "description", "venue", "eventDate", "eventEndsAt", "organizerName", "status", "revision",
                "assignedReviewerId", "assignedReviewerName", "assignedReviewerActive", "canAssign", "canDecide", "canPublish", "maxSeats", "fees",
                "targetDepartment", "categoryName", "generateCertificate", "hasBanner", "hasCertificateTemplate"), fields(item));
            assertEquals("PENDING", item.get("status").asText()); ids.add(item.get("id").asLong());
        }
        for (JsonNode item : next.get("items")) assertFalse(ids.contains(item.get("id").asLong()));
        JsonNode directory = page(BASE + "/reviewers", dispatcher, 0);
        assertEquals(Set.of("items", "page", "hasNext"), fields(directory)); assertEquals(20, directory.get("items").size()); assertTrue(directory.get("hasNext").asBoolean());
        for (JsonNode item : directory.get("items")) {
            assertEquals(Set.of("id", "name"), fields(item)); User candidate = users.findById(item.get("id").asLong()).orElseThrow();
            assertTrue(candidate.isEnabled()); assertEquals(Role.ADMIN, candidate.getRole());
        }
        mvc.perform(get(BASE).with(user(dispatcher)).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/reviewers").with(user(dispatcher)).param("page", "10001")).andExpect(status().isBadRequest());
    }

    @Test void publicationQueueAndPublishEndpointRequireExplicitRevisionBoundDecision() throws Exception {
        Event pending = assign(dispatcher, proposal(owner), reviewer);
        approve(pending, reviewer, 204);
        Event approvedPrivate = reload(pending);
        assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, approvedPrivate.getStatus());
        JsonNode queue = page(BASE + "/publication-queue", dispatcher, 0);
        JsonNode projection = find(queue, pending.getId());
        assertEquals("APPROVED_PENDING_PUBLICATION", projection.get("status").asText());
        assertTrue(projection.get("canPublish").asBoolean());
        String privateCatalog = mvc.perform(get("/api/v1/cujammu/events/catalog").with(user(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(privateCatalog.contains("\"id\":" + pending.getId()), "approved but unpublished proposals must stay out of the public catalog");

        long expectedRevision = approvedPrivate.getLifecycleVersion();
        mvc.perform(post(BASE + "/{id}/publish", pending.getId()).with(user(student)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision", expectedRevision))))
            .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{id}/publish", pending.getId()).with(user(dispatcher)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision", expectedRevision - 1))))
            .andExpect(status().isConflict());
        assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, reload(pending).getStatus());

        long beforeAudit = audits.count();
        mvc.perform(post(BASE + "/{id}/publish", pending.getId()).with(user(dispatcher)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision", expectedRevision))))
            .andExpect(status().isNoContent());
        Event published = reload(pending);
        assertEquals(EventStatus.APPROVED, published.getStatus()); assertEquals(dispatcher.getId(), published.getPublishedByUserId());
        assertNotNull(published.getPublishedAt()); assertTrue(operations.findByEventId(pending.getId()).isPresent());
        long finalAudit = audits.count(), finalMail = mail.count(), finalOperations = operations.count();
        assertEquals(beforeAudit + 1, finalAudit);
        String publicCatalog = mvc.perform(get("/api/v1/cujammu/events/catalog").with(user(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(publicCatalog.contains("\"id\":" + pending.getId()), "publication makes the event publicly discoverable");
        mvc.perform(post(BASE + "/{id}/publish", pending.getId()).with(user(dispatcher)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision", expectedRevision))))
            .andExpect(status().isNoContent());
        assertEquals(EventStatus.APPROVED, reload(pending).getStatus());
        assertEquals(finalAudit, audits.count()); assertEquals(finalMail, mail.count()); assertEquals(finalOperations, operations.count());
    }

    @Test void ownProposalQueueIsBoundedAndCannotExposeAnotherOrganizersRecords() throws Exception {
        Event mine = proposal(owner), another = proposal(alternative);
        assertNotEquals(mine.getId(), another.getId());
        mvc.perform(get("/api/v1/organizer/event-proposals").with(user(owner)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(mine.getId()))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"));
        mvc.perform(get("/api/v1/organizer/event-proposals").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizer/event-proposals").with(user(owner)).param("page", "-1"))
            .andExpect(status().isBadRequest());
    }

    @Test void proposalSubmissionReceiptDeduplicatesExactRetriesAndRejectsKeyReuse() throws Exception {
        String key = UUID.randomUUID().toString();
        Map<String, Object> command = Map.of("requestKey", key, "title", "Synthetic shared proposal",
                "description", "Synthetic event proposal details for durable retry verification.", "categoryId", category.getId(),
                "venue", "Synthetic hall", "eventDate", LocalDateTime.now().withNano(0).plusDays(5).toString(),
                "maxSeats", 25, "fees", 0.0, "targetDepartment", "ALL", "generateCertificate", true);
        long beforeEvents = events.count(), beforeAudit = audits.count();
        var firstResponse = mvc.perform(post("/api/v1/organizer/event-proposals").with(user(owner)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(command)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.replayed").value(false)).andReturn();
        JsonNode first = json.readTree(firstResponse.getResponse().getContentAsString());
        mvc.perform(post("/api/v1/organizer/event-proposals").with(user(owner)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(command)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.get("id").asLong()))
            .andExpect(jsonPath("$.replayed").value(true));
        assertEquals(beforeEvents + 1, events.count()); assertEquals(beforeAudit + 1, audits.count());
        Map<String, Object> changed = new HashMap<>(command); changed.put("title", "Changed synthetic payload");
        mvc.perform(post("/api/v1/organizer/event-proposals").with(user(owner)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(changed)))
            .andExpect(status().isConflict());
        Map<String, Object> studentCommand = new HashMap<>(command); studentCommand.put("requestKey", UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/organizer/event-proposals").with(user(student)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(studentCommand)))
            .andExpect(status().isForbidden());
        assertEquals(beforeEvents + 1, events.count()); assertEquals(beforeAudit + 1, audits.count());
    }

    @Test void concurrentIdenticalProposalSubmissionsConvergeOnOneReceiptAndEvent() throws Exception {
        var command = new com.college.events.dto.EventProposalReviewDtos.Submit(UUID.randomUUID(), "Concurrent synthetic proposal",
                "Synthetic event proposal details for concurrent retry verification.", category.getId(), "Synthetic hall",
                LocalDateTime.now().withNano(0).plusDays(6), null, 24, 0.0, null, "ALL", true);
        long beforeEvents = events.count(), beforeAudit = audits.count(); var ready = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<com.college.events.dto.EventProposalReviewDtos.SubmitReceipt> request = () -> {
                try { as(owner); ready.await(5, TimeUnit.SECONDS); return service.submitProposal(command); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var first = pool.submit(request); var second = pool.submit(request); ready.countDown();
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.id(), b.id()); assertNotEquals(a.replayed(), b.replayed());
            assertEquals(beforeEvents + 1, events.count()); assertEquals(beforeAudit + 1, audits.count());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void sharedOwnerEditLoadsSafeDetailsAndResubmitsOnlyAtCurrentRevision() throws Exception {
        User otherOrganizer = account(Role.ORGANIZER);
        Event proposal = proposal(owner); proposal.setContactEmail("private@example.invalid"); proposal.setBannerImage("private/banner.png");
        proposal = events.saveAndFlush(proposal); proposal = assign(dispatcher, proposal, reviewer); reject(proposal, reviewer, 204);
        proposal = events.findById(proposal.getId()).orElseThrow(); long id = proposal.getId(), revision = proposal.getLifecycleVersion();
        mvc.perform(get("/api/v1/organizer/event-proposals/{id}", id).with(user(owner)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.revision").value(revision))
            .andExpect(jsonPath("$.hasBanner").value(true)).andExpect(jsonPath("$.contactEmail").value("private@example.invalid"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private/banner.png"))));
        mvc.perform(get("/api/v1/organizer/event-proposals/{id}", id).with(user(otherOrganizer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizer/event-proposals/{id}", id).with(user(student))).andExpect(status().isForbidden());
        Map<String, Object> command = new HashMap<>(); command.put("expectedRevision", revision); command.put("title", "Revised shared proposal");
        command.put("description", "Updated synthetic details for the shared proposal editing workflow."); command.put("categoryId", category.getId());
        command.put("venue", "Synthetic revised hall"); command.put("eventDate", LocalDateTime.now().withNano(0).plusDays(8).toString());
        command.put("eventEndsAt", null); command.put("maxSeats", 45); command.put("fees", 25.0); command.put("contactEmail", "private@example.invalid");
        command.put("targetDepartment", "ALL"); command.put("generateCertificate", true);
        long beforeAudit = audits.count();
        mvc.perform(put("/api/v1/organizer/event-proposals/{id}", id).with(user(owner)).with(csrf()).contentType("application/json").content(json.writeValueAsString(command)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.title").value("Revised shared proposal")).andExpect(jsonPath("$.revision").value(org.hamcrest.Matchers.greaterThan((int) revision)));
        Event updated = events.findById(id).orElseThrow(); assertEquals(EventStatus.PENDING, updated.getStatus()); assertEquals("Revised shared proposal", updated.getTitle());
        assertEquals("private/banner.png", updated.getBannerImage()); assertEquals(beforeAudit + 1, audits.count());
        assertNull(updated.getAssignedReviewerId()); assertNull(updated.getReviewerAssignedByUserId()); assertNull(updated.getReviewerAssignedAt()); assertNull(updated.getLastReviewerAssignmentRevision());
        command.put("title", "Stale revision update");
        mvc.perform(put("/api/v1/organizer/event-proposals/{id}", id).with(user(owner)).with(csrf()).contentType("application/json").content(json.writeValueAsString(command)))
            .andExpect(status().isConflict());
        assertEquals("Revised shared proposal", events.findById(id).orElseThrow().getTitle()); assertEquals(beforeAudit + 1, audits.count());
    }

    @Test void reviewerCanSeeDecisionTermsWithoutPrivateMediaLocatorsOrContactDetails() throws Exception {
        Event proposal = proposal(owner); proposal.setMaxSeats(75); proposal.setFees(125.50); proposal.setGenerateCertificate(false);
        proposal.setBannerImage("private/synthetic-banner.png"); proposal.setCertificateTemplatePath("private/synthetic-template.png");
        proposal.setContactEmail("synthetic-private-contact@example.invalid"); proposal = events.saveAndFlush(proposal);
        JsonNode item = find(page(BASE, dispatcher, 0), proposal.getId());
        assertEquals(75, item.get("maxSeats").asInt()); assertEquals(125.50, item.get("fees").asDouble());
        assertEquals("ALL", item.get("targetDepartment").asText()); assertEquals(category.getName(), item.get("categoryName").asText());
        assertFalse(item.get("generateCertificate").asBoolean()); assertTrue(item.get("hasBanner").asBoolean()); assertTrue(item.get("hasCertificateTemplate").asBoolean());
        assertFalse(item.toString().contains(proposal.getBannerImage())); assertFalse(item.toString().contains(proposal.getCertificateTemplatePath()));
        assertFalse(item.toString().contains(proposal.getContactEmail()));
    }

    @Test void commandValidationRejectsMissingOrNegativeVersionsAndInvalidReviewerIds() throws Exception {
        Event proposal = proposal(owner);
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(Map.of("reviewerId", reviewer.getId()), Map.of("expectedRevision", -1, "reviewerId", reviewer.getId()), Map.of("expectedRevision", 0, "reviewerId", 0))) {
            mvc.perform(put(assignmentUrl(proposal)).with(user(dispatcher)).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        }
        unchanged(proposal);
    }

    private User account(Role role) { return users.saveAndFlush(User.builder().name("Synthetic reviewer " + UUID.randomUUID()).email(UUID.randomUUID() + "@example.invalid")
        .password("synthetic-only").role(role).enabled(true).build()); }
    private Event proposal(User organizer) { return events.saveAndFlush(Event.builder().title("Synthetic unassigned proposal " + UUID.randomUUID())
        .description("Synthetic university proposal details for assignment boundary checks.").venue("Synthetic room").organizer(organizer).category(category)
        .eventDate(LocalDateTime.now().withNano(0).plusDays(3)).maxSeats(30).targetDepartment("ALL").build()); }
    private Event assign(User actor, Event proposal, User target) throws Exception {
        mvc.perform(put(assignmentUrl(proposal)).with(user(actor)).with(csrf()).contentType("application/json").content(assignment(proposal, target)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.assignedReviewerId").value(target.getId()));
        return reload(proposal);
    }
    private void approve(Event proposal, User actor, int expectedStatus) throws Exception {
        mvc.perform(post(BASE + "/" + proposal.getId() + "/approve").with(user(actor)).with(csrf()).contentType("application/json").content(decision(proposal)))
            .andExpect(status().is(expectedStatus));
    }
    private void reject(Event proposal, User actor, int expectedStatus) throws Exception {
        mvc.perform(post(BASE + "/" + proposal.getId() + "/reject").with(user(actor)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedRevision", proposal.getLifecycleVersion(), "reason", "Synthetic review reason"))))
            .andExpect(status().is(expectedStatus));
    }
    private JsonNode page(String path, User actor, int number) throws Exception {
        return json.readTree(mvc.perform(get(path).with(user(actor)).param("page", Integer.toString(number)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString());
    }
    private Set<String> fields(JsonNode node) { Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result; }
    private JsonNode find(JsonNode page, long eventId) {
        for (JsonNode item : page.get("items")) if (item.get("id").asLong() == eventId) return item;
        throw new AssertionError("Newly created proposal absent from bounded queue");
    }
    private String assignmentUrl(Event event) { return BASE + "/" + event.getId() + "/reviewer"; }
    private String assignment(Event event, User target) throws Exception { return json.writeValueAsString(Map.of("expectedRevision", event.getLifecycleVersion(), "reviewerId", target.getId())); }
    private String decision(Event event) throws Exception { return json.writeValueAsString(Map.of("expectedRevision", event.getLifecycleVersion())); }
    private Event reload(Event event) { return events.findById(event.getId()).orElseThrow(); }
    private void as(User actor) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities())); }
    private void unchanged(Event original) {
        Event current = reload(original);
        assertEquals(original.getLifecycleVersion(), current.getLifecycleVersion()); assertEquals(original.getStatus(), current.getStatus());
        assertEquals(original.getAssignedReviewerId(), current.getAssignedReviewerId()); assertEquals(original.getReviewerAssignedByUserId(), current.getReviewerAssignedByUserId());
        assertEquals(original.getReviewerAssignedAt(), current.getReviewerAssignedAt()); assertEquals(original.getLastReviewerAssignmentRevision(), current.getLastReviewerAssignmentRevision());
        assertEquals(original.getApprovedAt(), current.getApprovedAt()); assertEquals(original.getRejectionReason(), current.getRejectionReason());
    }
}
