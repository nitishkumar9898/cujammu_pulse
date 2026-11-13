package com.college.events.controller;

import com.college.events.model.*;
import com.college.events.dto.FacilityBookingCommand;
import com.college.events.dto.FeedDtos;
import com.college.events.repository.FacilityRepository;
import com.college.events.repository.UserRepository;
import com.college.events.repository.HostelDeviceRepository;
import com.college.events.service.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cujammu")
@RequiredArgsConstructor
@Slf4j
public class CUJammuApiController {

    private final CUJammuMasterService cuJammuMasterService;
    private final CoEAcademicService coEAcademicService;
    private final AcademicBatchPreviewService academicBatchPreviewService;
    private final CommunityChatService communityChatService;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;

    private final EventTeamService eventTeamService;
    private final EventJudgingService eventJudgingService;
    private final VolunteerService volunteerService;
    private final LostAndFoundService lostAndFoundService;
    private final TournamentBracketService tournamentBracketService;

    private final FeatureFlagService featureFlagService;
    private final PulseFeedService pulseFeedService;
    private final LiveSportsScoreService liveSportsScoreService;
    private final HostelGateCredentialService hostelGateCredentialService;
    private final HostelDeviceRepository deviceRepository;
    private final DynamicFormService dynamicFormService;
    private final LibraryTurnstileService libraryTurnstileService;
    private final com.college.events.security.DomainAccess access;

    // ---- 1. Departments API ----
    @GetMapping("/departments")
    public ResponseEntity<List<com.college.events.dto.DepartmentViews.DirectoryItem>> getDepartments() {
        return ResponseEntity.ok(cuJammuMasterService.getAllActiveDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(@RequestBody Map<String, String> body) {
        var dept = cuJammuMasterService.createDepartment(
                body.get("code"), body.get("name"), body.get("building"), body.get("hod"));
        return ResponseEntity.ok(dept);
    }

    // ---- 2. Hostels & Gate Pass API ----
    @GetMapping("/hostels")
    public ResponseEntity<List<com.college.events.dto.HostelPassViews.Hostel>> getHostels() {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok(cuJammuMasterService.getAllActiveHostels().stream()
                .map(com.college.events.dto.HostelPassViews.Hostel::from).toList());
    }

    @PostMapping("/hostels")
    public ResponseEntity<?> createHostel(@RequestBody Map<String, Object> body) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        Hostel hostel = cuJammuMasterService.createHostel(
                (String) body.get("name"),
                (String) body.get("genderType"),
                (String) body.get("curfewTime"),
                body.get("capacity") != null ? ((Number) body.get("capacity")).intValue() : 300,
                (String) body.get("wardenName"),
                (String) body.get("wardenPhone")
        );
        return ResponseEntity.ok(hostel);
    }

    @PostMapping("/hostels/gate-pass/apply")
    public ResponseEntity<?> applyGatePass(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody com.college.events.dto.HostelPassViews.Apply body) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        User user = getUser(userDetails);
        HostelGatePass pass = cuJammuMasterService.applyHostelGatePass(
                user, body.hostelId(), body.passType(), body.destination(), body.reason(), body.expectedExitTime(), body.expectedReturnTime());
        return ResponseEntity.ok(com.college.events.dto.HostelPassViews.OwnPass.from(pass));
    }

    @GetMapping("/hostels/gate-pass/my")
    public ResponseEntity<?> ownGatePasses() {
        featureFlagService.requireEnabledForRead("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(cuJammuMasterService.ownGatePasses());
    }

    @PostMapping("/hostels/gate-pass/approve")
    public ResponseEntity<?> approveGatePass(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody com.college.events.dto.HostelPassViews.Decision body) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        Long passId = body.passId();
        boolean approve = body.approve();
        String reason = body.rejectionReason();

        HostelGatePass pass = cuJammuMasterService.wardenApprovePass(
                passId, userDetails.getUsername(), approve, reason);
        return ResponseEntity.ok(Map.of("id", pass.getId(), "status", pass.getStatus()));
    }

