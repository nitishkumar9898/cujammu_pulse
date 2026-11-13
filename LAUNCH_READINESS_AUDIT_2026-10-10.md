# CUJAMMU-PULSE launch readiness audit

> Historical initial audit snapshot. Implementation followed this audit: compilation and several authorization/workflow findings have been repaired and tested. See [current integrated evidence and remaining work](LOCAL_REVIEW_STATUS_2026-10-10.md). The original failing build and test counts below describe the initial snapshot, not the current artifacts. Full production readiness remains unproven.

Audit date: 10 October 2026 (Asia/Kolkata). Scope: current local working tree, including uncommitted and untracked implementation; accepted full university platform scope. This is not an audit of GitHub, the existing APK, or a deployed service.

**Verdict: not launch ready.** Shared web source builds and isolated tests pass, but the backend currently fails compilation, universal Android cannot enter authenticated workflows, and source inspection found object authorization, file access/expiry, certificate policy and unfinished workflow gaps. Remaining work includes implementation, wiring and operational acceptance, not just deployment approval.

Audit method: lead inspection plus three read-only backend, client and release reviewers; normative specifications, actual controller/service/model/screen call sites, current builds/tests and deployment inventory. Business source was not changed. Build/test outputs were regenerated. No real campus records, secrets or historical database contents were inspected. Preserve unrelated modifications and existing data.

## 1. Fresh evidence and limits

| Verification | Current result | What it establishes |
|---|---|---|
| `rtk npm run typecheck` in `cujammu-pulse` | PASS | Shared TypeScript consistency |
| `rtk npm test` in `cujammu-pulse` | 106/106 PASS | Existing isolated client/domain tests |
| `rtk npm run build:web` | PASS | Expo web export generation |
| `rtk npm run test:web` | 44/44 PASS | Desktop/narrow Edge workflows using synthetic mocked API fixtures |
| `rtk proxy C:\tools\apache-maven-3.9.9\bin\mvn.cmd -q -f college-events/pom.xml test` | FAIL at main compilation; no tests executed | Current backend cannot be certified by historical passing reports |
| Kotlin Gradle `:app:assembleDebug --offline`, then `--no-daemon` retry | FAIL before source compilation: unable to establish loopback connection | Native build remains unverified; no fresh APK acceptance |
| Live web/API/DB E2E, production-store concurrency, physical camera/device, load, DR, independent assessment, office UAT | Not performed | Required launch evidence remains open |

Host runtime is JDK 25; Maven compiler target is Java 21. The observed duplicate-method and unresolved-type failures are structural, not evidence that Java 21 itself failed. Pin and verify the required JDK baseline during repair. Browser mocks do not exercise Spring authentication, persistence, transactions or real delivery.

## 2. Immediate blockers, with source evidence

### A. Backend build integration is broken — P0

- Duplicate nine-argument `bookFacility` declarations: [CUJammuMasterService.java](college-events/src/main/java/com/college/events/service/CUJammuMasterService.java), lines 435 and 495. The first also references undeclared `priorityLevel` at lines 465/480.
- Missing `HostelDeviceRepository` type/import: [CUJammuApiController.java](college-events/src/main/java/com/college/events/controller/CUJammuApiController.java), line 50.
- Unresolved `ReportStatus`: [ChatAbuseReportRepository.java](college-events/src/main/java/com/college/events/repository/ChatAbuseReportRepository.java), line 17. The enum is package-private in the model package.
- Missing `Hostel` import/type: [HostelDeviceRepository.java](college-events/src/main/java/com/college/events/repository/HostelDeviceRepository.java), line 19.
- Additional source integration defects: [VolunteerService.java](college-events/src/main/java/com/college/events/service/VolunteerService.java), lines 61 and 89–98 reference undeclared `audit`, `registrations`, `certificateRepository` and `task.getEvent()`, although `VolunteerTask` stores `eventId`.
- Library controller calls do not match service signatures: controller lines 273/279 use `(User, Map)` while service methods use `(User, String, Double, Double)` / `(User, String)`; exports refer to a nonexistent model-package `LibraryTurnstileService` at lines 307/318.

Required: integrate existing edits, resolve first structural errors before diagnosing Lombok cascades, align controller/service/model contracts, run clean compile/package and complete backend tests on the agreed toolchain. Do not delete records or weaken checks to regain green status.

### B. Lost & Found has missing object/state authority — P0

