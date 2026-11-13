# ADR: Scoped audience poll lifecycle and immutable votes

Date: 3 October 2026. Requirement CF-17. Applies to the current Spring/JPA bridge; the target MongoDB architecture remains unchanged.

## Authority and lifecycle

Only an approved event's owner (faculty/organizer) or current administrator can create/publish a poll. Faculty status alone grants no authority over another event. The initial supported audience is currently enabled STUDENT accounts with a confirmed REGISTERED or ATTENDED registration for that event. Waitlisted, cancelled and unrelated accounts cannot vote or read participant poll results. Managers see aggregate operational results; they do not receive other voters' identities or choices.

Creation produces an immutable DRAFT. The question, ordered options and duration are validated before persistence; publication freezes that definition and sets server UTC opening/closing instants. New votes require OPEN, an approved event, current eligible registration and `now < closesAt`. Exactly at the deadline voting stops without requiring a scheduler. Explicit close is irreversible. Automatic deadline closure is an effective read projection; it does not claim that a background job has finalized or approved an institutional award.

This initial policy has 2–8 distinct comma-free options of at most 50 characters, a question of at most 200 characters and a 1–1440 minute window. Comma-free labels preserve the bounded existing storage representation. It supports event-wide audience polls, not separate session audiences, election secrecy, disciplinary voting or academic grading.

## Retries and contention

Creation uses a client UUID scoped to event and creator with an immutable payload fingerprint. An identical authorized retry returns the original resource; reuse with a changed definition conflicts. Published options cannot be edited beneath accepted votes.

One vote is stored per poll/authenticated user, backed by a database unique constraint. A repeat of the same choice returns the original accepted state. A changed choice conflicts; the server never silently changes the ballot. Current account, audience and feature checks still apply before replay. After close, a still-authorized voter may recover their existing receipt but cannot create a new vote. Event cancellation prevents new votes and is serialized with voting.

Lock order is Events control, Polls control, event-operation guard, poll row. Publish, close and votes share the poll guard; event cancellation/registration changes use the existing event guard. This establishes the tested local database invariant, not a claim about production MongoDB transactions or load capacity. Current reads also take these guards for freshness, so production throughput and a MongoDB read/guard design still require qualification. Revisions protect manager intent; repeated completed actions only replay under their documented original revision.

Totals and percentages are computed from committed vote rows that passed eligibility when accepted. Later access revocation blocks new commands and receipt access; it does not silently delete or disqualify an accepted vote. Zero votes produce zero percentages; display rounding is not used to change counts. Aggregates are refreshed explicitly in the shared screen. Realtime delivery, result adjudication/disqualification and final award approval remain distinct future workflows.

## Privacy, audit and notification classification

Responses allowlist the poll definition, effective state, revision, actor capabilities, aggregate option counts and the current caller's own choice. No voter list, IP, name, account ID, creation key or fingerprint is returned. New ballots do not collect raw IP addresses or duplicate display names. Many eligible students may share campus NAT; IP is never vote identity. Existing legacy network/name fields are retained pending a separately reviewed migration and retention decision.

Administrative changes and first vote acceptance produce audit events in the domain transaction. Audit details omit ballot choice, question, request secrets and IP. Administrative audit custody still needs the architecture's independently retained production controls.

| Trigger | Intended audience | Priority and safe payload | Delivery status in this slice |
|---|---|---|---|
| Draft accepted / matching retry | Requesting manager | Ordinary command receipt; no campus notification | HTTP receipt only |
| Poll published | Eligible subscribed/opted-in registered audience at the committed revision | Normal; event/poll reference and safe dashboard link | Durable outbox/audience snapshot/fan-out pending |
| Vote accepted / matching retry | Current voter only | Private receipt; no choice broadcast | HTTP receipt only |
| Explicit close / deadline / result approval | Eligible subscribed poll audience and event owner under the applicable policy | Normal; safe status/link, no individual ballots | Durable delivery and finalization policy pending |
| Rejected/ineligible vote | Current caller; redacted abuse monitoring when implemented | Safe denial, no other participant details | API denial; governed anomaly pipeline pending |

Delivery dedupe must use poll lifecycle revision and recipient; reminders must recheck effective closure/cancellation. Structured poll/audit retention requires university approval. No uploaded media is accepted by this workflow, so it cannot create an exception to CF-30.

## Migration and rollout

Add nullable protocol/version, immutable creation-key metadata, lifecycle revision and UTC window fields. Existing unversioned polls/votes remain preserved and unavailable through the new workflow until reviewed mapping establishes valid options, audience and timestamps. Do not infer protocol approval or convert ambiguous old local times automatically. No legacy rows, databases or files are deleted.

Review an additive migration and legacy uniqueness/data quality before deployment. Quiesce old unguarded writers; deploy compatible clients together. Old creation bodies and raw vote-entity responses are intentionally superseded by validated DTOs. Rollback must retain the new guards or fence poll mutations; never reopen an old unscoped writer. The legacy Kotlin prototype is a reference, not proof of current native compatibility.

## Acceptance boundary

Required evidence includes private draft access, scope/role denials, frozen options, server-time boundary, same/different-choice concurrency, close-vote contention, retry binding, CSRF and response-field checks, and shared-screen state isolation. H2/browser tests do not prove native-device identity, MongoDB behavior, realtime delivery, privacy approval or production readiness. CF-17 remains partial until its remaining acceptance and release gates pass.
