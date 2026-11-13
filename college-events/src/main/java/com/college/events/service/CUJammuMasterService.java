package com.college.events.service;

import com.college.events.model.*;
import com.college.events.dto.DepartmentViews;
import com.college.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.college.events.security.DomainAccess;

@Service
@Slf4j
public class CUJammuMasterService {

    private final DepartmentRepository departmentRepository;
    private final HostelRepository hostelRepository;
    private final HostelGatePassRepository hostelGatePassRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityBookingRepository facilityBookingRepository;
    private final DomainAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    @Autowired(required = false)
    private FeatureFlagService featureFlags;

    /**
     * Spring uses the guarded constructor.  The compatibility constructor keeps
     * focused legacy unit tests source-compatible while those tests can exercise
     * the domain policy without bootstrapping the feature-control store.
     */
    public CUJammuMasterService(DepartmentRepository departmentRepository,
                                HostelRepository hostelRepository,
                                HostelGatePassRepository hostelGatePassRepository,
                                FacilityRepository facilityRepository,
                                FacilityBookingRepository facilityBookingRepository,
                                DomainAccess access,
                                AuditService audit,
                                NotificationService notifications,
                                FeatureFlagService featureFlags) {
        this(departmentRepository, hostelRepository, hostelGatePassRepository,
                facilityRepository, facilityBookingRepository, access, audit,
                notifications);
        this.featureFlags = featureFlags;
    }

    @Autowired
    public CUJammuMasterService(DepartmentRepository departmentRepository,
                                HostelRepository hostelRepository,
                                HostelGatePassRepository hostelGatePassRepository,
                                FacilityRepository facilityRepository,
                                FacilityBookingRepository facilityBookingRepository,
                                DomainAccess access,
                                AuditService audit,
                                NotificationService notifications) {
        this.departmentRepository = departmentRepository;
        this.hostelRepository = hostelRepository;
        this.hostelGatePassRepository = hostelGatePassRepository;
        this.facilityRepository = facilityRepository;
        this.facilityBookingRepository = facilityBookingRepository;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ---- Department Operations ----
    public List<DepartmentViews.DirectoryItem> getAllActiveDepartments() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(DepartmentViews.DirectoryItem::from).toList();
    }

    @Transactional
    public DepartmentViews.DirectoryItem createDepartment(String code, String name, String building, String hod) {
        access.requireRole(Role.ADMIN);
        if (departmentRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Department with code " + code + " already exists.");
        }
        Department dept = Department.builder()
                .code(code.toUpperCase().trim())
                .name(name.trim())
                .building(building)
                .headOfDepartment(hod)
                .contactEmail(code.toLowerCase().trim() + "@cujammu.ac.in")
                .active(true)
                .build();
        return DepartmentViews.DirectoryItem.from(departmentRepository.save(dept));
    }

