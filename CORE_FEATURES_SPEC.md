# CUJAMMU-PULSE: Core Feature Baseline

Version 1.4 | 2 October 2026 | Owner-confirmed core requirements and authorized smart additions, not a claim that the prototype implements them.

This catalogue is normative with `UNIVERSITY_PLATFORM_ARCHITECTURE.md` and `PLATFORM_PARITY_AND_ACCEPTANCE.md`. All features below must ship on both web and Android with the same labels, steps, statuses and backend outcomes. Existing Thymeleaf/Compose class and file names are prototype references; production retains the shared-client architecture and reviewed platform adapters. Alumni, AI, monitoring and the other previously accepted modules remain in scope.

## 1. Core feature register

| ID | Feature | Shared product requirement |
|---|---|---|
| CF-01 | Theme engine | Light / Dark / Auto (System), shared semantic tokens; persist preference per user and respect system changes in Auto |
| CF-02 | University branding | CUJAMMU-PULSE, approved circular logo, same onboarding and navigation identity; accessible contrast and motion |
| CF-03 | Academic departments | Initial 22-department configuration from approved registry; dynamic extension, filter chips and account selection using stable IDs |
| CF-04 | Hostel / night curfew | Day-Out/Night-Out application, warden approval, dynamic pass, guard departure/return scans, curfew alert and LATE_ENTRY |
| CF-05 | Venues and sports grounds | 24-hour visual calendar, green/red/yellow states, conflict-safe instant booking where authorized, reviewed VC-office preemption |
| CF-06 | Library digital turnstile | Study vs Book Issue QR, live HH:MM:SS dwell timer, matching-purpose exit, midnight daily CSV |
| CF-07 | Configurable event attendance | Single/Dual/No-QR; entry versus enabled attendance checkpoint; separately approved duration evidence, private reports and truthful certificates |
| CF-08 | CoE scorecards / timetable | Batch mapping by verified roll number, reviewed one-click publication, own SGPA/CGPA badges and official PDF preview/download/print |
| CF-09 | Community / event chat | Realtime WhatsApp-style thread, 22:00-07:00 Night-Silence, countdown/status header, scoped event-group cleanup |
| CF-10 | Team registration | CUJ-prefixed invite codes, joiner, roster view and deadline/authorized roster locking |
| CF-11 | Judging / leaderboard | Multiple rounds, four-parameter default rubric, assigned judging and live score breakdown/rankings |
| CF-13 | Volunteer coordination | Approved event recruitment link, verified student roll/enrollment, roster/task hub, delegated operations and reviewed hours/certificates |
| CF-14 | Campus lost and found | Category filters, photos, report, confidential claim evidence, handover verification and claim log |
| CF-15 | 24/7 helpdesk intake | CUJ-TKT-YYYYMM-XXXX display numbers, grievance form/thread, assignment, escalation and resolution status |
| CF-16 | Tournament brackets | Multi-round fixtures, participant/resource clash checks, live fixture cards, results and winner badges |
| CF-17 | Audience choice polls | Eligible authenticated single vote, server-enforced uniqueness, abuse signals and realtime percentages |
| CF-18 | Postpone, suggest, reschedule and cancel | Events/matches, next feasible slots with explanations, impact review, versioned approvals and retained permitted evidence |
| CF-19 | Complete transactional notifications | All affected users, durable shared inbox, priority delivery attempts, acknowledgements, retry/failure dashboard and scoped notification catalogue |
| CF-20 | Personal campus calendar | Own timetable/events/duties, privacy-safe clash warnings and versioned calendar updates |
| CF-21 | Readiness and resource intelligence | Organizer checklist, accountable owners, staffing/resource suggestions and fair waitlist offer reminders |
| CF-22 | Feedback and operational insights | Optional feedback, privacy-preserving utilization/completion/disruption analytics and explained reviewable recommendations |
| CF-23 | Campus disruption and continuity | Verified closure/maintenance notices, impact preview, affected organizer tasks and supervised outage fallback |
| CF-24 | Faculty organizer workspace | Faculty can propose/host with event-scoped organizer duty; assigned admin approval opens scoped operations workspace |
| CF-25 | Volunteer recruitment | Authenticated shared registration link, mandatory verified roll/enrollment and basic details; accepted event roster |
| CF-26 | Configurable QR/evidence policy | SINGLE_QR entry-only or entry+attendance; DUAL_QR entry + organizer-enabled attendance; NO_QR manual |
| CF-27 | Scanner/manual permissions | Master OFF by default, ALL accepted roster or SELECTED QR delegates; manual separately selected, revocable and audited |
| CF-28 | Ordered attendance exports | Mandatory verified identifier in receipts; complete final CSV and scan log sorted ascending by approved identifier comparator |
| CF-29 | Runtime admin feature controls | Module/feature/function toggles, dependency-aware evaluated manifest, live web/app hide/show and backend enforcement without a compatible-client update |
| CF-30 | Thirty-day uploaded-media lifecycle | Uploaded originals/PDFs and derivatives expire after 30 days; automatic verified purge, restore protection and storage-efficient processing |

