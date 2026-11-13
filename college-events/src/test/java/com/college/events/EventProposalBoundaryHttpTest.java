package com.college.events;

import com.college.events.dto.EventDto;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.service.AuditService;
import com.college.events.service.EventService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Independent boundary tests: committed H2 state, MVC ownership and actual managed media. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:event-proposal-boundary-http;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "app.default-admin-password=", "app.upload.dir=target/test-uploads/event-proposal-boundary-http",
    "logging.file.name=target/test-logs/event-proposal-boundary-http.log", "app.media.cleanup-cron=-",
    "app.identity.enabled=false", "app.email.backend=console", "spring.cache.type=none"
})
@AutoConfigureMockMvc
@DirtiesContext
class EventProposalBoundaryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EventService service;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired AuditLogRepository audits;
    @Autowired MailDeliveryRepository mail;
    @Autowired MediaAssetRepository media;
    @MockitoSpyBean AuditService audit;

    User owner, other, admin, student;
    Category category;

    @BeforeEach void setup() {
        owner = account(Role.ORGANIZER); other = account(Role.FACULTY);
        admin = account(Role.ADMIN); student = account(Role.STUDENT);
        category = categories.saveAndFlush(Category.builder().name("Proposal " + UUID.randomUUID()).build());
    }

    @AfterEach void clearAuthentication() {
        SecurityContextHolder.clearContext();
        reset(audit);
    }

    @Test void createRequiresFreshAuthorizedActorAndCannotImpersonateAnotherOrganizer() {
        long before = events.count(), beforeAudit = audits.count(), beforeMail = mail.count();
        assertThrows(AccessDeniedException.class, () -> service.createEvent(command(null), owner));
        as(student);
        assertThrows(AccessDeniedException.class, () -> service.createEvent(command(null), owner));
        as(owner);
        assertThrows(AccessDeniedException.class, () -> service.createEvent(command(null), other));
        User persisted = users.findById(owner.getId()).orElseThrow();
        persisted.setRole(Role.STUDENT); users.saveAndFlush(persisted);
        assertThrows(AccessDeniedException.class, () -> service.createEvent(command(null), owner));
        assertEquals(before, events.count()); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ORGANIZER", "FACULTY"})
    void authorizedProposalPersistsItsActualOwnerAndAtomicAudit(Role role) {
        User actor = role == Role.ORGANIZER ? owner : other;
        as(actor); long beforeAudit = audits.count();
        Event created = service.createEvent(command(null), actor);
        Event saved = reload(created);
        assertEquals(actor.getId(), saved.getOrganizer().getId()); assertEquals(EventStatus.PENDING, saved.getStatus());
        assertNull(saved.getApprovedAt()); assertFalse(operations.findByEventId(saved.getId()).isPresent());
        assertEquals(beforeAudit + 1, audits.count());
    }

    @Test void disabledOrganizerCannotCreateOrEditWithItsExistingPrincipal() {
        Event proposal = proposal(owner); as(owner);
        User disabled = users.findById(owner.getId()).orElseThrow(); disabled.setEnabled(false); users.saveAndFlush(disabled);
        assertThrows(AccessDeniedException.class, () -> service.createEvent(command(null), owner));
        assertThrows(AccessDeniedException.class, () -> service.updateEvent(proposal.getId(), command(proposal), owner));
        unchanged(proposal);
    }

    @ParameterizedTest @ValueSource(strings = {"title", "description", "venue"})
    void markupOnlyTextCannotBecomeAnEmptyStoredProposal(String field) {
        Event proposal = proposal(owner); as(owner); EventDto invalid = command(proposal);
        switch (field) {
            case "title" -> invalid.setTitle("<strong></strong>");
            case "description" -> invalid.setDescription("<strong></strong><em></em>");
            case "venue" -> invalid.setVenue("<b></b>");
            default -> throw new AssertionError("Unexpected fixture");
        }
        long beforeEvents = events.count(), beforeAudit = audits.count();
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(invalid, owner));
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(proposal.getId(), invalid, owner));
        unchanged(proposal); assertEquals(beforeEvents, events.count()); assertEquals(beforeAudit, audits.count());
    }

    @ParameterizedTest @ValueSource(strings = {"malformed", "display-name", "control-character", "oversized"})
    void contactEmailMustBeOneBoundedPlainAddress(String invalidCase) {
        Event proposal = proposal(owner); as(owner); EventDto invalid = command(proposal);
        invalid.setContactEmail(switch (invalidCase) {
            case "malformed" -> "not-an-email";
            case "display-name" -> "Synthetic Reviewer <review@example.invalid>";
            case "control-character" -> "review\r\n@example.invalid";
            case "oversized" -> "x".repeat(250) + "@example.invalid";
            default -> throw new AssertionError("Unexpected fixture");
        });
        long beforeEvents = events.count(), beforeAudit = audits.count();
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(invalid, owner));
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(proposal.getId(), invalid, owner));
        unchanged(proposal); assertEquals(beforeEvents, events.count()); assertEquals(beforeAudit, audits.count());
    }

    @Test void editRejectsWrongOwnerAndForgedActor() {
        Event proposal = proposal(owner); as(other);
        assertThrows(AccessDeniedException.class, () -> service.updateEvent(proposal.getId(), command(proposal), owner));
        assertThrows(AccessDeniedException.class, () -> service.updateEvent(proposal.getId(), command(proposal), other));
        unchanged(proposal);
    }

    @Test void editRequiresCurrentRevisionAndPersistsOneAuditedProposalRevision() {
        Event proposal = proposal(owner); as(owner); long beforeAudit = audits.count();
        EventDto missing = command(proposal); missing.setExpectedLifecycleVersion(null);
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(proposal.getId(), missing, owner));
        EventDto edit = command(proposal); edit.setTitle("Updated synthetic proposal");
        service.updateEvent(proposal.getId(), edit, owner);
        Event changed = reload(proposal);
        assertEquals(edit.getTitle(), changed.getTitle()); assertTrue(changed.getLifecycleVersion() > proposal.getLifecycleVersion());
        assertEquals(EventStatus.PENDING, changed.getStatus()); assertEquals(beforeAudit + 1, audits.count());
        assertThrows(IllegalStateException.class, () -> service.updateEvent(proposal.getId(), edit, owner));
        unchanged(changed); assertEquals(beforeAudit + 1, audits.count());
    }

    @ParameterizedTest @EnumSource(value = EventStatus.class, names = {"APPROVED", "CANCELLED", "COMPLETED"})
    void operationalStatesCannotBecomeEditableOrRejectedProposals(EventStatus status) {
        Event proposal = proposal(owner); proposal.setStatus(status);
        proposal = events.saveAndFlush(proposal); final Event original = proposal;
        long beforeAudit = audits.count(), beforeMail = mail.count();
        as(owner);
        assertThrows(IllegalStateException.class, () -> service.updateEvent(original.getId(), command(original), owner));
        as(admin);
        assertThrows(IllegalStateException.class, () -> service.rejectEvent(original.getId(), "Synthetic review", original.getLifecycleVersion()));
        unchanged(original); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @ParameterizedTest @ValueSource(strings = {"approval", "cancellation", "completion", "operations", "registration"})
    void pendingStatusCannotEraseOperationalHistory(String history) {
        Event candidate = proposal(owner);
        switch (history) {
            case "approval" -> candidate.setApprovedAt(LocalDateTime.now().withNano(0).minusDays(1));
            case "cancellation" -> candidate.setCancelledAt(LocalDateTime.now().withNano(0).minusDays(1));
            case "completion" -> candidate.setCompletedAt(LocalDateTime.now().withNano(0).minusDays(1));
            case "operations" -> operations.saveAndFlush(EventOperations.builder().event(candidate).build());
            case "registration" -> registrations.saveAndFlush(Registration.builder().event(candidate).user(student).build());
            default -> throw new AssertionError("Unexpected fixture");
        }
        final Event original = events.saveAndFlush(candidate);
        long beforeAudit = audits.count(), beforeMail = mail.count();
        as(owner);
        assertThrows(IllegalStateException.class, () -> service.updateEvent(original.getId(), command(original), owner));
        as(admin);
        assertThrows(IllegalStateException.class, () -> service.rejectEvent(original.getId(), "Synthetic review", original.getLifecycleVersion()));
        assertThrows(IllegalStateException.class, () -> service.approveEvent(original.getId(), original.getLifecycleVersion()));
        unchanged(original); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void administratorCannotReviewOwnProposal() {
        Event proposal = proposal(admin); as(admin);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Synthetic review", proposal.getLifecycleVersion()));
        unchanged(proposal); assertTrue(operations.findByEventId(proposal.getId()).isEmpty());
    }

    @Test void reviewRejectsAnonymousNonAdministratorAndRevokedAdministrator() {
        Event proposal = proposal(owner);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        as(other);
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Synthetic review", proposal.getLifecycleVersion()));
        as(admin); User revoked = users.findById(admin.getId()).orElseThrow(); revoked.setRole(Role.STUDENT); users.saveAndFlush(revoked);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Synthetic review", proposal.getLifecycleVersion()));
        unchanged(proposal);
    }

    @Test void staleAdministratorReviewCannotApproveOrRejectAnEditedRevision() {
        Event proposal = proposal(owner); as(owner);
        EventDto edit = command(proposal); edit.setTitle("A materially revised proposal"); service.updateEvent(proposal.getId(), edit, owner);
        Event current = reload(proposal); long beforeAudit = audits.count(), beforeMail = mail.count(); as(admin);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Review of old version", proposal.getLifecycleVersion()));
        unchanged(current); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void disabledAdministratorAndBlankReasonCannotRecordDecisions() {
        Event proposal = proposal(owner); as(admin); long beforeAudit = audits.count(), beforeMail = mail.count();
        assertThrows(IllegalArgumentException.class, () -> service.rejectEvent(proposal.getId(), "  ", proposal.getLifecycleVersion()));
        User disabled = users.findById(admin.getId()).orElseThrow(); disabled.setEnabled(false); users.saveAndFlush(disabled);
        assertThrows(AccessDeniedException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Synthetic review", proposal.getLifecycleVersion()));
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void concurrentApprovalAndEditHaveOneWinnerForTheReviewedRevision() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count();
        EventDto edit = command(proposal); edit.setTitle("Concurrent revised proposal");
        CyclicBarrier ready = new CyclicBarrier(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> approve = () -> {
                try {
                    as(admin); ready.await(5, TimeUnit.SECONDS);
                    service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()); return true;
                } catch (IllegalStateException | AccessDeniedException | org.springframework.orm.ObjectOptimisticLockingFailureException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            Callable<Boolean> revise = () -> {
                try {
                    as(owner); ready.await(5, TimeUnit.SECONDS);
                    service.updateEvent(proposal.getId(), edit, owner); return true;
                } catch (IllegalStateException | AccessDeniedException | org.springframework.orm.ObjectOptimisticLockingFailureException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var approval = workers.submit(approve); var revision = workers.submit(revise);
            boolean approved = approval.get(20, TimeUnit.SECONDS), revised = revision.get(20, TimeUnit.SECONDS);
            assertNotEquals(approved, revised, "Exactly one command may accept the originally reviewed revision");
            Event winner = reload(proposal); assertEquals(beforeAudit + 1, audits.count());
            if (approved) {
                assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, winner.getStatus()); assertEquals(proposal.getTitle(), winner.getTitle());
                assertTrue(operations.findByEventId(proposal.getId()).isEmpty());
            } else {
                assertEquals(EventStatus.PENDING, winner.getStatus()); assertEquals(edit.getTitle(), winner.getTitle());
                assertTrue(operations.findByEventId(proposal.getId()).isEmpty());
            }
        } finally {
            workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void approvalReplayStaysPrivateAndPublicationReplayCreatesOneWorkspaceAndNotificationSet() {
        Event proposal = proposal(owner); as(admin); long beforeAudit = audits.count(), beforeMail = mail.count(), beforeOperations = operations.count();
        service.approveEvent(proposal.getId(), proposal.getLifecycleVersion());
        Event approved = reload(proposal); long approvedMail = mail.count();
        assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, approved.getStatus()); assertNotNull(approved.getApprovedAt());
        assertEquals(beforeOperations, operations.count()); assertEquals(beforeAudit + 1, audits.count()); assertEquals(beforeMail, approvedMail);
        service.approveEvent(proposal.getId(), proposal.getLifecycleVersion());
        unchanged(approved); assertEquals(beforeOperations, operations.count()); assertEquals(beforeAudit + 1, audits.count()); assertEquals(approvedMail, mail.count());
        long publicationRevision = approved.getLifecycleVersion();
        service.publishApprovedEvent(proposal.getId(), publicationRevision);
        Event published = reload(proposal);
        assertEquals(EventStatus.APPROVED, published.getStatus()); assertNotNull(published.getPublishedAt());
        assertEquals(beforeOperations + 1, operations.count()); assertTrue(mail.count() > approvedMail);
        long publishedMail = mail.count(), publishedAudit = audits.count();
        service.publishApprovedEvent(proposal.getId(), publicationRevision);
        unchanged(published); assertEquals(beforeOperations + 1, operations.count()); assertEquals(publishedAudit, audits.count()); assertEquals(publishedMail, mail.count());
        as(student);
        assertThrows(AccessDeniedException.class, () -> service.publishApprovedEvent(proposal.getId(), publicationRevision));
        unchanged(published);
    }

    @Test void rejectionReplayIsNoWriteAndResubmissionInvalidatesOldDecision() {
        Event proposal = proposal(owner); as(admin); long beforeAudit = audits.count(), beforeMail = mail.count();
        service.rejectEvent(proposal.getId(), "Revise the synthetic programme", proposal.getLifecycleVersion());
        Event rejected = reload(proposal);
        assertEquals(EventStatus.REJECTED, rejected.getStatus()); assertEquals(beforeAudit + 1, audits.count()); assertEquals(beforeMail + 1, mail.count());
        service.rejectEvent(proposal.getId(), "Revise the synthetic programme", proposal.getLifecycleVersion());
        unchanged(rejected); assertEquals(beforeAudit + 1, audits.count()); assertEquals(beforeMail + 1, mail.count());
        assertThrows(IllegalStateException.class, () -> service.rejectEvent(proposal.getId(), "A different decision", proposal.getLifecycleVersion()));
        unchanged(rejected);
        as(owner); EventDto edit = command(rejected); edit.setTitle("Revised after review"); service.updateEvent(proposal.getId(), edit, owner);
        Event resubmitted = reload(proposal); assertEquals(EventStatus.PENDING, resubmitted.getStatus()); assertNull(resubmitted.getRejectionReason());
        as(admin);
        assertThrows(AccessDeniedException.class, () -> service.rejectEvent(proposal.getId(), "Revise the synthetic programme", proposal.getLifecycleVersion()));
        unchanged(resubmitted);
    }

    @Test void crossOwnerEditGetAndInvalidPostCannotRenderPrivateProposal() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count();
        var getResult = mvc.perform(get("/organizer/events/{id}/edit", proposal.getId()).with(user(other))).andExpect(status().isForbidden()).andReturn();
        var postResult = mvc.perform(post("/organizer/events/{id}/edit", proposal.getId()).with(user(other)).with(csrf()).param("title", "x"))
            .andExpect(status().isForbidden()).andReturn();
        assertFalse(getResult.getResponse().getContentAsString().contains(proposal.getTitle()));
        assertFalse(postResult.getResponse().getContentAsString().contains(proposal.getTitle()));
        unchanged(proposal); assertEquals(beforeAudit, audits.count());
    }

    @Test void ownInvalidFormCanBeCorrectedWithoutChangingProposal() throws Exception {
        Event proposal = proposal(owner);
        mvc.perform(post("/organizer/events/{id}/edit", proposal.getId()).with(user(owner)).with(csrf()).param("title", "x"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("dto", "title"));
        unchanged(proposal);
    }

    @Test void organizerFormIncludesRevisionAndStaleSubmissionRendersRecoverableError() throws Exception {
        Event proposal = proposal(owner);
        var form = mvc.perform(get("/organizer/events/{id}/edit", proposal.getId()).with(user(owner)))
            .andExpect(status().isOk()).andReturn();
        EventDto presented = (EventDto) form.getModelAndView().getModel().get("dto");
        assertEquals(proposal.getLifecycleVersion(), presented.getExpectedLifecycleVersion());
        assertTrue(form.getResponse().getContentAsString().contains("name=\"expectedLifecycleVersion\""));
        var request = post("/organizer/events/{id}/edit", proposal.getId()).with(user(owner)).with(csrf())
            .param("title", "Updated through the real form").param("description", "Synthetic proposal description supplied through the real form.")
            .param("categoryId", category.getId().toString()).param("venue", "Synthetic room").param("maxSeats", "30")
            .param("eventDate", proposal.getEventDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")))
            .param("expectedLifecycleVersion", Long.toString(proposal.getLifecycleVersion()));
        long beforeAudit = audits.count();
        mvc.perform(request).andExpect(status().is3xxRedirection());
        Event saved = reload(proposal); assertEquals("Updated through the real form", saved.getTitle());
        assertEquals(beforeAudit + 1, audits.count());
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeExists("errorMsg"));
        unchanged(saved); assertEquals(beforeAudit + 1, audits.count());
    }

    @Test void administratorFormsWireRevisionToPersistedApprovalAndRejection() throws Exception {
        Event approved = proposal(owner), rejected = proposal(owner); long beforeAudit = audits.count();
        mvc.perform(post("/admin/events/{id}/approve", approved.getId()).with(user(admin)).with(csrf())
            .param("expectedRevision", Long.toString(approved.getLifecycleVersion())))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMsg"));
        assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION, reload(approved).getStatus()); assertTrue(operations.findByEventId(approved.getId()).isEmpty());
        mvc.perform(post("/admin/events/{id}/publish", approved.getId()).with(user(admin)).with(csrf())
            .param("expectedRevision", Long.toString(reload(approved).getLifecycleVersion())))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMsg"));
        assertEquals(EventStatus.APPROVED, reload(approved).getStatus()); assertTrue(operations.findByEventId(approved.getId()).isPresent());
        mvc.perform(post("/admin/events/{id}/reject", rejected.getId()).with(user(admin)).with(csrf())
            .param("expectedRevision", Long.toString(rejected.getLifecycleVersion())).param("reason", "Synthetic reviewed reason"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMsg"));
        assertEquals(EventStatus.REJECTED, reload(rejected).getStatus()); assertEquals("Synthetic reviewed reason", reload(rejected).getRejectionReason());
        assertEquals(beforeAudit + 3, audits.count());
    }

    @Test void administratorDetailRendersRevisionBoundApprovalAndVisibleRejectionForm() throws Exception {
        Event proposal = proposal(owner);
        String html = mvc.perform(get("/admin/events/{id}", proposal.getId()).with(user(admin)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String action : new String[]{"approve", "reject"}) {
            String actionPath = "/admin/events/" + proposal.getId() + "/" + action;
            var form = java.util.regex.Pattern.compile("<form[^>]*action=\"" + java.util.regex.Pattern.quote(actionPath)
                + "\"[^>]*>([\\s\\S]*?)</form>").matcher(html);
            assertTrue(form.find(), "Missing " + action + " form");
            assertTrue(form.group(1).contains("name=\"expectedRevision\""));
            assertTrue(form.group(1).contains("value=\"" + proposal.getLifecycleVersion() + "\""));
            if (action.equals("reject")) {
                assertTrue(form.group(1).contains("name=\"reason\""));
                assertTrue(form.group(1).contains("<textarea"));
            }
        }
        var section = java.util.regex.Pattern.compile("<section[^>]*id=\"proposal-rejection\"[^>]*>").matcher(html);
        assertTrue(section.find()); assertFalse(section.group().contains("hidden"));
        assertFalse(section.group().contains("d-none")); assertFalse(section.group().contains("display:none"));
    }

    @Test void legacyDecisionFormsRequireCsrfAndCannotWriteWithoutIt() throws Exception {
        Event proposal = proposal(owner); long beforeAudit = audits.count(), beforeMail = mail.count();
        mvc.perform(post("/admin/events/{id}/approve", proposal.getId()).with(user(admin)).param("expectedRevision", "0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/events/{id}/reject", proposal.getId()).with(user(admin)).param("expectedRevision", "0").param("reason", "Synthetic review")).andExpect(status().isForbidden());
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count());
    }

    @Test void failedReplacementAndSuccessfulReplacementPreserveOriginalMediaDeadlineAndBytes() throws Exception {
        as(owner); EventDto create = command(null); create.setBannerImage(png()); Event proposal = service.createEvent(create, owner);
        String originalKey = proposal.getBannerImage(); var originalMedia = media.findByObjectKey(originalKey).orElseThrow();
        Path originalPath = Path.of("target/test-uploads/event-proposal-boundary-http").resolve(originalKey);
        byte[] originalBytes = Files.readAllBytes(originalPath);
        EventDto invalid = command(proposal); invalid.setBannerImage(new MockMultipartFile("bannerImage", "invalid.png", "image/png", new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(proposal.getId(), invalid, owner));
        unchanged(proposal); assertArrayEquals(originalBytes, Files.readAllBytes(originalPath));
        EventDto valid = command(proposal); valid.setBannerImage(png()); service.updateEvent(proposal.getId(), valid, owner);
        assertNotEquals(originalKey, reload(proposal).getBannerImage()); assertArrayEquals(originalBytes, Files.readAllBytes(originalPath));
        var retained = media.findByObjectKey(originalKey).orElseThrow();
        assertEquals(originalMedia.getExpiresAt(), retained.getExpiresAt()); assertEquals("ACTIVE", retained.getStatus());
    }

    @Test void proposalBannerAndCertificateTemplateReceiveDistinctPrivateEventBindings() throws Exception {
        as(owner); EventDto create = command(null); create.setBannerImage(png()); create.setCertificateTemplate(png());
        Event proposal = service.createEvent(create, owner);
        var banner = media.findByObjectKey(proposal.getBannerImage()).orElseThrow();
        var template = media.findByObjectKey(proposal.getCertificateTemplatePath()).orElseThrow();
        assertEquals("EVENT_BANNER", banner.getResourceType()); assertEquals(proposal.getId(), banner.getResourceId());
        assertEquals("EVENT_CERT_TEMPLATE", template.getResourceType()); assertEquals(proposal.getId(), template.getResourceId());
        assertEquals(owner.getId(), template.getOwnerUserId());
        assertTrue(Math.abs(java.time.Duration.between(banner.getExpiresAt(), template.getExpiresAt()).toMillis()) < 5_000);
    }

    @Test void auditFailureRollsBackProposalAndNewMediaWhilePreservingOriginal() throws Exception {
        as(owner); EventDto create = command(null); create.setBannerImage(png()); Event proposal = service.createEvent(create, owner);
        long beforeMedia = media.count(), beforeAudit = audits.count();
        Path originalPath = Path.of("target/test-uploads/event-proposal-boundary-http").resolve(proposal.getBannerImage());
        byte[] originalBytes = Files.readAllBytes(originalPath); long beforeObjects;
        try (var paths = Files.list(originalPath.getParent())) { beforeObjects = paths.filter(Files::isRegularFile).count(); }
        doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(), anyString(), anyString(), anyString());
        EventDto edit = command(proposal); edit.setBannerImage(png()); edit.setTitle("Must roll back");
        assertThrows(IllegalStateException.class, () -> service.updateEvent(proposal.getId(), edit, owner));
        unchanged(proposal); assertEquals(beforeMedia, media.count()); assertEquals(beforeAudit, audits.count());
        assertArrayEquals(originalBytes, Files.readAllBytes(originalPath));
        try (var paths = Files.list(originalPath.getParent())) { assertEquals(beforeObjects, paths.filter(Files::isRegularFile).count()); }
    }

    @Test void auditFailureCannotCommitCreationApprovalOrRejection() {
        Event proposal = proposal(owner); long beforeEvents = events.count(), beforeAudit = audits.count();
        long beforeMail = mail.count(), beforeOperations = operations.count();
        doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(), anyString(), anyString(), anyString());
        as(owner); assertThrows(IllegalStateException.class, () -> service.createEvent(command(null), owner));
        assertEquals(beforeEvents, events.count());
        as(admin);
        assertThrows(IllegalStateException.class, () -> service.approveEvent(proposal.getId(), proposal.getLifecycleVersion()));
        unchanged(proposal);
        assertThrows(IllegalStateException.class, () -> service.rejectEvent(proposal.getId(), "Synthetic review", proposal.getLifecycleVersion()));
        unchanged(proposal); assertEquals(beforeAudit, audits.count()); assertEquals(beforeMail, mail.count()); assertEquals(beforeOperations, operations.count());
    }

    @Test void legacyHardDeleteCannotRemoveEventOrEvidence() {
        Event proposal = proposal(owner); as(owner);
        assertThrows(RuntimeException.class, () -> service.deleteEvent(proposal.getId(), owner));
        unchanged(proposal); as(admin);
        assertThrows(RuntimeException.class, () -> service.deleteEvent(proposal.getId(), admin)); unchanged(proposal);
    }

    private User account(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic proposal actor").email(UUID.randomUUID() + "@example.invalid")
            .password("synthetic-only").role(role).enabled(true).build());
    }

    private Event proposal(User organizer) {
        return events.saveAndFlush(Event.builder().title("Private synthetic proposal " + UUID.randomUUID())
            .description("Synthetic programme details for independent boundary verification.")
            .organizer(organizer).category(category).venue("Synthetic room").eventDate(LocalDateTime.now().withNano(0).plusDays(3))
            .eventEndsAt(LocalDateTime.now().withNano(0).plusDays(3).plusHours(2)).maxSeats(30).targetDepartment("ALL")
            .assignedReviewerId(admin.getId()).reviewerAssignedByUserId(admin.getId())
            .reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build());
    }

    private EventDto command(Event proposal) {
        EventDto dto = new EventDto(); dto.setTitle("Synthetic valid proposal");
        dto.setDescription("Synthetic programme details for independent boundary verification.");
        dto.setCategoryId(category.getId()); dto.setVenue("Synthetic room"); dto.setEventDate(LocalDateTime.now().withNano(0).plusDays(3));
        dto.setEventEndsAt(dto.getEventDate().plusHours(2)); dto.setMaxSeats(30); dto.setFees(0.0); dto.setTargetDepartment("ALL");
        if (proposal != null) dto.setExpectedLifecycleVersion(proposal.getLifecycleVersion());
        return dto;
    }

    private Event reload(Event event) { return events.findById(event.getId()).orElseThrow(); }

    private void unchanged(Event expected) {
        Event actual = reload(expected);
        assertEquals(expected.getLifecycleVersion(), actual.getLifecycleVersion()); assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getStatus(), actual.getStatus()); assertEquals(expected.getEventDate(), actual.getEventDate());
        assertEquals(expected.getEventEndsAt(), actual.getEventEndsAt()); assertEquals(expected.getMaxSeats(), actual.getMaxSeats());
        assertEquals(expected.getBannerImage(), actual.getBannerImage()); assertEquals(expected.getRejectionReason(), actual.getRejectionReason());
        assertEquals(expected.getApprovedAt(), actual.getApprovedAt()); assertEquals(expected.getCancelledAt(), actual.getCancelledAt());
        assertEquals(expected.getCompletedAt(), actual.getCompletedAt());
    }

    private void as(User actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
    }

    private MockMultipartFile png() throws Exception {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("bannerImage", "synthetic.png", "image/png", output.toByteArray());
    }
}
