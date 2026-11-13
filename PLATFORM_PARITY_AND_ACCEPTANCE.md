# CUJAMMU-PULSE: Shared UI, Workflow and Release Contract

Version 1.4 | 2 October 2026 | Normative target contract; these features/tests are specified, not reported as implemented or passed.

The 29 active requirements CF-01..CF-30 excluding retired CF-12 are defined in [CORE_FEATURES_SPEC.md](CORE_FEATURES_SPEC.md), with lifecycle/smart additions in [SCHEDULING_AND_NOTIFICATION_SPEC.md](SCHEDULING_AND_NOTIFICATION_SPEC.md), event QR/volunteer contracts in [EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md) and runtime controls/media deletion in [RUNTIME_FEATURES_AND_MEDIA_SPEC.md](RUNTIME_FEATURES_AND_MEDIA_SPEC.md). CF-12 is retired without ID reuse. Every active requirement must link to its feature/action/API/scenario IDs; current user-directed scope supersedes historical feature lists.

## 1. Definition of same experience

Android and website are required release clients. iOS is a possible later build of the shared source, not an additional promised deliverable. Both required clients use the same screens, ordered walkthrough, navigation labels, action names, forms, information, validation, authorization outcomes, confirmation dialogs, state transitions and server-side effects. Equal viewport/font scale uses shared layout rules. Central responsive breakpoints apply on all supported screen sizes.

Both clients must implement staff/admin workflows as well as student journeys. App convenience cannot remove a web feature; web convenience cannot introduce a feature unavailable in the app. A module is incomplete until both clients and the same backend permissions pass the same scenario specification.

OS permission prompts, secure SSO browser, keyboard/font rendering, file selection, camera/torch hardware, background execution and push delivery differ physically. Adapters preserve application workflow and expose capability/failure honestly; never display an operational success from a mock or unsupported API. Browser/device constraints cannot be solved by weakening privacy controls. Any unavoidable capability restriction requires a recorded ADR and owner-approved equivalent workflow before the feature counts as parity-complete.

## 2. Design and walkthrough

Shared first-use walkthrough: university purpose -> privacy and account verification -> event/library/venue/hostel features -> contextual permissions explained -> Get Started. Skip does not bypass mandatory notices/verification. Contextual permission is requested only when needed, not blanket camera/location permission on first launch.

Shared signed-in flow: verified identity -> relevant dashboard -> role/scope-aware module selection -> clear workflow progress -> review/confirm action -> server result -> timeline/history/notification. Role switching uses actual grants and never self-selects authority. Session expiry preserves permissible drafts and restores context after reauthentication.

Design-system ownership covers navy/gold identity, Light/Dark/Auto tokens, accessible typography, restrained floating surfaces/action dock/assistant, shared breakpoints, reduced motion and keyboard/screen-reader behavior. Status always includes words/icons, not just red/green. Critical actions remain spatially stable. A floating assistant is dismissible and never obscures another action.

## 3. Feature/action matrix

The following action sets must exist on both clients, scoped by the same policy. Labels are initial product vocabulary, centrally translated and reviewed before implementation.

