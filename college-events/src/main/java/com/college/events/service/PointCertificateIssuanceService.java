package com.college.events.service;

import com.college.events.dto.CertificateDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Evidence finalization, never an ATTENDED-status certificate shortcut. */
@Service @RequiredArgsConstructor
public class PointCertificateIssuanceService {
    private static final String TYPE = "POINT_PARTICIPATION";
    private final CertificateRepository certificates;
    private final CertificateFinalizationRepository finalizations;
    private final EventRepository events;
    private final EventOperationsRepository operations;
    private final RegistrationRepository registrations;
    private final EventQrAttendanceEvidenceRepository qrEvidence;
    private final ManualAttendanceEvidenceRepository manualEvidence;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional
    public CertificateDtos.Finalization finalizeEvent(long eventId, CertificateDtos.Finalize command) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        features.requireEnabledForUpdate("MODULE_CERTIFICATES");
        User actor = access.currentUser();
        access.requireEventManager(eventId);
        EventOperations policy = operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Approved operations policy is unavailable"));
        Event event = events.findForApproval(eventId).orElseThrow(DomainAccess::denied);
        String key;
        try { key = UUID.fromString(command.requestKey()).toString(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("A valid finalization request key is required"); }
        if (command.reason() == null || command.reason().isBlank() || command.reason().length() > 240)
            throw new IllegalArgumentException("A bounded finalization reason is required");
        String digest = digest(eventId + "|" + command.expectedOperationsRevision() + "|" + command.reason().trim());
        requireCompleted(event);
        CertificateFinalization previous = finalizations.findByEventId(eventId).orElse(null);
        if (previous != null) {
            if (!key.equals(previous.getRequestKey()) || !digest.equals(previous.getCommandDigest()) || actor.getId() != previous.getFinalizedByUserId())
                throw new IllegalStateException("Attendance was already finalized; corrections require a reviewed revision");
            return receipt(previous);
        }
        if (policy.getRevision() != command.expectedOperationsRevision()) throw new IllegalStateException("Operations changed; refresh and retry");
        if (!"POINT_ATTENDANCE".equals(policy.getCertificateEvidencePolicy()) || policy.getCertificatePolicyVersion() < 1)
            throw new IllegalStateException("Certificate policy is unreviewed or incompatible; duration and manual evidence require reviewed implementation");
        boolean single = "SINGLE_QR".equals(policy.getAttendanceMode()) && "ENTRY_AND_ATTENDANCE".equals(policy.getSingleQrPolicy());
        boolean dual = "DUAL_QR".equals(policy.getAttendanceMode());
        if (!single && !dual) throw new IllegalStateException("Entry-only and NO_QR policies cannot issue point certificates");
        if (policy.isScannerMasterEnabled() || policy.isManualWindowOpen()) throw new IllegalStateException("Close attendance operations before finalization");
        if (registrations.countByEvent(event) > 500) throw new IllegalStateException("This event requires a resumable certificate job; this bounded implementation supports at most 500 registrations");
        List<Registration> roster = registrations.findByEventOrderByRegisteredAtAsc(event);
        Map<Long, List<EventQrAttendanceEvidence>> qr = new HashMap<>();
        for (EventQrAttendanceEvidence e : qrEvidence.findByEventIdOrderByOccurredAtAscIdAsc(eventId))
            qr.computeIfAbsent(e.getRegistration().getId(), ignored -> new ArrayList<>()).add(e);
        Set<Long> manual = new HashSet<>();
        manualEvidence.findByEventIdOrderByOccurredAtAscIdAsc(eventId).forEach(e -> manual.add(e.getRegistration().getId()));
        Set<String> identifiers = new HashSet<>();
        for (Registration registration : roster) {
            if (registration.getStatus() == RegistrationStatus.CANCELLED || registration.getStatus() == RegistrationStatus.WAITLISTED) continue;
            String identifier = registration.getUser().getVerifiedEnrollmentNo();
            if (identifier == null || identifier.isBlank() || !identifiers.add(identifier))
                throw new IllegalStateException("Missing or duplicate verified identifiers require office resolution before finalization");
        }
        int issued = 0, ineligible = 0, unresolved = 0;
        CertificateFinalization job = finalizations.saveAndFlush(CertificateFinalization.builder().event(event).requestKey(key).commandDigest(digest)
                .finalizedByUserId(actor.getId()).operationsRevision(policy.getRevision()).policyVersion(policy.getCertificatePolicyVersion())
                .attendanceMode(policy.getAttendanceMode()).singleQrPolicy(policy.getSingleQrPolicy()).evidencePolicy(policy.getCertificateEvidencePolicy())
                .status("FINALIZED").finalizedAt(campusNow()).build());
        for (Registration registration : roster) {
            if (registration.getStatus() != RegistrationStatus.REGISTERED && registration.getStatus() != RegistrationStatus.ATTENDED) { ineligible++; continue; }
            List<EventQrAttendanceEvidence> ledger = qr.getOrDefault(registration.getId(), List.of());
            EventQrAttendanceEvidence entry = ledger.stream().filter(e -> "ENTRY".equals(e.getAction())).findFirst().orElse(null);
            EventQrAttendanceEvidence checkpoint = ledger.stream().filter(e -> "ATTENDANCE_CHECKPOINT".equals(e.getAction())).findFirst().orElse(null);
            if (manual.contains(registration.getId()) || certificates.existsByRegistration(registration)) { unresolved++; continue; }
            if (!proof(entry, registration, event) || dual && (!proof(checkpoint, registration, event) || checkpoint.getOccurredAt().isBefore(entry.getOccurredAt()))) {
                if (ledger.isEmpty()) ineligible++; else unresolved++;
                continue;
            }
            Certificate cert = certificates.save(Certificate.builder().registration(registration).event(event).user(registration.getUser())
                    .templateId(TYPE).certificateNumber("CUJ-" + UUID.randomUUID()).certificateType(TYPE).finalization(job)
                    .entryEvidenceId(entry.getId()).checkpointEvidenceId(dual ? checkpoint.getId() : null)
                    .recipientNameSnapshot(registration.getUser().getName()).verifiedIdentifierSnapshot(registration.getUser().getVerifiedEnrollmentNo())
                    .eventTitleSnapshot(event.getTitle()).eventDateSnapshot(event.getEventDate()).build());
            notifications.createNotification(registration.getUser(), "Your verified point-attendance certificate is available.", "/app/");
            audit.logAction(actor.getUsername(), actor.getRole().name(), "POINT_CERTIFICATE_ISSUED", "eventId=" + eventId + ";certificateId=" + cert.getId() + ";policyVersion=" + job.getPolicyVersion());
            issued++;
        }
        job.setIssuedCount(issued); job.setIneligibleCount(ineligible); job.setUnresolvedCount(unresolved);
        if (unresolved > 0) job.setStatus("FINALIZED_WITH_UNRESOLVED");
        finalizations.saveAndFlush(job);
        audit.logAction(actor.getUsername(), actor.getRole().name(), "CERTIFICATE_ATTENDANCE_FINALIZED", "eventId=" + eventId + ";issued=" + issued + ";unresolved=" + unresolved + ";reason=" + command.reason().trim());
        return receipt(job);
    }