CF-12 is retired and not reused; there are 29 active IDs in CF-01..30 excluding CF-12. Removed functionality is absent from target screens/actions/APIs/jobs/data design, rather than a hidden re-enableable module. Runtime control and media rules are normative in [RUNTIME_FEATURES_AND_MEDIA_SPEC.md](RUNTIME_FEATURES_AND_MEDIA_SPEC.md). Admin can toggle all implemented compatible business functions; security/cleanup/safe-exit foundations remain protected.

Initial department count is a configuration requirement, not an independently verified current university fact. Seed through an idempotent versioned migration after office validation; never reseed over administrator changes or hard-code 22 into UI logic.

## 2. A - Library Study vs Book Issue dual-QR

Shared screen labels: `Library Turnstile`, `[Scan Study QR]`, `[Scan Book Issue QR]`, `[Confirm Exit]`, `[View History]`. Purpose enum: `STUDY` or `BOOK_ISSUE_RETURN`; the Book Issue label covers issue/return counter visits, not an implemented book circulation system.

1. Student chooses purpose and scans the authorized library challenge. Server binds authenticated student, library, purpose, ENTRY action, nonce and expiry; validates identity/session and configured presence policy.
2. Transaction creates one ACTIVE visit and updates any occupancy projection; repeated entry returns the original active visit. Mismatched purpose cannot silently change an active visit.
3. Display live timer such as `01h 14m 22s` from accepted server entry time plus calibrated server-time offset. Timer resumes after refresh/restart and foreground return. Avoid accumulating one-second callback ticks, which drift when the browser/app sleeps. Offline timer is labelled provisional/stale and cannot write final duration.
4. `[Confirm Exit]` opens the scanner. Validate same library, same original purpose and EXIT context. Matching purpose QR in exit mode may use the same station UI, but a raw ENTRY token must not be accepted as EXIT. Wrong-purpose message: `Galt QR scan kiya hai! Aapne STUDY ke liye entry li thi.` Record mismatch evidence without rolling it back with a failed close; session remains ACTIVE.
5. Valid exit completes the visit with server timestamps and exact observed interval to the chosen timestamp precision. Duplicate exit returns the original duration. This is entry-exit elapsed duration, not guaranteed uninterrupted study presence.
6. Closing sweep uses actual library hours/holidays; forgotten exit is AUTO_CLOSED_UNSCANNED with estimated bounded duration. Midnight reporting is separate from closure; it never invents 3/4-hour study sessions.

At 00:00 Asia/Kolkata, one durable job per institution/library/local date generates the preceding day's `library_logs_YYYY-MM-DD.csv`. Date D means visits with entry on local date D; include ACTIVE/cross-midnight visits as INCOMPLETE_AT_REPORT_CUTOFF with duration/status clearly distinguished. Later validated exits/corrections generate an audited revision. Independent library job keys and storage paths prevent same-filename collisions. Use leases, retries, deterministic snapshot/revision metadata, checksum, private storage and notification of READY/FAILED; an unavailable scheduler catches up missed dates. Empty days still generate a header-only report.

CSV columns: report date/revision, visit ID, approved display name/roll identifier, department, purpose, entry/exit instants, duration seconds/display, status, duration quality, mismatch count. Fields/identity disclosure require report scope. Export authorization checks librarian or admin with assigned-library report capability; general student cannot download visitor history. Text/formula injection is escaped; no public report URLs.

