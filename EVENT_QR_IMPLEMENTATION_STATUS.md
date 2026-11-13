# Event QR attendance implementation status

Implemented in this slice:

- Owner-scoped opaque credential issuance for approved registered events.
- `SINGLE_QR`, `DUAL_QR`, and `NO_QR` policy checks; checkpoint credentials require an active approved checkpoint window.
- Short-lived scanner intents with request-key replay protection and hashed challenge codes.
- Authenticated owner confirmation, credential/action consumption, append-only evidence, and duplicate-stable receipts.
- Commit-time reauthorization of the original scanner's live window, accepted membership, selected/ALL grant and event lifecycle.
- DTO-only API routes; raw token values never appear in logs, reports or pending-owner responses.

Verification: `EventQrAttendanceServiceTest` and `LibraryTurnstilePolicyTest` pass; compilation passes; the complete Maven verification suite exited 0 after integration (expected framework/contention warnings remain in logs).

Remaining: WebAuthn/native user verification, notification delivery for newly enabled checkpoints, entry/checkpoint/exit presence-cycle rules, offline policy, ascending CSV/styled XLSX reports, client UI parity, independent concurrency/HTTP tests, and production Mongo/replica-set evidence. The old public QR routes remain intentionally fail-closed.

## Pending-intent and credential-reissue hardening â€” 19 October 2026

Repeated submissions for one credential are now deduplicated while a live owner confirmation is pending, even when the scanner sends different request keys. Credential reissue revokes pending intents for the prior credential version in the same transaction; confirmation also checks credential-version and scanner-epoch snapshots captured at scan time. Revocation advances the intent revision. Added H2 persistence coverage for pending/expiry/revocation queries and two concurrent READ_COMMITTED transactions contending on the event-operation lock; service tests cover notification dedupe and stale-intent rejection. Targeted QR persistence/unit tests passed on this host. The concurrency evidence is H2/JPA only and does not establish production Mongo or multi-instance behavior. Operator WebAuthn/native fresh verification, retry-safe challenge recovery, durable distributed abuse throttling, enrolled-device authorization, camera/device acceptance and approved database deployment remain release blockers; live camera scanning stays unavailable.

## Credential lifetime and scanner epoch revocation â€” 19 October 2026

Display credentials now expire after 90 seconds; an attendance-checkpoint credential expires at the earlier of that display deadline or the approved scanner-window close. Credentials bind to the event's current `scannerEpoch`, and both scan-intent creation and owner confirmation reject legacy/null or stale epochs after scanner OFF/change/reopen, membership/grant revocation or other epoch-incrementing lifecycle changes. This closes the interval where a still-unexpired QR could survive a scanner-control epoch change. Because the epoch is event-wide, changing one selected volunteer's grant also invalidates every not-yet-consumed pass for that event; this is fail-closed but can cause extra pass refreshes. `EventQrAttendanceServiceTest` passed 7/7, including short-TTL, checkpoint-close cap, stale-intent denial and stale-confirmation denial. `EventsScreen` now issues an entry credential for the authenticated user's REGISTERED event and displays it as an in-memory QR; it clears on expiry, app backgrounding, or explicit close. Responsive browser E2E passed 2/2 and universal TypeScript typecheck/web export passed. WebAuthn/native fresh user verification, operator camera journey, checkpoint display/refresh, migration of persisted credential rows, Mongo transaction proof and device/UAT acceptance remain open.

### Confirmation retry behavior — 23 October 2026

Owner confirmation now returns the original RECORDED receipt when an authenticated owner retries after that same intent already committed; an existing attendance record linked to a different intent remains DUPLICATE. This provides stable recovery after a lost HTTP response without bypassing owner authentication or creating another evidence row. Regression coverage verifies the original evidence ID/time is returned and no credential lookup or write occurs. Targeted QR suites passed after this change. The idempotency key is still not persisted as a command receipt; safe retry is scoped to the opaque owner-bound intent key and existing evidence relation.

## Verification refresh — 23 October 2026

The byte-budget regression now directly exercises the production BoundedResponseBodyHandler through a deterministic Flow subscriber: an oversized chunk yields the configured budget error and cancels upstream. Clean Maven suite passed 576 tests / 95 suites with zero failures, errors or skips; QR slices contributed 12 passing tests. Live HTTP transport could not be tested because this Windows JDK cannot initialize its selector loopback pipe, and remains an external CI release gate.
