package com.college.events.service;

import com.college.events.dto.EventAttendanceDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Online owner-confirmed QR attendance foundation. A scanned bearer value creates
 * a short-lived intent only; attendance is appended after the registered owner
 * authenticates and confirms the challenge.
 */
@Service
@RequiredArgsConstructor
public class EventQrAttendanceService {
    private static final Duration INTENT_TTL = Duration.ofSeconds(90);
    private static final Duration DISPLAY_CREDENTIAL_TTL = Duration.ofSeconds(90);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationRepository registrations;
    private final EventOperationsRepository operations;
    private final EventQrCredentialRepository credentials;
    private final EventScanIntentRepository intents;
    private final EventQrAttendanceEvidenceRepository evidence;
    private final DomainAccess access;
    private final EventOperationsService eventOperations;
    private final FeatureFlagService features;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional
    public EventAttendanceDtos.CredentialView issueCredential(long eventId, long registrationId,
                                                               EventAttendanceDtos.IssueCredential command) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        User owner = access.currentUser();
        long ownedEvent = registrations.findOwnedEventId(registrationId, owner.getId()).orElseThrow(DomainAccess::denied);
        if (ownedEvent != eventId) throw DomainAccess.denied();
        EventOperations operation = operations.findForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        Registration registration = registrations.findForUpdate(registrationId)
                .orElseThrow(DomainAccess::denied);
        if (registration.getEvent().getId() != eventId || registration.getUser().getId() == null
                || !registration.getUser().getId().equals(owner.getId())) throw DomainAccess.denied();
        Event event = registration.getEvent();
        if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Event is not available");
        if (registration.getStatus() != RegistrationStatus.REGISTERED
                && registration.getStatus() != RegistrationStatus.ATTENDED)
            throw new IllegalStateException("Registration is not eligible for an event credential");
        validateIssuePolicy(operation, command.action());