    @Transactional(readOnly = true)
    public CertificateDtos.Own own(int page) {
        readGuard();
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid certificate page");
        var rows = certificates.findProvenOwned(access.currentUser().getId(), PageRequest.of(page, 20));
        return new CertificateDtos.Own(rows.stream().filter(this::proven).map(c -> new CertificateDtos.Item(c.getRegistration().getId(), c.getEvent().getId(),
                c.getEventTitleSnapshot(), c.getCertificateNumber(), c.getCertificateType(), c.getIssuedAt(), "/api/v1/cujammu/certificates/" + c.getRegistration().getId() + "/download")).toList(), page, rows.hasNext());
    }
    @Transactional(readOnly = true)
    public List<Certificate> ownCertificates() {
        return ownPage(0).getContent();
    }
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<Certificate> ownPage(int page) {
        readGuard();
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid certificate page");
        var request = PageRequest.of(page, 20);
        var rows = certificates.findProvenOwned(access.currentUser().getId(), request);
        return new org.springframework.data.domain.SliceImpl<>(rows.stream().filter(this::proven).toList(), request, rows.hasNext());
    }
    @Transactional
    public Certificate owned(long registrationId) {
        readGuard();
        User actor = access.currentUser();
        Registration registration = registrations.findWithEventById(registrationId).orElseThrow(DomainAccess::denied);
        if (!registration.getUser().getId().equals(actor.getId())) throw DomainAccess.denied();
        Certificate cert = certificates.findByRegistration(registration).filter(this::proven)
                .orElseThrow(() -> new IllegalStateException("A finalized evidence-backed certificate is not available"));
        audit.logAction(actor.getUsername(), actor.getRole().name(), "CERTIFICATE_DOWNLOAD_AUTHORIZED", "certificateId=" + cert.getId());
        return cert;
    }
    @Transactional(readOnly = true)
    public CertificateDtos.Verification verify(String opaqueNumber) {
        if (opaqueNumber == null || !opaqueNumber.matches("CUJ-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !features.isEnabled("MODULE_CERTIFICATES") || !features.isEnabled("MODULE_EVENTS")) return new CertificateDtos.Verification(false, null, null);
        return certificates.findByCertificateNumber(opaqueNumber).filter(this::proven)
                .map(c -> new CertificateDtos.Verification(true, c.getCertificateType(), c.getFinalization().getPolicyVersion()))
                .orElse(new CertificateDtos.Verification(false, null, null));
    }
    private boolean proven(Certificate c) {
        CertificateFinalization job = c.getFinalization();
        boolean valid = job != null && TYPE.equals(c.getCertificateType()) && c.getEntryEvidenceId() != null
                && List.of("FINALIZED", "FINALIZED_WITH_UNRESOLVED").contains(job.getStatus())
                && "POINT_ATTENDANCE".equals(job.getEvidencePolicy()) && job.getPolicyVersion() > 0
                && ("DUAL_QR".equals(job.getAttendanceMode()) && c.getCheckpointEvidenceId() != null
                    || "SINGLE_QR".equals(job.getAttendanceMode()) && "ENTRY_AND_ATTENDANCE".equals(job.getSingleQrPolicy()))
                && c.getEvent().getStatus() == EventStatus.COMPLETED && c.getEvent().getCancelledAt() == null
                && c.getEvent().getCompletedAt() != null && c.getEvent().getCompletedByUserId() != null
                && c.getEvent().getApprovedAt() != null && c.getEvent().isGenerateCertificate()
                && c.getRecipientNameSnapshot() != null && c.getEventTitleSnapshot() != null && c.getEventDateSnapshot() != null
                && c.getVerifiedIdentifierSnapshot() != null
                && job.getEvent().getId().equals(c.getEvent().getId()) && c.getUser().getId().equals(c.getRegistration().getUser().getId())
                && c.getRegistration().getEvent().getId().equals(c.getEvent().getId())
                && !c.getEvent().getCompletedAt().isBefore(c.getEvent().getEventDate())
                && (c.getRegistration().getStatus() == RegistrationStatus.REGISTERED || c.getRegistration().getStatus() == RegistrationStatus.ATTENDED);
        if (!valid) return false;
        var entry = qrEvidence.findById(c.getEntryEvidenceId()).orElse(null);
        if (!proof(entry, c.getRegistration(), c.getEvent())) return false;
        if ("DUAL_QR".equals(job.getAttendanceMode())) {
            var checkpoint = qrEvidence.findById(c.getCheckpointEvidenceId()).orElse(null);
            return proof(checkpoint, c.getRegistration(), c.getEvent()) && !checkpoint.getOccurredAt().isBefore(entry.getOccurredAt());
        }
        return true;
    }
    @Transactional(readOnly = true)
    public List<Certificate> managed(Event event) {
        readGuard(); access.requireEventManager(event.getId());
        return certificates.findByEventOrderByIssuedAtDesc(event).stream().filter(this::proven).toList();
    }
    private static boolean proof(EventQrAttendanceEvidence e, Registration registration, Event event) {
        long eventId = event.getId();
        Instant start = event.getEventDate().atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = event.getCompletedAt().atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        return e != null && "QR_OWNER_CONFIRMED".equals(e.getSource()) && e.getId() != null && e.getOccurredAt() != null
                && !e.getOccurredAt().isBefore(start) && !e.getOccurredAt().isAfter(end)
                && !e.getOccurredAt().isAfter(Instant.now()) && e.getEvent().getId() == eventId && e.getRegistration().getId().equals(registration.getId())
                && e.getIntent() != null && "CONFIRMED".equals(e.getIntent().getStatus()) && e.getIntent().getConfirmedAt() != null
                && e.getIntent().getEvent().getId() == eventId && e.getIntent().getRegistration().getId().equals(registration.getId())
                && e.getOperator() != null && e.getIntent().getOperator() != null && e.getOperator().getId().equals(e.getIntent().getOperator().getId())
                && e.getIntent().getCredential() != null && e.getIntent().getCredential().getRegistration().getId().equals(registration.getId())
                && e.getIntent().getCredential().getEvent().getId() == eventId
                && e.getAction().equals(e.getIntent().getAction())
                && e.getOccurredAt().equals(e.getIntent().getConfirmedAt());
    }
    private void readGuard() { features.requireEnabledForRead("MODULE_EVENTS"); features.requireEnabledForRead("MODULE_CERTIFICATES"); }
    private static void requireCompleted(Event e) {
        if (e.getStatus() != EventStatus.COMPLETED || e.getApprovedAt() == null || e.getCancelledAt() != null || !e.isGenerateCertificate()
                || e.getCompletedAt() == null || e.getCompletedByUserId() == null || e.getEventDate() == null || e.getCompletedAt().isBefore(e.getEventDate()))
            throw new IllegalStateException("Only completed approved events with certificates enabled may finalize eligibility");
    }
    private static CertificateDtos.Finalization receipt(CertificateFinalization job) {
        return new CertificateDtos.Finalization(job.getEvent().getId(), job.getRevision(), job.getStatus(), TYPE, job.getPolicyVersion(),
                job.getIssuedCount(), job.getIneligibleCount(), job.getUnresolvedCount(), job.getFinalizedAt());
    }
    private static LocalDateTime campusNow() { return LocalDateTime.now(ZoneId.of("Asia/Kolkata")).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
