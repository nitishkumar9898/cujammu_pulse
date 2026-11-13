# CUJAMMU-PULSE: Project and Architecture Review

Reviewed: 2 October 2026. Scope: analysis and suggestions only. No application source, settings, database, or existing architecture document was changed.

Later documentation update (2 October 2026): this review preserves original source defects while historical scope recommendations are superseded by [29 active requirements CF-01..CF-30 except retired CF-12](CORE_FEATURES_SPEC.md). Current target is [UNIVERSITY_PLATFORM_ARCHITECTURE.md](UNIVERSITY_PLATFORM_ARCHITECTURE.md) v1.4 with [schedule/notifications](SCHEDULING_AND_NOTIFICATION_SPEC.md), [faculty/volunteer/QR-mode contracts](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md), [runtime controls and 30-day uploaded-media expiry](RUNTIME_FEATURES_AND_MEDIA_SPEC.md) and [parity/release tests](PLATFORM_PARITY_AND_ACCEPTANCE.md). Updating specifications does not fix source defects or remove source modules/data; historical QR/evidence/file-retention assumptions do not override current contracts. Admin toggles affect implemented compatible clients and server workflows; uploaded originals/derivatives including academic PDFs expire at acceptance + 30 days UTC, with explicit legal holds, external official archives and tracked backup purge.

## 1. Assessment and evidence limits

The project contains a substantial Spring Boot event-management implementation and a broad native Android UI prototype. It also contains early backend implementations for library visits, facility bookings, hostel passes, academic records, community features, and competitions. These are useful foundations, but the current source does not establish the production, security, offline, or web/mobile parity guarantees claimed by the architecture documents.

Reviewed both root architecture documents, the 25-page SRS text and its rendered DFD/use-case/registration-sequence pages, Maven/Gradle and deployment configuration, core entities/repositories/services/controllers, web templates, Android navigation/screens/API declarations, test inventory, and load-test script. Findings below are static source observations and design analysis. The application and APK were not started; existing databases were not queried. No fresh build, penetration test, load test, or real-device test was performed. Runtime exploitability and UI rendering still require verification. An existing JAR or APK does not prove it was built from the current source.

The active source roots are `college-events/src` and `cujammu-pulse-android/app/src`. The `college-events/bin` tree duplicates older resources; `college-events - Copy` contains a target directory. Existing working-tree changes were left untouched.

## 2. What the product is becoming

The original SRS describes a web event-management system: students, organizers, admins, proposal approval, registration, waitlisting, static QR tickets, attendance, emails, and reports. The first architecture expands this into web plus Android. The master architecture expands it further into a campus operations platform.

The four core domains requested by the owner are:

1. Event lifecycle: proposal, approval, publication, registration, attendance, certificates, reporting.
2. Library visits: entry, exit, visit purpose, occupancy, incomplete sessions, daily reports.
3. Auditorium and facility availability: calendar, booking, approvals, conflicts, equipment and relocation.
4. QR attendance: identity, authorized scan, duplicate prevention, attendance policy and auditable corrections.

These should share identity and policy, but retain distinct workflows. A library visit is not an event registration. An approved event is not automatically a confirmed room reservation. A QR credential is not itself proof of uninterrupted participation.

## 3. Architecture claims versus implementation