| Feature ID | Shared screens / workflow | Shared actions |
|---|---|---|
| IDENTITY | Walkthrough, secure sign-in, verification, profile, sessions, privacy | Skip, Next, Get Started, Sign In, Edit Profile, Review Sessions, Sign Out, Request Correction |
| THEME_BRANDING | Shared CUJAMMU-PULSE circular logo, theme preview/preferences, accessible walkthrough | Light, Dark, Auto (System), Save Preference |
| REGISTRY | Departments/clubs/terms/facilities/hostels, verified imports | Add, Edit, Deactivate, Preview Import, Validate, Submit, Approve |
| EVENTS | Discovery, detail, proposal wizard, review, organizer dashboard | Search, Filter, Create Event, Save Draft, Submit, Request Changes, Approve, Reject, Publish, Reschedule, Cancel, Complete |
| REGISTRATION | Eligibility, custom form, registration/offer, own registrations | Register, Join Waitlist, Accept Offer, Cancel Registration, View Pass |
| VENUES | Venues & Grounds 24-hour green/red/yellow schedule, facility detail, instant/pending request, readiness/relocation | Check Availability, Book Venue, Request Booking, Confirm, Reject, Cancel, View Conflicts, Request Relocation, Record Handover |
| QR_ATTENDANCE | SINGLE_QR / DUAL_QR / NO_QR, own entry/activated second card, pending owner proof, scoped scanner/manual and evidence/status | Configure Mode, Select Evidence Policy, Show Entry Pass, Enable Attendance QR, Close Attendance Window, Scan Entry, Scan Attendance, Scan Exit when configured, Confirm Scan, Manual Attendance, Request Correction, Review Correction, Download Attendance CSV, Download Scan Log CSV, Download Color-Coded XLSX |
| LIBRARY | Study/Book Issue scanner, server-anchored HH:MM:SS timer, active visit/history, librarian occupancy/review and midnight reports | Scan Study QR, Scan Book Issue QR, Confirm Exit, View History, Request Correction, Review, Download Daily CSV |
| HOSTEL | Request, own pass/history, warden queue, guard scan, exceptions | Apply Pass, Submit, Approve, Reject, Cancel, Request Extension, Scan Departure, Scan Return, Review Exception |
| CHAT | Group directory/membership, WhatsApp-style thread, Night-Silence countdown, attachment, reports, event cleanup preview | Open Group, Send, Attach, Edit, Delete, Report, Mute, Review Report, Quarantine, Restore, Delete Temporary Group, Keep for Now |
| FEED | Campus posts, official announcements, report/moderation | Create Post, Publish Announcement, Like, Comment, Report, Review |
| SPORTS | Tournament, roster, fixtures, match scoring, standings/results | Register Team, Generate Fixtures, View Clash, Start, Record Score, Correct Score, Suspend, Resume, Reschedule, Confirm Result, Appeal |
| AUDIENCE_POLLS | Eligible audience question/options, own vote receipt, live percentages, final results | Create Poll, Preview, Publish, Vote, View Results, Close Poll, Finalize Result |
| TEAMS | Team/invite/roster/submissions | Create Team, Invite, Join, Leave, Review Roster, Lock Roster, Submit Entry |
| JUDGING | Assigned entries, rubric, scores, results/appeals | Declare Conflict, Save Score Draft, Submit Score, Review Round, Publish Result, Appeal, Record Decision |
| FACULTY_OPERATIONS | Approved faculty/organizer event workspace, sessions, volunteer recruitment, permissions, attendance and reports | Create Event, Submit, Open Operations Workspace, Configure Sessions, View Change History |
| VOLUNTEERS | Verified basic-details/roll-enrollment recruitment, accepted roster, assignments/hours and certificate | Open Volunteer Registration, Copy Recruitment Link, Register as Volunteer, Review Volunteers, Accept, Reject, Close Registration, Rotate Link, Assign Shift, Remove Volunteer, Submit Hours, Review Hours, Complete, View Certificate |
| ATTENDANCE_PERMISSIONS | Default-OFF scanner master, ALL accepted/SELECTED actions/windows, independent manual delegation | Enable QR Scanning, Disable QR Scanning, All Accepted Volunteers, Select Volunteers, Review Current Permissions, Grant Manual Attendance, Revoke Manual Access |
| ACADEMICS | Timetables, staged batch, roll mapping/review, own SGPA/CGPA and official PDF | Upload Batch, Preview Mapping, Validate, Submit Review, Verify, Publish Verified Batch, View Official PDF, Download Official PDF, Print, Request Correction, Reissue |
| ALUMNI | Account linking, profile/visibility, mentorship/jobs | Verify Alumni, Edit Visibility, Opt In, Withdraw, Request Mentorship, Post Job, Report |
| CERTIFICATES | Own issued documents, authorized issuance/revocation, verification | Preview, Download, Share, Verify, Issue, Revoke, Reissue |
| SUPPORT | Lost/found category/photo/claim log, numbered CUJ-TKT grievance ticket, assigned support/status | Filter Category, Report Item, Upload Photo, Claim, Verify Claim, Record Handover, Open Ticket, Reply, Escalate, Resolve, Reopen |
| LIFECYCLE | Event/match postponement, nonbinding next-slot suggestions, impact/approval, cancellation/change history | Postpone, Find Next Available Slots, Preview Impact, Submit Change for Approval, Confirm New Schedule, Cancel Event/Match, View Change History, Acknowledge Change, Withdraw Registration |
| CAMPUS_CALENDAR | Own timetable/events/matches/duties, clash warning and revised calendar entry | View My Calendar, Check Clash, Add/Update My Calendar, Review Source, Manage Calendar Connection |
| READINESS | Organizer checklist, resource/staff risks, responsible owner and due time | Review Readiness, Assign Owner, Update Task, View Resource Alternatives, Review Waitlist Offer |
| FEEDBACK_INSIGHTS | Optional feedback, governed operational aggregates and explained recommendations | Submit Feedback, Report Issue, View Insights, Review Recommendation |
| DISRUPTIONS | Verified closure/maintenance windows, dependency impact, review tasks and recovery banner | Preview Impact, Submit Notice, Approve Notice, Publish, Review Affected Activities, Resolve Notice |
| NOTIFICATIONS | Shared inbox, priority banner, delivery/preference settings and authorized receipt summary | Open, Mark Read, Acknowledge Change, Manage Preferences, View Notification Status, Review Delivery, Retry Authorized Delivery |
| ASSISTANT | Conversation, approved citations, tool preview, help escalation | Ask, View Source, Preview Action, Confirm Authorized Action, Cancel, Give Feedback, Open Support |
| GOVERNANCE | Scoped policies/grants/feature controls/audits | Request Grant, Approve Grant, Revoke, Edit Policy, Review Audit, Toggle Approved Feature |
| FEATURE_CONTROL | Admin module/feature/business-function catalogue, dependency/safe-off preview, effective revision and compatible-client visibility | Enable, Disable, Preview Impact, Confirm Change, View Effective State, Review Change History |
| MEDIA_LIFECYCLE | Authorized private upload, immutable 30-day expiry, expiry placeholders, scoped purge/hold status | Upload, View Expiry, Download Before Expiry, View Deletion Status, Review Authorized Hold, Review Purge Failure |
| OPERATIONS | Minimal protected operational view and incident workflow | Review Alert, Acknowledge, Record Incident, Escalate, Review Runbook |

Infrastructure administration (database/KMS/CI/SIEM consoles) remains outside ordinary app UI and uses separate privileged access. The platform's protected operational summaries and incident actions exist on both clients; exposing root-level infrastructure consoles inside the app is not a parity requirement.

## 4. Implementation registry

Implement a version-controlled machine-readable registry before domain screen development. Each entry includes:

```json
{
  "featureId": "HOSTEL",
  "screenId": "hostel.pass.review",
  "actionId": "hostel.pass.approve",
  "labelKey": "hostel.approve",
  "operationId": "approveHostelPass",
  "requiredCapability": "hostel.pass.approve",
  "scope": "assignedHostel",
  "allowedStates": ["REQUESTED"],
  "clients": ["android", "web"],
  "scenarioIds": ["HOSTEL-APPROVAL-01", "AUTH-SCOPE-01"],
  "states": ["loading", "empty", "ready", "pending", "success", "denied", "conflict", "expired", "offline", "error"],
  "platformAdapters": ["auth", "notification"]
}
```

This is a required design example, not an existing application permission engine. Backend capability checks remain authoritative. Unknown flags fail closed for privileged modules; one canonical flag key controls both clients/API/jobs. Extend every business action with its module/feature/function control keys, dependency keys, supported-client implementation version and safe-off behavior. Apply effective control revisions to routes, menus, search, buttons, dashboards, assistant tools, realtime subscriptions and jobs. Compatible implemented clients receive changes without releases; disconnected clients cannot learn new state instantly and must revalidate before online use. Enabling a flag never grants permissions or invents missing client code. CI compares registry, routes, handlers, translations and test scenarios. Every interactive control has an action ID and exercised outcome; decorative elements are not rendered as fake buttons.

## 5. Cross-client validation