Compatibility route to implement: `GET /api/v1/cujammu/library/export-csv?date=YYYY-MM-DD&libraryId=...`. Date defaults to the latest completed local reporting day and library must resolve within caller's scope. READY returns the authorized CSV; pending generation returns explicit pending metadata/status, never fake empty success. Canonical job/status API can live under `/api/v1/libraries/{id}/reports/daily`; the legacy route delegates to that same service. `http://localhost:8080` is a development example only; production uses HTTPS.

## 3. B - Conflict-free venues and grounds

`Venues & Grounds` shows a 24-hour day grid with date, campus/facility filters, timezone, opening/closure overlays and shared legends:

- Green AVAILABLE: instant confirmation allowed for an eligible authorized requester when policy permits. Recheck server state transactionally on `[Book Venue]`; the green screen is never a reservation guarantee.
- Red BOOKED: approved public event title and department if disclosure is permitted. Private meetings show `Reserved` without confidential details.
- Yellow PENDING_REVIEW / HELD: show expiry/availability implications; blocking pending holds participate in overlap checks.
- Maintenance/closed status has distinct text/icon/overlay; availability outside hours does not imply bookability.

Overlap condition: `existing.start < requested.end && existing.end > requested.start`, on intervals expanded by setup/cleanup/resource buffers. Adjacent bookings are allowed only when buffers/resources permit. All changes share the facility calendar guard transaction described in main architecture. On conflict: `Venue Conflict: Brig. Rajendra Singh Auditorium is already booked!`, or the actual authorized facility display name; do not disclose private booking data. Concurrent confirmations, reschedule and maintenance cannot create overlaps.

VC-office priority derives from verified grant/policy; reviewed preemption, alternatives and notification remain required. The calendar cannot accept a user's supplied priority=100 as authority.

## 4. C - Configurable event attendance and report colors

Normative modes/permissions are [EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md). Faculty/organizer configures SINGLE_QR (entry-only or entry+attendance), DUAL_QR (entry plus later enabled ATTENDANCE_CHECKPOINT) or NO_QR (authorized manual). QR-2 is not implicitly EXIT. Scanner master defaults OFF; ALL accepted roster or SELECTED delegates get scoped QR actions only when enabled. Manual is independently authorized. Required labels include Entry Pass QR, Enable Attendance QR, Scan Entry, Scan Attendance and Scan Exit only where an actual exit evidence policy applies. Wrong action/session, waitlist/cancelled/revoked pass or unauthorized operator is rejected. Pending owner proof is not success; camera feedback/haptic follows accepted server result.

Optional approved duration-policy threshold: `eligible presence seconds >= 0.80 * approved session duration seconds`; compare precise seconds before display rounding. Clamp intervals to approved session start/end and sum valid re-entry intervals. A single or second attendance checkpoint cannot establish this duration; actual interval/approved reviewed evidence is required. Event duration/start/end changes must follow approved version and avoid retroactively altering eligibility without reviewed correction. Mandatory multi-session events aggregate according to frozen policy.

| Status | Meaning | UI / XLSX style |
|---|---|---|
| ONLY_ENTRY | Admission recorded; any further policy-required attendance evidence is not yet satisfied | Amber, explicit admission/evidence label |
| ATTENDANCE_CONFIRMED | Meets published point-in-time attendance policy; not proof of duration | Green with evidence label |
| FULL_ATTENDANCE | Meets approved evidence policy; duration mode requires >=80% and verification | Green |
| EARLY_DEPARTURE | Completed evidence below threshold | Orange |
| INCOMPLETE | Missing policy-required entry/checkpoint/exit or unresolved required evidence | Red / labelled reason |

