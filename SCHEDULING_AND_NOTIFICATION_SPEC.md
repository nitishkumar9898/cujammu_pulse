# CUJAMMU-PULSE: Schedule Changes, Notifications and Smart Operations

Version 1.4 | 2 October 2026 | Normative implementation specification; not a claim of deployed behavior.

Read with [UNIVERSITY_PLATFORM_ARCHITECTURE.md](UNIVERSITY_PLATFORM_ARCHITECTURE.md), [CORE_FEATURES_SPEC.md](CORE_FEATURES_SPEC.md), [PLATFORM_PARITY_AND_ACCEPTANCE.md](PLATFORM_PARITY_AND_ACCEPTANCE.md) and [AGENTS.md](AGENTS.md). CF-18..23 specify lifecycle/notifications; current scope has 29 active IDs CF-01..30 excluding retired CF-12. Existing alumni, AI, privacy and monitoring remain. Suggestions support authorized humans; they never independently cancel events or penalize students.

## 1. Shared event and sports lifecycle

Current runtime controls/30-day uploaded-media rules are [RUNTIME_FEATURES_AND_MEDIA_SPEC.md](RUNTIME_FEATURES_AND_MEDIA_SPEC.md). [EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md) defines faculty/volunteer duties and QR/manual controls. Lifecycle changes invalidate relevant epochs; a replacement must revalidate availability and explicitly reactivate windows/scanner/manual controls subject to institution feature configuration. Retained roster membership does not restore old scan authority. No second checkpoint is automatically exit/80% duration proof.

Both web and Android expose `[Postpone]`, `[Find Next Available Slots]`, `[Preview Impact]`, `[Submit Change for Approval]`, `[Confirm New Schedule]`, `[Cancel Event/Match]`, `[View Change History]` and `[View Notification Status]` to assigned authorized organizers while applicable business functions are enabled. Students see the same change banner, details, inbox, `[Acknowledge Change]`, `[Add/Update My Calendar]` and `[Withdraw Registration]`. Hiding a button is not authorization.

Separate lifecycle, schedule revision, change request, reservation and registration status. Published events can be POSTPONED, return to PUBLISHED on approved rescheduling, or CANCELLED. Sessions/matches have their own status. SUSPENDED sports retains scores; postponement is not score reset. A started event follows terminate/abandon policy, retains permitted evidence and reviews certificate consequences. Completed/finalized event/results require correction/annulment, not ordinary cancellation.

An event-wide change explicitly enumerates impacted sessions, matches and resources. Cancelling one match does not silently cancel the tournament; cancelling a tournament presents the affected fixtures and dependent bracket consequences before confirmation. Unauthorized users cannot set CANCELLED, priority, approver or attendance-policy fields through generic request binding.

### Planned change versus immediate postponement

1. Planned change: preview candidates and impacts while the current published schedule remains effective. A draft/request does not announce a new date or release the current reservation. Authorized short-lived candidate holds are visibly pending, bounded and count as unavailable to conflicting requests.
2. Immediate postponement: an authorized organizer invokes `[Postpone]`, gives reason category and participant-safe explanation, reviews scope and confirms. Institutional policy determines whether additional approval is required; operational emergency authority is explicit. The effective transition to POSTPONED commits with QR revocation, registration/scan suspension, resource disposition, audit and durable notification intent. Notify affected users immediately that a new date is pending; do not wait for a replacement date.
3. Reviewers decide the new slot through the existing facility/event approval rules. A suggestion is never a reservation. Confirmed rescheduling atomically applies the new schedule revision and linked bookings, resets obsolete reminders, reissues valid passes, preserves eligible registrations and creates a new notification intent. If slot confirmation loses a race, return a conflict and recompute suggestions.
4. Each effective change has immutable actor, office scope, private operational reason, safe public reason, approval reference, previous/new values, revision and commit time. Client clocks do not order changes. No unaudited backdated edits.

