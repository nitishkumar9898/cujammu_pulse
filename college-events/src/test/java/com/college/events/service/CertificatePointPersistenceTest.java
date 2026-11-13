package com.college.events.service;

import com.college.events.dto.CertificateDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/certificate-point.log"}, showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CertificatePointPersistenceTest {
    @Autowired CertificateRepository certificates;
    @Autowired CertificateFinalizationRepository finalizations;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired EventQrAttendanceEvidenceRepository evidence;
    @Autowired ManualAttendanceEvidenceRepository manual;
    @Autowired EventQrCredentialRepository credentials;
    @Autowired EventScanIntentRepository intents;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    PointCertificateIssuanceService service;
    TransactionTemplate tx;
    User organizer, attendee, stranger;
    AuditService audit;
    FeatureFlagService features;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager); audit = mock(AuditService.class); features = mock(FeatureFlagService.class);
        when(features.isEnabled(anyString())).thenReturn(true);
        service = new PointCertificateIssuanceService(certificates, finalizations, events, operations, registrations, evidence, manual,
                new DomainAccess(users, events), features, audit, mock(NotificationService.class));
        organizer = user(Role.ORGANIZER); attendee = user(Role.STUDENT); stranger = user(Role.STUDENT);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    User user(Role role) { return users.saveAndFlush(User.builder().name("Synthetic " + role).email(UUID.randomUUID() + "@example.invalid")
            .verifiedEnrollmentNo("CS-" + UUID.randomUUID()).password("synthetic-hash").role(role).enabled(true).build()); }
    void as(User u) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities())); }
    Event event(String mode, String policy) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        Event event = events.saveAndFlush(Event.builder().title("Synthetic point evidence event").description("Synthetic only")
                .organizer(organizer).venue("Synthetic hall").maxSeats(100).status(EventStatus.COMPLETED).eventDate(now.minusHours(2))
                .approvedAt(now.minusDays(1)).completedAt(now.minusMinutes(5)).completedByUserId(organizer.getId()).generateCertificate(true).build());
        operations.saveAndFlush(EventOperations.builder().event(event).attendanceMode(mode).singleQrPolicy("ENTRY_AND_ATTENDANCE")
                .certificateEvidencePolicy(policy).certificatePolicyVersion("POINT_ATTENDANCE".equals(policy) ? 1 : 0).build());
        return event;
    }
    Registration registration(Event e, User u) { return registrations.saveAndFlush(Registration.builder().event(e).user(u).status(RegistrationStatus.ATTENDED).build()); }
    EventQrAttendanceEvidence proof(Registration r, String action, Instant time) {
        var credential = credentials.saveAndFlush(EventQrCredential.builder().event(r.getEvent()).registration(r).action(action)
                .tokenDigest(UUID.randomUUID().toString()).status("CONSUMED").expiresAt(time.plusSeconds(120)).issuedAt(time.minusSeconds(60)).consumedAt(time).build());
        var intent = intents.saveAndFlush(EventScanIntent.builder().event(r.getEvent()).registration(r).operator(organizer).credential(credential)
                .intentKey(UUID.randomUUID().toString()).requestKey(UUID.randomUUID().toString()).action(action).challengeDigest("synthetic")
                .status("CONFIRMED").createdAt(time.minusSeconds(30)).confirmedAt(time).expiresAt(time.plusSeconds(90)).build());
        return evidence.saveAndFlush(EventQrAttendanceEvidence.builder().event(r.getEvent()).registration(r).operator(organizer).intent(intent)
                .action(action).source("QR_OWNER_CONFIRMED").occurredAt(time).build());
    }
    Instant during(Event e) { return e.getEventDate().plusMinutes(30).atZone(ZoneId.of("Asia/Kolkata")).toInstant(); }
    CertificateDtos.Finalize command(Event e) { return new CertificateDtos.Finalize(operations.findByEventId(e.getId()).orElseThrow().getRevision(), UUID.randomUUID().toString(), "Synthetic office-reviewed finalization"); }
    CertificateDtos.Finalization finalize(Event e, CertificateDtos.Finalize c) { as(organizer); return tx.execute(s -> service.finalizeEvent(e.getId(), c)); }

    @Test void singlePointIssuesOnceAndAttendedLegacyStatusNeverQualifiesWithoutProof() throws Exception {
        Event e = event("SINGLE_QR", "POINT_ATTENDANCE"); Registration valid = registration(e, attendee), legacy = registration(e, stranger);
        proof(valid, "ENTRY", during(e)); var command = command(e);
        var receipt = finalize(e, command);
        assertEquals(1, receipt.issuedCount()); assertEquals(1, receipt.ineligibleCount());
        assertEquals(receipt, finalize(e, command)); assertEquals(1, certificates.countByEvent(e));
        as(attendee); var own = tx.execute(s -> service.own(0)); assertEquals(1, own.items().size()); assertFalse(own.hasNext());
        String opaque = own.items().get(0).certificateNumber();
        assertTrue(tx.execute(s -> service.verify(opaque)).valid());
        String publicResult = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tx.execute(s -> service.verify(opaque)));
        assertFalse(publicResult.contains(attendee.getName())); assertFalse(publicResult.contains(attendee.getVerifiedEnrollmentNo()));
        assertFalse(publicResult.contains(e.getTitle()));
        as(stranger); assertTrue(tx.execute(s -> service.own(0)).items().isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> tx.execute(s -> service.owned(valid.getId())));
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> service.owned(legacy.getId())));
        assertFalse(tx.execute(s -> service.verify("CUJ-1-1-123")).valid());
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var renderer = new CertificateService(engine, service);
        org.springframework.test.util.ReflectionTestUtils.setField(renderer, "baseUrl", "https://example.invalid");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> tx.execute(s -> renderer.generateCertificatePdf(valid)));
        as(attendee);
        byte[] pdf = tx.execute(s -> renderer.generateCertificatePdf(valid));
        assertNotNull(pdf); assertTrue(pdf.length > 1000);
        try (var document = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Verified Point Attendance"));
            assertTrue(text.contains("does not certify attendance duration"));
            assertTrue(text.contains("/certificates/verify/"));
            assertFalse(text.contains("OFFICIAL SEAL"));
        }
    }
    @Test void dualRequiresBothChronologicalProofsAndPersistsUnresolvedQuality() {
        Event e = event("DUAL_QR", "POINT_ATTENDANCE"); Registration valid = registration(e, attendee), incomplete = registration(e, stranger);
        proof(valid, "ENTRY", during(e)); proof(valid, "ATTENDANCE_CHECKPOINT", during(e).plusSeconds(60));
        proof(incomplete, "ENTRY", during(e));
        var receipt = finalize(e, command(e));
        assertEquals(1, receipt.issuedCount()); assertEquals(1, receipt.unresolvedCount());
        assertEquals("FINALIZED_WITH_UNRESOLVED", finalizations.findByEventId(e.getId()).orElseThrow().getStatus());
    }
    @Test void oldOrEntryOnlyPoliciesAndPrematureCompletionAreBlocked() {
        Event old = event("SINGLE_QR", "UNREVIEWED"); proof(registration(old, attendee), "ENTRY", during(old));
        assertThrows(IllegalStateException.class, () -> finalize(old, command(old)));
        Event entryOnly = event("SINGLE_QR", "POINT_ATTENDANCE");
        tx.executeWithoutResult(s -> { var p = operations.findForUpdate(entryOnly.getId()).orElseThrow(); p.setSingleQrPolicy("ENTRY_ONLY"); operations.saveAndFlush(p); });
        assertThrows(IllegalStateException.class, () -> finalize(entryOnly, command(entryOnly)));
        Event premature = event("SINGLE_QR", "POINT_ATTENDANCE"); premature.setStatus(EventStatus.APPROVED); events.saveAndFlush(premature);
        assertThrows(IllegalStateException.class, () -> finalize(premature, command(premature)));
        assertEquals(0, certificates.countByEvent(old));
    }
    @Test void outsideEventWindowAndManualEvidenceDoNotBecomePointCertificates() {
        Event e = event("SINGLE_QR", "POINT_ATTENDANCE"); Registration early = registration(e, attendee), mixed = registration(e, stranger);
        proof(early, "ENTRY", e.getEventDate().minusSeconds(1).atZone(ZoneId.of("Asia/Kolkata")).toInstant());
        proof(mixed, "ENTRY", during(e));
        manual.saveAndFlush(ManualAttendanceEvidence.builder().event(e).registration(mixed).operator(organizer).action("ENTRY")
                .idempotencyKey(UUID.randomUUID().toString()).verifiedIdentifierSnapshot(stranger.getVerifiedEnrollmentNo()).reasonCode("OTHER").occurredAt(during(e)).build());
        var receipt = finalize(e, command(e)); assertEquals(0, receipt.issuedCount()); assertEquals(2, receipt.unresolvedCount());
    }
    @Test void auditFailureRollsBackTheWholeIssuanceJob() {
        Event e = event("SINGLE_QR", "POINT_ATTENDANCE"); proof(registration(e, attendee), "ENTRY", during(e));
        doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(), anyString(), eq("POINT_CERTIFICATE_ISSUED"), anyString());
        assertThrows(IllegalStateException.class, () -> finalize(e, command(e)));
        assertEquals(0, certificates.countByEvent(e)); assertTrue(finalizations.findByEventId(e.getId()).isEmpty());
    }
    @Test void twoSimultaneousIdenticalFinalizationsCommitOneCertificate() throws Exception {
        Event e = event("SINGLE_QR", "POINT_ATTENDANCE"); proof(registration(e, attendee), "ENTRY", during(e)); var c = command(e);
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<CertificateDtos.Finalization>>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> { as(organizer); ready.countDown(); go.await(10, TimeUnit.SECONDS);
                try { return tx.execute(s -> service.finalizeEvent(e.getId(), c)); } finally { SecurityContextHolder.clearContext(); } }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            assertEquals(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, certificates.countByEvent(e));
        } finally { go.countDown(); pool.shutdownNow(); }
    }
}
