# Sports and tournament implementation status

## Implemented in this slice

- Typed create/update/commentary contracts; no request body is bound to a JPA entity.
- Shared web/Android client now selects an authorized managed event and its registered team roster to create a fixture; local campus time is validated without timezone conversion, and uncertain retries freeze the draft and reuse the original request key until the result is recovered.
- Fixture creation verifies both team IDs belong to the selected event and persists server-derived canonical team names; synthetic browser flow covers the create path and a simulated lost-response retry on desktop and narrow web layouts.
- Public fixture reads are limited to APPROVED/COMPLETED events; pending/rejected drafts require event-manager authorization. An uncertain fixture-create response locks the payload and allows only same-key recovery, and managed-event retrieval has a retry action.
- Approved-event owner/admin checks through `DomainAccess` on every write.
- Fixture creation requires two distinct event-registered team records; it derives fixture labels from those canonical team names and rejects cross-event IDs or caller-supplied label mismatches.
- `MODULE_LIVE_SPORTS` and `MODULE_TOURNAMENTS` server-side feature guards.
- Aggregate `@Version` revisions and pessimistic row locks for score commands.
- Explicit lifecycle transition validation and registered-team winner validation.
- UUID request keys, payload hashes and stable same-key retries for the latest command.
- DTO-only match, fixture and commentary reads; victory social-post side effect removed from the transaction.
- Immutable `SportsMatchScoreRevision` and `FixtureScoreRevision` snapshots are committed with creation and accepted score/lifecycle changes; unique aggregate-revision constraints prevent duplicate history rows.
- Revision snapshot writes are transaction-bound, so a failed history write rolls back the mutable projection; same-key retries do not append duplicate snapshots.
- Manager-scoped revision-history endpoints return bounded DTO snapshots only when the requested match/fixture belongs to the supplied event; public score reads and fixture lists fail closed when their runtime module is disabled.
- After the first accepted score state, subsequent score/lifecycle corrections require a bounded operator reason; the reason is included in the command hash and immutable history snapshot.
- Durable `SportsScoreCommandReceipt` rows bind the request key and payload hash to the event/aggregate and accepted revision; old identical retries return without reapplying, while changed reuse or cross-aggregate reuse is rejected.

## Still required before this group is complete

- Deterministic score-event replay, explicit undo authorization and durable audit/outbox records (snapshots are evidence only, not the complete command ledger).
- Bracket advancement and participant/resource clash guards; durable command receipts now cover request-key history, but deterministic state replay remains separate.
- Postponement/cancellation availability suggestions and affected-audience notifications.
- Independent persistence/concurrency/HTTP security tests and web/native parity implementation.
- Production replica-set, load, restore, observability, accessibility and independent security assessment evidence.