        EventQrCredential credential = credentials.findForUpdateByRegistrationAndAction(registrationId, command.action())
                .orElseGet(() -> EventQrCredential.builder().event(event).registration(registration)
                        .action(command.action()).build());
        String raw = randomToken();
        credential.setTokenDigest(digest(raw));
        credential.setStatus("ACTIVE");
        credential.setCredentialVersion(credential.getCredentialVersion() + 1);
        credential.setScannerEpoch(operation.getScannerEpoch());
        credential.setIssuedAt(Instant.now());
        credential.setConsumedAt(null);
        credential.setExpiresAt(expiry(operation, command.action()));
        EventQrCredential saved = credentials.save(credential);
        // Credential rows are reissued in place: invalidate pending intents that
        // point at an older token/version before returning the new pass.
        intents.revokePendingByCredentialId(saved.getId());
        audit.logAction(owner.getEmail(), owner.getRole().name(), "EVENT_QR_CREDENTIAL_ISSUED",
                "eventId=" + eventId + ";registrationId=" + registrationId + ";action=" + command.action()
                        + ";credentialVersion=" + saved.getCredentialVersion());
        return new EventAttendanceDtos.CredentialView(command.action(), raw, saved.getExpiresAt(), saved.getCredentialVersion());
    }

    @Transactional
    public EventAttendanceDtos.IntentCreated createIntent(long eventId, EventAttendanceDtos.CreateIntent command) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        String requestKey = uuid(command.requestKey());
        User operator = access.currentUser();
        eventOperations.requireScanCapability(eventId, operator.getId(), command.action());
        EventOperations operation = operations.findForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));

        EventScanIntent replay = intents.findByEventIdAndRequestKey(eventId, requestKey).orElse(null);
        if (replay != null) {
            if (!replay.getAction().equals(command.action())
                    || !replay.getCredential().getTokenDigest().equals(digest(command.credential())))
                throw new IllegalArgumentException("Request identifier was reused with another credential or action");
            return new EventAttendanceDtos.IntentCreated(replay.getIntentKey(), replay.getAction(), null,
                    replay.getExpiresAt(), replay.getStatus());
        }

        EventQrCredential credential = credentials.findForUpdateByDigest(digest(command.credential()))
                .orElseThrow(() -> new IllegalArgumentException("Event credential is invalid or expired"));
        if (!credential.getEvent().getId().equals(eventId) || !credential.getAction().equals(command.action())
                || !"ACTIVE".equals(credential.getStatus()) || credential.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("Event credential is invalid or expired");
        requireCurrentScannerEpoch(credential, operation);
        if (!eligible(credential.getRegistration())) throw new IllegalArgumentException("Registration is not eligible");
        // The event-operation row is locked above, serializing scan creation with
        // scanner-control changes and with another scan for this event. Do not let
        // a repeated frame/request key fan out multiple owner notifications.
        if (intents.existsLivePendingByCredentialId(credential.getId(), Instant.now()))
            throw new IllegalStateException("An attendance confirmation is already pending for this pass");

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expires = Instant.now().plus(INTENT_TTL);
        EventScanIntent intent = intents.save(EventScanIntent.builder().event(credential.getEvent())
                .registration(credential.getRegistration()).credential(credential).operator(operator)
                .intentKey(UUID.randomUUID().toString()).requestKey(requestKey).action(command.action())
                .credentialVersionAtScan(credential.getCredentialVersion()).scannerEpochAtScan(credential.getScannerEpoch())
                .challengeDigest(digest(code)).status("PENDING").expiresAt(expires).build());
        notifications.createNotification(credential.getRegistration().getUser(),
                "Attendance confirmation requested for " + credential.getEvent().getTitle() + ". Open your pending confirmation.",
                "/events/" + eventId + "/attendance");
        audit.logAction(operator.getEmail(), operator.getRole().name(), "EVENT_QR_SCAN_INTENT_CREATED",
                "eventId=" + eventId + ";intent=" + intent.getIntentKey() + ";action=" + command.action());
        return new EventAttendanceDtos.IntentCreated(intent.getIntentKey(), intent.getAction(), code, expires, intent.getStatus());
    }

    @Transactional(readOnly = true)
    public EventAttendanceDtos.PendingIntents pendingForOwner() {
        User owner = access.currentUser();
        List<EventAttendanceDtos.PendingIntent> items = intents.findPendingForOwner(owner.getId(), Instant.now()).stream()
                .map(i -> new EventAttendanceDtos.PendingIntent(i.getIntentKey(), i.getEvent().getTitle(), i.getAction(), i.getExpiresAt(), i.getStatus()))
                .toList();
        return new EventAttendanceDtos.PendingIntents(items);
    }

    @Transactional
    public EventAttendanceDtos.Receipt confirm(String intentKey, EventAttendanceDtos.ConfirmIntent command) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        String confirmationRequest = uuid(command.requestKey());
        User owner = access.currentUser();
        String canonicalIntent = uuid(intentKey);
        long eventId = intents.findOwnedEventId(canonicalIntent, owner.getId()).orElseThrow(DomainAccess::denied);
        EventOperations operation = operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        EventScanIntent intent = intents.findForUpdateByIntentKey(canonicalIntent)
                .orElseThrow(DomainAccess::denied);
        if (!intent.getRegistration().getUser().getId().equals(owner.getId())) throw DomainAccess.denied();
        EventQrAttendanceEvidence existing = evidence.findByEventIdAndRegistrationIdAndAction(
                intent.getEvent().getId(), intent.getRegistration().getId(), intent.getAction()).orElse(null);
        if (existing != null) {
            // A committed owner confirmation may have lost its HTTP response. A retry
            // for the same intent returns the original successful receipt; evidence
            // from a different intent remains a duplicate and cannot be overwritten.
            String result = existing.getIntent().getId().equals(intent.getId()) ? "RECORDED" : "DUPLICATE";
            return new EventAttendanceDtos.Receipt(existing.getId(), existing.getAction(), result, existing.getOccurredAt());
        }
        if (!"PENDING".equals(intent.getStatus()) || !intent.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Attendance confirmation expired");
        if (!MessageDigest.isEqual(intent.getChallengeDigest().getBytes(StandardCharsets.UTF_8), digest(command.challengeCode()).getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Attendance confirmation code is invalid");
        if (!eligible(intent.getRegistration())) throw new IllegalStateException("Registration is no longer eligible");

        EventQrCredential credential = credentials.findForUpdateByDigest(intent.getCredential().getTokenDigest())
                .orElseThrow(() -> new IllegalStateException("Event credential is unavailable"));
        if (intent.getCredentialVersionAtScan() == null
                || intent.getCredentialVersionAtScan() != credential.getCredentialVersion()
                || intent.getScannerEpochAtScan() == null
                || !intent.getScannerEpochAtScan().equals(credential.getScannerEpoch()))
            throw new IllegalStateException("Attendance pass changed after scanning; scan the current pass again");
        if (!credential.getId().equals(intent.getCredential().getId()) || !"ACTIVE".equals(credential.getStatus())
                || !credential.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Event credential is already consumed or revoked");
        requireCurrentScannerEpoch(credential, operation);

        // Scanner permission/lifecycle may have changed while the owner was deciding.
        eventOperations.requireStoredScanCapability(intent.getEvent().getId(), intent.getOperator(), intent.getAction());

        Instant acceptedAt = Instant.now();
        EventQrAttendanceEvidence accepted = evidence.save(EventQrAttendanceEvidence.builder()
                .event(intent.getEvent()).registration(intent.getRegistration()).operator(intent.getOperator())
                .intent(intent).action(intent.getAction()).source("QR_OWNER_CONFIRMED")
                .occurredAt(acceptedAt).build());
        intent.setStatus("CONFIRMED");
        intent.setConfirmedAt(acceptedAt);
        credential.setStatus("CONSUMED");
        credential.setConsumedAt(acceptedAt);
        // Legacy ATTENDED also fences post-admission cancellation. It is not certificate eligibility;
        // point-attendance certificates read the frozen policy and accepted ledger independently.
        if (("ENTRY".equals(intent.getAction()) || "ATTENDANCE_CHECKPOINT".equals(intent.getAction()))
                && intent.getRegistration().getStatus() == RegistrationStatus.REGISTERED)
            intent.getRegistration().setStatus(RegistrationStatus.ATTENDED);
        audit.logAction(owner.getEmail(), owner.getRole().name(), "EVENT_QR_ATTENDANCE_CONFIRMED",
                "eventId=" + intent.getEvent().getId() + ";intent=" + intent.getIntentKey() + ";action=" + intent.getAction()
                        + ";confirmationRequest=" + confirmationRequest);
        return new EventAttendanceDtos.Receipt(accepted.getId(), accepted.getAction(), "RECORDED", accepted.getOccurredAt());
    }

    private static boolean eligible(Registration registration) {
        return registration.getStatus() == RegistrationStatus.REGISTERED || registration.getStatus() == RegistrationStatus.ATTENDED;
    }

    private static void requireCurrentScannerEpoch(EventQrCredential credential, EventOperations operation) {
        if (credential.getScannerEpoch() == null || credential.getScannerEpoch() != operation.getScannerEpoch())
            throw new IllegalStateException("Event credential was revoked by a scanner-control change; refresh the pass");
    }

    private static void validateIssuePolicy(EventOperations operation, String action) {
        if ("ENTRY".equals(action)) {
            if ("NO_QR".equals(operation.getAttendanceMode())) throw new IllegalStateException("QR credentials are disabled for this event");
            return;
        }
        if (!"ATTENDANCE_CHECKPOINT".equals(action)) throw new IllegalArgumentException("Unsupported attendance action");
        boolean singleAllowed = "SINGLE_QR".equals(operation.getAttendanceMode())
                && "ENTRY_AND_ATTENDANCE".equals(operation.getSingleQrPolicy());
        boolean dualAllowed = "DUAL_QR".equals(operation.getAttendanceMode())
                && operation.isScannerMasterEnabled()
                && operation.getScannerActions() != null && operation.getScannerActions().contains("ATTENDANCE_CHECKPOINT")
                && operation.getScannerClosesAt() != null && operation.getScannerClosesAt().isAfter(java.time.LocalDateTime.now());
        if (!singleAllowed && !dualAllowed) throw new IllegalStateException("Attendance checkpoint is not enabled");
    }

    private static Instant expiry(EventOperations operation, String action) {
        Instant displayExpiry = Instant.now().plus(DISPLAY_CREDENTIAL_TTL);
        if ("ATTENDANCE_CHECKPOINT".equals(action) && operation.getScannerClosesAt() != null) {
            Instant windowExpiry = operation.getScannerClosesAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            return windowExpiry.isBefore(displayExpiry) ? windowExpiry : displayExpiry;
        }
        return displayExpiry;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String uuid(String raw) {
        try { return UUID.fromString(raw).toString(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("A valid request identifier is required"); }
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