    // ---- Hostel Operations ----
    public List<Hostel> getAllActiveHostels() {
        requireHostelFeatureForRead();
        return hostelRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public Hostel createHostel(String name, String genderType, String curfewTime, Integer capacity, String wardenName, String wardenPhone) {
        requireHostelFeatureForCommand();
        access.requireRole(Role.ADMIN);
        if (hostelRepository.existsByName(name)) {
            throw new IllegalArgumentException("Hostel with name " + name + " already exists.");
        }
        Hostel hostel = Hostel.builder()
                .name(name.trim())
                .genderType(genderType.toUpperCase())
                .curfewTime(curfewTime != null ? curfewTime : "21:00")
                .capacity(capacity != null ? capacity : 300)
                .wardenName(wardenName)
                .wardenPhone(wardenPhone)
                .active(true)
                .build();
        return hostelRepository.save(hostel);
    }

    // ---- Hostel Gate Pass Operations ----
    @Transactional
    public HostelGatePass applyHostelGatePass(User user, Long hostelId, String passType, String destination, String reason, LocalDateTime expectedExit, LocalDateTime expectedReturn) {
        requireHostelFeatureForCommand();
        user = access.requireSelf(user.getId());
        if (user.getRole() != Role.STUDENT) throw DomainAccess.denied();
        Hostel hostel = hostelRepository.findById(hostelId)
                .orElseThrow(() -> new IllegalArgumentException("Hostel not found with ID: " + hostelId));
        if (!hostel.isActive() || !java.util.Objects.equals(user.getHostelName(), hostel.getName())) throw DomainAccess.denied();
        if (!java.util.Set.of("DAY_OUT", "NIGHT_OUT").contains(passType == null ? "" : passType)
                || destination == null || destination.isBlank() || destination.length() > 255
                || reason == null || reason.isBlank() || reason.length() > 2000
                || expectedExit == null || expectedReturn == null || !expectedReturn.isAfter(expectedExit)) {
            throw new IllegalArgumentException("Invalid pass request");
        }

        HostelGatePass pass = HostelGatePass.builder()
                .user(user)
                .hostel(hostel)
                .passType(passType)
                .destination(destination)
                .reason(reason)
                .expectedExitTime(expectedExit)
                .expectedReturnTime(expectedReturn)
                .status("PENDING")
                .build();

        return hostelGatePassRepository.save(pass);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public HostelGatePass wardenApprovePass(Long passId, String wardenEmail, boolean approve, String rejectionReason) {
        requireHostelFeatureForCommand();
        User actor = access.requireRole(Role.ADMIN, Role.WARDEN);
        if (rejectionReason != null && rejectionReason.length() > 255) throw new IllegalArgumentException("Rejection reason is too long");
        if (!approve && (rejectionReason == null || rejectionReason.isBlank())) throw new IllegalArgumentException("Rejection reason is required");
        HostelGatePass pass = hostelGatePassRepository.findForDecision(passId)
                .orElseThrow(() -> new IllegalArgumentException("Pass not found: " + passId));
        requireHostelOperator(actor, pass.getHostel());
        if (actor.getId().equals(pass.getUser().getId())) throw DomainAccess.denied();
        String desired = approve ? "APPROVED" : "REJECTED";
        if (desired.equals(pass.getStatus()) && actor.getEmail().equals(pass.getWardenApprovedBy())) return pass;
        if (!"PENDING".equals(pass.getStatus())) throw new IllegalStateException("Pass decision is already final");
        if (pass.getExpectedReturnTime().isBefore(campusNow())) throw new IllegalStateException("Pass request has expired");
        pass.setWardenApprovedBy(actor.getEmail());

        if (approve) {
            pass.setStatus("APPROVED");
        } else {
            pass.setStatus("REJECTED");
            pass.setRejectionReason(rejectionReason);
        }
        HostelGatePass saved = hostelGatePassRepository.save(pass);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "HOSTEL_GATE_PASS_" + desired,
                "passId=" + saved.getId() + ";hostelId=" + pass.getHostel().getId());
        notifications.createNotification(pass.getUser(),
                "Your hostel gate-pass request was " + (approve ? "approved" : "not approved") + ".", "/app/");
        return saved;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public HostelGatePass recordGateScan(String qrToken, boolean isExit) {
        requireHostelFeatureForCommand();
        User actor = access.requireRole(Role.ADMIN, Role.WARDEN, Role.STAFF);
        if (qrToken == null || qrToken.isBlank() || qrToken.length() > 64) throw new IllegalArgumentException("Invalid gate pass");
        HostelGatePass pass = hostelGatePassRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Gate-Pass QR Token"));
        requireHostelOperator(actor, pass.getHostel());

        LocalDateTime now = campusNow();
        if (isExit) {
            if ("ACTIVE_OUT".equals(pass.getStatus())) return pass;
            if (!"APPROVED".equals(pass.getStatus())) throw new IllegalStateException("Departure requires an approved pass");
            if (pass.getExpectedExitTime() == null || now.isBefore(pass.getExpectedExitTime())) throw new IllegalStateException("Departure window has not opened");
            if (now.isAfter(pass.getExpectedReturnTime())) throw new IllegalStateException("Pass has expired");
            pass.setActualExitTime(now);
            pass.setStatus("ACTIVE_OUT");
        } else {
            if ("RETURNED_ON_TIME".equals(pass.getStatus()) || "LATE_ENTRY".equals(pass.getStatus())) return pass;
            if (!"ACTIVE_OUT".equals(pass.getStatus())) throw new IllegalStateException("Return requires a recorded departure");
            pass.setActualReturnTime(now);
            // Check curfew / late entry
            if (now.isAfter(pass.getExpectedReturnTime())) {
                pass.setStatus("LATE_ENTRY");
            } else {
                pass.setStatus("RETURNED_ON_TIME");
            }
        }
        return hostelGatePassRepository.save(pass);
    }

    private void requireHostelOperator(User actor, Hostel hostel) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.WARDEN && actor.getEmail().equalsIgnoreCase(
                hostel.getWardenEmail() == null ? "" : hostel.getWardenEmail())) return;
        if (actor.getRole() == Role.STAFF && java.util.Objects.equals(actor.getHostelName(), hostel.getName())) return;
        throw DomainAccess.denied();
    }