[LostAndFoundService.java](college-events/src/main/java/com/college/events/service/LostAndFoundService.java), lines 41–65, accepts arbitrary supplied claimant identity and changes handover state without binding the actor, verifying a scoped handover operator or enforcing a reviewed transition. Repository has no claim serialization/version guard. [CUJammuApiController.java](college-events/src/main/java/com/college/events/controller/CUJammuApiController.java), lines 591–615, returns full entities containing claimant/reporter contact and identifier fields. Route security is generic authentication; module enablement is not authorization.

Required: principal-bound claim requests, confidential proof, scoped finder/office verification, state machine, contention/version/idempotency, claim history, audit/outbox and DTO allowlists. Add negative HTTP and simultaneous-claim tests after compilation is restored. This is a source-level security finding; no deployed exploit was attempted.

### C. Private uploaded-file delivery and retention are incomplete — P0

[WebMvcConfig.java](college-events/src/main/java/com/college/events/config/WebMvcConfig.java), lines 73–79, serves `/uploads/**` as static resources with an hour cache. Authentication alone permits access to a known object path; [MediaAsset.java](college-events/src/main/java/com/college/events/model/MediaAsset.java) has no owner/resource ACL binding. [MediaExpiryInterceptor.java](college-events/src/main/java/com/college/events/config/MediaExpiryInterceptor.java), lines 26–29, permits untracked assets and ordinary access after expiry under legal hold, including a missing hold expiry.

Required: private authorized gateway, owner/resource/purpose binding, fail-closed reviewed legacy inventory, immutable 30-day deadline on every access, bounded cache grants, private quarantine and parser limits. A hold may delay physical purge; it must not restore ordinary expired access. Build derivative/version/cache lineage, protected deletion ledger, restore suppression, purge proofs/retry alerts and disclosed bounded backup deletion. Existing upload acceptance/expiry and tracked-local purge are useful foundations, not proof of deletion across all copies.

### D. Universal Android parity is currently blocked — P0

[App.tsx](cujammu-pulse/src/App.tsx), line 93, immediately enters `native-pending` on every non-web platform; line 208 displays the unavailable identity shell. [api.ts](cujammu-pulse/src/api.ts), line 35, uses browser same-origin cookie transport. Shared action declarations do not establish working installed-client parity.

Required: approved native OIDC/PKCE flow, secure credential storage, HTTPS origin and bearer adapter, token/session revocation, account-switch clearing, device permission/camera/file adapters and actual Android E2E. Preserve Kotlin as migration reference; it still accepts local hardcoded OTP `123456` and contains local demo flows. Do not treat the existing APK as a launch artifact.

### E. Certificate eligibility and issuance disagree — P0

[EventQrAttendanceService.java](college-events/src/main/java/com/college/events/service/EventQrAttendanceService.java), line 159, maps ENTRY to `ATTENDED`. [StudentController.java](college-events/src/main/java/com/college/events/controller/StudentController.java), lines 251–269, lazily creates certificates for all `ATTENDED` registrations during a GET without checking entry-only policy, event certificate enablement, closure or evidence finalization. [CertificatesScreen.tsx](cujammu-pulse/src/certificates/CertificatesScreen.tsx) directly offers downloads from registration status; [CertificateService.java](college-events/src/main/java/com/college/events/service/CertificateService.java), line 30, requires an already existing certificate. This can advertise a missing PDF and exposes a policy path capable of issuing an unsupported certificate.

Required: separate admission, attendance evidence, eligibility and issuance states; one durable duplicate-safe finalization/issuance workflow; approved certificate type; corrections/revocation; certificate listing from issuance records; protected downloads; minimal opaque public verification. The generated `/verify/{number}` URL also needs a matching verified route. Volunteer certificates must use cumulative approved evidence rather than one new award per task.

### F. Approved primary store and institutional identity remain implementation work — P0

[pom.xml](college-events/pom.xml), line 51, and [application.properties](college-events/src/main/resources/application.properties), lines 2–9, use JPA/SQL, default file H2 and `ddl-auto=update`. MongoDB replica-set primary, validators/indexes/transaction manager, migration and recovery implementation are absent from the inspected backend. Existing SQL locks are valuable reference but cannot prove Mongo behavior.

[OidcResourceServerConfig.java](college-events/src/main/java/com/college/events/config/OidcResourceServerConfig.java), lines 28–31, is experimental and disabled by default; default browser login is local form authentication. Finish approved IdP provisioning/federation, web BFF/native login, verified identity/grants, privileged MFA, recovery and revocation. Credentials and university policy are external inputs; implementation can be prepared using synthetic configuration.

## 3. Full active CF backlog

All 29 active requirements remain in scope; CF-12 stays retired. “Partial” means useful source exists, not launch acceptance. Every row inherits backend build, identity, primary-store, client parity and applicable release gates above.