CSV includes `attendance_status`, `status_color` and `reason`; XLSX and screen apply actual row colors. CSV is plain text and cannot preserve fills: [Microsoft format documentation](https://support.microsoft.com/en-us/excel/save-a-workbook-to-text-format-txt-or-csv). Offer `[Download CSV]` and `[Download Color-Coded XLSX]` on both clients. Do not mislabel an XLSX binary as CSV.

After session/event closure and eligibility finalization, one certificate issuance job unlocks the approved truthful certificate type if policy enables it. Retries cannot duplicate issuance. Entry-only cannot issue it; SINGLE_QR ENTRY_AND_ATTENDANCE can meet a point-attendance policy, never a duration claim. Missing required exit routes to reviewed correction; no fabricated exact duration or silent certificate grant. Entry-exit math alone still cannot detect leaving without scanning; supervised exits or proportionate spot checks address that residual risk. Final summary and scan log use verified roll/enrollment and deterministic ascending order per CF-28; CSV colors remain text labels and actual fills use XLSX.

## 5. D - CoE scorecards and timetables

CoE uploader or specifically assigned faculty: `[Upload Batch]` -> malware/format checks -> verified roll mapping -> `[Preview Mapping]` -> independent verifier -> `[Publish Verified Batch]`. Publication is one-click only after approval prerequisites; not one-click blind OCR publication. Missing/ambiguous student, term or PDF page mapping blocks that item. Publish immutable version/document references and notification outbox atomically; private artifacts must already exist and pass checks.

Student `Score Cards` shows own term, SGPA/CGPA and publication version, `[View Official PDF]`, `[Download Official PDF]`, `[Print]`, `[Request Correction]`. Grade values are institution-published, never LLM-generated. Owner-only authorization applies to listing, previews and every download; no supplied roll number overrides principal mapping. Generic admin is not academic publisher. Printing uses private preview/platform adapter. Timetables use department/program/semester targeting, approved version and shared notification workflow.

## 6. E - Hostel passes and late entry

Shared `[Apply Day-Out Pass]` / `[Apply Night-Out Pass]` form captures approved destination/reason and expected return. Warden receives a private notification and can `[Approve]` / `[Reject]` after reviewing assigned-hostel policy. Server-issued dynamic QR appears only for approved pass. Guard `[Scan Departure]` and `[Scan Return]` see minimal identity/pass validity, not unnecessary private reason details.

Canonical late-return result is `LATE_ENTRY` (replace target-document LATE_RETURN naming; existing prototype migration maps old values). Configure hostel curfew/grace/timezone and explicit approved exceptions. Owner-provided initial examples: boys hostel 21:00, girls hostel 19:30 local time; these are proposed configuration values requiring university validation, not asserted official rules or code based only on gender. Preserve both curfew comparison and approved-return comparison/reason; an authorized extended pass can override curfew according to policy. Night-Out can legitimately span dates. Duplicate return cannot change accepted time. Notify warden privately; no automatic punishment or public late-student list.

## 7. F - Community and event chat

WhatsApp-style shared thread with delivery states, attachment preview, roster and moderation actions. Membership follows verified department/event assignment. Quiet window is `[22:00, 07:00)` Asia/Kolkata: student posts denied by the server beginning exactly 22:00; configured authorized announcements/emergency moderators remain allowed. UI countdown/header computes server-aligned policy time; changing phone clock or using REST instead of UI cannot bypass silence.

After event COMPLETED, authorized organizer sees `Delete temporary event group?` with `[Delete Temporary Group]` / `[Keep for Now]` and scope preview. Confirm disables event-group access and purges eligible messages/media/indexes/caches. Permanent department groups and approved structured records are untouched; their uploaded media still expires after 30 days. Protected evidenced holds explain retained scope; routine retention cannot extend uploaded bytes. Backup/restore deletion follows CF-30, without claiming immediate erasure from every physical copy. Cleanup is idempotent/audited and never copies message content into logs.

## 8. Remaining core workflows and safeguards

### Teams and judging

Invite format `CUJ-XXXXXX` is a display example. Use securely random, rate-limited, expiring event-scoped codes with enough entropy, unique normalized code hash and retry on collision. Code is not authorization by itself: membership/eligibility/team capacity/deadline must pass. Lock roster transactionally before judged rounds; exceptions are audited.

Default four judging parameters preserve prototype vocabulary: Innovation, Technical Depth, Execution, Presentation, each 0-25, total 0-100. Freeze versioned rubric per round; integer/fixed precision and bounded ranges. Approved alternative weights/criteria require policy/rubric version. Shared leaderboard shows permitted aggregate/parameter breakdown, publication state and ties; draft/confidential judge identity is not exposed.

### Volunteers, lost/found and helpdesk

Volunteer tasks include assigned scope/shift, start/finish evidence, submitted hours and supervisor review. Prevent overlapping credited shifts and self-approval; approved cumulative hours/task completion drive one certificate eligibility decision. No extra certificate from each task retry.

Lost/found category chips, safe photo upload and ownership-scoped reporting; claims collect confidential identifying evidence. Finder/staff verifies handover and appends claim history; concurrent claims cannot both finalize ownership. No public phone/address or proof clues. Images use private/quarantined processing and approved public redacted variants.

Helpdesk is available for intake 24/7; responder availability and SLA depend on actual staffing, not a promise of instant around-the-clock human resolution. Generate unique display `CUJ-TKT-YYYYMM-XXXX` using server monthly sequence (minimum four digits, expand beyond 9999) and opaque internal ID. Grievance form/modal, private thread, status timeline, assigned office, escalation and reopen flow exist on both clients. Knowing a ticket number does not grant access.

### Tournament brackets and audience polls

Brackets show round/match/roster, live score, winner badge and provisional/final result. Score events, corrections, byes/ties, advancement and rescheduling remain deterministic and replay-safe. Do not label a provisional winner as final; late changes invalidate/review affected next-round fixtures.

Poll workflow: authorized organizer drafts question/options -> validates audience/session/open-close window -> publishes -> eligible authenticated student `[Vote]` -> original confirmed receipt -> live percentages -> closes/finalizes -> approved result. One accepted vote per `(institutionId, pollId, voterSubjectId)` enforced with unique index and transactional vote/count/outbox; retries return original receipt. Default immutable choice after acceptance; changing choices requires a separately approved explicit policy. Vote identity is private; public responses return aggregate counts and the caller's own state, not other voters.

Campus-NAT users may legitimately share IP. IP/device patterns support rate limits/anomaly review, never a unique-IP constraint or sole voting identity. Store only justified minimized/pseudonymized network evidence with approved retention and trusted-proxy handling. Percentages derive from committed eligible counts, define rounding/zero-vote behavior and update via authorized realtime channel. Closed/ineligible/revoked users cannot vote; caller cannot forge audience or deadline. Audit poll-rule changes and admin disqualification; any tally adjustment uses reviewed record, not arbitrary count overwrite.

## 9. Implementation and traceability

Persist theme preference, approved branding version, registry seed version, daily report job/snapshot/revision, volunteer shifts/approved hours, ticket counters, invite hashes, poll definitions/votes/tallies and cleanup jobs with validation/indexes/retention. Schema migration cannot silently modify existing user preferences, published results or vote outcomes.

Trace all active IDs CF-01..30 except retired CF-12 to shared action IDs, API operation, capability/scope, canonical collections, state machine, failure cases and acceptance scenario. Extend CI manifest and Mongo transaction tests before counting features complete. Daily reports/cleanup use durable jobs with leases/outbox; live timers, countdowns, percentage bars and color legends are projections of server-confirmed data.

CF-18 through CF-23 are specified in [SCHEDULING_AND_NOTIFICATION_SPEC.md](SCHEDULING_AND_NOTIFICATION_SPEC.md). Postponement with no confirmed date creates an immediate affected-audience notice; confirmed replacement requires fresh guarded availability checks. Cancellation stops new entries and preserves safe exits/permitted evidence. Suggestions are not bookings. Every module must classify notification triggers/audience/priority; all affected registered users receive durable change notices, while private data and unrelated campus users remain outside that audience. Personal calendar, readiness, feedback and disruption workflows are required shared-client additions with approved policy configuration.

Uploaded media across all modules expires 30 days after server-accepted upload, including uploaded academic PDFs. Original/derivative expiry is not renewed by access or feature toggles; permanent official originals require a separately governed university archive outside transient platform storage. Structured records have their own approved retention. Expiry blocks access even if physical purge retries; backups/caches/versions and narrowly authorized preservation holds follow CF-30. Re-enabling a feature cannot resurrect expired media.

CF-24 through CF-28 and the refined CF-07/13 are specified in [EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md). This newer contract replaces mandatory event ENTRY/EXIT dual-QR assumptions with distinct selectable modes/evidence policies; library and hostel exit workflows are unchanged. Recruitment approval, live scanner controls, independent manual delegation, owner-bound live credentials and ascending verified-identifier reports are mandatory on both clients.

Production invariants: no accepted capacity oversell, no simultaneous conflicting confirmed booking, no extra accepted vote, no unauthorized academic document, no approved hours twice, no mismatched library close, no premature certificate. These are requirements to test and monitor; they do not imply zero unknown vulnerability. Geofence/GPS/campus Wi-Fi are configured corroboration with documented accuracy/browser restrictions and supervised fallback, not an unspoofable universal 500m guarantee. Per-library/venue boundary is approved and versioned; do not send precise location histories to chat/AI/logs.