Specify old-reservation policy per change: retain for a bounded review window or release future occupancy through the reservation coordinator. Display retained holds truthfully. A confirmed relocation must secure the replacement and release the old booking in one bounded transaction when possible. Pending drafts do not erase the old allocation. Long-running multi-session changes use a durable, idempotent workflow: freeze affected public mutations, track each step/compensation, expose CHANGE_IN_PROGRESS/FAILED_REVIEW and publish the final schedule only after all required invariants hold. Never advertise partial changes as complete.

## 2. Next-availability suggestion engine

Use a deterministic constraint engine over server-authorized calendars, not an LLM deciding availability. Inputs: affected sessions/matches, required duration, preferred date range, approved campus/timezone, permissible hours, setup/cleanup buffers, capacity, seating/accessibility, sport/surface/equipment requirements, maintenance/closures, staffing/security availability and institution calendar constraints. Organizer can choose same venue first, alternate equivalent venue, next earliest slot or least-disruption ranking.

Hard constraints include all occupied/nonexpired-held intervals and required resources, exam/teaching blocks supplied by the authorized calendar, known team/opponent/judge/volunteer assignments, configured sport recovery gaps and dependencies on preceding bracket results. A multi-session alternative must satisfy the whole required bundle; do not offer individually available slots that conflict when combined. Capacity cannot fall below retained confirmed participants without an explicitly reviewed relocation/admission plan; never silently move some registrants to a waitlist.

Soft preferences include shortest delay, same venue, accessible travel buffer and minimal known participant/staff clashes. Analysis uses authorized availability/aggregates; never disclose private scorecards, hostel reasons or unrelated calendar titles. Missing availability is UNKNOWN. Show checked data and remaining uncertainty; no continuous location collection.

Return top feasible candidates with exact local date/time, venue, capacity/resource fit, available-until/hold status, conflicts/unknowns, explanation, calendar versions and computed-at time. Stable documented ranking weights and tie-breaking are configurable; offer a constraint summary when no slot exists and allow expanded date range/resource changes for reviewed approval. Suggestion search is bounded/paginated; invalid large ranges cannot exhaust the server.

On `[Confirm New Schedule]`, reauthorize the actor and revalidate every hard constraint in the facility/resource guard transactions. A cached suggestion, AI answer or client green calendar cannot bypass those guards. Multi-resource locks/guard writes follow stable ordering. Also contend on the affected event/session/match version so cancel/reschedule/register/scan races resolve against a single effective state. Student registrations and scans must use that lifecycle coordinator; otherwise an enrollment can commit after cancellation.

Staff, referee, equipment and other scarce-resource commitments treated as hard constraints use their own shared calendar/inventory guards and bounded hold leases in that same coordinator; a free-looking read of separate assignments cannot prove exclusivity. Known advisory personal clashes remain warnings unless approved policy makes them hard constraints. Multi-step change workflows have a named recovery owner, heartbeat, bounded retry/deadline and explicit escalation/compensation policy. Both clients show the temporary freeze and safe operator actions; an abandoned worker cannot freeze activity indefinitely without an incident.

## 3. Cancellation and downstream reconciliation

`[Cancel Event/Match]` requires scope, safe reason, impact preview, applicable approval and confirmation of affected audience/resources. Validate state/version. Duplicate confirmation returns original result; concurrent reschedule/cancel resolves consistently, never conflicting public schedules.

Cancellation blocks new registration/promotion/entry, revokes old credentials, releases appropriate reservations/holds through guards, cancels old reminders, records dispositions and invalidates unearned certificate jobs. Update fixtures under sport policy. Preserve approved structured attendance/scores/certificate records and evidenced protected holds; uploaded bytes still follow 30-day rule. Corrections/revocations need reasons/authority; event group cleanup uses current retention policy.

Physical egress remains safe during an interruption. An assigned operator can record exit/closure against an already active presence record through a separately authorized terminal-state workflow, even after entry credentials are revoked; this cannot create new attendance entry or invent duration. The same safe exit rule applies to immediate postponement of a started activity. Emergency evacuation never depends on an app or QR network being operational.