Shared scenario files drive web Playwright and native device tests (such as Maestro), with separate adapters. Browser device emulation does not test native Android. Matched-viewport screenshots assess layout/tokens/hierarchy with explicit tolerances for renderer/font differences; do not claim byte-identical pixels. Test supported browsers/Android releases, real camera hardware, screen readers, keyboard-only web, font scaling, slow network, offline transitions, low memory and interrupted uploads. Define supported-device matrix during foundation and publish it.

Mutation tests inspect server state/audit/outbox, not merely toast text. Every role is exercised with valid and invalid resource assignments. Same account must see committed changes across clients; revoked permissions must stop both clients and active subscriptions.

## 6. Mandatory correctness and security scenarios

| Test ID | Adversarial / failure case | Required result |
|---|---|---|
| AUTH-NATIVE-01 | Android client has no approved OIDC/PKCE provider/session; caller tries any credentials, guessed/static OTP, local role choice, or password reset | No credential/OTP is accepted or disclosed, no account/session/reset success is claimed, and protected routes remain inaccessible until a server-verified session exists |
| ANDROID-NET-01 | Debug API endpoint targets a non-emulator host, or release endpoint is absent/non-HTTPS/contains userinfo/query/fragment; request logging is enabled | Debug permits only the explicit emulator loopback endpoint; release packaging requires a configured HTTPS base URL; API bodies and credentials are not written to logs |
| AUTH-ROLE-01 | Registration submits ADMIN/warden/judge authority | Rejected or ignored; only verified provisioning can grant |
| AUTH-SCOPE-01 | Student/organizer/warden accesses another person's or scope's resource | No data or mutation; safe denial and audited signal |
| AUTH-REVOKE-01 | Grant/session revoked while token and websocket exist | Sensitive API and channel access stopped within documented bound |
| AUTH-SWITCH-01 | Sign out/account switch after private queries and offline activity | No old-user state/attachments visible; pending operator evidence isolated under approved policy |
| DATA-DTO-01 | Nested response/export/log captured | No password hash/reset/session/QR token or unauthorized fields |
| WEB-CSRF-01 | Cross-origin forged cookie-authenticated mutation | Denied; ordinary same-origin workflow succeeds |
| DB-SEAT-01 | 100 simultaneous eligible requests for 10 session seats | Exactly 10 allocated; remaining ordered by committed server queue sequence |
| DB-WAITLIST-01 | Concurrent cancellations/promotions/offer expiry; queue has multiple waiters including disabled accounts; event is cancelled, past, or registration deadline has passed; timestamps are reordered | Event guard serializes admission and generated registration ID is the JPA queue sequence; at most the oldest enabled waiter is promoted for an approved future event before its registration deadline; disabled accounts and closed/past/deadline-expired events receive no promotion. Separate offer acceptance/deadline/reminder lifecycle and real concurrent Mongo proof remain required |
| DB-MONGO-02 | Concurrent event cancellation and admission; same cancellation key is retried concurrently or reused for registration; stale lifecycle revision | One lifecycle transition wins under a shared Mongo transaction guard; cancellation retry returns its immutable receipt with one actor-scoped idempotency, audit and outbox record; cross-command key reuse and stale revisions fail without a partial transition. This remains foundation-only until wired through the platform's authorized event lifecycle and tested against a real replica set |
| DB-VENUE-01 | Concurrent overlapping bookings incl. maintenance/buffers | Only conflict-safe authorized allocation commits |
| DB-VENUE-02 | Caller supplies high priority / multiple conflicts | Cannot elevate authority; all conflicts adjudicated |
| VENUE-BOOKING-RETRY-01 | Same owner retries identical booking after an uncertain response or reuses its key with changed command | Identical retry returns original receipt; changed content is rejected; unique key and facility guard prevent duplicate allocations |
| VENUE-BOOKING-CANCEL-01 | Requester cancels own pending booking, changes reason/version, another account guesses ID, retry follows lost response | Own pending cancellation requires reason and expected version; facility guard serializes release; identical retry returns cancelled result; other owners and finalized bookings denied |
| VENUE-BOOKING-OWNER-01 | Refresh session, guess another booking ID, request oversized page, feature OFF/cache response | Bounded no-store history is derived only from authenticated account; response allowlists status/time/facility details; no other owner's data appears |
| SCHEDULE-05 | Search 1-30 days for a 15-720 minute venue slot around overlapping, cancelled and preempted bookings | Bounded suggestions omit known overlaps, expose no competing booking details, state unchecked constraints and never reserve; booking confirmation rechecks the guard |
| SCAN-01 | Expired/wrong-purpose/session/cancelled/waitlisted pass | Rejected with meaningful safe result |
| SCAN-02 | Repeated request and repeated exit after longer wait | One original action; participation duration cannot increase |
| SCAN-OFFLINE-01 | Same QR at disconnected gates, restart and sync retry | Explicit provisional/conflict policy; dedupe; no auto certificate |
| SCAN-OFFLINE-02 | Valid observed scan syncs after expiry; scan was already expired when observed | Bounded evidence/revocation policy reconciles former; latter rejected; uncertain time reviewed |
| QR-CHALLENGE-01 | Shared venue challenge submitted with another person's ID | Server-derived authenticated subject wins; client impersonation denied |
| LIBRARY-01 | Concurrent entry; missing exit; holiday closing | One active visit; bounded estimated closure, accurate occupancy |
| HOSTEL-01 | Unapproved/revoked/wrong-hostel pass; return before exit | Denied; guards cannot approve or read private reasons |
| CHAT-01 | Removed member reconnects/subscribes/downloads attachment | Access denied; membership change not merely hidden in UI |
| SPORT-01 | Duplicate/out-of-order score events and corrections | Deterministic replayed score and audit; no double-count |
| JUDGE-01 | Self/conflicted judge, altered rubric, repeated submit | Forbidden/controlled; submitted version cannot silently change |
| ACADEMIC-01 | Wrong roll mapping, malicious PDF, uploader publishes own batch | Quarantined/denied; independent verified mapping and approval |
| ACADEMIC-02 | Student changes result ID or downloads old signed URL after revoke | Ownership/version policy enforced; expiry/revocation window documented |
| ACADEMIC-REVIEW-01 | Unassigned/cross-department publisher or reviewer; administrator attempts generic publication | Department-scoped active grant required at command time; generic admin has no publisher/reviewer authority |
| ACADEMIC-REVIEW-02 | Submitter reviews own item; two reviewers race; duplicate submit retries; result is pending/rejected | Self-review denied; review transition row-locked and single-final; identical pending retry stable; students see only published records |
| ACADEMIC-REVIEW-03 | Revoke authority while queue is open; reviewer submits stale item | Grant row lock orders revocation against command; later command is denied; stale final decisions are rejected |
| ACADEMIC-REISSUE-01 | Correct a published or returned result; retry the same submission concurrently; a new revision is awaiting or denied review; publisher lacks department grant or tampers with student/department identity | Shared publisher UI lists only granted departments, sends stable UUID idempotency key, and requires correction reason for existing results; server derives student/department and authorizes current grant; new numbered revision keeps old published result visible until newer revision approval; mismatched key reuse is denied |
| ACADEMIC-BATCH-01 | Oversize/malformed CSV, duplicate student-term rows, wrong department, inactive/unverified identifier, hostile values | Preview-only parser enforces exact headers, 500 KB/250-row bounds and field/GPA constraints; flags every duplicate term row, bulk-matches active verified students only in the assigned department, returns no raw file or saved results; later commit must repeat all checks transactionally |
| ACADEMIC-BATCH-02 | Batch retry, concurrent same-student writes, one invalid row among valid rows, correction without reason, revoked publisher | One bounded transaction revalidates grant and all identities under sorted student locks; all rows enter the independent review queue or none do; request key is bound to SHA-256 content/department and returns the same batch receipt on replay; revisions remain separately reviewed |
| ACADEMIC-BATCH-03 | Submitter attempts own-batch review; row-level reviewer bypasses batch decision; two reviewers race; row tampering or partial notification failure | Department-scoped reviewer sees bounded batch details, cannot review own submission, and acts with expected batch version; batch and all rows lock in stable order and change all-or-none; individual queue excludes batch rows; scoped notices and audit commit with the decision |
| ALUMNI-01 | Graduation/email transition and directory withdrawal | No stale student powers; authorized record metadata retained by policy, uploaded copies expire; directory disclosure stops |
| FILE-01 | Polyglot/oversize/parser-bomb/remote URL upload | Quarantine/reject, bounded sandbox, no SSRF/network fetch |
| OUTBOX-01 | Worker crash after provider accepts, before acknowledgement | Idempotent retry/reconciliation; no claimed exactly-once guarantee |
| AI-01 | Prompt injection, impersonation, hidden data instruction, malicious citation | No unauthorized context/tool/action; safe refusal/escalation |
| AI-02 | Cross-user cached answer, revoked scope, model provider outage | Cache isolation/revalidation; non-AI fallback; no leak |
| OPERATIONS-01 | Primary failover, audit outage, old backup, restore | Measured recovery; incidents visible; restricted risky operations on audit failure |
| PARITY-01 | Every enabled action across clients/roles/device states | Same workflow/policy/outcome; no fake controls or client-only completion |
| CORE-COVERAGE-01 | Trace 29 active IDs CF-01..CF-30 except retired CF-12 to both clients, APIs and tests | Complete coverage including runtime control/media deletion; retired ID never reassigned |
| THEME-01 | Light/Dark/Auto, changed OS preference, restart/sign-in on another client | Shared persisted preference, correct system following and accessible tokens/logo |
| DEPARTMENT-01 | Initial 22-entry approved registry, repeat seed, newly added department | No duplicate/overwritten records; shared dynamic chips/selection |
| LIBRARY-TIMER-01 | App/browser sleeps, refreshes or changes device wall clock | Timer uses accepted server entry and calibrated time; no tick drift or fabricated final duration |
| LIBRARY-MISMATCH-01 | STUDY visit exits via Book Issue QR or raw ENTRY context | Clear mismatch/action rejection; active visit and mismatch evidence preserved |
| LIBRARY-REPORT-01 | Midnight date rollover, open visit, empty day, missed job/retry and corrected exit | Previous local-day scoped CSV once per revision; catch-up, status/quality and history correct |
| LIBRARY-EXPORT-01 | Student/unassigned admin requests legacy export/date/library | Same report-service authorization; no private visitor data leak |
| LIBRARY-LEGACY-01 | Admin enables library while legacy scan/export routes are called directly | Unsupported routes return explicit unavailable response; no visit is created or private report returned |
| ATTENDANCE-80-01 | Approved duration policy: exactly 80%, below, out-of-window/re-entry/missing exit or only checkpoint | Precise sufficient interval evidence; checkpoint alone never satisfies duration; truthful eligibility/status |
| REPORT-FORMAT-01 | Export status report from both clients | CSV contains status/color labels; XLSX contains real row styling; same authorized rows/formula safety |
| HOSTEL-CURFEW-01 | Configured 21:00/19:30, approved extension and date-spanning Night-Out | Institution policy controls LATE_ENTRY with reason; no gender-only hard-code |
| CHAT-SILENCE-01 | Send at exactly 22:00 / 07:00, changed client clock, direct API | Correct server [22:00,07:00) gate and permitted emergency exception |
| CHAT-CLEANUP-01 | Completed-event cleanup repeated while hold/permanent groups exist | Event-only revoke/purge; held evidence protected; permanent records untouched |
| TEAM-CODE-01 | Code collision/brute-force/retry/expired invite/concurrent last roster seat | Secure scoped code; no unauthorized join, duplicate member or team overfill |
| TEAM-OWNERSHIP-01 | Student own-team history after deadline; outsider reads; leader-only invite; pagination and late responses | Private roster remains readable without granting mutation rights; stale group/event results never replace current scope |
| TEAM-CONTENTION-01 | Concurrent different-team joins and registration cancellation against join | One event membership at most; cancelled registration cannot coexist with newly accepted membership; revisions advance on membership changes |
| CHAT-CONTAINMENT-01 | Self-declared department, unrelated faculty role, silence boundaries, terminal event, feature OFF, legacy cleanup | Unverified department access denied; no global-role posting bypass; no hold-unaware deletion; server controls and client clearing agree |
| CHAT-MEMBERSHIP-01 | Verified admin grant/revoke, editable department mismatch, self-grant, stale revision, disabled account, concurrent revoke/read/post, directory paging | Explicit scoped membership is required; provenance retained; generic admin cannot read private messages; 20-row pages exclude outsiders; both shared clients expose the same commands |
| CHAT-POST-RETRY-01 | Lost response; concurrent identical sends; key reused with changed content; same key in another user/group; revoked or quarantined replay | One accepted message per scoped key; payload mismatch rejects; current authorization still applies; client retains key for explicit unchanged retry and deduplicates receipt |
| CHAT-POST-LIMIT-01 | Concurrent new posts at sender/group cap; exact 60-second boundary; duplicate at cap | Atomic database-backed bounds; quota rejection is 429 with safe retry guidance; replay does not consume quota |
| FEATURE-RECOVERY-01 | Chat disabled before a membership revocation | Protected admin inspection/revoke remains available with ADMIN/CSRF checks; new grants and ordinary chat remain blocked |
| RUBRIC-FOUR-01 | Four 0-25 values, invalid range, round version/tie and draft leakage | Valid 0-100 total, frozen policy and authorized live score breakdown |
| VOLUNTEER-HOURS-01 | Overlapping shifts, self-approval and certificate job retry | Only supervisor-approved nonoverlapping hours credited; one eligibility/issuance |
| LOST-CLAIM-01 | Two simultaneous claims, private evidence/photo and repeated handover | One verified handover; no public proof/contact leak; safe claim history |
| HELPDESK-ID-01 | Monthly rollover/concurrent creation/over 9999 tickets/guessed ID | Unique expanding display sequence, private opaque ID, access checks and factual SLA |
| POLL-VOTE-01 | Same user concurrent votes/retry, many users sharing IP | One vote per eligible subject; NAT users allowed; stable original receipt |
| POLL-WINDOW-01 | Ineligible/expired poll vote, revocation, changed clock or tampered totals | Server audience/window/policy gate; counts cannot be client-overwritten |
| POLL-RESULT-01 | Zero votes, rounding, realtime reconnect, reviewed disqualification | Committed aggregate percentages; private voters and audited corrected tally |
| POLL-LIFECYCLE-01 | Draft create retry/body mismatch, unrelated organizer, private draft, duplicate publish/close, stale revision and close-vote race | Event-scoped manager only; immutable validated definition, expected revision and stable retries; no accepted new vote after effective close |
| POLL-SCOPE-01 | Unregistered/waitlisted/cancelled account, guessed poll ID, disabled event/module, raw voter fields and delayed response after event change | Current eligible audience or scoped manager only; no voter list/IP/foreign choice; old scope cannot replace current shared-screen state |
| CLIENT-UPDATE-01 | Older installed Android and new web/API during staged rollout | Supported workflows stay compatible; incompatible risky actions safely require upgrade; rollback tested |
| SCHEDULE-01 | Immediate postponement without date versus pending planned change | Truthful POSTPONED/date-pending banner and notice; pending draft does not alter current effective schedule |
| SCHEDULE-02 | Two users confirm same suggested slot; closure/hold expiry intervenes | Fresh authorization/version/guard checks; one conflict-free result; no orphaned original booking. Current JPA slice locks the event lifecycle row, facility and single linked reservation then rechecks overlap; production replica-set race/closure/expiry evidence and explicit lifecycle revision remain pending. |
| SCHEDULE-03 | Cancel/reschedule races with register/scan and multi-session worker crash | One consistent lifecycle; no new post-cancel entry; visible frozen workflow, named recovery owner/deadline and compensation. The current JPA slice adds an authenticated request-key receipt and expected schedule-history revision guard; multi-session recovery and production replica-set evidence remain pending. |
| SCHEDULE-04 | No feasible slot, unknown private calendar, smaller venue/resource shortage | Explained bounded alternatives/unknowns; no private title leak or silent attendee eviction |
| CANCEL-01 | Cancel repeated after partial attendance or live sports score | Stable cancellation; safe physical exit/evidence preserved; scoped fixture/certificate review |
| CANCEL-02 | Multi-session cancellation releases resources while worker crashes/retries | Idempotent guarded release and task reconciliation; no leaked allocation, revived admission or duplicate closure notices |
| CANCEL-03 | Repeated cancellation/postponement, concurrent registration and affected inbox recipients | Lifecycle mutation serializes on event guard; only confirmed/waitlisted recipients receive safe inbox notice; duplicate terminal cancellation does not fan out again; private reason stays private. Request-key replay and stale schedule revision are rejected/replayed before mutation in the current JPA slice. |
| NOTIFY-01 | Registration/change commit races and fan-out worker restart | Complete scoped revision audience; one logical inbox item per recipient; recoverable bounded batches |
| NOTIFY-02 | Push disabled/offline/provider outage, reordered old message/reminder | Durable catch-up; current revision wins; stale reminder suppressed; truthful failure/receipt status |
| NOTIFY-03 | Quiet hours, organizer claims emergency, cross-user deep link/receipt | Approved priority rules; unauthorized override denied; no private data or receipt access |
| NOTIFY-04 | Approved load target and representative large audience | Measured 99% inbox <=10s and healthy-provider dispatch <=30s or release target renegotiated explicitly; lag/escalation visible |
| NOTIFY-05 | Shared inbox reads another subject's notice, marks another user's item read, feature disabled, response cached | Authenticated current-user allowlist only, no-store response, ownership-scoped idempotent mark-read, web/Android route and API both honor runtime disable |
| NOTIFY-06 | Recipient acknowledges the same operational notice repeatedly or guesses another user's notice ID | Acknowledgement is a separate persisted state from delivery, scoped to current user, idempotent, marks the item read and is shared across web/Android |
| CALENDAR-01 | Revised/cancelled occurrence, denied device permission, incomplete timetable | Shared own agenda and stable revision; opt-in equivalent; unknown data warning, no private calendar upload |
| CALENDAR-CLASH-01 | Own confirmed event overlap, adjacent intervals, unknown duration, other-user schedule, 50-result limit and feature disable | Explicit owner-scoped advisory read; half-open overlap; known conflicts first; bounded coverage warning; no registration mutation; lifecycle/revision distinct from registration; IST shared UI. Local HTTP and responsive browser checks pass; native/deployed evidence remains open. |
| STUDENT-VERIFY-01 | Admin exact-account lookup, active department selection, explicit official-source review, stale version, duplicate identifier, partial legacy state, uncertain reply and unauthorized actor | Private no-store DTO; server role/enabled checks; immutable first binding, unique identifier, atomic audit/inbox intent, no-write identical retry; shared UI recovers through authoritative lookup. H2/HTTP/browser evidence is local; Mongo/native/source-office acceptance remains required. |
| PROPOSAL-BOUNDARY-01 | Fresh owner/reviewer, spoofing/revocation, invalid edit POST, historical event edits, stale/concurrent decisions, retries, media replacement and audit failure | Owner checked before rendering; first-time proposals only; observed revision required; no self-review; atomic audit/outbox; duplicate decisions do not repeat effects; old assets retain immutable expiry. Legacy H2/MVC/browser checks do not complete assigned-review, publication separation, Mongo or shared/native parity. |
| PROPOSAL-ASSIGNMENT-01 | Unassigned/assigned proposals, active reviewer directory, organizer self-review, stale/reassignment races, feature shutdown, audit/outbox rollback and replay; real synthetic web submission through approval | Explicit active-admin assignment; only current assignee may decide; version/row locking, no-write retry and private audit/inbox intent; shared queue/decision UI; approved event becomes catalog-visible. Final local evidence: 84 focused tests; assembled 670-test package; web 16/16 responsive scenarios; Android JS export; loopback synthetic UI/API 3/3. Publication separation, production Mongo and installed-device/deployed parity remain open. |
| PROPOSAL-SUBMISSION-01 | Active faculty/organizer, valid metadata, missing/inactive role, same-key exact retry, same-key changed payload, simultaneous duplicate requests, uncertain client response, category lookup and disabled feature | Derive owner from authenticated principal; validate bounded command; serialize owner request-key use; persist immutable fingerprint receipt atomically with proposal/audit; exact retry returns original ID, key reuse conflicts, client cannot set owner/status/revision; shared web/Android-source form retries unchanged payload. H2 concurrent HTTP/service and responsive browser evidence is local only; Mongo, native installed-device, uploads and production checks remain open. |
| PROPOSAL-EDIT-01 | Owner-only detail, active role and feature check, pending/rejected lifecycle, stale revision, forbidden cross-owner access, rejected proposal resubmission and assignment invalidation, uncertain response reload | Return a bounded no-store owner DTO without media locators; require the observed lifecycle revision; preserve existing attachment bytes/deadlines; atomically update, audit and resubmit; clear old reviewer assignment so a new review must be explicitly assigned; stale/uncertain results reload authoritative state. Shared clients expose edit/resubmit only for PENDING/REJECTED. H2 and responsive browser evidence is local only; Mongo/native/device/attachment replacement remain open. |
| READINESS-01 | Missing staff/resource/accessibility, concurrent scarce-resource claim, offer expiry/retry | Named owner/blocker and guarded staff/equipment commitments; no automatic expenditure or unfair promotion |
| READINESS-02 | Readiness fetch fails, later succeeds, then refresh fails after confirmed checks are visible | Accessible retry recovers the live checklist; failed refresh preserves the last server-confirmed state and never reports readiness as newly confirmed |
| READINESS-03 | Facility booking is absent, pending review, confirmed, or unavailable | Resource confirmation passes only when at least one active event-linked facility booking exists and every active booking is confirmed; pending, absent, or unreadable evidence remains a blocker |
| READINESS-04 | Event manager creates a task, retries its request key, changes content under that key, or assigns a disabled/unrelated account | Same command replay returns the original event-scoped task; changed replay conflicts; only an enabled event organizer or administrator can own it; audit and task commit together |
| READINESS-05 | Two managers update the same task revision, update across event IDs, or mark the task complete without a resolution note | One current revision wins, stale writes return conflict, event ownership is checked on every command, completion requires a note from the assignee or an administrator, and task completion never certifies event readiness |
| READINESS-06 | Approval, operations, attendance policy, linked facility booking, accepted verified volunteers and open volunteer tasks | Roster/task counts are informational only; no approved staffing/referee target, shift-availability or accessibility evidence means coverage remains failed and overall readiness remains false |
| READINESS-07 | `MODULE_EVENTS` is disabled during readiness reads or concurrent task mutation, while the child flag is still configured on | Server routes and reads become unavailable; a mutation contends on the parent flag before the readiness flag; no child endpoint stays usable through stale client state |
| INSIGHTS-01 | Small group, feedback deletion/hold, predicted no-show | Approved privacy thresholds/purpose; honest anonymity, no automatic discipline |
| DISRUPTION-01 | Closure create/edit/remove races with booking, large campus scope, recovery/notice replay | Shared facility/resource and campus-scope guards enforce effective closure; scoped tasks/notice; no silent autonomous reschedule |
| FACULTY-01 | Verified faculty proposes event; unauthorized faculty manages unrelated event or approves own | Assigned approval/workspace/duties only; no universal event/admin/academic powers |
| VOLUNTEER-RECRUIT-01 | Approval retry, unapproved/closed/rotated recruitment link and duplicate applicant | One approved workspace; scoped authenticated window/link and unique application |
| VOLUNTEER-ID-01 | Missing/false/unmapped roll/enrollment, cross-person typed identifier, nonstudent operator | At least one verified student identifier required; safe office resolution and distinct staff provisioning |
| ATTENDANCE-MODE-01 | Single entry-only vs entry+attendance, dual checkpoint, NO_QR and policy edit after evidence | Frozen truthful outcomes; no NO_QR minting or retroactive interpretation; reviewed changes |
| ATTENDANCE-WINDOW-01 | Second QR before/open/close/reopen, late entrant, no entry, failed notification | Own dashboard card/current epoch; eligible bounded mint; entry prerequisite; durable audience notice without secret |
| SCANNER-MASTER-01 | OFF default/direct API/stale UI, owner tries closed action, QR activation without scanner enable | No scan accepted unless master, lifecycle and action window allow; separate controls |
| SCANNER-ROSTER-01 | ALL/newly accepted volunteer, SELECTED/unselected, applicant/remove/revoke race | Accepted eligible roster policy only; selected membership enforced; revocation/control contention |
| MANUAL-GRANT-01 | ALL/QR-only volunteer calls manual, manual-only scans QR, revoke/expiry, QR master OFF with valid NO_QR/manual window | QR/manual/export separated; authorized organizer/selected manual delegate succeeds independently of QR master; other paths denied |
| QR-OWNER-01 | Copied QR, wrong principal proof, expired pending intent, duplicate concurrent scanners | No final attendance without scoped owner proof and valid controls; one stable accepted receipt |
| QR-PRIVACY-01 | Credential/display/notification/log/export inspection on web/native | Verified identifier bound privately; no share/download/public QR, token or unnecessary personal disclosure |
| MANUAL-FALLBACK-01 | Dead phone, wrong/ambiguous/unregistered ID, operator records self, duplicate QR/manual | Scoped verified identity/reason; no arbitrary admission/self-credit; consistent action result and review |
| ATTENDANCE-EXPORT-01 | Mixed identifiers CS-2/CS-10, leading zeros, many pages and concurrent update | Global ascending versioned comparator, original strings, consistent watermark; summary includes absent users |
| ATTENDANCE-EXPORT-02 | Volunteer guesses report ID, formula-like ID, pending proof/offline, later correction | Scoped private formula-safe report; explicit provisional/final quality and new revision; no QR secret |
| ATTENDANCE-RESUME-01 | Event postponed/cancelled/rescheduled while scans/grants/windows active | Stale credentials/control rejected; safe exit preserved; new schedule needs reviewed reactivation |
| FEATURE-01 | Admin toggles implemented module/feature/function with web/native already open; lost update notification then reconnect | Same effective revision hides/shows routes, buttons, search and dashboards without release/code change; catch-up correct; measured propagation bound |
| FEATURE-02 | Direct API/assistant/job calls disabled action or enables child under disabled parent; stale cached flag | Server denies business action; dependency conjunction enforced; role/resource checks still mandatory when enabled |
| FEATURE-03 | Disable/revoke races with scan, registration, realtime subscription and queued task | Version/guard contention defines accepted-before vs denied-after boundary; channels/tools/jobs enforce current state; no client-only disable |
| FEATURE-04 | Unauthorized/scoped admin alters global toggle, stale revision/replayed change, CSRF | Scope/MFA/CSRF/version/idempotency controls; stable audited outcome and outbox; no loss of administration/recovery controls |
| FEATURE-05 | Disable during active library/hostel/event session, queued certificate, upload/purge or emergency | Named safe-off plan preserves necessary exits/recovery; no new disabled operations; purge/security/audit continue; no data deletion implied by disabling |
| FEATURE-06 | Enable feature absent on older client; offline client retains stale UI; account switches | Compatibility gate gives truthful supported path; no invented functionality/no silent bypass; online revalidation and private cache clearing |
| MEDIA-01 | Exact uploadAcceptedAt + 30 days UTC across originals, thumbnails, preview PDFs, quarantine, cached/AI derivatives | Authorization blocks at deadline; immutable common lineage expiry; private retryable physical purge with deletion evidence |
| MEDIA-02 | Worker crash, versioned object delete marker, multipart orphan, replicated copy, hold release | Idempotent tracked purge of actual versions/copies; no false PURGED status; retry/escalation and authorized hold lifecycle |
| MEDIA-03 | Restore older backup containing expired object; backup retention or CDN eviction delayed | Tombstone/expiry suppression before serving; bounded tracked backup/cache purge dependency; no resurrection or instant physical deletion claim |
| MEDIA-04 | Signed upload accepted before disable but completes afterward; malicious upload/deep link/signed URL after expiry; in-flight download | Completion reauthorizes feature/scope/expiry; quarantine/limits upheld; short URLs never outlive expiry; documented bounded in-flight revocation limitation |
| MEDIA-05 | Access/edit/re-enable/restart/replacement metadata attempts to reset deadline; unapproved hold or academic permanent copy | Original immutable expiry retained; replacements are new audited uploads, no automatic renewal loop; only explicitly authorized scoped reviewed hold, external official archive |

