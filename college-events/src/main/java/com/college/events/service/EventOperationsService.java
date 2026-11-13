package com.college.events.service;

import com.college.events.dto.EventOperationsDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** CF-24/25 control and roster foundation. Credential issuance/scans stay unavailable until CF-26/27 controls exist. */
@Service
@RequiredArgsConstructor
public class EventOperationsService {
    private final EventRepository events;
    private final EventOperationsRepository operations;
    private final VolunteerMembershipRepository memberships;
    private final EventOperatorGrantRepository grants;
    private final RegistrationRepository registrations;
    private final ManualAttendanceEvidenceRepository manualEvidence;
    private final UserRepository users;
    private final DomainAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final FeatureFlagService featureFlags;

    @Transactional(readOnly = true)
    public EventSummaries myEvents() {
        User user = access.currentUser();
        List<Event> visible;
        if (user.getRole() == Role.ADMIN) {
            visible = java.util.stream.Stream.concat(
                    events.findByStatusOrderByEventDateDesc(EventStatus.APPROVED, org.springframework.data.domain.PageRequest.of(0, 50)).stream(),
                    events.findByStatusOrderByEventDateDesc(EventStatus.COMPLETED, org.springframework.data.domain.PageRequest.of(0, 50)).stream())
                    .sorted(java.util.Comparator.comparing(Event::getEventDate).reversed()).limit(50).toList();
        } else if (user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER) {
            visible = java.util.stream.Stream.concat(
                    events.findByOrganizerAndStatus(user, EventStatus.APPROVED, org.springframework.data.domain.PageRequest.of(0, 50)).stream(),
                    events.findByOrganizerAndStatus(user, EventStatus.COMPLETED, org.springframework.data.domain.PageRequest.of(0, 50)).stream())
                    .sorted(java.util.Comparator.comparing(Event::getEventDate).reversed()).limit(50).toList();
        } else throw DomainAccess.denied();
        return new EventSummaries(visible.stream().map(event -> new EventSummary(event.getId(), event.getTitle(), event.getStatus().name(),
                event.getEventDate(), operations.findByEventId(event.getId()).map(EventOperations::getRevision).orElse(-1L))).toList());
    }

    @Transactional(readOnly = true)
    public EventSummaries volunteerOpportunities() {
        List<Event> candidates = events.findByStatusOrderByEventDateDesc(EventStatus.APPROVED,
                org.springframework.data.domain.PageRequest.of(0, 100));
        return new EventSummaries(candidates.stream().map(event -> operations.findByEventId(event.getId()).orElse(null))
                .filter(java.util.Objects::nonNull).filter(EventOperations::isRecruitmentOpen)
                .map(operation -> new EventSummary(operation.getEvent().getId(), operation.getEvent().getTitle(),
                        operation.getEvent().getStatus().name(), operation.getEvent().getEventDate(), operation.getRevision())).toList());
    }

    /** Read-only own history survives recruitment closure; it conveys no operator capability. */
    @Transactional(readOnly = true)
    public OwnVolunteerApplicationPage myVolunteerApplications(int page, int size) {
        User applicant = access.requireRole(Role.STUDENT);
        featureFlags.requireEnabledForRead("MODULE_EVENTS");
        if (page < 0 || page > 10000 || size < 1 || size > 50)
            throw new IllegalArgumentException("Invalid volunteer application page");
        var results = memberships.findOwnApplications(applicant.getId(),
                List.of(EventStatus.APPROVED, EventStatus.COMPLETED, EventStatus.CANCELLED),
                org.springframework.data.domain.PageRequest.of(page, size));
        return new OwnVolunteerApplicationPage(results.stream().map(member -> {
            Event event = member.getEvent();
            return new OwnVolunteerApplication(member.getId(), member.getRevision(), member.getStatus(),
                    event.getId(), event.getTitle(), event.getStatus().name(), event.getEventDate(),
                    member.getAppliedAt(), member.getDecidedAt());
        }).toList(), page, size, results.hasNext());
    }

    @Transactional(readOnly = true)
    public EventSummaries myOperatorEvents() {
        User user = access.currentUser();
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER) return myEvents();
        if (user.getRole() != Role.STUDENT) throw DomainAccess.denied();
        List<EventSummary> assigned = memberships.findApprovedEventMemberships(user.getId(), org.springframework.data.domain.PageRequest.of(0, 100))
                .stream().map(m -> m.getEvent()).distinct().map(event -> new EventSummary(event.getId(), event.getTitle(), event.getStatus().name(),
                        event.getEventDate(), operations.findByEventId(event.getId()).map(EventOperations::getRevision).orElse(-1L))).toList();
        return new EventSummaries(assigned);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ensureWorkspace(Event event) {
        if (event.getStatus() != EventStatus.APPROVED) return;
        if (operations.findByEventId(event.getId()).isEmpty()) {
            operations.save(EventOperations.builder().event(event).attendanceMode(normalizeMode(event.getAttendanceMode())).build());
        }
    }