Volunteer assignments, staffing commitments and equipment handovers enter scoped review rather than deletion. Partial cancellation reconciles only affected sessions/resources; idempotent recovery does not release unrelated tournament allocations.

Rescheduling retains eligible registration by default; show revised response/withdrawal deadline. Material changes may require acceptance under approved rules. Non-acknowledgement alone cannot remove seats or impose penalties. Review changed eligibility; earned attendance never becomes invented presence at another session.

## 4. Transactional notification contract

Every effective change commits a durable notification intent in the same transaction as its domain state and audit. Outbox workers fan out to the authorized affected audience with bounded batches, leases, retry/backoff, idempotent inbox/delivery keys and dead-letter escalation. A committed change must not depend on the push provider being available. Never send participant alerts for an unapproved draft or a rolled-back transaction.

University inbox is source of truth; realtime/push/email are adapters. Material changes create priority inbox and permitted channel attempts. Private data stays behind sign-in; generic lock-screen text. SMS/WhatsApp requires approved provider, contact purpose and operational capacity; inclusion does not enable accounts/send real messages.

Define distinct intent QUEUED, inbox CREATED, per-channel DISPATCHED/PROVIDER_ACCEPTED, DELIVERED when provider evidence supports it, FAILED/RETRYING/EXHAUSTED, user READ and explicit ACKNOWLEDGED states. Provider acceptance does not prove delivery; READ does not mean acknowledgement, and acknowledgement does not mean consent to new fees. Users without push permission still get their in-app item and permitted email; delivery outage is visible to organizers without exposing raw contact details/device tokens.

Design targets under approved load: 99% priority inbox within 10 seconds and 99% submitted to a permitted healthy external channel within 30 seconds. Agree audience limits/load/escalation before release. Targets do not guarantee instantaneous device receipt; track p95/p99, completeness/failures and missing endpoints.

Snapshot recipients at the effective change revision using a durable versioned audience/history or a bounded transaction; coordinate registration writes with that revision. Include confirmed participants, waitlisted users, relevant team members, assigned volunteers/judges/referees and office/resource owners. Former participants receive only an outstanding obligation-relevant notice. Recheck current identity/access/contact rules at delivery; withdrawn/deactivated users do not gain sensitive access. Later joiners see the current schedule and receive a current-revision registration confirmation. Fan-out retries must not lose or duplicate a recipient across batches.

Inbox uniqueness is institution + notification intent + recipient; channel/device deliveries have their own dedupe keys. Suppress stale scheduled reminders after a revision. Payload carries safe resource reference/revision, and clients fetch current authorized details before acting; old/offline push cannot reopen cancelled registration or downgrade the displayed schedule. Never rely on push arrival order. Multiple genuine changes stay in history; a current-state summary can collapse display without deleting mandatory records. Signed provider callbacks, trusted endpoint lookup and replay checks protect delivery receipts; guessed IDs cannot mark another user's item read.

Night-Silence chat rules and notification quiet hours are separate policies. Routine social messages queue/digest during quiet hours. Material near-term cancellation/reschedule is operational priority with an institution-approved rule; only authorized safety/emergency alerts may invoke the reviewed override. No organizer can label marketing as emergency. The mandatory in-app operational record remains available; external contact uses approved purpose/channel policy, not a blanket preference bypass.

FCM documents message expiry/collapse behavior and does not guarantee delivery order, supporting the durable inbox/current-revision design: [message lifespan](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan), [collapse behavior and ordering](https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types). Recheck platform/provider constraints at implementation.

## 5. Platform notification catalogue

Every notification type has an event/intent ID, trigger, priority, audience resolver, safe template/localization, capability, channel policy, expiry, dedupe key and audit/retention rule. The following catalogue is required; new domain actions must classify their notification impact in code review.