Execute DB tests against real replica-set MongoDB with synchronized contention and failure injection. Assert histories/counters, not only response totals. Test unknown transaction commit outcomes, referential migration integrity, schema/index migration and retry limits. Add property-based state-machine/invariant tests for runtime controls, expiry, eligibility and score replay.

### Core requirement traceability

These are required acceptance scenarios, not claims that the prototype already passes them. Each implementation must also record its screen/action IDs and OpenAPI operation IDs against the same requirement.

| Core requirement | Required scenario IDs |
|---|---|
| CF-01 Theme engine | THEME-01, PARITY-01 |
| CF-02 University branding | THEME-01, PARITY-01 |
| CF-03 Academic departments | DEPARTMENT-01, AUTH-SCOPE-01 |
| CF-04 Hostel passes and curfew | HOSTEL-01, HOSTEL-CURFEW-01, AUTH-SCOPE-01 |
| CF-05 Venues and grounds | DB-VENUE-01, DB-VENUE-02, VENUE-BOOKING-RETRY-01, VENUE-BOOKING-REVIEW-01, VENUE-BOOKING-CANCEL-01, VENUE-BOOKING-OWNER-01, SCHEDULE-05 |
| CF-06 Library turnstile and reports | LIBRARY-01, LIBRARY-TIMER-01, LIBRARY-MISMATCH-01, LIBRARY-REPORT-01, LIBRARY-EXPORT-01, LIBRARY-LEGACY-01 |
| CF-07 Configurable event attendance | ATTENDANCE-MODE-01, ATTENDANCE-WINDOW-01, SCAN-01, SCAN-02, ATTENDANCE-80-01, REPORT-FORMAT-01, SCAN-OFFLINE-01, SCAN-OFFLINE-02 |
| CF-08 Scorecards and timetables | ACADEMIC-01, ACADEMIC-02, ACADEMIC-REVIEW-01, ACADEMIC-REVIEW-02, ACADEMIC-REVIEW-03, ACADEMIC-REISSUE-01, ACADEMIC-BATCH-01, ACADEMIC-BATCH-02, ACADEMIC-BATCH-03, AUTH-SCOPE-01, PARITY-01 |
| CF-09 Community and event chat | CHAT-01, CHAT-SILENCE-01, CHAT-CLEANUP-01, CHAT-MEMBERSHIP-01, CHAT-POST-RETRY-01, CHAT-POST-LIMIT-01 |
| CF-10 Team registration | TEAM-CODE-01, DB-SEAT-01 |
| CF-11 Judging and leaderboard | JUDGE-01, RUBRIC-FOUR-01 |
| CF-13 Volunteer coordination | VOLUNTEER-RECRUIT-01, VOLUNTEER-ID-01, VOLUNTEER-HOURS-01, AUTH-SCOPE-01 |
| CF-14 Lost and found | LOST-CLAIM-01, FILE-01 |
| CF-15 Helpdesk | HELPDESK-ID-01, AUTH-SCOPE-01 |
| CF-16 Tournament brackets | SPORT-01, PARITY-01 |
| CF-17 Audience choice polls | POLL-VOTE-01, POLL-WINDOW-01, POLL-RESULT-01, POLL-LIFECYCLE-01, POLL-SCOPE-01 |
| CF-18 Lifecycle and availability suggestions | SCHEDULE-01, SCHEDULE-02, SCHEDULE-03, SCHEDULE-04, CANCEL-01, CANCEL-02, CANCEL-03, DB-MONGO-02 (foundation-only; not platform implementation evidence) |
| CF-19 Comprehensive notifications | NOTIFY-01, NOTIFY-02, NOTIFY-03, NOTIFY-04, NOTIFY-05, NOTIFY-06, CANCEL-03, AUTH-SCOPE-01 |
| CF-20 Personal campus calendar | CALENDAR-01, CALENDAR-CLASH-01, PARITY-01 |
| CF-21 Readiness/resource intelligence | READINESS-01, READINESS-02, READINESS-03, READINESS-04, READINESS-05, READINESS-06, READINESS-07, DB-WAITLIST-01 |
| CF-22 Feedback/operational insights | INSIGHTS-01, AUTH-SCOPE-01 |
| CF-23 Disruption/continuity | DISRUPTION-01, OPERATIONS-01 |
| CF-24 Faculty organizer workspace | FACULTY-01, PROPOSAL-BOUNDARY-01, PROPOSAL-ASSIGNMENT-01, PROPOSAL-SUBMISSION-01, PROPOSAL-EDIT-01, VOLUNTEER-RECRUIT-01 |
| CF-25 Volunteer recruitment | VOLUNTEER-RECRUIT-01, VOLUNTEER-ID-01, AUTH-SCOPE-01 |
| CF-26 Configurable QR/evidence | ATTENDANCE-MODE-01, ATTENDANCE-WINDOW-01, ATTENDANCE-80-01, QR-OWNER-01, QR-PRIVACY-01 |
| CF-27 Scanner/manual delegation | SCANNER-MASTER-01, SCANNER-ROSTER-01, MANUAL-GRANT-01, MANUAL-FALLBACK-01, ATTENDANCE-RESUME-01 |
| CF-28 Ordered attendance exports | ATTENDANCE-EXPORT-01, ATTENDANCE-EXPORT-02, REPORT-FORMAT-01 |
| CF-29 Runtime module/feature/function control | FEATURE-01, FEATURE-02, FEATURE-03, FEATURE-04, FEATURE-05, FEATURE-06 |
| CF-30 Uploaded-media expiry/deletion | MEDIA-01, MEDIA-02, MEDIA-03, MEDIA-04, MEDIA-05 |