    @Transactional(readOnly = true)
    public OperationsView view(long eventId) {
        access.requireEventManager(eventId);
        return view(requireOperations(eventId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationsView configure(long eventId, Configure command) {
        featureFlags.requireEnabledForUpdate("MODULE_EVENTS");
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        String certificatePolicy = command.certificateEvidencePolicy() == null ? "UNREVIEWED" : command.certificateEvidencePolicy();
        if (!List.of("UNREVIEWED", "POINT_ATTENDANCE").contains(certificatePolicy))
            throw new IllegalArgumentException("Certificate evidence policy is incompatible with this implementation");
        if ("POINT_ATTENDANCE".equals(certificatePolicy) && ("NO_QR".equals(command.attendanceMode())
                || "SINGLE_QR".equals(command.attendanceMode()) && "ENTRY_ONLY".equals(command.singleQrPolicy())))
            throw new IllegalArgumentException("Point certificates require an approved attendance QR policy");
        boolean attendancePolicyChanged = !operation.getAttendanceMode().equals(command.attendanceMode())
                || !operation.getSingleQrPolicy().equals(command.singleQrPolicy())
                || !operation.getCertificateEvidencePolicy().equals(certificatePolicy);
        if (attendancePolicyChanged && registrations.existsByEvent(operation.getEvent()))
            throw new IllegalStateException("Attendance policy is frozen after registration opens; submit a reviewed policy change");
        operation.setAttendanceMode(command.attendanceMode());
        operation.setSingleQrPolicy(command.singleQrPolicy());
        if (attendancePolicyChanged)
            operation.setCertificatePolicyVersion(operation.getCertificatePolicyVersion() + 1);
        operation.setCertificateEvidencePolicy(certificatePolicy);
        if (attendancePolicyChanged) {
            operation.setScannerMasterEnabled(false);
            operation.setScannerScope("OFF");
            operation.setScannerOpensAt(null);
            operation.setScannerClosesAt(null);
            operation.setScannerEpoch(operation.getScannerEpoch() + 1);
            operation.setManualWindowOpen(false);
            operation.setManualEpoch(operation.getManualEpoch() + 1);
            grants.findByEventIdAndGrantType(eventId, "QR").forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
            grants.findByEventIdAndGrantType(eventId, "MANUAL").forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
        }
        audit("EVENT_OPERATIONS_CONFIGURED", eventId, "attendanceMode=" + command.attendanceMode()
                + ";singleQrPolicy=" + command.singleQrPolicy() + ";certificatePolicy=" + certificatePolicy
                + ";certificatePolicyVersion=" + operation.getCertificatePolicyVersion() + ";scannerEpoch=" + operation.getScannerEpoch());
        operations.flush();
        return view(operation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationsView recruitment(long eventId, Recruitment command) {
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        operation.setRecruitmentOpen(command.open());
        operation.setMaxVolunteerApplicants(command.maxApplicants());
        audit(command.open() ? "VOLUNTEER_RECRUITMENT_OPENED" : "VOLUNTEER_RECRUITMENT_CLOSED", eventId,
                "open=" + command.open() + ";revision=" + operation.getRevision());
        operations.flush();
        return view(operation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VolunteerView apply(long eventId) {
        User applicant = access.requireRole(Role.STUDENT);
        EventOperations operation = requireOperationsForUpdate(eventId);
        VolunteerMembership existing = memberships.findByEventIdAndUserId(eventId, applicant.getId()).orElse(null);
        if (existing != null) return volunteer(existing);
        if (!operation.isRecruitmentOpen()) throw new IllegalStateException("Volunteer recruitment is closed");
        String identifier = applicant.getVerifiedEnrollmentNo();
        if (identifier == null || identifier.isBlank()) throw new IllegalStateException("A verified roll or enrollment number is required");
        long activeApplications = memberships.countByEventIdAndStatusIn(eventId, List.of("APPLIED", "ACCEPTED"));
        if (activeApplications >= operation.getMaxVolunteerApplicants()) throw new IllegalStateException("Volunteer applications are full");
        VolunteerMembership member = memberships.save(VolunteerMembership.builder().event(operation.getEvent()).user(applicant)
                .verifiedIdentifier(identifier.trim()).status("APPLIED").build());
        audit("VOLUNTEER_APPLIED", eventId);
        return volunteer(member);
    }

    @Transactional(readOnly = true)
    public VolunteerList volunteers(long eventId) {
        access.requireEventManager(eventId);
        return new VolunteerList(memberships.findByEventId(eventId).stream().map(this::volunteer).toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VolunteerView decide(long eventId, long membershipId, Decision command) {
        access.requireEventManager(eventId);
        requireOperationsForUpdate(eventId);
        VolunteerMembership member = memberships.findForUpdate(membershipId).orElseThrow(DomainAccess::denied);
        if (member.getEvent().getId() != eventId) throw DomainAccess.denied();
        if (member.getRevision() != command.expectedRevision()) throw new IllegalStateException("Volunteer roster changed; refresh and retry");
        if (!"APPLIED".equals(member.getStatus())) return volunteer(member);
        member.setStatus("ACCEPT".equals(command.decision()) ? "ACCEPTED" : "REJECTED");
        member.setDecidedAt(LocalDateTime.now());
        if (!"ACCEPTED".equals(member.getStatus())) {
            grants.findByEventIdAndGrantType(eventId, "QR").stream().filter(g -> g.getUser().getId().equals(member.getUser().getId()))
                    .forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
            grants.findByEventIdAndGrantType(eventId, "MANUAL").stream().filter(g -> g.getUser().getId().equals(member.getUser().getId()))
                    .forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
        }
        audit("VOLUNTEER_" + member.getStatus(), eventId,
                "membershipId=" + member.getId() + ";userId=" + member.getUser().getId() + ";status=" + member.getStatus());
        notifyUser(member.getUser(),
                "Your volunteer application for " + member.getEvent().getTitle() + " was "
                        + member.getStatus().toLowerCase() + ".");
        memberships.flush();
        return volunteer(member);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationsView scannerControl(long eventId, ScannerControl command) {
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        if (!"OFF".equals(command.scope())) {
            if (command.actions().isEmpty()) throw new IllegalArgumentException("Select at least one scanner action");
            if ("NO_QR".equals(operation.getAttendanceMode())) throw new IllegalStateException("QR scanning is unavailable for NO_QR events");
            if (command.opensAt() == null || command.closesAt() == null || !command.closesAt().isAfter(command.opensAt())
                    || java.time.Duration.between(command.opensAt(), command.closesAt()).compareTo(java.time.Duration.ofHours(24)) > 0)
                throw new IllegalArgumentException("Scanner window must be bounded to at most 24 hours");
            validateQrActions(operation, command.actions());
        }
        // Scope, action or window changes always revoke prior selected grants. A
        // later SELECTED mode requires an explicit fresh grant for each volunteer.
        grants.findByEventIdAndGrantType(eventId, "QR").forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
        operation.setScannerScope(command.scope());
        operation.setScannerMasterEnabled(!"OFF".equals(command.scope()));
        operation.setScannerEpoch(operation.getScannerEpoch() + 1);
        operation.setScannerActions(String.join(",", command.actions().stream().distinct().sorted().toList()));
        operation.setScannerOpensAt("OFF".equals(command.scope()) ? null : command.opensAt());
        operation.setScannerClosesAt("OFF".equals(command.scope()) ? null : command.closesAt());
        if ("OFF".equals(command.scope())) {
            grants.findByEventIdAndGrantType(eventId, "QR").forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
        }
        audit("SCANNER_SCOPE_" + command.scope(), eventId, "scope=" + command.scope() + ";actions="
                + operation.getScannerActions() + ";epoch=" + operation.getScannerEpoch() + ";opensAt="
                + operation.getScannerOpensAt() + ";closesAt=" + operation.getScannerClosesAt());
        operations.flush();
        return view(operation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GrantView delegateScanner(long eventId, long userId, Delegate command) {
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        if (!operation.isScannerMasterEnabled() || !"SELECTED".equals(operation.getScannerScope()))
            throw new IllegalStateException("Selected scanner grants require scanner scope SELECTED");
        VolunteerMembership member = acceptedMember(eventId, userId);
        EventOperatorGrant grant = grants.findForUpdate(eventId, userId, "QR").orElseGet(() -> EventOperatorGrant.builder()
                .event(operation.getEvent()).user(member.getUser()).grantType("QR").build());
        if ("NO_QR".equals(operation.getAttendanceMode()) || command.actions().stream().anyMatch("EXIT"::equals))
            throw new IllegalStateException("Requested QR action is not enabled by the approved attendance policy");
        validateQrActions(operation, command.actions());
        grant.setActions(String.join(",", command.actions().stream().distinct().sorted().toList()));
        grant.setEnabled(true); grant.setEpoch(grant.getEpoch() + 1); grant.setExpiresAt(null);
        grants.save(grant);
        operation.setScannerEpoch(operation.getScannerEpoch() + 1);
        audit("SCANNER_SELECTED_GRANT", eventId, "userId=" + userId + ";actions=" + grant.getActions()
                + ";epoch=" + grant.getEpoch() + ";expiresAt=" + grant.getExpiresAt());
        operations.flush();
        return grantView(grant);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GrantView delegateManual(long eventId, long userId, ManualDelegate command) {
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        featureFlags.requireEnabledForUpdate("MODULE_NOTIFICATIONS");
        if (!operation.isManualWindowOpen() || operation.getManualClosesAt() == null
                || command.expiresAt().isAfter(operation.getManualClosesAt()))
            throw new IllegalStateException("Manual attendance grant must fit within the open manual window");
        VolunteerMembership member = acceptedMember(eventId, userId);
        EventOperatorGrant grant = grants.findForUpdate(eventId, userId, "MANUAL").orElseGet(() -> EventOperatorGrant.builder()
                .event(operation.getEvent()).user(member.getUser()).grantType("MANUAL").build());
        grant.setActions(String.join(",", command.actions().stream().distinct().sorted().toList()));
        grant.setReason(command.reason().trim());
        grant.setEnabled(true); grant.setEpoch(grant.getEpoch() + 1); grant.setExpiresAt(command.expiresAt());
        grants.save(grant);
        operation.setManualEpoch(operation.getManualEpoch() + 1);
        audit("MANUAL_ATTENDANCE_GRANT", eventId, "userId=" + userId + ";actions=" + grant.getActions()
                + ";epoch=" + grant.getEpoch() + ";expiresAt=" + grant.getExpiresAt());
        operations.flush();
        notifyUser(member.getUser(), "Manual attendance access for "
                + operation.getEvent().getTitle() + " is enabled for " + grant.getActions()
                + " until " + grant.getExpiresAt() + ".");
        return grantView(grant);
    }

    @Transactional(readOnly = true)
    public GrantList manualGrants(long eventId) {
        access.requireEventManager(eventId);
        return new GrantList(grants.findByEventIdAndGrantType(eventId, "MANUAL").stream().map(this::grantView).toList());
    }

    @Transactional(readOnly = true)
    public GrantList attendanceExportGrants(long eventId) {
        access.requireEventManager(eventId);
        return new GrantList(grants.findByEventIdAndGrantType(eventId, "EXPORT").stream().map(this::grantView).toList());
    }

    @Transactional(readOnly = true)
    public EventSummaryPage myAttendanceExportEvents(int page) {
        featureFlags.requireEnabledForRead("MODULE_EVENTS");
        User actor = access.currentUser();
        if (actor == null || !actor.isEnabled() || (actor.getRole() != Role.FACULTY && actor.getRole() != Role.ORGANIZER))
            throw DomainAccess.denied();
        var grantPage = grants.findEnabledExportGrantsForUser(actor.getId(), LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(page, 25));
        List<EventSummary> result = grantPage.getContent().stream()
                .filter(EventOperationsService::active)
                .filter(grant -> !java.util.Collections.disjoint(split(grant.getActions()), List.of("CSV", "XLSX", "SCAN_LOG")))
                .map(EventOperatorGrant::getEvent)
                .map(event -> new EventSummary(event.getId(), event.getTitle(), event.getStatus().name(), event.getEventDate(),
                        operations.findByEventId(event.getId()).map(EventOperations::getRevision).orElse(-1L)))
                .toList();
        return new EventSummaryPage(result, grantPage.getNumber(), grantPage.hasNext());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GrantView delegateAttendanceExport(long eventId, long userId, ExportDelegate command) {
        featureFlags.requireEnabledForUpdate("MODULE_EVENTS");
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        User delegate = users.findById(userId).orElseThrow(DomainAccess::denied);
        if (!delegate.isEnabled() || (delegate.getRole() != Role.FACULTY && delegate.getRole() != Role.ORGANIZER))
            throw new IllegalArgumentException("Attendance export can only be delegated to an enabled faculty or organizer account");
        EventOperatorGrant grant = grants.findForUpdate(eventId, userId, "EXPORT").orElseGet(() -> EventOperatorGrant.builder()
                .event(operation.getEvent()).user(delegate).grantType("EXPORT").build());
        grant.setActions("CSV,XLSX,SCAN_LOG");
        grant.setReason(command.reason().trim());
        grant.setEnabled(true);
        grant.setExpiresAt(command.expiresAt());
        grant.setEpoch(grant.getEpoch() + 1);
        grants.save(grant);
        audit("ATTENDANCE_EXPORT_GRANT", eventId, "userId=" + userId + ";epoch=" + grant.getEpoch() + ";expiresAt=" + grant.getExpiresAt());
        operations.flush();
        return grantView(grant);
    }

    /** Serializes export authorization against grant revocation and event-control changes. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long requireAttendanceExportCapability(long eventId, String action) {
        EventOperations operation = operations.findForUpdate(eventId).orElseThrow(DomainAccess::denied);
        EventStatus status = operation.getEvent().getStatus();
        if (status != EventStatus.APPROVED && status != EventStatus.COMPLETED) throw DomainAccess.denied();
        User actor = access.currentUser();
        if (actor == null || !actor.isEnabled()) throw DomainAccess.denied();
        if (actor.getRole() == Role.ADMIN || access.isEventOwner(actor, operation.getEvent())) return operation.getRevision();
        if (actor.getRole() != Role.FACULTY && actor.getRole() != Role.ORGANIZER) throw DomainAccess.denied();
        EventOperatorGrant grant = grants.findForUpdate(eventId, actor.getId(), "EXPORT").orElse(null);
        if (!active(grant) || !split(grant.getActions()).contains(action)) throw DomainAccess.denied();
        return operation.getRevision();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationsView manualWindow(long eventId, ManualWindow command) {
        access.requireEventManager(eventId);
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireVersion(operation, command.expectedRevision());
        if (command.open()) {
            LocalDateTime opensAt = command.opensAt() == null ? LocalDateTime.now() : command.opensAt();
            LocalDateTime closesAt = command.closesAt() == null ? opensAt.plusHours(2) : command.closesAt();
            if (!closesAt.isAfter(opensAt) || java.time.Duration.between(opensAt, closesAt).compareTo(java.time.Duration.ofHours(24)) > 0)
                throw new IllegalArgumentException("Manual attendance window must be bounded to at most 24 hours");
            operation.setManualWindowOpen(true);
            operation.setManualOpensAt(opensAt);
            operation.setManualClosesAt(closesAt);
            operation.setManualWindowReason(command.reason().trim());
            grants.findByEventIdAndGrantType(eventId, "MANUAL").stream()
                    .filter(g -> active(g) && split(g.getActions()).contains("ENTRY"))
                    .forEach(g -> notifyUser(g.getUser(), "Manual attendance for "
                            + operation.getEvent().getTitle() + " is open until " + closesAt + "."));
        } else {
            List<EventOperatorGrant> allManualGrants = grants.findByEventIdAndGrantType(eventId, "MANUAL");
            List<EventOperatorGrant> affected = allManualGrants.stream().filter(EventOperationsService::active).toList();
            operation.setManualWindowOpen(false);
            operation.setManualEpoch(operation.getManualEpoch() + 1);
            operation.setManualWindowReason(command.reason().trim());
            allManualGrants.stream().filter(EventOperatorGrant::isEnabled)
                    .forEach(g -> { g.setEnabled(false); g.setEpoch(g.getEpoch() + 1); });
            affected.forEach(g -> notifyUser(g.getUser(), "Manual attendance access for "
                    + operation.getEvent().getTitle() + " has been closed."));
        }
        audit(command.open() ? "MANUAL_WINDOW_OPENED" : "MANUAL_WINDOW_CLOSED", eventId, "epoch="
                + operation.getManualEpoch() + ";opensAt=" + operation.getManualOpensAt() + ";closesAt=" + operation.getManualClosesAt());
        operations.flush();
        return view(operation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(long eventId, long userId, String kind, long expectedRevision) {
        access.requireEventManager(eventId);
        EventOperations operation;
        if ("EXPORT".equals(kind)) {
            operation = operations.findForUpdate(eventId).orElseThrow(DomainAccess::denied);
            EventStatus status = operation.getEvent().getStatus();
            if (status != EventStatus.APPROVED && status != EventStatus.COMPLETED) throw DomainAccess.denied();
        } else {
            operation = requireOperationsForUpdate(eventId);
        }
        requireVersion(operation, expectedRevision);
        if (!"QR".equals(kind) && !"MANUAL".equals(kind) && !"EXPORT".equals(kind)) throw new IllegalArgumentException("Unknown grant type");
        EventOperatorGrant grant = grants.findForUpdate(eventId, userId, kind).orElse(null);
        if (grant == null || !grant.isEnabled()) return;
        grant.setEnabled(false); grant.setEpoch(grant.getEpoch() + 1);
        if ("MANUAL".equals(kind)) operation.setManualEpoch(operation.getManualEpoch() + 1);
        else if ("QR".equals(kind)) operation.setScannerEpoch(operation.getScannerEpoch() + 1);
        audit(kind + "_GRANT_REVOKED", eventId, "userId=" + userId + ";epoch=" + grant.getEpoch());
        operations.flush();
        if ("MANUAL".equals(kind)) notifyUser(grant.getUser(), "Manual attendance access for "
                + operation.getEvent().getTitle() + " has been revoked.");
    }

    @Transactional(readOnly = true)
    public CapabilityView myCapabilities(long eventId) {
        User user = access.currentUser();
        EventOperations operation = requireOperations(eventId);
        VolunteerMembership member = memberships.findByEventIdAndUserId(eventId, user.getId()).orElse(null);
        boolean acceptedVolunteer = member != null && "ACCEPTED".equals(member.getStatus())
                && java.util.Objects.equals(member.getVerifiedIdentifier(), user.getVerifiedEnrollmentNo());
        EventOperatorGrant qr = grants.findByEventIdAndUserIdAndGrantType(eventId, user.getId(), "QR").orElse(null);
        EventOperatorGrant manual = grants.findByEventIdAndUserIdAndGrantType(eventId, user.getId(), "MANUAL").orElse(null);
        boolean qrOn = acceptedVolunteer && operation.isScannerMasterEnabled() && ("ALL".equals(operation.getScannerScope()) || "SELECTED".equals(operation.getScannerScope()) && active(qr));
        LocalDateTime now = LocalDateTime.now();
        qrOn = qrOn && windowActive(operation.getScannerOpensAt(), operation.getScannerClosesAt(), now);
        boolean manager = user.getRole() == Role.ADMIN || access.isEventOwner(user, operation.getEvent());
        if (!manager && member == null) throw DomainAccess.denied();
        boolean manualWindow = operation.isManualWindowOpen() && windowActive(operation.getManualOpensAt(), operation.getManualClosesAt(), now);
        boolean manualOn = manualWindow && (manager || acceptedVolunteer && active(manual) && split(manual.getActions()).contains("ENTRY"));
        return new CapabilityView(qrOn, manualOn, qrOn ? split(operation.getScannerActions()) : List.of(), manualOn ? (manager ? List.of("ENTRY") : split(manual.getActions())) : List.of(), operation.getScannerEpoch());
    }

    /** Manual fallback confirms a person against this event's confirmed roster; it never creates registration or certificate eligibility. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ManualCheckInReceipt manualCheckIn(long eventId, ManualCheckIn command) {
        EventOperations operation = requireOperationsForUpdate(eventId);
        User operator = access.currentUser();
        requireManualEntryCapability(operation, operator);
        String key;
        try { key = UUID.fromString(command.idempotencyKey()).toString(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("A valid request identifier is required"); }

        // Resolve an authorized retry before participant lookup. The owner aggregate lock above
        // serializes same-event requests with grant/window changes and concurrent duplicate keys.
        ManualAttendanceEvidence replay = manualEvidence.findByEventIdAndIdempotencyKey(eventId, key).orElse(null);
        if (replay != null) {
            if (!java.util.Objects.equals(replay.getVerifiedIdentifierSnapshot(), command.verifiedIdentifier())
                    || !java.util.Objects.equals(replay.getReasonCode(), command.reasonCode())) {
                throw new IllegalArgumentException("Request identifier was already used with different attendance details");
            }
            return manualReceipt(replay, "DUPLICATE");
        }

        Registration registration = registrations.findConfirmedByVerifiedIdentifierForUpdate(eventId, command.verifiedIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("No eligible confirmed participant matched this event"));
        ManualAttendanceEvidence prior = manualEvidence.findByEventIdAndRegistrationIdAndAction(eventId, registration.getId(), "ENTRY").orElse(null);
        if (prior != null) return manualReceipt(prior, "DUPLICATE");

        ManualAttendanceEvidence evidence = manualEvidence.save(ManualAttendanceEvidence.builder()
                .event(operation.getEvent()).registration(registration).operator(operator).action("ENTRY")
                .idempotencyKey(key).verifiedIdentifierSnapshot(registration.getUser().getVerifiedEnrollmentNo())
                .reasonCode(command.reasonCode()).occurredAt(Instant.now()).build());
        manualEvidence.flush();
        audit("MANUAL_ATTENDANCE_RECORDED", eventId, "evidenceId=" + evidence.getId() + ";registrationId=" + registration.getId() + ";action=ENTRY");
        return manualReceipt(evidence, "RECORDED");
    }

    /** Invoke within the same write transaction immediately before accepting a scan. */
    public void requireScanCapability(long eventId, long operatorUserId, String action) {
        EventOperations operation = requireOperationsForUpdate(eventId);
        User operator = access.currentUser();
        if (operator.getId() != operatorUserId) throw DomainAccess.denied();
        requireStoredScanCapability(operation, operator, action);
    }

    /** Re-checks a pending intent's original operator at owner-confirmation commit. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void requireStoredScanCapability(long eventId, User operator, String action) {
        EventOperations operation = requireOperationsForUpdate(eventId);
        requireStoredScanCapability(operation, operator, action);
    }

    private void requireStoredScanCapability(EventOperations operation, User operator, String action) {
        if (!operation.isScannerMasterEnabled() || "NO_QR".equals(operation.getAttendanceMode())
                || !windowActive(operation.getScannerOpensAt(), operation.getScannerClosesAt(), LocalDateTime.now())
                || !split(operation.getScannerActions()).contains(action)) throw DomainAccess.denied();
        if (operator == null || !operator.isEnabled()) throw DomainAccess.denied();
        VolunteerMembership member = memberships.findByEventIdAndUserId(operation.getEvent().getId(), operator.getId()).orElse(null);
        if (member == null || !"ACCEPTED".equals(member.getStatus())
                || !java.util.Objects.equals(member.getVerifiedIdentifier(), operator.getVerifiedEnrollmentNo())) throw DomainAccess.denied();
        if ("SELECTED".equals(operation.getScannerScope())) {
            EventOperatorGrant grant = grants.findForUpdate(operation.getEvent().getId(), operator.getId(), "QR").orElse(null);
            if (!active(grant) || !split(grant.getActions()).contains(action)) throw DomainAccess.denied();
        } else if (!"ALL".equals(operation.getScannerScope())) throw DomainAccess.denied();
    }

    public void requireManualCapability(long eventId, long operatorUserId, String action) {
        EventOperations operation = requireOperationsForUpdate(eventId);
        User operator = access.currentUser();
        if (operator.getId() != operatorUserId) throw DomainAccess.denied();
        if ("ENTRY".equals(action)) { requireManualEntryCapability(operation, operator); return; }
        if (!operation.isManualWindowOpen() || !windowActive(operation.getManualOpensAt(), operation.getManualClosesAt(), LocalDateTime.now())) throw DomainAccess.denied();
        VolunteerMembership member = memberships.findByEventIdAndUserId(eventId, operatorUserId).orElse(null);
        if (member == null || !"ACCEPTED".equals(member.getStatus())
                || !java.util.Objects.equals(member.getVerifiedIdentifier(), operator.getVerifiedEnrollmentNo())) throw DomainAccess.denied();
        EventOperatorGrant grant = grants.findForUpdate(eventId, operatorUserId, "MANUAL").orElse(null);
        if (!active(grant) || !split(grant.getActions()).contains(action)) throw DomainAccess.denied();
    }

    private void requireManualEntryCapability(EventOperations operation, User operator) {
        if (!operation.isManualWindowOpen() || !windowActive(operation.getManualOpensAt(), operation.getManualClosesAt(), LocalDateTime.now())) throw DomainAccess.denied();
        if (operator.getRole() == Role.ADMIN || access.isEventOwner(operator, operation.getEvent())) return;
        VolunteerMembership member = memberships.findByEventIdAndUserId(operation.getEvent().getId(), operator.getId()).orElse(null);
        if (member == null || !"ACCEPTED".equals(member.getStatus())
                || !java.util.Objects.equals(member.getVerifiedIdentifier(), operator.getVerifiedEnrollmentNo())) throw DomainAccess.denied();
        EventOperatorGrant grant = grants.findForUpdate(operation.getEvent().getId(), operator.getId(), "MANUAL").orElse(null);
        if (!active(grant) || !split(grant.getActions()).contains("ENTRY")) throw DomainAccess.denied();
    }

    private VolunteerMembership acceptedMember(long eventId, long userId) {
        VolunteerMembership member = memberships.findByEventIdAndUserId(eventId, userId).orElseThrow(DomainAccess::denied);
        if (!"ACCEPTED".equals(member.getStatus())
                || !java.util.Objects.equals(member.getVerifiedIdentifier(), member.getUser().getVerifiedEnrollmentNo()))
            throw new IllegalStateException("Volunteer membership is not eligible for operator access");
        return member;
    }
    private static boolean active(EventOperatorGrant grant) { return grant != null && grant.isEnabled() && (grant.getExpiresAt() == null || grant.getExpiresAt().isAfter(LocalDateTime.now())); }
    private static List<String> split(String actions) { return actions == null || actions.isBlank() ? List.of() : List.of(actions.split(",")); }
    private GrantView grantView(EventOperatorGrant grant) { return new GrantView(grant.getUser().getId(), grant.getGrantType(), split(grant.getActions()), grant.isEnabled(), grant.getEpoch(), grant.getExpiresAt()); }
    private static boolean windowActive(LocalDateTime open, LocalDateTime close, LocalDateTime now) { return open != null && close != null && !now.isBefore(open) && now.isBefore(close); }
    private ManualCheckInReceipt manualReceipt(ManualAttendanceEvidence evidence, String status) {
        return new ManualCheckInReceipt(evidence.getId(), evidence.getAction(), evidence.getVerifiedIdentifierSnapshot(),
                evidence.getRegistration().getUser().getName(), status, evidence.getOccurredAt());
    }
    private static void validateQrActions(EventOperations operation, List<String> actions) {
        if (actions.stream().anyMatch("EXIT"::equals)) throw new IllegalArgumentException("EXIT attendance evidence is not configured for this implementation");
        if (!"DUAL_QR".equals(operation.getAttendanceMode()) && actions.stream().anyMatch("ATTENDANCE_CHECKPOINT"::equals))
            throw new IllegalArgumentException("Checkpoint scanning requires DUAL_QR mode");
    }

    private EventOperations requireOperations(long eventId) {
        EventOperations operation = operations.findByEventId(eventId).orElseThrow(() -> new IllegalStateException("Approved event operations are not available"));
        if (operation.getEvent().getStatus() != EventStatus.APPROVED && operation.getEvent().getStatus() != EventStatus.COMPLETED)
            throw new IllegalStateException("Event operations are not available");
        return operation;
    }
    private EventOperations requireOperationsForUpdate(long eventId) {
        EventOperations operation = operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Approved event operations are not available"));
        if (operation.getEvent().getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Event operations are not available");
        return operation;
    }
    private static void requireVersion(EventOperations operation, long expected) {
        if (operation.getRevision() != expected) throw new IllegalStateException("Operations changed; refresh and retry");
    }
    private OperationsView view(EventOperations o) { return new OperationsView(o.getRevision(), o.getAttendanceMode(), o.getSingleQrPolicy(),
            o.isRecruitmentOpen(), o.getMaxVolunteerApplicants(), o.isScannerMasterEnabled(), o.getScannerScope(), o.getScannerEpoch(), o.getManualEpoch(),
            o.getScannerOpensAt(), o.getScannerClosesAt(), o.isManualWindowOpen(), o.getManualOpensAt(), o.getManualClosesAt(),
            o.getCertificateEvidencePolicy(), o.getCertificatePolicyVersion()); }
    private VolunteerView volunteer(VolunteerMembership m) { User u = m.getUser(); return new VolunteerView(m.getId(), u.getId(), m.getRevision(), m.getStatus(),
            m.getVerifiedIdentifier(), u.getName(), u.getDepartment(), m.getAppliedAt(), m.getDecidedAt()); }
    private void audit(String action, long eventId) { audit(action, eventId, ""); }
    private void notifyUser(User user, String message) {
        if (featureFlags.isEnabled("MODULE_NOTIFICATIONS")) notifications.createNotification(user, message, "/notifications");
    }
    private void audit(String action, long eventId, String safeDetails) {
        User u = access.currentUser();
        audit.logAction(u.getEmail(), u.getRole().name(), action, "eventId=" + eventId + ";" + safeDetails);
    }
    private static String normalizeMode(String mode) { return "SINGLE_QR".equals(mode) || "NO_QR".equals(mode) ? mode : "DUAL_QR"; }
}