| ID | Present evidence | Build / implement / wire before acceptance |
|---|---|---|
| CF-01 Theme | Shared tokens, Light/Dark/System, device AsyncStorage | Per-account preference isolation/sync; Auto/device/contrast/motion/accessibility verification |
| CF-02 Branding | Shared CUJ text/monogram and shell | Approved circular asset, consistent onboarding/navigation and accessible production branding |
| CF-03 Departments | Directory and departmental identity slices | Office-validated initial registry, stable verified membership, controlled dynamic changes/versioned seeds |
| CF-04 Hostel | Application/warden approval and owner-bound credential services; client shows raw credential | Guard device registration and bound camera operations, departure/return intent + owner confirmation, final receipt; approved curfew/grace/extension rules; revocation/offline proof; complete private notifications |
| CF-05 Venues | SQL facility lock, suggestions, pending review/cancel, request receipts | Fix duplicate implementation; 24-hour grid and overlays, opening hours/holiday/maintenance intervals, buffers/resources/accessibility, authorized instant booking and reviewed preemption, event/reservation lifecycle integration, target-DB race proof |
| CF-06 Library | Purpose/session/history, elapsed time, CSV and styled XLSX projections | Signed station challenge/action/purpose validation and camera evidence; fix API signatures; one-active-visit contention including initially absent row; server time calibration; actual closing policy separate from reporting; scoped previous-local-day durable jobs/revisions/catch-up/private downloads. Controller currently ignores requested date when selecting export day |
| CF-07 Attendance | QR owner intent/evidence and manual-operation services | Credential QR display/scanner UI, accepted/pending distinction, report download actions, frozen policy and duration/re-entry evidence, offline reconciliation, correction/finalization and truthful certificate eligibility |
| CF-08 Academics | Numeric scorecards, scoped independent review and CSV batch preview | Quarantined PDF ingest/page mapping/private artifacts, official preview/download/print/correction, immutable reissue/publication ledger, timetable publishing/view. Client submits `pdfUrl:null`; service deliberately rejects unmanaged file URLs |
| CF-09 Chat | Membership, bounded history/posting/dedupe and server silence checks | Authenticated realtime/reconnect/revocation, delivery states, attachments/moderation/countdown; protected temporary-group cleanup currently explicitly unavailable; retention/purge and cross-group abuse controls |
| CF-10 Teams | Invite/join/roster/SQL lock slices | Verified participant/event binding, expiring rate-limited invite policy, withdrawal/removal/dissolution acceptance, deadline/roster lock correction and notification/concurrency proof |
| CF-11 Judging | Four-score rubric, assignments, score revisions and leaderboard | Canonical participant/team identities, conflicts of interest, versioned round/rubric publication, ties/appeals/reopen, reviewed result publication and live shared UI |
| CF-13 Volunteers | Recruitment and task/hour review service slices | Repair incomplete certificate additions; shared task hub/create/assign/complete/review APIs currently lack screen calls; shift evidence and overlapping credit guards, independent supervisors, cumulative certificate eligibility |
| CF-14 Lost/found | Report and direct claim surfaces | Fix P0 authority/privacy; category filters/photos, confidential claims, competing claim serialization, finder/office handover and immutable log |
| CF-15 Helpdesk | Scoped numbered intake/thread/status and concurrency tests exist | Scoped responder assignment/escalation workflow, private attachments/notifications, staff SLA policy and both-client acceptance; 24/7 intake does not establish 24/7 staffing |
| CF-16 Tournaments | Fixture/score/revision services and score screen | Shared fixture/bracket management, participant/resource clashes, sport-specific live score/replay/correction, match lifecycle/notifications and winner publication. Current score UI submits COMPLETED immediately |
| CF-17 Polls | Scoped authenticated vote uniqueness/window/results | Persisted deadline closure/finalization, audience notifications, realtime catch-up, disqualification/appeal policy and target-DB/native tests |
| CF-18 Lifecycle | Event postpone/cancel/reschedule receipts/history | Planned change vs effective postpone, lifecycle/resource revisions, full impacts and reservations, match parity, frozen resumable multi-session reconciliation/recovery, concurrent registration/scan/cancel proof |
| CF-19 Notifications | Durable scoped inbox and IN_APP intents, after-commit publication/reconciliation | All-trigger audience catalogue/snapshots, durable email/push workers, dedupe/leases/retry/dead-letter/replay, stale reminder suppression, receipts and truthful delivery states. Existing email retries are an in-memory queue; callbacks alone cannot survive process failure |
| CF-20 Calendar | Own registered-event list | Timetable/duties/occurrences, privacy-safe clashes, versioned updates and authorized platform export/reminders |
| CF-21 Readiness | Three read-only approval/operations/policy checks | Assigned checklist tasks, resource/staff commitments, readiness owner/status, fair waitlist offers/deadlines/reminders and explained uncertainty; home route is missing |
| CF-22 Insights | Ratings/comments/averages | Eligible event-scoped feedback authorization, confidential moderation/review, aggregate privacy thresholds, utilization/disruption analytics and explained human-reviewed recommendations |
| CF-23 Disruptions | Registered-event schedule-change history | Verified closure/maintenance authority, impact preview, accountable organizer tasks, expiry/update/cancel, human approvals and rehearsed continuity fallback |
| CF-24 Faculty | Scoped operations/recruitment after approval slices | Shared event proposal/edit/admin-review/approval-to-workspace journey; assigned approver/grant model, full operator workflows and negative-role evidence |
| CF-25 Recruitment | Verified application/accepted roster | Stable authenticated share link/identity fields, rejection/reapply policy, accepted membership and task/gate coupling, notifications and concurrent roster proof |
| CF-26 QR policies | SINGLE/DUAL/NO modes and checkpoint configuration | Complete entry-only vs point-attendance semantics, live bounded checkpoint notification, high-assurance owner confirmation, approved duration cycles and corrected policy evidence |
| CF-27 Delegation | OFF/ALL/SELECTED and separate manual grants/services | Full consuming scanner and revoke UI, owner/manual fallback, grant expiry/revocation contention, newly accepted roster behavior, idempotent changes and live/device proof |
| CF-28 Exports | Identifier sorting and CSV/XLSX projections | Reachable shared download/log actions, full identifier-safe comparators, frozen final report/revisions, unresolved quality, checksum/manifest/READY-FAILED jobs and private access |
| CF-29 Runtime | Module booleans, four action switches, protected foundations, admin revision/audit | Every business function catalogue, scope/compatible-client evaluated manifest, idempotent reviewed changes, dependency preview/drain/error states, live multi-instance channel/job fencing. Retire legacy unversioned mutators. Library OFF currently blocks safe exit; add recovery/draining |
| CF-30 Media | Immutable acceptance/expiry and tracked-local purge attempts | Fix P0 access/hold; object gateway/quarantine, all file families/derivatives/versions, owner metadata, hold governance, purge proofs/retries, cache invalidation, backup/restore deletion suppression and provider qualification |

