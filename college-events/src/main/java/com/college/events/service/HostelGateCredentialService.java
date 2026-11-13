package com.college.events.service;

import com.college.events.dto.HostelPassViews;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.college.events.service.NotificationService;

/** Owner-confirmed hostel gate transitions; raw credentials are never persisted or logged. */
@Service
@RequiredArgsConstructor
public class HostelGateCredentialService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ACTIONS = Set.of("EXIT", "ENTRY");

    private final HostelGatePassRepository passes;
    private final HostelPassCredentialRepository credentials;
    private final HostelScanIntentRepository intents;
    private final HostelGateEvidenceRepository evidence;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final AuditService audit;

    /** Optional for isolated policy tests; production wires the durable inbox service. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notifications;

    @Transactional
    public HostelPassViews.CredentialView issueCurrentCredential(Long passId) {
        features.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        User actor = access.currentUser();
        HostelGatePass pass = passes.findForDecision(passId).orElseThrow(() -> new IllegalArgumentException("Pass not found"));
        if (!Objects.equals(actor.getId(), pass.getUser().getId())) throw DomainAccess.denied();
        String action = switch (pass.getStatus()) {
            case "APPROVED" -> "EXIT";
            case "ACTIVE_OUT" -> "ENTRY";
            default -> throw new IllegalStateException("A credential is unavailable for the current pass state");
        };
        HostelPassViews.CredentialView view = issueCredential(pass, action);
        if (notifications != null) {
            notifications.createNotification(pass.getUser(),
                    "Hostel " + action + " credential issued for pass " + pass.getId() + ".", "/hostel");
        }
        audit.logAction(actor.getEmail(), actor.getRole().name(), "HOSTEL_CREDENTIAL_ISSUED",
                "passId=" + pass.getId() + ";action=" + action);
        return view;
    }

    @Transactional
    public HostelPassViews.ScanIntentCreated createScanIntent(String rawCredential, String requestKey) {
        features.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        validUuid(requestKey);
        User operator = access.requireRole(Role.ADMIN, Role.WARDEN, Role.STAFF);
        if (rawCredential == null || rawCredential.isBlank() || rawCredential.length() > 256)
            throw new IllegalArgumentException("Invalid gate credential");
        String digest = digest(rawCredential.trim());
        HostelScanIntent existing = intents.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            if (!existing.getCredential().getTokenDigest().equals(digest)
                    || !Objects.equals(existing.getOperatorId(), operator.getId()))
                throw new IllegalArgumentException("Request identifier was reused with different gate data");
            if (notifications != null) {
                notifications.createNotification(operator,
                        "Scan intent reused for pass " + existing.getPass().getId() + " absorbed.", "/hostel");
            }
            return new HostelPassViews.ScanIntentCreated(existing.getIntentKey(), existing.getPass().getId(),
                    existing.getAction(), null, existing.getExpiresAt(), existing.getStatus());
        }
        HostelPassCredential credential = credentials.findActiveForUpdate(digest)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired gate credential"));
        HostelGatePass pass = credential.getPass();
        requireHostelOperator(operator, pass.getHostel());
        LocalDateTime now = campusNow();
        if (credential.getExpiresAt().isBefore(now)) {
            credential.deactivate(); credentials.saveAndFlush(credential);
            throw new IllegalArgumentException("Gate credential has expired");
        }
        if (!isActionAllowed(pass, credential.getAction())) throw new IllegalStateException("Gate action is not valid for the pass state");
        String challenge = String.format("%06d", RANDOM.nextInt(1_000_000));
        HostelScanIntent intent = HostelScanIntent.builder().intentKey(UUID.randomUUID().toString()).requestKey(requestKey)
                .credential(credential).pass(pass).action(credential.getAction()).operatorId(operator.getId())
                .challengeDigest(digest(challenge)).expiresAt(now.plusMinutes(2)).status("PENDING").build();
        HostelScanIntent saved = intents.saveAndFlush(intent);
        if (notifications != null) {
            notifications.createNotification(operator,
                    "Scan intent created for pass " + pass.getId() + ". Challenge: " + challenge + ".", "/hostel");
        }
        audit.logAction(operator.getEmail(), operator.getRole().name(), "HOSTEL_SCAN_INTENT_CREATED",
                "passId=" + pass.getId() + ";action=" + credential.getAction() + ";intent=" + saved.getIntentKey());
        return new HostelPassViews.ScanIntentCreated(saved.getIntentKey(), pass.getId(), saved.getAction(), challenge,
                saved.getExpiresAt(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public List<HostelPassViews.PendingScanIntent> pendingIntent(Long passId) {
        if (!features.isFeatureEnabled("MODULE_HOSTEL_GATEPASS")) throw new IllegalStateException("Hostel gate-pass module is disabled");
        User actor = access.currentUser();
        HostelGatePass pass = passes.findById(passId).orElseThrow(() -> new IllegalArgumentException("Pass not found"));
        if (!Objects.equals(actor.getId(), pass.getUser().getId())) throw DomainAccess.denied();
        return intents.findByPassIdAndStatusOrderByCreatedAtDesc(passId, "PENDING").stream()
                .filter(i -> i.getExpiresAt().isAfter(campusNow()))
                .map(i -> new HostelPassViews.PendingScanIntent(i.getIntentKey(), passId, i.getAction(), i.getExpiresAt(), i.getStatus()))
                .toList();
    }

    @Transactional
    public HostelPassViews.OwnerConfirmedReceipt confirmScan(HostelPassViews.ConfirmScan command) {
        features.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        if (command == null || command.intentKey() == null || command.challengeCode() == null
                || command.challengeCode().length() != 6 || !command.challengeCode().chars().allMatch(Character::isDigit))
            throw new IllegalArgumentException("Invalid scan confirmation");
        User owner = access.currentUser();
        HostelScanIntent intent = intents.findForConfirmation(command.intentKey())
                .orElseThrow(() -> new IllegalArgumentException("Scan intent not found"));
        if (!Objects.equals(owner.getId(), intent.getPass().getUser().getId())) throw DomainAccess.denied();
        if ("CONFIRMED".equals(intent.getStatus())) {
            HostelGateEvidence prior = evidence.findByPassIdAndAction(intent.getPass().getId(), intent.getAction()).orElseThrow();
            return receipt(prior, null);
        }
        LocalDateTime now = campusNow();
        if (!"PENDING".equals(intent.getStatus()) || !intent.getExpiresAt().isAfter(now)) {
            intent.markExpired(); intents.saveAndFlush(intent);
            throw new IllegalStateException("Scan intent has expired");
        }
        if (!MessageDigest.isEqual(digest(command.challengeCode()).getBytes(StandardCharsets.UTF_8), intent.getChallengeDigest().getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Scan confirmation code is invalid");
        HostelGatePass pass = passes.findForDecision(intent.getPass().getId()).orElseThrow(DomainAccess::denied);
        if (!isActionAllowed(pass, intent.getAction())) throw new IllegalStateException("Gate action is no longer valid");
        HostelPassCredential credential = credentials.findForUpdate(intent.getCredential().getId())
                .filter(HostelPassCredential::isActive)
                .orElseThrow(() -> new IllegalStateException("Gate credential is no longer active"));
        credential.deactivate(); credentials.saveAndFlush(credential);
        if ("EXIT".equals(intent.getAction())) {
            pass.setActualExitTime(now); pass.setStatus("ACTIVE_OUT");
        } else {
            pass.setActualReturnTime(now);
            pass.setStatus(isLate(pass.getHostel(), pass.getExpectedReturnTime(), now) ? "LATE_ENTRY" : "RETURNED_ON_TIME");
        }
        HostelGatePass savedPass = passes.saveAndFlush(pass);
        intent.markConfirmed(); intents.saveAndFlush(intent);
        HostelGateEvidence savedEvidence = evidence.saveAndFlush(HostelGateEvidence.builder().pass(savedPass).intent(intent)
                .action(intent.getAction()).operatorId(intent.getOperatorId()).occurredAt(now).resultingStatus(savedPass.getStatus()).build());
        if ("LATE_ENTRY".equals(savedPass.getStatus()) && notifications != null) {
            notifications.createNotification(savedPass.getUser(),
                    "Late hostel entry recorded for your approved pass.", "/hostel");
        } else if ("RETURNED_ON_TIME".equals(savedPass.getStatus()) && notifications != null) {
            notifications.createNotification(savedPass.getUser(),
                    "Hostel return recorded for your approved pass.", "/hostel");
        }
        audit.logAction(owner.getEmail(), owner.getRole().name(), "HOSTEL_GATE_SCAN_CONFIRMED",
                "passId=" + savedPass.getId() + ";action=" + intent.getAction() + ";intent=" + intent.getIntentKey());
        HostelPassViews.CredentialView next = "EXIT".equals(intent.getAction()) ? issueCredential(savedPass, "ENTRY") : null;
        return receipt(savedEvidence, next);
    }

    private HostelPassViews.CredentialView issueCredential(HostelGatePass pass, String action) {
        credentials.findActiveForUpdate(pass.getId(), action).ifPresent(old -> { old.deactivate(); credentials.saveAndFlush(old); });
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        LocalDateTime now = campusNow();
        LocalDateTime expiry = "EXIT".equals(action) ? pass.getExpectedReturnTime() : pass.getExpectedReturnTime().plusHours(12);
        HostelPassCredential credential = HostelPassCredential.builder().pass(pass).action(action).tokenDigest(digest(raw))
                .issuedAt(now).expiresAt(expiry).active(true).build();
        HostelPassCredential saved = credentials.saveAndFlush(credential);
        return new HostelPassViews.CredentialView(pass.getId(), action, raw, saved.getExpiresAt(), saved.getRevision());
    }

    private static boolean isActionAllowed(HostelGatePass pass, String action) {
        return ("EXIT".equals(action) && "APPROVED".equals(pass.getStatus()))
                || ("ENTRY".equals(action) && "ACTIVE_OUT".equals(pass.getStatus()));
    }

    private static boolean isLate(Hostel hostel, LocalDateTime expectedReturn, LocalDateTime now) {
        if (expectedReturn != null && now.isAfter(expectedReturn)) return true;
        try { return now.toLocalTime().isAfter(LocalTime.parse(hostel.getCurfewTime(), DateTimeFormatter.ofPattern("HH:mm"))); }
        catch (RuntimeException ex) { throw new IllegalStateException("Hostel curfew configuration is invalid"); }
    }

    private void requireHostelOperator(User actor, Hostel hostel) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.WARDEN && actor.getEmail().equalsIgnoreCase(hostel.getWardenEmail() == null ? "" : hostel.getWardenEmail())) return;
        if (actor.getRole() == Role.STAFF && Objects.equals(actor.getHostelName(), hostel.getName())) return;
        throw DomainAccess.denied();
    }

    private HostelPassViews.OwnerConfirmedReceipt receipt(HostelGateEvidence value, HostelPassViews.CredentialView next) {
        return new HostelPassViews.OwnerConfirmedReceipt(value.getId(), value.getPass().getId(), value.getAction(),
                value.getResultingStatus(), value.getOccurredAt(), next);
    }

    private static void validUuid(String value) { try { UUID.fromString(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("A valid request identifier is required"); } }
    private static byte[] randomBytes(int size) { byte[] value = new byte[size]; RANDOM.nextBytes(value); return value; }
    private static String digest(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException("Digest unavailable", ex); } }
    private static LocalDateTime campusNow() { return LocalDateTime.now(CAMPUS_ZONE); }
}