| Domain | Required triggers | Authorized audience / default policy |
|---|---|---|
| Events | Registration confirmation/rejection, waitlist/offer/expiry, approved postpone/reschedule/venue/time changes, cancellation, actionable reminders, certificate/reissue | Affected participant/team; assigned organizers/staff for operational impact; material changes priority |
| Sports | Fixture/opponent/venue changes, postponement/suspension/resume/cancellation, check-in reminders, reviewed results/bracket updates/appeal decisions | Affected teams/players/referees and subscribed authorized audience; live scores throttled/in-app by default |
| Facilities | Approval/rejection/hold expiry, relocation/preemption/maintenance conflicts, handover due | Requester and assigned facility/security/resource owners; no private booking disclosure |
| Library | Entry/exit receipt, stale-visit resolution/closing reminder, reviewed correction, daily report READY/FAILED, occupancy closure | Own student; assigned librarians for reports/issues; timer ticks do not trigger push |
| Hostel | Pass request to assigned warden, approval/rejection/revocation/extension, return deadline and recorded late return | Own resident/assigned warden/guard operational minimum; no unrelated guardian disclosure |
| Academics | Approved timetable/exam change, verified scorecard publication/reissue/revoke, correction-request status | Own student/eligible class; authorized academic staff; grades/PDF absent from push |
| Teams/judging/polls | Invite/join/roster lock/deadline, assignment/conflict/round decision, result publication, subscribed poll opening/closing/result | Assigned members/judges/eligible opted-in audience; no vote or judging-draft leak |
| Volunteers | Assignment/change/cancel, shift reminders, hours review and certificate status | Assigned volunteers and supervisors; no broadcast roster |
| Chat/feed | Mentions/replies, moderated actions, official subscribed announcements, approved emergency notice | Current members/affected author; quiet hours and mute/digest; not every message campus-wide |
| Lost/found/support | Matching-category opt-in lead, claim/handover status, ticket reply/assignment/SLA escalation/resolution/reopen | Owner/claimant/assigned staff; generic private-ticket alert |
| Alumni | Mentorship/job response, verified event change and directory/privacy action | Own applicant/mentor/authorized subscriber; opt-in promotion |
| Identity/privacy/security | New session/privileged grant change, credential/recovery action, suspicious-access review and privacy request status | Account owner or designated responder; never credentials or raw incident evidence |
| Operations | Failed fan-out/job, invariant drift, backup/audit failure, incident and assessment action | Named on-call/office owners through protected operational channels, never all students |

Do not broadcast every change to the whole university. Notify everyone affected by the change; match scope, membership and purpose. Notifications never confer resource access. Recipient lists, receipts and exports are private scoped resources, not public attendee directories.

## 6. Additional smart features

### CF-20 Personal agenda and clash assistance

Shared `My Campus Calendar` combines own approved class/exam timetable, registered events, selected matches and assigned duties with source/version/status badges. Offer personal schedule clash warnings and safe travel buffers before registration; warnings do not override official eligibility. Incomplete calendar data is labelled. `[Add/Update My Calendar]` produces standards-compatible calendar entries with stable occurrence IDs, revision and cancellation updates; warn that a previously downloaded offline file may need reimport. Device-calendar read/write is opt-in and limited to needed entries; do not upload the user's entire personal calendar. Revocable scoped subscription, if offered, is treated as a private bearer secret with approved minimum fields, not a public URL.

### CF-21 Readiness, waitlist and resource intelligence

Readiness links approval, resources, accessibility, volunteer/referee coverage, emergency contact and handover. Show blockers/owner/due time. Capacity/resource failures create review tasks. FIFO offers have transparent deadlines/reminders; promotion stays transactional/eligible. Recommend authorized alternatives without removing attendees or inventing bookings. Anomaly flags are reviewable, never automatic discipline.

### CF-22 Feedback and privacy-preserving operational insights