Additional accepted scope: alumni needs verified graduation/account linking, scoped reviewers and actual mentorship request/accept/decline; campus feed needs authorized moderated content and private media; assistant needs ACL-filtered approved retrieval/tools, accurate deterministic answers, citations/escalation, multilingual injection/leakage evaluation and approved provider/model handling. Operations console currently reads feature/media inventory; it is not evidence of production monitoring or incident coverage.

## 4. Wiring and traceability defects hidden by completion labels

- [App.tsx](cujammu-pulse/src/App.tsx), line 179: `volunteers` and `readiness` are absent from route mapping and fall back to home. Most tile CTA/body text falls back to venues at line 240.
- [api.ts](cujammu-pulse/src/api.ts): credential issuance, scan-intent creation, report URL, scanner revoke, pending hostel intent and volunteer task functions exist without screen consumers.
- [AttendanceScreen.tsx](cujammu-pulse/src/attendance/AttendanceScreen.tsx): pending owner confirmation only; no complete QR display/camera/report workflow.
- [EventsScreen.tsx](cujammu-pulse/src/events/EventsScreen.tsx): cancellation handler is not rendered; an empty successful catalogue renders a misleading perpetual loader.
- [LibraryScreen.tsx](cujammu-pulse/src/library/LibraryScreen.tsx), line 23: “Scan entry QR” only posts purpose; no scanned challenge. Timer uses local clock without calibrated server offset.
- [ScoreCardsScreen.tsx](cujammu-pulse/src/academics/ScoreCardsScreen.tsx): official PDFs remain unavailable. Numeric result review is not a full official-document workflow.
- [registry.ts](cujammu-pulse/src/registry.ts) has 24 module entries, not 29 modules. Active CF count and module count are different; `implemented:true` is not screen/API/persistence/security acceptance.
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md), lines 462–558, overstates complete implementation and misassigns CF-19..23 / CF-25..28. Restore normative IDs: CF-19 notifications, CF-20 calendar, CF-21 readiness, CF-23 disruption; CF-25 recruitment, CF-26 QR policy, CF-27 delegation, CF-28 exports. Preserve historical evidence but replace the current blanket completion verdict with validated per-action status.