| Area | Source observed | Implication |
|---|---|---|
| Web frontend | Thymeleaf, Bootstrap and JavaScript templates | React/Next.js in the master document is a proposed direction, not the current frontend |
| Backend | Spring Boot 3.5.14, MVC controllers, services and JPA repositories | A layered monolith exists; the diagrams should distinguish it from future infrastructure |
| Java | Maven property says 17; compiler source/target says 25; README and Docker say 21 | Build/deployment baseline is inconsistent |
| Database | Default persistent H2; MySQL dependency; no PostgreSQL driver in POM | PostgreSQL architecture is not configured; changing only DB_URL is insufficient |
| Authentication | Spring form login, JSESSIONID and one concurrent session | Stateless JWT/2FA architecture is not implemented in this path |
| Android | Compose screens, Retrofit declarations, emulator URL | Broad UI prototype; examined screens do not execute real service calls |
| Offline | Room dependency exists; no Room database/DAO or WorkManager implementation found | Offline tickets, scan queue and sync guarantees are unimplemented |
| QR | UUID tokens encoded by ZXing into URL or JSON | No rotating HMAC/TOTP verification found in examined source |
| Cache | Redis cache configuration; default cache type is none | Redis dependency does not implement atomic seat reservation |
| Storage | Local uploads served through a public resource path | No private object vault or S3 implementation found |
| Notifications | Email and database notifications | FCM/SMS/WhatsApp delivery is not established by current source |
| Real-time | No WebSocket implementation found in source search | Immediate cross-client synchronization is a target, not demonstrated behavior |

Single source of truth is a sound principle. The current web controllers call services directly while the campus REST controller exposes a subset of functionality. Sharing business services can provide consistent behavior without forcing server-rendered pages to call their own REST API. Android nevertheless needs complete, authenticated APIs for its workflows.

## 4. Findings that need attention before a real campus pilot

### A. Role assignment and API authorization

`AuthController` displays a restricted registration-role list, but the POST binds the full Role enum and its validation switch permits other roles through the default branch. `UserService.registerUser` persists the submitted role. There is no explicit server-side allowlist in this registration path. A modified submission could request ADMIN or another privileged role, subject to completing the OTP flow. A dropdown restriction is not authorization.

`SecurityConfig` permits paths such as `/api/v1/cujammu/departments` and `/hostels` without limiting the rule to GET. The same paths have POST creation handlers with no principal or role check. Static configuration therefore permits anonymous creation requests, subject to ordinary validation/runtime behavior. Other campus operations fall through to “authenticated” even when they should require a specific institutional role and resource assignment.

Examples needing scoped guards: hostel approval and gate scans, library occupant lists and exports, event attendance and exports, scorecard distribution, judging, and chat cleanup. Some later sports/feature/form handlers have explicit role checks, so this is uneven protection rather than an absence of all security.

Proposed policy: enforce registration role allowlists; provision institutional authority through admin approval; protect endpoints by HTTP method and capability; check ownership/assignment in business services. An organizer for event A should not mutate event B. A warden should approve only their hostel's passes. Enable method security if using annotations. Spring documents request and method authorization as complementary layers: [request rules](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html), [method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

### B. Entity responses can expose sensitive data

Campus APIs return JPA entities containing nested User objects. User has public getters for password hash, reset token, contact data and other fields, without a JSON exclusion in that class. Public feed/alumni data and authenticated library/attendance responses need a serialization review. Depending on fetching/serialization, responses may expose those fields or fail while traversing relationships. This is a source-level exposure risk, not a captured runtime response.

Use allowlisted response DTOs and minimal public profiles; exclude authentication fields defensively; paginate reports; separate public certificate verification from private student details. Do not put names, department and enrollment data into QR payloads unless needed. Treat the public uploads path as public storage, never a marksheet vault.

### C. Session authentication and CSRF policy conflict

The backend uses browser sessions while excluding all `/api/v1/cujammu/**` requests from CSRF protection. This needs correction for cookie-authenticated mutations. The Android API client has neither token authentication nor a persistent session-cookie mechanism, and no real login API usage was found.

Choose a concrete authentication design: retain secure sessions and CSRF for web; provide authenticated mobile APIs using an appropriate token flow or a deliberately supported session flow. Define logout, renewal/revocation, lost-device behavior, and simultaneous web/app login. The current maximumSessions(1) setting also conflicts with a potential shared session-based web/app experience.

### D. Registration concurrency

`RegistrationService.register` reads confirmed count, compares capacity, then inserts. Two different students can both see the final seat as free. A transaction and the existing unique `(user_id, event_id)` constraint prevent neither this capacity race nor concurrent waitlist promotion.