    private LocalDateTime campusNow() { return LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")); }

    @Transactional(readOnly = true)
    public List<com.college.events.dto.HostelPassViews.OwnPass> ownGatePasses() {
        requireHostelFeatureForRead();
        User actor = access.currentUser();
        return hostelGatePassRepository.findByUserOrderByCreatedAtDesc(actor).stream()
                .map(com.college.events.dto.HostelPassViews.OwnPass::from).toList();
    }

    @Transactional(readOnly = true)
    public List<com.college.events.dto.HostelPassViews.PendingPass> pendingGatePasses(Long hostelId) {
        requireHostelFeatureForRead();
        User actor = access.requireRole(Role.ADMIN, Role.WARDEN, Role.STAFF);
        Hostel hostel = hostelRepository.findById(hostelId).orElseThrow(() -> new IllegalArgumentException("Hostel not found"));
        requireHostelOperator(actor, hostel);
        return hostelGatePassRepository.findByHostelIdAndStatusOrderByCreatedAtAsc(hostelId, "PENDING").stream()
                .map(com.college.events.dto.HostelPassViews.PendingPass::from).toList();
    }

    private void requireHostelFeatureForCommand() {
        if (featureFlags != null) featureFlags.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
    }

    private void requireHostelFeatureForRead() {
        if (featureFlags != null && !featureFlags.isFeatureEnabled("MODULE_HOSTEL_GATEPASS")) {
            throw new IllegalStateException("Required feature is disabled");
        }
    }

    // ---- Facility & Venue Booking with Conflict Checking ----
    public List<com.college.events.dto.FacilityViews.CatalogItem> getAllActiveFacilities() {
        requireVenueFeatureForRead();
        return facilityRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(com.college.events.dto.FacilityViews.CatalogItem::from).toList();
    }

    @Transactional(readOnly = true)
    public com.college.events.dto.FacilityBookingReviewDtos.PendingPage pendingFacilityBookings(int page) {
        requireVenueFeatureForRead();
        access.requireRole(Role.ADMIN);
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid review page");
        org.springframework.data.domain.Slice<FacilityBooking> result = facilityBookingRepository
                .findByStatusOrderByCreatedAtAsc("PENDING", org.springframework.data.domain.PageRequest.of(page, 50));
        List<com.college.events.dto.FacilityBookingReviewDtos.Pending> items = result.getContent().stream()
                .map(booking -> new com.college.events.dto.FacilityBookingReviewDtos.Pending(
                        booking.getId(), booking.getVersion(), booking.getFacility().getId(),
                        booking.getFacility().getName(), booking.getTitle(),
                        booking.getBookedBy().getName() == null ? "" : booking.getBookedBy().getName(),
                        booking.getDepartment() == null ? "" : booking.getDepartment(), booking.getStartTime(),
                        booking.getEndTime(), booking.getRequestedMics(), booking.isRequestedProjector()))
                .toList();
        return new com.college.events.dto.FacilityBookingReviewDtos.PendingPage(items, page, result.hasNext());
    }

    @Transactional(readOnly = true)
    public com.college.events.dto.FacilityBookingReviewDtos.MinePage myFacilityBookings(int page) {
        requireVenueFeatureForRead();
        User actor = access.currentUser();
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid page");
        var result = facilityBookingRepository.findMine(actor.getId(),
                org.springframework.data.domain.PageRequest.of(page, 30));
        List<com.college.events.dto.FacilityBookingReviewDtos.Mine> items = result.getContent().stream()
                .map(booking -> new com.college.events.dto.FacilityBookingReviewDtos.Mine(
                        booking.getId(), booking.getVersion(), booking.getFacility().getId(),
                        booking.getFacility().getName(), booking.getTitle(), booking.getStatus(),
                        booking.getStartTime(), booking.getEndTime()))
                .toList();
        return new com.college.events.dto.FacilityBookingReviewDtos.MinePage(items, page, result.hasNext());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.college.events.dto.FacilityBookingReviewDtos.Receipt decideFacilityBooking(
            Long bookingId, long expectedVersion, String decision, String reason) {
        requireVenueFeatureForCommand();
        User reviewer = access.requireRole(Role.ADMIN);
        Long facilityId = facilityBookingRepository.findFacilityIdByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking request unavailable"));
        Facility lockedFacility = facilityRepository.findForBooking(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility unavailable"));
        FacilityBooking booking = facilityBookingRepository.findForReview(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking request unavailable"));
        if (booking.getVersion() != expectedVersion) throw new IllegalStateException("Booking request changed; refresh the review list");
        if (!"PENDING".equals(booking.getStatus())) throw new IllegalStateException("Booking request is already final");
        String outcome = decision == null ? "" : decision.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("APPROVE", "REJECT").contains(outcome)) throw new IllegalArgumentException("Choose approve or reject");
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.length() > 300 || ("REJECT".equals(outcome) && safeReason.isBlank())) {
            throw new IllegalArgumentException("A short reason is required when rejecting a booking");
        }
        if ("APPROVE".equals(outcome)) {
            if (!lockedFacility.isActive()) throw new IllegalStateException("Facility is unavailable");
            if (lockedFacility.isUnderMaintenance()) throw new IllegalStateException("Facility is under maintenance closure");
            List<FacilityBooking> conflicts = facilityBookingRepository.findConflictingBookingsExcept(
                    lockedFacility.getId(), booking.getStartTime(), booking.getEndTime(), booking.getId());
            if (!conflicts.isEmpty()) throw new IllegalStateException("The requested time now conflicts with another reservation");
            booking.setStatus("CONFIRMED");
        } else {
            booking.setStatus("REJECTED");
        }
        booking.setReviewedBy(reviewer);
        booking.setReviewedAt(campusNow());
        booking.setReviewReason(safeReason.isBlank() ? null : safeReason);
        FacilityBooking saved = facilityBookingRepository.save(booking);
        audit.logAction(reviewer.getEmail(), reviewer.getRole().name(), "FACILITY_BOOKING_" + outcome,
                "bookingId=" + saved.getId() + ";facilityId=" + lockedFacility.getId() + ";reason=" + safeReason);
        notifications.createNotification(saved.getBookedBy(),
                "Your reservation request for " + lockedFacility.getName() + " was "
                        + ("APPROVE".equals(outcome) ? "approved" : "not approved") + ".",
                "/venues");
        return new com.college.events.dto.FacilityBookingReviewDtos.Receipt(saved.getId(), lockedFacility.getId(),
                saved.getVersion(), saved.getStatus(), saved.getStartTime(), saved.getEndTime());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.college.events.dto.FacilityBookingReviewDtos.Receipt cancelFacilityBooking(
            Long bookingId, long expectedVersion, String reason) {
        requireVenueFeatureForCommand();
        User actor = access.currentUser();
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.isBlank() || safeReason.length() > 300) {
            throw new IllegalArgumentException("A short cancellation reason is required");
        }
        Long facilityId = facilityBookingRepository.findFacilityIdByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking request unavailable"));
        Facility lockedFacility = facilityRepository.findForBooking(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility unavailable"));
        FacilityBooking booking = facilityBookingRepository.findForReview(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking request unavailable"));
        if (!java.util.Objects.equals(actor.getId(), booking.getBookedBy().getId())) {
            throw DomainAccess.denied();
        }
        if (!"PENDING".equals(booking.getStatus()) && booking.getReviewedBy() != null
                && java.util.Objects.equals(actor.getId(), booking.getReviewedBy().getId())
                && safeReason.equals(booking.getReviewReason())) {
            return new com.college.events.dto.FacilityBookingReviewDtos.Receipt(booking.getId(), lockedFacility.getId(),
                    booking.getVersion(), booking.getStatus(), booking.getStartTime(), booking.getEndTime());
        }
        if (booking.getVersion() != expectedVersion) throw new IllegalStateException("Booking request changed; refresh and retry");
        if (!"PENDING".equals(booking.getStatus())) {
            throw new IllegalStateException("Only a pending request can be cancelled here");
        }
        booking.setStatus("CANCELLED");
        booking.setReviewedAt(campusNow());
        booking.setReviewedBy(actor);
        booking.setReviewReason(safeReason);
        FacilityBooking saved = facilityBookingRepository.save(booking);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "FACILITY_BOOKING_CANCELLED",
                "bookingId=" + saved.getId() + ";facilityId=" + lockedFacility.getId() + ";reason=" + safeReason);
        return new com.college.events.dto.FacilityBookingReviewDtos.Receipt(saved.getId(), lockedFacility.getId(),
                saved.getVersion(), saved.getStatus(), saved.getStartTime(), saved.getEndTime());
    }

    public List<FacilityBooking> checkFacilityConflicts(Long facilityId, LocalDateTime start, LocalDateTime end) {
        requireVenueFeatureForRead();
        access.currentUser();
        validateInterval(start, end);
        return facilityBookingRepository.findConflictingBookings(facilityId, start, end);
    }

    @Transactional(readOnly = true)
    public com.college.events.dto.FacilityAvailabilityDtos.Suggestions suggestFacilitySlots(
            Long facilityId, int durationMinutes, int days) {
        requireVenueFeatureForRead();
        access.currentUser();
        if (durationMinutes < 15 || durationMinutes > 720 || days < 1 || days > 30) {
            throw new IllegalArgumentException("Duration must be 15-720 minutes and search range 1-30 days");
        }
        Facility facility = facilityRepository.findById(facilityId)
                .filter(Facility::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Facility unavailable"));
        if (facility.isUnderMaintenance()) {
            return new com.college.events.dto.FacilityAvailabilityDtos.Suggestions(
                    facility.getId(), facility.getName(), durationMinutes, LocalDateTime.now(), LocalDateTime.now(),
                    LocalDateTime.now(), "MAINTENANCE", List.of("Maintenance closure"),
                    List.of("Office and operator availability"),
                    List.<com.college.events.dto.FacilityAvailabilityDtos.Slot>of());
        }
        LocalDateTime computedAt = campusNow();
        LocalDateTime from = ceilToQuarterHour(computedAt);
        LocalDateTime until = from.plusDays(days);
        List<FacilityBooking> occupied = facilityBookingRepository
                .findConflictingBookings(facilityId, from, until).stream()
                .sorted(java.util.Comparator.comparing(FacilityBooking::getStartTime)
                        .thenComparing(FacilityBooking::getEndTime))
                .toList();
        List<com.college.events.dto.FacilityAvailabilityDtos.Slot> slots = new java.util.ArrayList<>();
        LocalDateTime cursor = from;
        for (FacilityBooking booking : occupied) {
            LocalDateTime bookingStart = booking.getStartTime().isBefore(from) ? from : booking.getStartTime();
            addAdvisorySlots(slots, cursor, bookingStart, durationMinutes, until);
            if (booking.getEndTime().isAfter(cursor)) cursor = booking.getEndTime();
            if (slots.size() >= 20) break;
        }
        if (slots.size() < 20) addAdvisorySlots(slots, cursor, until, durationMinutes, until);
        return new com.college.events.dto.FacilityAvailabilityDtos.Suggestions(facility.getId(),
                facility.getName(), durationMinutes, from, until, computedAt, "ADVISORY_ONLY",
                List.of("Known active facility-booking overlaps"),
                List.of("Opening hours", "Maintenance closures", "Staff and equipment availability", "Event-specific capacity and suitability"),
                List.copyOf(slots));
    }

    private static LocalDateTime ceilToQuarterHour(LocalDateTime value) {
        LocalDateTime minute = value.withSecond(0).withNano(0);
        int remainder = minute.getMinute() % 15;
        return remainder == 0 ? minute : minute.plusMinutes(15 - remainder);
    }

    private static void addAdvisorySlots(List<com.college.events.dto.FacilityAvailabilityDtos.Slot> target,
                                         LocalDateTime gapStart, LocalDateTime gapEnd,
                                         int durationMinutes, LocalDateTime searchEnd) {
        LocalDateTime candidate = ceilToQuarterHour(gapStart);
        while (target.size() < 20 && !candidate.plusMinutes(durationMinutes).isAfter(gapEnd)
                && !candidate.plusMinutes(durationMinutes).isAfter(searchEnd)) {
            target.add(new com.college.events.dto.FacilityAvailabilityDtos.Slot(candidate,
                    candidate.plusMinutes(durationMinutes)));
            candidate = candidate.plusMinutes(15);
        }
    }

    /** Legacy callers may supply priority; it never confers reservation authority. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FacilityBooking bookFacility(Facility facility, Event event, User bookedBy, String title,
                                        LocalDateTime start, LocalDateTime end, int ignoredPriority,
                                        int mics, boolean projector) {
        return bookFacility(facility, event, bookedBy, title, start, end, mics, projector, null);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FacilityBooking bookFacility(Facility facility, Event event, User bookedBy, String title,
                                        LocalDateTime start, LocalDateTime end, int mics,
                                        boolean projector, String requestKey) {
        requireVenueFeatureForCommand();
        User actor = access.requireRole(Role.ADMIN, Role.ORGANIZER, Role.FACULTY);
        if (!actor.getId().equals(bookedBy.getId())) throw DomainAccess.denied();
        if (event != null) access.requireEventManager(event.getId());
        validateInterval(start, end);
        if (title == null || title.isBlank() || title.length() > 200 || mics < 0 || mics > 100) {
            throw new IllegalArgumentException("Invalid booking details");
        }
        String safeTitle = title.trim();
        String requestDigest = null;
        if (requestKey != null) {
            if (!requestKey.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
                throw new IllegalArgumentException("Invalid booking request key");
            }
            requestDigest = bookingDigest(facility.getId(), event == null ? null : event.getId(), safeTitle, start, end, mics, projector);
            FacilityBooking previous = facilityBookingRepository.findByRequestKey(requestKey).orElse(null);
            if (previous != null) return replayBooking(previous, actor, requestDigest);
        }
        // Every writer for a facility must acquire this row before inspecting overlaps.
        Facility locked = facilityRepository.findForBooking(facility.getId())
                .filter(Facility::isActive).orElseThrow(() -> new IllegalArgumentException("Facility unavailable"));
        if (locked.isUnderMaintenance()) throw new IllegalStateException("Facility is under maintenance closure");
        if (requestKey != null) {
            FacilityBooking previous = facilityBookingRepository.findByRequestKey(requestKey).orElse(null);
            if (previous != null) return replayBooking(previous, actor, requestDigest);
        }
        List<FacilityBooking> conflicts = facilityBookingRepository.findConflictingBookings(locked.getId(), start, end);

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Venue Conflict: " + locked.getName() + " is already booked.");
        }

        FacilityBooking booking = FacilityBooking.builder()
                .facility(locked)
                .event(event)
                .bookedBy(actor)
                .department(actor.getDepartment())
                .title(safeTitle)
                .startTime(start)
                .endTime(end)
                .status("PENDING")
                .priorityLevel(10)
                .requestedMics(mics)
                .requestedProjector(projector)
                .requestKey(requestKey)
                .requestDigest(requestDigest)
                .build();

        FacilityBooking saved = facilityBookingRepository.save(booking);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "FACILITY_BOOKING_REQUESTED",
                "bookingId=" + saved.getId() + ";facilityId=" + locked.getId() + ";start=" + start
                        + ";end=" + end + ";requestedMics=" + mics + ";projector=" + projector);
        return saved;
    }

    private static FacilityBooking replayBooking(FacilityBooking previous, User actor, String requestDigest) {
        if (previous.getBookedBy() == null || !previous.getBookedBy().getId().equals(actor.getId())
                || !java.util.Objects.equals(previous.getRequestDigest(), requestDigest)) {
            throw new IllegalStateException("Booking request key was already used for another command");
        }
        return previous;
    }

    private static String bookingDigest(Long facilityId, Long eventId, String title, LocalDateTime start, LocalDateTime end,
                                        int mics, boolean projector) {
        String command = facilityId + "\n" + eventId + "\n" + start + "\n" + end + "\n" + mics + "\n" + projector
                + "\n" + title.length() + ":" + title;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(command.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest algorithm unavailable", impossible);
        }
    }

    private void requireVenueFeatureForCommand() {
        if (featureFlags != null) featureFlags.requireEnabledForUpdate("MODULE_VENUES");
    }

    private void requireVenueFeatureForRead() {
        if (featureFlags != null && !featureFlags.isFeatureEnabled("MODULE_VENUES")) {
            throw new IllegalStateException("Required feature is disabled");
        }
    }

    private void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)
                || java.time.Duration.between(start, end).compareTo(java.time.Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Booking must have a valid interval of at most seven days");
        }
    }
}