After completed activities, offer optional accessible feedback and issue reporting, with one submission policy and moderated abuse handling. Explain whether feedback is anonymous, pseudonymous or identified; do not promise anonymity when staff can link a response. Dashboards show aggregated attendance/completion, booking utilization, cancelled/postponed trends, volunteer coverage and delivery failures. Small-group suppression, approved access and retention protect reidentification risk; rankings must not expose individual study habits/academic results. Recommendations include explanation, data freshness and measurable outcome; evaluate language/accessibility and false positives before enabling prediction. Deterministic rules ship first; ML forecasts are separately reviewed and cannot become authority.

### CF-23 Campus disruption and continuity centre

Authorized offices can publish bounded closure/maintenance/disruption windows for a facility or campus. Preview affected events/matches and assigned duties; create organizer review tasks and next-slot suggestions. A closure is immediately considered by every relevant booking path, but it does not silently reschedule each event. Explain whether a notice requires action and show expiry/recovery status on both clients. Safety messages come from verified authority with audit and abuse controls; AI cannot publish them. Maintain supervised scanner/manual fallback runbooks and status banners for outages; restored connectivity reconciles provisional evidence under existing rules.

Creating, changing or removing an effective closure writes the same affected facility/resource calendar guards as booking confirmation before evaluating/applying effects; no cached closure flag substitutes for that contention. For a campus-wide large batch, a versioned campus-scope closure guard checked by all facility commands activates the restriction atomically, then durable bounded jobs reconcile impacted activities. It cannot leave unprocessed facilities accepting forbidden new bookings. Recovery/reopening is an authorized revision, not a TTL deletion.

These additions improve operations without adding unrestricted surveillance, facial recognition, autonomous adverse decisions or a mandatory new microservice/LLM. Shared accessible banners and status messages must include text/icons and assistive announcements: [W3C status messages](https://www.w3.org/WAI/WCAG21/Understanding/status-messages), [W3C use of color](https://www.w3.org/WAI/WCAG21/Understanding/use-of-color.html).

## 7. Data, API and observability additions

MongoDB additions: `scheduleChangeRequests`, `scheduleRevisions`, bounded `suggestionSnapshots`, `resourceAssignments`, `institutionCalendarBlocks`, `notificationIntents`, `notificationRecipientSnapshots`/versioned audience references, `notificationDeliveries`, `notificationAcknowledgements`, `reminderJobs`, `readinessTasks`, `feedbackResponses` and `disruptionNotices`. Existing event/match/facility guard aggregates remain authoritative. Unique request/idempotency/revision and recipient intent keys; feedback/acknowledgement subject ownership; TTL only for eligible caches, never cancellation authority or mandatory records. Bound arrays, snapshot jobs and search horizons; private data/retention classification per collection.

Command route families: `/events/{id}/postpone`, `/events/{id}/schedule-suggestions`, `/events/{id}/schedule-changes`, `/events/{id}/cancel`, analogous `/matches/{id}` commands, `/notifications/{id}/acknowledge`, `/notifications/delivery-summary`, `/me/calendar`, `/events/{id}/readiness`, `/events/{id}/feedback`, `/disruptions`. Use version preconditions and idempotency on mutations; suggestion retrieval cannot create a booking. OpenAPI operation IDs/actions must map CF IDs and required capabilities. Streaming channels reauthorize recipients and send committed revision/status only.

Monitor suggestion/search conflicts, stuck changes, stale passes, resource reconciliation, inbox/dispatch lag, completeness, expired reminders and delivery/acknowledgement evidence. Named office/on-call owns escalation. Unknown device/provider receipt stays UNKNOWN.

## 8. Required implementation evidence

Cross-client tests cover planned/immediate postponement, unavailable/expired slots, lifecycle races, bundle recovery, sports dependencies, resource-release retries, audience races, quiet hours, disabled functions, push/provider outage, message order, worker retries and receipt ownership. Load tests prove notification targets/bounded search. Office UAT approves calendar/curfew/cancellation, safety/contact policy and transient media expiry before production.