Required acceptance matrix columns: requirement → actor/resource scope → actual screen/action → versioned API → persisted state/audit/outbox → positive/negative/retry/race scenario → web evidence → Android evidence → production-store evidence → release blocker/owner. A registry-only test cannot certify the matrix.

## 5. Production packaging and operations backlog

- Backend Dockerfile skips tests, uses image tags without reviewed digests, runs without a non-root USER and gives uploads `chmod 777`. It does not build/copy universal web `dist`, although runtime expects it outside backend.
- Upload environment naming is inconsistent (`APP_UPLOAD_DIR` vs `UPLOAD_DIR` / `app.upload.dir`); fix and test a custom storage path.
- Healthcheck requests `/actuator/health`, while public permit is `/api/health`. Validate readiness without redirects; a login page must not count as healthy. Public health currently returns UP/200 despite DB failure and exposes diagnostic errors/queue/provider state; separate liveness, readiness and protected diagnostics.
- OTP/auth/API throttling is process-local. Build distributed restart/multi-node bounds, shared-NAT policy and meaningful abuse telemetry.
- No pipeline/IaC/security scan configuration or complete dedicated release/restore/incident evidence was found in the inspected repository inventory. Actuator and an operations screen do not prove responders receive alerts.
- Build CI for backend/shared web/native, generated contracts/parity, SBOM and reviewed dependency/secret/SAST/container/IaC scans, signed artifacts and staging authenticated assessment. Do not claim CVE-free status without actual scan/triage/retest.
- Configure private production topology/keys/audit custody, redacted metrics/traces, SLOs/alerts and named responders; measure actual delivery to a responder.
- Rehearse DB + identity + file/key restore, deletion suppression, failure/rollback and incident drill with approved RPO/RTO. Replication alone is not backup.
- Complete institutional identity/policy/data inventory/provider/privacy decisions, accessibility/load/concurrency/device tests, independent authenticated security assessment and office UAT; then request university production sign-off.

## 6. Recommended implementation sequence and done criteria

| Group | Concrete output | Gate before moving on |
|---|---|---|
| 1. Build + truthful inventory | Repair structural source contracts/toolchain; correct completion/CF mapping; baseline current tests | Clean backend compile/package + full tests; fresh web checks; explicit native limitation |
| 2. Authorization + certificate/file policy | Lost/found scoped DTO/state commands, private expiry gateway, correct evidence/issuance rules | Independent negative HTTP/object tests, entry-only certificate denial, expiry/hold/legacy-path denial |
| 3. Production foundation | Reviewed Mongo migration/rollback, university identity/BFF/native transport, object store/worker contracts | Synthetic staging replica-set transaction/index/contention evidence; login/revocation/account-switch tests |
| 4. Physical campus vertical slices | Complete hostel, venues, library, event QR/evidence/reports on both clients | Real camera/device + browser flows, duplicates/revocation/races, durable private reports, no fake success |
| 5. Academic and remaining domains | Private academics/timetables, chat/teams/judging/volunteers/services, alumni/feed/polls | Each scope completes real screen + API + persistence + authorization + audit/outbox and corrected traceability |
| 6. Cross-domain reconciliation | Notifications/calendar/readiness/disruption, full runtime control/media lineage | Crash/restart/multi-instance, stale reminder, safe OFF/drain, restore expiry and live catch-up tests |
| 7. Release qualification | Packaged web/Android/backend, CI/scans/signatures, monitoring/restore, independent security, office UAT | Required release evidence, no unresolved Critical/High findings, named exception owners where allowed, university approval |

This sequence preserves the accepted full scope. External credentials/policy/sign-off block their dependent external step; they do not prevent reversible local implementation using synthetic fixtures. No precise completion percentage or launch date is supported by current evidence.

## 7. Normative references

Local authority: `UNIVERSITY_PLATFORM_ARCHITECTURE.md`, `CORE_FEATURES_SPEC.md`, `SCHEDULING_AND_NOTIFICATION_SPEC.md`, `EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md`, `RUNTIME_FEATURES_AND_MEDIA_SPEC.md`, `PLATFORM_PARITY_AND_ACCEPTANCE.md`, project `AGENTS.md`. Historical review/handoffs were used as leads and checked against current source; their past successful tests are not current verification.

Primary technical cross-checks: [MongoDB transaction production considerations](https://www.mongodb.com/docs/manual/core/transactions-production-consideration/) and [Expo OAuth/OpenID authentication](https://docs.expo.dev/guides/authentication/). Mongo transaction acceptance requires the appropriate real topology; Expo native PKCE and protected storage require an implemented platform flow rather than shared registry declarations.
