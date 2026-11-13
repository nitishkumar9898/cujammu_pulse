# ADR: Explicit judging assignments and score integrity

**Status:** Accepted for the first hardened judging slice (3 October 2026)

## Decision

Judging writes use typed commands and the fixed `FOUR_CRITERIA_25_V1` rubric. An administrator or approved event owner explicitly assigns a verified `JUDGE` or `FACULTY` account to an event. A scorer must be that assignee (or the event manager), the event must be approved, the judging feature must be enabled, and every score is range checked at 0–25 per criterion. Score rows carry an optimistic revision, request key/hash and rubric version; the row is locked before update and same-key retries are stable.

Leaderboard responses contain only participant and aggregate score data. Score-detail responses are manager/assignment scoped DTOs. The previous raw entity and client-supplied judge identity path is removed.

## Limits

Participant eligibility is still represented by the existing participant identifier/name contract; a canonical event-team/registration binding and a separate immutable score-event/correction ledger are required before competition results can be final. Appeal/reopen, tie-break configuration, rubric publication, notification outbox and independent client/security evidence remain release gates.

The existing Maven suite and `AudiencePollHttpTest` passed after integration; this is not an independent judging security or concurrency assessment.