    @GetMapping("/hostels/gate-pass/pending")
    public ResponseEntity<?> pendingGatePasses(@RequestParam Long hostelId) {
        featureFlagService.requireEnabledForRead("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(cuJammuMasterService.pendingGatePasses(hostelId));
    }

    @PostMapping("/hostels/gate-pass/scan")
    public ResponseEntity<?> scanGatePass(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "HOSTEL_CREDENTIAL_VERIFICATION_UNAVAILABLE"));
    }

    @PostMapping("/hostels/gate-pass/{passId}/credential")
    public ResponseEntity<?> issueHostelCredential(@PathVariable Long passId) {
        featureFlagService.requireEnabledForRead("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(hostelGateCredentialService.issueCurrentCredential(passId));
    }

    @PostMapping("/hostels/gate-pass/scan-intents")
    public ResponseEntity<?> createHostelScanIntent(@Valid @RequestBody com.college.events.dto.HostelPassViews.ScanIntent command) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(hostelGateCredentialService.createScanIntent(command.credential(), command.requestKey()));
    }

    @GetMapping("/hostels/gate-pass/{passId}/scan-intents/pending")
    public ResponseEntity<?> pendingHostelScanIntents(@PathVariable Long passId) {
        featureFlagService.requireEnabledForRead("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(hostelGateCredentialService.pendingIntent(passId));
    }

    @PostMapping("/hostels/gate-pass/scan-intents/confirm")
    public ResponseEntity<?> confirmHostelScan(@RequestBody com.college.events.dto.HostelPassViews.ConfirmScan command) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(hostelGateCredentialService.confirmScan(command));
    }

    // ---- Device registration ----
    @PostMapping("/hostels/devices/register")
    public ResponseEntity<?> registerDevice(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody com.college.events.dto.HostelPassViews.DeviceRegister body) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        getUser(userDetails);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "DEVICE_PROVISIONING_UNAVAILABLE",
                "error", "Scoped gate device provisioning requires an approved operator assignment."));
    }

    @PutMapping("/hostels/devices/revoke")
    public ResponseEntity<?> revokeDevice(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody com.college.events.dto.HostelPassViews.DeviceRevoke body) {
        featureFlagService.requireEnabledForUpdate("MODULE_HOSTEL_GATEPASS");
        getUser(userDetails);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "DEVICE_PROVISIONING_UNAVAILABLE",
                "error", "Scoped gate device provisioning requires an approved operator assignment."));
    }

    // ---- 3. Facilities & Conflict-Free Venue Booking API ----
    @GetMapping("/facilities")
    public ResponseEntity<List<com.college.events.dto.FacilityViews.CatalogItem>> getFacilities() {
        return ResponseEntity.ok(cuJammuMasterService.getAllActiveFacilities());
    }

    @GetMapping("/facilities/{id}/conflicts")
    public ResponseEntity<?> checkConflicts(@PathVariable Long id,
                                            @RequestParam String startTime,
                                            @RequestParam String endTime) {
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = LocalDateTime.parse(endTime);
        List<FacilityBooking> conflicts = cuJammuMasterService.checkFacilityConflicts(id, start, end);
        return ResponseEntity.ok(Map.of(
                "hasConflict", !conflicts.isEmpty(),
                "conflictCount", conflicts.size()
        ));
    }

    @GetMapping("/facilities/bookings/pending")
    public ResponseEntity<com.college.events.dto.FacilityBookingReviewDtos.PendingPage> pendingFacilityBookings(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(cuJammuMasterService.pendingFacilityBookings(page));
    }

    @GetMapping("/facilities/bookings/mine")
    public ResponseEntity<com.college.events.dto.FacilityBookingReviewDtos.MinePage> myFacilityBookings(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(cuJammuMasterService.myFacilityBookings(page));
    }

    @PutMapping("/facilities/bookings/{bookingId}/decision")
    public ResponseEntity<?> decideFacilityBooking(@PathVariable Long bookingId,
            @Valid @RequestBody com.college.events.dto.FacilityBookingReviewDtos.Decision command) {
        try {
            return ResponseEntity.ok(cuJammuMasterService.decideFacilityBooking(bookingId,
                    command.expectedVersion(), command.decision(), command.reason()));
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", conflict.getMessage()));
        }
    }

    @PutMapping("/facilities/bookings/{bookingId}/cancel")
    public ResponseEntity<?> cancelFacilityBooking(@PathVariable Long bookingId,
            @Valid @RequestBody com.college.events.dto.FacilityBookingReviewDtos.Cancel command) {
        try {
            return ResponseEntity.ok(cuJammuMasterService.cancelFacilityBooking(bookingId,
                    command.expectedVersion(), command.reason()));
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", conflict.getMessage()));
        }
    }

    @GetMapping("/facilities/{id}/availability-suggestions")
    public ResponseEntity<com.college.events.dto.FacilityAvailabilityDtos.Suggestions> suggestFacilitySlots(
            @PathVariable Long id,
            @RequestParam(defaultValue = "60") int durationMinutes,
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(cuJammuMasterService.suggestFacilitySlots(id, durationMinutes, days));
    }

    @PostMapping("/facilities/{id}/book")
    public ResponseEntity<?> bookFacility(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          @Valid @RequestBody FacilityBookingCommand body) {
        User user = getUser(userDetails);
        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found"));

        try {
            FacilityBooking booking = cuJammuMasterService.bookFacility(
                    facility, null, user, body.title(), body.startTime(), body.endTime(),
                    body.requestedMics(), body.requestedProjector(), body.requestKey());
            return ResponseEntity.ok(Map.of("id", booking.getId(), "facilityId", booking.getFacility().getId(),
                    "version", booking.getVersion(), "status", booking.getStatus(),
                    "startTime", booking.getStartTime(), "endTime", booking.getEndTime()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    // ---- 4. Library Digital Turnstile (Dual QR) API ----
    @PostMapping("/library/scan/entry")
    public ResponseEntity<?> libraryScanEntry(@RequestBody(required = false) Map<String, Object> body) {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        return unavailableLibraryWorkflow();
    }

    @PostMapping("/library/scan/exit")
    public ResponseEntity<?> libraryScanExit(@RequestBody(required = false) Map<String, Object> body) {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        return unavailableLibraryWorkflow();
    }

    @GetMapping("/library/active")
    public ResponseEntity<?> getActiveLibraryOccupants() {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        access.currentUser();
        return unavailableLibraryWorkflow();
    }

@GetMapping("/library/history")
    public ResponseEntity<?> getOwnLibraryHistory(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(libraryTurnstileService.getOwnHistory(access.currentUser(), page, size).stream()
                .map(log -> {
                    Map<String, Object> view = new java.util.LinkedHashMap<>();
                    view.put("id", log.getId()); view.put("sessionType", log.getSessionType());
                    view.put("entryTime", log.getEntryTime()); view.put("exitTime", log.getExitTime());
                    view.put("durationSeconds", log.getDurationSeconds()); view.put("durationFormatted", log.getDurationFormatted());
                    view.put("status", log.getStatus()); view.put("mismatchAttemptCount", log.getMismatchAttemptCount());
                    return view;
                }).toList());
    }

    @GetMapping("/library/export-csv")
    public ResponseEntity<?> exportLibraryCsv(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        // Role-only access is not sufficiently scoped for institution-wide visitor data.
        // Keep exports closed until durable report snapshots and assigned-library grants exist.
        return unavailableLibraryWorkflow();
    }

    @GetMapping("/library/export-xlsx")
    public ResponseEntity<?> exportLibraryXlsx(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        featureFlagService.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        return unavailableLibraryWorkflow();
    }

    private static ResponseEntity<Map<String, String>> unavailableLibraryWorkflow() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("code", "LIBRARY_VERIFICATION_UNAVAILABLE",
                        "error", "Verified library QR challenges and scoped reporting are not configured."));
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    // ---- 5. Event Dual-QR Attendance API ----
    @PostMapping("/events/{id}/attendance/entry")
    public ResponseEntity<?> recordEventEntry(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Legacy event scans are disabled; use the verified event attendance workflow."));
    }

    @PostMapping("/events/{id}/attendance/exit")
    public ResponseEntity<?> recordEventExit(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Legacy event scans are disabled; exit evidence is not enabled for this workflow."));
    }

    @GetMapping("/events/{id}/attendance/export-csv")
    public ResponseEntity<byte[]> exportEventAttendanceCsv(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    // ---- 6. CoE Exam Timetables & Score Cards API ----
    @GetMapping("/exams/schedules")
    public ResponseEntity<?> getExamSchedules(@RequestParam(required = false) String department) {
        if (department != null && !department.isBlank()) {
            return ResponseEntity.ok(coEAcademicService.getSchedulesByDepartment(department));
        }
        return ResponseEntity.ok(coEAcademicService.getAllExamSchedules());
    }

    @PostMapping("/exams/schedules")
    public ResponseEntity<?> publishExamSchedule(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestBody Map<String, String> body) {
        ExamSchedule schedule = coEAcademicService.publishExamSchedule(
                body.get("title"), body.get("departmentName"), body.get("programName"),
                body.get("semester"), body.get("academicYear"), body.get("pdfUrl"), userDetails.getUsername()
        );
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/exams/scorecards/distribute")
    public ResponseEntity<?> distributeScoreCard(@Valid @RequestBody com.college.events.dto.AcademicDtos.SubmitScoreCard body) {
        CoEAcademicService.SubmissionReceipt card = coEAcademicService.distributeScoreCard(
                body.verifiedIdentifier(), body.program(), body.semester(), body.academicYear(),
                body.sgpa(), body.cgpa(), body.resultStatus(), body.pdfUrl(), body.requestKey(), body.correctionReason()
        );
        return ResponseEntity.ok(card);
    }

    @PostMapping("/exams/scorecards/batch/preview")
    public ResponseEntity<?> previewScoreCardBatch(@Valid @RequestBody com.college.events.dto.AcademicDtos.BatchPreviewCommand body) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(academicBatchPreviewService.preview(body.department(), body.csvContent()));
    }

    @PostMapping("/exams/scorecards/batch/submit")
    public ResponseEntity<?> submitScoreCardBatch(@Valid @RequestBody com.college.events.dto.AcademicDtos.BatchSubmitCommand body) {
        return ResponseEntity.ok(academicBatchPreviewService.submit(body.department(), body.csvContent(), body.requestKey()));
    }

    @GetMapping("/exams/scorecards/batches/review")
    public ResponseEntity<?> pendingScoreCardBatches(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(academicBatchPreviewService.pendingReviewBatches(page));
    }

    @GetMapping("/exams/scorecards/batches/{id}/review")
    public ResponseEntity<?> scoreCardBatchForReview(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(academicBatchPreviewService.batchForReview(id));
    }

    @PostMapping("/exams/scorecards/batches/{id}/review")
    public ResponseEntity<?> reviewScoreCardBatch(@PathVariable Long id,
            @Valid @RequestBody com.college.events.dto.AcademicDtos.ReviewBatchCommand body) {
        return ResponseEntity.ok(academicBatchPreviewService.reviewBatch(id, body.publish(), body.expectedVersion(), body.reason()));
    }

    @GetMapping("/exams/scorecards/review")
    public ResponseEntity<?> pendingScoreCards(@RequestParam String department, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(coEAcademicService.pendingReviews(department, page));
    }

    @GetMapping("/exams/scorecards/review-scopes")
    public ResponseEntity<?> myAcademicReviewScopes() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(coEAcademicService.myReviewDepartments());
    }

    @GetMapping("/exams/scorecards/publish-scopes")
    public ResponseEntity<?> myAcademicPublishScopes() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(coEAcademicService.myPublishDepartments());
    }

    @PostMapping("/exams/scorecards/{id}/review")
    public ResponseEntity<?> reviewScoreCard(@PathVariable Long id,
                                              @Valid @RequestBody com.college.events.dto.AcademicDtos.ReviewScoreCard body) {
        return ResponseEntity.ok(coEAcademicService.reviewScoreCard(id, body.publish(), body.reason()));
    }

    @GetMapping("/exams/scorecards/my")
    public ResponseEntity<?> getMyScoreCards(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(coEAcademicService.getMyScoreCards());
    }

    // ---- 7. Community & Event Chat API ----
    @GetMapping("/chat/groups")
    public ResponseEntity<com.college.events.dto.CommunityChatDtos.Groups> getChatGroups() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(communityChatService.getAllActiveGroups());
    }

    @GetMapping("/chat/groups/page")
    public ResponseEntity<com.college.events.dto.CommunityChatDtos.GroupPage> getChatGroupPage(@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(communityChatService.groupPage(page));
    }

    @GetMapping("/chat/groups/{id}/messages")
    public ResponseEntity<com.college.events.dto.CommunityChatDtos.Messages> getGroupMessages(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(communityChatService.getGroupMessages(id));
    }

    @PostMapping("/chat/groups/{id}/messages")
    public ResponseEntity<?> postMessage(@PathVariable Long id,
                                         @Valid @RequestBody com.college.events.dto.CommunityChatDtos.Post body) {
        var msg = communityChatService.postMessage(id, body.content(), body.requestKey());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(msg);
    }

    @DeleteMapping("/chat/events/{id}/cleanup")
    public ResponseEntity<?> cleanupEventGroup(@PathVariable Long id) {
        communityChatService.deleteEventGroup(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Ephemeral event group successfully purged."));
    }

    // ---- 8. Team Registration & Roster API ----
    @GetMapping("/events/{id}/teams/mine")
    public ResponseEntity<com.college.events.dto.TeamDtos.Teams> ownTeams(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(eventTeamService.ownTeams(id));
    }
    @GetMapping("/teams/eligible-events")
    public ResponseEntity<com.college.events.dto.TeamDtos.EligibleEvents> eligibleTeamEvents() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(eventTeamService.eligibleEvents());
    }
    @GetMapping("/teams/mine")
    public ResponseEntity<com.college.events.dto.TeamDtos.History> ownTeamHistory(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(eventTeamService.ownTeamHistory(page));
    }
    @PostMapping("/events/{id}/teams/create")
    public ResponseEntity<com.college.events.dto.TeamDtos.TeamView> createTeam(@PathVariable Long id,
                                        @Valid @RequestBody com.college.events.dto.TeamDtos.Create body) {
        return ResponseEntity.ok(eventTeamService.createTeam(id, body));
    }

    @PostMapping("/teams/join")
    public ResponseEntity<com.college.events.dto.TeamDtos.TeamView> joinTeam(
                                      @Valid @RequestBody com.college.events.dto.TeamDtos.Join body) {
        return ResponseEntity.ok(eventTeamService.joinTeamWithCode(body));
    }

    @PutMapping("/teams/{id}/lock")
    public ResponseEntity<com.college.events.dto.TeamDtos.TeamView> toggleLockTeam(@PathVariable Long id,
                                            @Valid @RequestBody com.college.events.dto.TeamDtos.Lock body) {
        return ResponseEntity.ok(eventTeamService.setLock(id, body));
    }

    @GetMapping("/events/{id}/teams")
    public ResponseEntity<com.college.events.dto.TeamDtos.Teams> getEventTeams(@PathVariable Long id) {
        return ResponseEntity.ok(eventTeamService.getTeamsForEvent(id));
    }

    @GetMapping("/teams/{id}/members")
    public ResponseEntity<com.college.events.dto.TeamDtos.Roster> getTeamMembers(@PathVariable Long id) {
        return ResponseEntity.ok(eventTeamService.getTeamMembers(id));
    }

    // ---- 9. Judging Panel & Live Leaderboard API ----
    @PostMapping("/events/{id}/judging/judges")
    public ResponseEntity<?> assignJudge(@PathVariable Long id,
                                         @Valid @RequestBody com.college.events.dto.JudgingDtos.Assign command) {
        return ResponseEntity.ok(eventJudgingService.assignJudge(id, command));
    }

    @GetMapping("/events/{id}/judging/judges")
    public ResponseEntity<?> listJudges(@PathVariable Long id) {
        return ResponseEntity.ok(eventJudgingService.assignments(id));
    }

    @DeleteMapping("/events/{id}/judging/judges/{judgeId}")
    public ResponseEntity<?> revokeJudge(@PathVariable Long id, @PathVariable Long judgeId) {
        return ResponseEntity.ok(eventJudgingService.revokeJudge(id, judgeId));
    }

    @PostMapping("/events/{id}/judging/score")
    public ResponseEntity<?> submitJudgeScore(@PathVariable Long id,
                                              @Valid @RequestBody com.college.events.dto.JudgingDtos.Score command) {
        return ResponseEntity.ok(eventJudgingService.submitScore(id, command));
    }

    @GetMapping("/events/{id}/judging/round/{round}/scores")
    public ResponseEntity<List<com.college.events.dto.JudgingDtos.ScoreView>> getRoundScores(@PathVariable Long id, @PathVariable int round) {
        return ResponseEntity.ok(eventJudgingService.getScoresForEventRound(id, round));
    }

    @GetMapping("/events/{id}/judging/round/{round}/leaderboard")
    public ResponseEntity<?> getRoundLeaderboard(@PathVariable Long id, @PathVariable int round) {
        return ResponseEntity.ok(eventJudgingService.getLeaderboard(id, round));
    }

    // ---- 11. Volunteer Management API ----
    @PostMapping("/events/{id}/volunteers/tasks")
    public ResponseEntity<?> createVolunteerTask(@PathVariable Long id,
                                                 @Valid @RequestBody com.college.events.dto.VolunteerDtos.CreateTask command) {
        return ResponseEntity.ok(volunteerService.createTask(id,command));
    }

    @PostMapping("/volunteers/tasks/{id}/assign")
    public ResponseEntity<?> assignVolunteer(@PathVariable Long id,
                                             @Valid @RequestBody com.college.events.dto.VolunteerDtos.Assign command) {
        return ResponseEntity.ok(volunteerService.assignVolunteer(id,command));
    }

    @PostMapping("/volunteers/tasks/{id}/approve-completion")
    public ResponseEntity<?> approveVolunteerCompletion(@PathVariable Long id,@Valid @RequestBody com.college.events.dto.VolunteerDtos.Review command) {
        return ResponseEntity.ok(volunteerService.approveCompletion(id,command));
    }

    @GetMapping("/events/{id}/volunteers/tasks")
    public ResponseEntity<?> getEventVolunteerTasks(@PathVariable Long id,@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok(volunteerService.getTasksForEvent(id,page));
    }

    @GetMapping("/volunteers/my-tasks")
    public ResponseEntity<?> getMyVolunteerTasks(@AuthenticationPrincipal UserDetails userDetails,@RequestParam(defaultValue="0") int page) {
        User user = getUser(userDetails);
        return ResponseEntity.ok(volunteerService.getTasksForUser(user.getId(),page));
    }

    // Lost/found commands are owned by the typed, principal-bound LostFoundApiController.

    // ---- 14. Tournament Brackets & Fixtures API ----
    @PostMapping("/tournaments/fixtures")
    public ResponseEntity<?> createFixture(@Valid @RequestBody com.college.events.dto.SportsDtos.CreateFixture command) {
        return ResponseEntity.ok(tournamentBracketService.createFixture(command));
    }

    @PutMapping("/tournaments/fixtures/{id}/score")
    public ResponseEntity<?> updateFixtureScore(@PathVariable Long id,
                                                @Valid @RequestBody com.college.events.dto.SportsDtos.FixtureScoreUpdate command) {
        return ResponseEntity.ok(tournamentBracketService.updateScore(id, command));
    }

    @GetMapping("/events/{id}/tournaments/fixtures")
    public ResponseEntity<List<com.college.events.dto.SportsDtos.FixtureView>> getEventFixtures(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentBracketService.getFixturesForEvent(id));
    }

    @GetMapping("/events/{eventId}/tournaments/fixtures/{fixtureId}/revisions")
    public ResponseEntity<List<com.college.events.dto.SportsDtos.FixtureScoreRevisionView>> getFixtureScoreHistory(
            @PathVariable Long eventId, @PathVariable Long fixtureId) {
        return ResponseEntity.ok(tournamentBracketService.getScoreHistory(eventId, fixtureId));
    }

    // ---- 15. Admin Master Feature Flags API ----
    @GetMapping("/features")
    public ResponseEntity<Map<String, Boolean>> getAllFeatureFlags() {
        return ResponseEntity.ok(featureFlagService.getAllFlagStatuses());
    }

    @PutMapping("/features/{flagKey}")
    public ResponseEntity<?> updateFeatureFlag(@PathVariable String flagKey,
                                               @AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody com.college.events.dto.FeatureControlDtos.Change command) {
        User user = getUser(userDetails);
        if (user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin privileges required to alter master feature switches"));
        }
        var saved=featureFlagService.setFeatureEnabled(flagKey,command.enabled(),command.expectedRevision(),command.reason());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(featureFlagService.view(saved));
    }

    // ---- 16. Campus Social Media Wall & Victory Showcase API ----
    @GetMapping("/feed")
    public ResponseEntity<?> getPulseFeed(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 10000 || size < 1) return ResponseEntity.badRequest().body(Map.of("error", "Invalid feed page"));
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) {
            return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled by university administration"));
        }
        var feed = pulseFeedService.getFeed(page, Math.min(size, 30));
        return ResponseEntity.ok(feed.map(post -> new FeedDtos.PostView(post.getId(), post.getContent(), post.getMediaType(), post.getCategory(),
                post.isWinnerAnnouncement(), post.getRelatedEventId(), post.getRelatedEventTitle(), post.getWinnerName(), post.getWinningPrize(),
                post.getLikesCount(), post.getCommentsCount(), post.isPinned(), post.getCreatedAt())));
    }

    @PostMapping("/feed")
    public ResponseEntity<?> createPulsePost(@AuthenticationPrincipal UserDetails userDetails,
                                             @Valid @RequestBody FeedDtos.Create command) {
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) {
            return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled"));
        }
        User user = getUser(userDetails);
        String mediaUrl = command.mediaUrl();
        if (mediaUrl != null && !mediaUrl.isBlank() && !(mediaUrl.startsWith("/uploads/") || mediaUrl.startsWith("https://"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Media URL must be an approved private path or HTTPS URL"));
        }

        PulsePost post = pulseFeedService.createPost(user, command.content().trim(), mediaUrl, command.mediaType(),
                command.category(), command.eventId(), command.eventTitle(), command.winnerName(), command.winningPrize(), command.winnerAnnouncement());
        return ResponseEntity.ok(new FeedDtos.PostView(post.getId(), post.getContent(), post.getMediaType(), post.getCategory(), post.isWinnerAnnouncement(),
                post.getRelatedEventId(), post.getRelatedEventTitle(), post.getWinnerName(), post.getWinningPrize(), post.getLikesCount(), post.getCommentsCount(), post.isPinned(), post.getCreatedAt()));
    }

    @PostMapping("/feed/{id}/like")
    public ResponseEntity<?> togglePostLike(@PathVariable Long id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled"));
        User user = getUser(userDetails);
        var result = pulseFeedService.toggleLike(id, user);
        return ResponseEntity.ok(new FeedDtos.LikeView(Boolean.TRUE.equals(result.get("liked")), ((Number) result.getOrDefault("likesCount", 0)).intValue()));
    }

    @PostMapping("/feed/{id}/comment")
    public ResponseEntity<?> addPostComment(@PathVariable Long id,
                                            @AuthenticationPrincipal UserDetails userDetails,
                                            @RequestBody Map<String, String> body) {
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled"));
        User user = getUser(userDetails);
        String content = body.get("content");
        if (content == null || content.trim().isEmpty() || content.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment cannot be blank"));
        }
        var comment = pulseFeedService.addComment(id, user, content);
        return ResponseEntity.ok(new FeedDtos.CommentView(comment.getId(), comment.getContent(), comment.getCreatedAt()));
    }

    @GetMapping("/feed/{id}/comments")
    public ResponseEntity<?> getPostComments(@PathVariable Long id) {
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled"));
        return ResponseEntity.ok(pulseFeedService.getComments(id).stream().limit(50).map(comment -> new FeedDtos.CommentView(comment.getId(), comment.getContent(), comment.getCreatedAt())).toList());
    }

    @DeleteMapping("/feed/{id}")
    public ResponseEntity<?> deletePulsePost(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlagService.isEnabled("MODULE_SOCIAL_PULSE")) return ResponseEntity.status(403).body(Map.of("error", "Social Feed is temporarily disabled"));
        User user = getUser(userDetails);
        pulseFeedService.deletePost(id, user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Post deleted"));
    }

    @GetMapping("/feed/victories")
    public ResponseEntity<?> getVictoryShowcases() {
        return ResponseEntity.ok(pulseFeedService.getVictoryShowcases());
    }

    // ---- 17. Cricbuzz-Style Live Sports Scorecard API ----
    @GetMapping("/sports/matches")
    public ResponseEntity<List<com.college.events.dto.SportsDtos.MatchView>> getAllSportsMatches() {
        return ResponseEntity.ok(liveSportsScoreService.getAllMatches());
    }

    @GetMapping("/sports/matches/live")
    public ResponseEntity<List<com.college.events.dto.SportsDtos.MatchView>> getLiveSportsMatches() {
        return ResponseEntity.ok(liveSportsScoreService.getLiveMatches());
    }

    @GetMapping("/sports/matches/{id}")
    public ResponseEntity<?> getSportsMatchDetails(@PathVariable Long id) {
        return ResponseEntity.ok(liveSportsScoreService.getMatchLiveState(id));
    }

    @PostMapping("/sports/matches")
    public ResponseEntity<?> createSportsMatch(@Valid @RequestBody com.college.events.dto.SportsDtos.CreateMatch command) {
        return ResponseEntity.ok(liveSportsScoreService.createMatch(command));
    }

    @PutMapping("/sports/matches/{id}/score")
    public ResponseEntity<?> updateSportsScore(@PathVariable Long id,
                                               @Valid @RequestBody com.college.events.dto.SportsDtos.ScoreUpdate command) {
        return ResponseEntity.ok(liveSportsScoreService.updateScore(id, command));
    }

    @GetMapping("/events/{eventId}/sports/matches/{matchId}/revisions")
    public ResponseEntity<List<com.college.events.dto.SportsDtos.MatchScoreRevisionView>> getSportsScoreHistory(
            @PathVariable Long eventId, @PathVariable Long matchId) {
        return ResponseEntity.ok(liveSportsScoreService.getScoreHistory(eventId, matchId));
    }

    @PostMapping("/sports/matches/{id}/commentary")
    public ResponseEntity<?> addSportsCommentary(@PathVariable Long id,
                                                 @Valid @RequestBody com.college.events.dto.SportsDtos.CommentaryCreate command) {
        return ResponseEntity.ok(liveSportsScoreService.addCommentary(id, command));
    }

    // ---- 18. Google-Form-Style Dynamic Event Form Builder API ----
    @GetMapping("/forms/{eventId}/schema")
    public ResponseEntity<List<CustomEventFormField>> getFormSchema(@PathVariable Long eventId) {
        return ResponseEntity.ok(dynamicFormService.getFormSchema(eventId));
    }

    @PostMapping("/forms/{eventId}/schema")
    public ResponseEntity<?> configureFormSchema(@PathVariable Long eventId,
                                                 @AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestBody List<com.college.events.dto.DynamicFormDtos.Field> fields) {
        return ResponseEntity.ok(dynamicFormService.configureFormSchema(eventId, fields));
    }

    @PostMapping("/forms/{eventId}/submit")
    public ResponseEntity<?> submitEventForm(@PathVariable Long eventId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody com.college.events.dto.DynamicFormDtos.Submit body) {
        User user = getUser(userDetails);
        CustomEventFormResponse resp = dynamicFormService.submitForm(
                eventId, user, body.name(), body.rollNo(), body.department(), body.semester(), body.answers());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("success", true, "response", Map.of("id", resp.getId(), "eventId", resp.getEventId())));
    }

    @GetMapping("/forms/{eventId}/my-response")
    public ResponseEntity<?> getMyFormResponse(@PathVariable Long eventId,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        return ResponseEntity.ok(dynamicFormService.getUserResponse(eventId, user.getId()).orElse(null));
    }

    @GetMapping("/forms/{eventId}/export-csv")
    public ResponseEntity<String> exportFormCsv(@PathVariable Long eventId,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        String csv = dynamicFormService.exportResponsesCsv(eventId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event_" + eventId + "_responses.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + userDetails.getUsername()));
    }
}
