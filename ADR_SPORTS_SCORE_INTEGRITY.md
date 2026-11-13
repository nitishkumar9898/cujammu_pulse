# ADR: Sports score command integrity

**Status:** Accepted for the first hardened backend slice (3 October 2026)

## Decision

Live matches and tournament fixtures accept typed allow-listed commands. A mutation must come from the authenticated administrator or owner of an approved event, pass the corresponding runtime feature flag, include a UUID request key and the aggregate revision, and acquire a pessimistic row lock before validation and persistence. Responses are DTOs and never serialize JPA entities.

Scores use an explicit lifecycle: `SCHEDULED -> LIVE -> SUSPENDED -> RESUMED -> PROVISIONAL_RESULT -> FINAL`, with bounded postponement/cancellation transitions. A terminal result must name one of the registered teams. A repeated request key is accepted only when its payload hash is identical; reusing it for different data is rejected. Victory-feed publication was removed from the transaction because external side effects require a durable outbox and deduplicated delivery worker.

## Rationale and limits

This closes the old entity-binding, role-only authorization, arbitrary status/winner, stale-write and transaction-side-effect risks. The current slice stores only the latest command key on each aggregate. It does not yet provide the complete append-only score-event ledger, correction/undo workflow, appeals, bracket advancement, venue clash validation, durable notification outbox or independent native-client evidence required by `SPORT-01`; those remain release blockers and must be added before production acceptance.

## Verification

`mvn -q -DskipTests compile` and the existing `AudiencePollHttpTest` passed after the change. Full sports persistence, concurrency and HTTP tests are still required.