The wider architecture also retains alumni, AI assistance, monitoring, audit and recovery requirements; the core list does not remove those modules or their security scenarios.

## 7. Production release gate

- Full feature/action matrix exercised on both clients; institution office UAT for each module.
- Reproducible locked build, test results, OpenAPI/registry compatibility and signed artifacts/SBOM.
- OWASP ASVS 5.0.0 Level 2 mapping and selected Level 3 controls; mobile MASVS assessment and API/LLM abuse tests with evidence. [ASVS primary repository](https://github.com/owasp/asvs), [MASVS](https://mas.owasp.org/MASVS/).
- No unresolved Critical/High security findings. Any lower-risk exception has accountable owner, expiry, compensating control and approval; a blocker is not renamed to ship.
- Independent authenticated web/API/mobile/infrastructure assessment and AI abuse review; retest remediations.
- Backup/restore/failover and incident drill meet approved RPO/RTO; alert test reaches named on-call responder.
- Privacy/data inventory, legal applicability register, retention/deletion/hold design and processor contracts approved.
- Load, accessibility, camera/offline and concurrency evidence; production capacity measured rather than inferred.
- Real production secrets absent from repo/CI artifacts; dev consoles/demo data/default credentials removed.
- Operators trained, support/privacy/security contacts confirmed, rollback/recovery limitations documented.
- University accepts release evidence and residual risk. “Production ready” is a gated release status; it is not claimed by this design document.

Testing tools are candidates, not installed for this task: [Playwright](https://github.com/microsoft/playwright), [Maestro](https://github.com/mobile-dev-inc/maestro), [MongoDB Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/). Review current compatible versions and licenses before adoption.

Venue booking review slice adds `VENUE-BOOKING-REVIEW-01`: organizer submission creates a pending interval hold; only an administrator may approve or reject; stale/concurrent decisions are rejected by version/locking; approval rechecks current facility and overlaps; rejection requires a reason; and the requester receives a safe scoped notice. This scenario is implemented in the current shared client/backend slice but has not passed its backend verification gate yet.

Pending-request cancellation adds `VENUE-BOOKING-CANCEL-01`: the requesting account may cancel only its own PENDING request, with a bounded reason and expected aggregate version. The same facility guard serializes the transition and frees the interval; matching retries return the cancelled receipt. Confirmed reservation cancellation remains policy-dependent and is not exposed by this command.