Use the database as capacity authority: lock the event row around registration/cancellation/promotion or implement a conditional allocation update in one transaction. Keep duplicate-user constraints. If Redis reservations are introduced later, specify reservation expiry, compensation, crash recovery and reconciliation with the database.

Also review `RegistrationRepository.findFirstByEventAndStatusOrderByRegisteredAtAsc`: its explicit JPQL orders all matching rows without an explicit limit yet returns Optional. The name alone should not be assumed to limit this annotated query; verify/fix it for multiple waitlisted users. Cancelled registrations currently remain blocked by existsByUserAndEvent, so re-registration policy needs definition.

### E. Venue concurrency and unauthorized priority

`CUJammuMasterService.bookFacility` checks overlap then inserts without a facility lock. Concurrent requests can book overlapping slots. The API accepts priorityLevel from the caller, and the service can preempt a booking at priority >=100 without verifying the caller's institutional authority. It handles only the first conflict, so a high-priority reservation can leave other overlaps unresolved.

Derive priority from verified authority/policy, never a client number. Validate end > start, capacity and equipment. Serialize reservations per facility or enforce overlap at the database. PostgreSQL supports range exclusion constraints suitable for reservations: [official range documentation](https://www.postgresql.org/docs/16/rangetypes.html). With MySQL, a transaction locking the facility row is a practical alternative.

Event creation currently stores venue as text; the booking API passes null as related event. Approval, postponement and cancellation do not establish a consistent facility-reservation lifecycle. Link event/session to booking explicitly and update both atomically. Include setup/cleanup buffers, maintenance blocks and opening hours.

### F. Attendance and certificate paths disagree

`AttendanceService.markAttendance` checks organizer ownership and registration status, records single-scan attendance, and immediately issues a certificate. That issuance path does not consult the dual-attendance eligibility flag or the event's generateCertificate setting.

`DualAttendanceService` maintains a separate record and grants eligibility after a hard-coded 1,800 seconds. It does not use the event's minDwellTimePercent. Repeating exit overwrites the exit timestamp and can change eligibility. This path does not show the same cancelled/waitlisted, event timing or organizer ownership checks as the old scanner. It also does not unify registration ATTENDED status and certificate issuance with the older path.

Use one attendance policy and one authoritative ledger. Separate admission/check-in, completion/eligibility, and certificate issuance. Reject or return the original result for duplicate scan requests. Validate event/session, token action, scanner authority, registration status and allowed time window. Make manual correction require a reason and keep the original scan evidence.

### G. Library sessions and occupancy

Useful logic exists for active sessions, purpose mismatch and closing unclosed visits. However, the entry API takes only passType and calls checkIn with null latitude/longitude; no QR proof or geofence is checked. An authenticated request alone can create a visit.

`/library/active` returns getTodayLogs, which includes completed visits, not just current occupants. Automatic closure writes entry +3 hours at the nightly sweep and entry +4 hours on a next-day check-in, rather than the actual closing time. Duration seconds are not computed consistently. Mismatch count is saved and then a runtime exception is thrown inside a transaction, which normally rolls that increment back.

Introduce a validated purpose enum, authorized QR challenge or gate scanner, one active visit per person/library under concurrency, configurable hours/holidays, and actual closing-time bounds. Keep auto-closed time as estimated, not verified study time. Exclude incomplete visits from verified-duration analytics. Configure an explicit campus timezone for schedulers and policy clocks.

### H. Android implementation status

AuthScreen uses hard-coded OTP values and local navigation. DualQrScannerScreen draws a viewfinder box and displays a fixed sample student's successful result. CameraX/ML Kit dependencies alone do not activate a camera. VenueCalendarScreen modifies a local list for a booking request. Library navigation discards its requested mode and opens an ENTRY scanner.

Source search found API declarations and a LibraryTurnstileScreen ApiClient import, but no real apiService invocation in screen code. No repository/ViewModel/offline database implementation was found. Core event discovery, registration, ticket, organizer and approval APIs/screens are not established as equivalent to the web workflows.

Preserve the UI as a design reference; add real auth, typed API contracts, repositories/ViewModels, loading/error states, camera permissions/scanning, and server-confirmed actions. Separate demo data visibly. Replace the emulator-only HTTP URL with build-specific HTTPS configuration and disable body logging for sensitive production requests.

### I. Deployment and tests

Docker builds with JDK 21 while Maven asks for source/target 25, an incompatible compiler configuration. Default datasource settings hard-code H2 driver/dialect and enable its console. Schema management uses ddl-auto=update. Docker Compose includes only the app; it does not supply the database/cache/object storage pictured in the master design.

Align Java/compiler/container versions; create explicit dev/test/production profiles; use schema migrations; disable development console and default credentials outside demos; verify healthcheck access against security rules; define database/uploads backups and restore procedure.

The test tree contains an application context test and chatbot tests, not the registration/attendance/concurrency tests claimed as passing by the SRS. The existing Locust script primarily reads public pages and does not authenticate or book seats. It cannot establish zero-overbooking or multi-gate correctness. Maintain a requirements-to-tests matrix and report measured results rather than “100% bug-free” claims.

### J. Secondary modules and consistency

- FeatureFlagService seeds MODULE_SOCIAL_PULSE while feed handlers check SOCIAL_FEED; unknown flags default true. An admin disable switch can therefore fail to enforce the intended state. Other modules also need consistent backend enforcement and cache invalidation across instances.
- CoEAcademicService stores a PDF URL and marks pushedToEmail=true without sending email in that method. No PDF slicing/encrypted vault implementation was found. The student dashboard refers to marksheetPdfUrl while the entity exposes scoreCardPdfUrl. Treat these as incomplete delivery/integration paths.
- Hostel scan service does not require an approved pass before updating its state; it compares return with expectedReturnTime rather than enforcing the configured hostel curfew policy.
- Chat has a night-silence check but no demonstrated membership enforcement, slow-mode or 3-report quarantine. Its profanity filter contains sample tokens. Real-time moderation and ephemeral deletion policies need further design.
- Department is still free text on User; enrollmentNo is not unique. Stable institutional identity and verified department membership matter for attendance, eligibility and academic data.
- Upload configuration uses app.upload-dir in properties but app.upload.dir in storage/resource classes. Defaults hide this mismatch until a deployment changes the directory.
- In-memory IP rate limits can penalize many students sharing campus NAT. The filter trusts X-Forwarded-For without demonstrating a trusted-proxy boundary. Prefer authenticated-user/device quotas plus a separate IP abuse limit.

## 5. Corrections to the master architecture's ten loops

| Proposed defense | Practical correction |
|---|---|
| Offline HMAC verified using public keys/hashes | HMAC is symmetric; hashes/public keys alone cannot verify it. For public-key offline verification use asymmetric signatures and an explicit key-rotation/revocation plan |
| Rotating QR makes screenshots impossible | Rotation shortens replay time; real-time forwarding remains possible. Define anti-replay, identity and gate supervision rather than promising impossibility |
| GPS/Wi-Fi confirms physical presence | Treat as supporting evidence; indoor GPS is noisy and device-reported location/network evidence is not absolute proof. Provide authorized fallback for unavailable permissions/devices |
| Entry-exit dwell prevents canteen escape | A student who leaves between scans still passes elapsed-time math. Sum tracked presence intervals or use proportionate spot checks; acknowledge residual proxy risk |
| Offline scanners prevent all duplicate entry | Separate disconnected gates cannot instantly know each other's scans. Use a campus LAN coordinator, partitioned gate ownership or explicitly provisional offline acceptance with reconciliation |
| Auto-close equals study duration | It is estimated duration and needs a separate status/analytics treatment |
| Higher priority automatically relocates events | Preemption needs authorized decision, affected-event notice, alternatives matching capacity/equipment and an audit trail; do not silently confirm displacement |
| Night-silence is sufficient moderation | Add membership, report review, rate controls, appeal and retention policy |
| Encrypted individual PDF ensures privacy | Ownership authorization, private storage and safe delivery are still essential; encryption alone is insufficient |
| Rain delay / hostel curfew are one-click features | Define valid state transitions, authority, scheduling dependencies and configurable institution-approved policy |

Offline-first Android needs local and network data sources, queued persistent writes and reconciliation, rather than a dependency entry alone: [Android official guidance](https://developer.android.com/topic/architecture/data-layer/offline-first).

## 6. Suggested target design

The SRS diagrams also need semantic repair. Figure 5.1 treats the internal MySQL database as an external context participant and omits the described system-to-student outputs. Figure 5.2 connects organizer event data and admin approval to Registration Process rather than Event Management. Figure 5.3 routes Send Mail toward Events DB and SMTP from Generate QR Token toward Email Server. These arrows do not match the described responsibilities. Redraw and balance DFD levels using actual data flows; include venue/library modules, trust boundaries, authorization and failed/waitlisted paths in the expanded design. The registration sequence illustrates only the available-seat success path and needs concurrency/duplicate/full-capacity alternatives.

Keep Spring Boot as a modular monolith for the first reliable release. Separate identity/access, events/registration, facility reservations, attendance, library visits, certificates, notifications and reporting. Keep one relational database and explicit service boundaries. Redis, private object storage and real-time channels can be introduced where measured needs justify them. No blanket frontend rewrite is required to fix core correctness.

Web and Android should share permissions, business rules and API meanings. Prefer capability/workflow parity over identical screens and buttons: desktop suits calendars/bulk management; Android suits tickets, camera and field scanning.

Minimum data additions: verified institutional identifier; department/facility foreign keys; event sessions with start/end; reservation status/history; attendance scan action and scanner/device identifier; idempotency key; receivedAt and deviceScannedAt for offline records; policy/version used for eligibility; certificate issue/revoke record; library session close reason and estimated/verified duration distinction.

Use server timestamps for online decisions, UTC storage and Asia/Kolkata display/policy configuration. Device offline timestamps need clock-skew handling and cannot be treated as inherently trusted. Record notification intent transactionally and dispatch after commit with retry/idempotency to prevent emails about rolled-back actions.

Define a state machine for each domain, including rejected, cancelled and corrected paths. Avoid scattered free-text status mutations. Keep registration admission, attendance completion and certificate eligibility distinct.

## 7. Suggestions ranked by value

### First reliable pilot

1. Verified users and institution-approved role assignment.
2. Facility calendar with approval status, buffers, opening hours, maintenance blocks and conflict-safe booking.
3. Complete event proposal-to-approval-to-registration flow with reliable capacity and waitlist handling.
4. Real Android authentication and QR scanning against the same backend policies.
5. Unified event attendance, duplicate-safe scans, time windows and reasoned manual correction.
6. Library entry/exit, accurate occupancy, forgotten-exit handling and librarian-only individual reports.
7. Certificate issuance after the applicable participation policy, with public minimal-data verification and revocation.
8. Email/in-app delivery state, CSV/Excel reports and operational audit logs.

### Valuable next additions

- Unified personal calendar and clash warnings for student, organizer and venue.
- Event/session-specific delegated scanner access, automatically expiring after the event.
- Venue resource checklist: projector, audio, seating, accessibility and handover.
- Waitlist promotion deadline and no-show policy, avoiding silently reserved unused seats.
- Configurable event rules: department/batch eligibility, registration windows, re-entry and participation threshold.
- Library occupancy heatmap and aggregates, without public individual visit histories or study-time rankings.
- Notification inbox and delivery monitoring; push can follow once mobile auth is reliable.
- Participant feedback, certificate portfolio and summary reports for approved institutional uses.
- Controlled offline gate workflow, introduced only after its conflict/revocation policy is defined.

### Later, after core adoption

Team registration, judging, tournament brackets and volunteer coordination are adjacent to events and can be added incrementally. Hostel passes, marksheets, alumni network and community chat each introduce substantial institutional policy/privacy/operational responsibilities; ship them as separate approved modules. Runtime controls and uploaded-media expiry require common cross-domain enforcement before implementation is accepted. AI assistance should begin with verified public event information and permission-aware retrieval.

## 8. Release checks to agree on

These are proposed acceptance scenarios, not tests already passed:

| Scenario | Required outcome |
|---|---|
| 100 concurrent requests for 10 seats | Exactly 10 confirmed; deterministic waitlist; no capacity breach |
| Two overlapping room reservations | Only an authorized conflict-free reservation commits |
| Student submits ADMIN role / approval request | Privileged role and action denied |
| Organizer scans someone else's event | Denied unless explicitly delegated |
| Cancelled/waitlisted/expired/wrong-event QR | Denied with understandable reason |
| Same QR scanned twice or request retried | One durable attendance action and stable repeat response |
| Repeated exit submission | Cannot extend participation time |
| Forgotten library exit / late-night entry | Estimated closure bounded by actual hours; no invented verified study duration |
| Two offline gates see same credential | Defined provisional/duplicate resolution; clear operator status |
| Private report/profile serialization | No password hash, reset token or unauthorized student records |
| Certificate for short attendance | Follows event/session policy; consistent across all scan paths |
| App restart or network failure | Confirmed state persists or visibly reloads; pending actions remain distinguishable |
| Venue cancelled/postponed/preempted | Event, booking, pass details and notifications remain consistent |
| Production build and restore drill | Reproducible build; private data retained; backup demonstrably restores |

## 9. Decisions for the owner

Decide whether the next release is an academic demo, a limited campus pilot, or a university-wide service. Agree the first four-module scope, who owns approvals and attendance disputes, how institutional identity is verified, which QR scanning mode is intended, and what must work without internet. Confirm institutional roles, email conventions, venue rules and hours with actual stakeholders rather than assuming the document's campus facts are authoritative.

Recommended next decision: a limited pilot for events, venues, library visits and QR attendance, using the existing Spring Boot/web foundation and Android UI. First resolve access control and data correctness; then connect and verify complete workflows; add broader campus modules after the pilot demonstrates reliable operation. This is a recommendation only; implementation is pending the owner's scope decision.

## 10. Source map

Primary project evidence: ARCHITECTURE.md; CUJAMMU_MASTER_ARCHITECTURE.md; college-events/College Event Management SRS final.pdf; college-events/pom.xml; Dockerfile; docker-compose.yml; application.properties; application-h2.properties.

Backend: config/SecurityConfig.java, CacheConfig.java, RateLimitingFilter.java, WebMvcConfig.java; controller/AuthController.java, CUJammuApiController.java, StudentController.java; service/UserService.java, RegistrationService.java, QRService.java, AttendanceService.java, DualAttendanceService.java, LibraryTurnstileService.java, CUJammuMasterService.java, CoEAcademicService.java, CommunityChatService.java, FeatureFlagService.java, FileStorageService.java; model/User.java, Role.java, Event.java, Registration.java, DualAttendance.java, LibraryGateLog.java, StudentScoreCard.java; repository/RegistrationRepository.java, FacilityBookingRepository.java; templates/student/dashboard-360.html; src/test; load-test/locustfile.py. Paths are relative to college-events/src/main/java/com/college/events except where stated.

Android: app/build.gradle.kts; app/src/main/AndroidManifest.xml; MainActivity.kt; data/api/ApiClient.kt and CUJammuApiService.kt; ui/screens/AuthScreen.kt, HomeScreen.kt, LibraryTurnstileScreen.kt, DualQrScannerScreen.kt and VenueCalendarScreen.kt. Kotlin paths are relative to cujammu-pulse-android/app/src/main/java/com/cujammu/pulse.
