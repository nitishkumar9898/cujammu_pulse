# ADR: Retry-safe, bounded chat posting

Date: 3 October 2026. CF-09, CHAT-POST-RETRY-01, CHAT-POST-LIMIT-01. Applies to the legacy Spring/JPA bridge; MongoDB remains the target architecture.

## Decision

Every new message command requires a client-generated UUID `requestKey`. The database unique scope is `(group_id, sender_id, request_key)`, not an IP address or client-selected sender. The authenticated sender, runtime controls and membership are checked on every attempt. Under the same group guard used by membership revocation, the server compares the existing immutable SHA-256 digest of trimmed content. Matching requests return the original accepted message; changed content with the same scoped key returns conflict. Keys are normalized UUID strings and are not authorization credentials.

Retrieving a prior receipt does not create another message, consume quota or bypass current feature/account/membership restrictions. Quarantined messages cannot be disclosed by replay. An authorized retry can retrieve an earlier receipt after quiet hours or event completion because it is a read of already accepted evidence; a new key still requires current lifecycle/posting permission and silence rules. Future message edits must preserve the original request digest. Protected deletion must retain an appropriate non-content replay tombstone for the supported retry period rather than silently making an old command executable again; deletion remains unavailable until that lifecycle is implemented.

New-message limits are database-backed accepted-message counts under the group lock: default 20 per sender within one group per 60 seconds and 200 per group per 60 seconds. Counts include quarantined accepted messages and exclude rejected attempts. The cutoff is strictly greater than server `now - 60s`; time is captured after lock acquisition. Future-stamped records count conservatively if the clock moves backwards. All writers must have synchronized clocks and matching reviewed limits. This is a local transaction invariant for the tested database, not a distributed production performance claim.

Limits are validated configuration (`app.chat.sender-per-group-per-minute`, `app.chat.group-per-minute`) with bounds 1..1000 and 1..10000. They are operational load controls, not disciplinary sanctions. Rejection returns HTTP 429, `Retry-After: 60`, no-store and a fixed safe code; it does not reveal message counts, another sender or content. Per-device, cross-group/global-user limits, moderation and campus abuse-response policy remain required future work. No campus-wide shared-IP voting/posting authority is introduced.

The shared client keeps a pending command key and draft in memory for explicit unchanged retries after uncertain failures. Success deduplicates the returned message ID. Changing text/group creates a new intended command; navigation/session loss does not persist private drafts to disk. A stable parent callback prevents routine feature/session refresh from destroying the pending send. This is not an offline durable queue and does not promise recovery across app termination.

## Side effects and observability

Accepted message metadata records authenticated sender, group, immutable request key/digest and UTC acceptance time; raw content, request keys and digests are not written to ordinary logs or returned by message DTOs. Replay and rejected commands create no new domain row. Group fan-out notifications, durable outbox/SSE and per-recipient read/delivery state are not introduced by this change and remain incomplete; an HTTP receipt means database acceptance only.

## Migration and compatibility

- Add nullable `request_key` (36), `request_hash` (64) and `accepted_at` fields to legacy messages, plus the composite unique constraint and group/sender acceptance-time indexes. New service writes always populate all three. Review a versioned migration against the selected production dialect; generated H2 test DDL is not production migration evidence.
- Preserve existing message rows. Do not assign guessed UUIDs/digests or convert old local timestamps under an assumed timezone. Legacy null-key rows remain readable but cannot be replayed through a key that never existed.
- Quiesce every old writer and wait at least one full 60-second quota window before enabling the upgraded posting path. Old instances must not keep accepting unkeyed writes. This avoids falsely counting null-acceptance-time history as evidence of the new rate invariant.
- Deploy compatible web/native request contracts together under the pending client-compatibility release gate. Old POST bodies without a key now fail validation; no server-generated fallback silently disables duplicate protection.
- Rollback must block posting or retain the guarded keyed implementation. Never drop accepted-message evidence, weaken membership checks or allow an old unkeyed writer to resume automatically.

Institutional message retention, protected holds/purge, realtime revocation, moderator governance, external notification delivery, native-device testing and MongoDB contention testing remain open. No zero-defect or module-complete claim follows from this ADR.
