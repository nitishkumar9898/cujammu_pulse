# ADR: Immutable sports score revisions

**Status:** Accepted for the next sports backend slice (3 October 2026)

## Decision

Every accepted live-match and tournament-fixture command writes an immutable state snapshot in the same transaction as the current aggregate projection. Creation is recorded as `CREATED`; later accepted score or lifecycle changes are recorded as `CORRECTION`. Each snapshot stores the aggregate identifier, event scope, aggregate revision, allow-listed score/status fields and the request key. A unique `(aggregate_id, aggregate_revision)` constraint prevents two histories from claiming the same optimistic revision.

The projection remains the read path for current scoreboards. The revision tables are the evidence path for audit, replay and future correction/appeal workflows. Same-key retries return the existing projection before creating another snapshot; changed payloads with a reused key remain rejected. If the snapshot cannot be committed, the transaction rolls back the projection update.

History is exposed only through manager-scoped, event-bound DTO endpoints. A caller cannot substitute another event identifier to inspect a match or fixture, and public score reads honor the runtime module switch.

Once an aggregate has an accepted score state, every later correction must include a bounded operator reason. The reason is part of the request fingerprint and is retained in the immutable snapshot; omitting it or changing it under a reused request key is rejected.

Each create/update command also commits a `SportsScoreCommandReceipt` keyed by aggregate type, event and request UUID. The service checks this durable receipt before applying a command, so an old identical retry cannot mutate the aggregate a second time; a changed or cross-aggregate reuse is rejected. The receipt and projection are committed atomically.

## Rationale and limits

This closes the prior gap where only the latest request key and score were retained, making accepted history unrecoverable. The snapshots do not yet implement deterministic replay commands, operator reason/undo APIs, bracket advancement, venue/resource clash validation, durable notification outbox or MongoDB migration. Those remain release blockers.

## Verification

`SportsScoreRevisionTest` verifies creation and accepted correction snapshots for both live matches and fixtures. Compile and the full Maven suite must remain green after integration; production replica-set contention and independent security assessment are still required.
