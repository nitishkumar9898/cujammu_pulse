# Chat posting implementation evidence

Updated 3 October 2026. CF-09 remains partial. This slice implements retry-safe posting and bounded server acceptance in the existing Spring/JPA bridge. See [decision and rollout requirements](ADR_CHAT_POSTING.md).

## Implemented

- Every post carries a UUID scoped to the authenticated sender and group. Matching retries return the original receipt; changed content with the same key conflicts. Stored immutable content fingerprints are not exposed in message DTOs.
- Current account, feature and group membership checks run before replay. Revoked membership and quarantined messages cannot be bypassed with a previously valid key.
- New messages share the group contention guard with membership changes. Default limits are 20 accepted messages per sender/group and 200 per group in a rolling 60-second window; replay does not consume capacity. HTTP 429 includes a safe error, no-store and Retry-After.
- Shared web/Android source retains an unchanged uncertain command in memory for explicit retry, deduplicates receipts and clears private state on scope/session changes. Conflicting commands require a draft edit. No private offline queue is persisted.
- Request validation, failure responses, action traceability and migration/rollback constraints are documented. Existing history remains intact; old unkeyed writers must be drained before rollout.

## Verification

- Full backend suite: **246 tests, zero failures/errors/skips**, isolated synthetic H2 fixtures.
- Included ChatPostingPersistenceTest: **11 tests**, covering concurrent retries, competing last quota slots, sender/group scope, exact time boundary, revoked/quarantined replay, terminal event/quiet-hour receipt retrieval, invalid input and configuration bounds.
- Authenticated HTTP checks cover CSRF, no-store, stable receipts, changed-body conflicts and 429 responses.
- Client unit suite: **57 passed**; TypeScript check and Expo web export passed.
- Full Edge browser suite: **32 passed**, desktop and small viewports against synthetic mocked API responses. An initial retry test counted both the retained textbox draft and saved message text; a stable message-row identifier now verifies one persisted row independently of the draft assertion.

Commands were run through `rtk proxy`: isolated Maven `test` with an in-memory H2 URL and target-only log/upload paths (see TEAM_AND_CHAT_IMPLEMENTATION_STATUS.md), plus client `npm test`, `npm run typecheck`, `npm run build:web` and `npm run test:web`. JDK/native-access, deprecated tooling and existing dependency warnings remain; passing checks are not a clean supply-chain assessment. Scoped tracked-file diff checks passed; the unrelated working tree remains modified.

An independent reviewer examined authorization, replay, quota contention and migration behavior and added database/HTTP regression coverage. H2 tests do not establish MongoDB concurrency or real Android behavior.

## Remaining

CF-09 still requires governed moderator provisioning, realtime subscriptions/revocation, durable notification outbox, protected attachment/hold/purge workflows, approved membership review/retention policy and production load evidence. Message history currently returns at most 100 rows. Pending retries are lost when the app process terminates. Cross-group abuse controls are not implemented.

Production rollout additionally needs a reviewed additive migration, coordinated compatible clients, synchronized clocks/configuration, MongoDB implementation/testing, native identity/device verification and the release gates in [the platform ledger](IMPLEMENTATION_PROGRESS.md). No deployment or university data import was performed; no zero-vulnerability or platform-completion claim is made.
