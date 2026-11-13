# Audience polls implementation evidence

Updated 3 October 2026. CF-17 is partial. This slice adds a scoped, typed audience-poll workflow to the Spring/JPA bridge and shared Expo client. The target MongoDB architecture remains pending; see [ADR_AUDIENCE_POLLS.md](ADR_AUDIENCE_POLLS.md).

## Delivered

- Approved event owner (faculty/organizer) or administrator can create a validated immutable draft, publish it with an expected revision, and close it. Draft creation is UUID/idempotency-key safe and binds a canonical definition fingerprint; changed reuse conflicts.
- Voting is limited to an enabled student with a current `REGISTERED` or `ATTENDED` registration for the approved event. Waitlisted, cancelled, unrelated and stale accounts are denied. A unique database key and poll lock make same-user votes converge; the original choice is immutable and a changed choice conflicts.
- Server UTC controls the opening/deadline window. A vote at or after the exact close instant is rejected. Existing authorized receipts can be read after close without creating a new vote. Event/module checks and current authorization run before every command.
- Responses are allowlisted aggregate DTOs: options, state, revision, percentages and the current caller's own choice. New votes do not persist voter names or raw IPs. Legacy unversioned poll rows remain retained but fail closed until an approved migration.
- Shared web/Android source provides bounded event and poll pages, manager draft/publish/close actions, explicit vote confirmation, immutable-choice messaging, refresh-based aggregate results, retry-aware uncertain sends and scope/session state clearing. All poll actions are registered for both clients.

## Evidence

- Full isolated backend suite: **263 tests, zero failures/errors/skips**. This includes the final audience-poll source (class timestamp verified after its last edit).
- Independent poll persistence/HTTP review: **17 tests passed**, including real H2 transactions, concurrent same/different choices, close-vote contention, exact deadline, required revision validation, private drafts, feature revocation, CSRF and response-field privacy.
- Client unit suite: **60 passed**; TypeScript check and Expo web export passed. Targeted poll browser checks: **12 passed** on desktop and small viewport; the full shared browser suite: **44 passed** on desktop and small viewport with synthetic mocked API responses.

These checks use synthetic identities and isolated H2/target paths. They do not prove MongoDB replica-set behavior, native Android authentication/camera behavior, real-time result delivery, notification outbox delivery, final tally disqualification, university audience policy or production load/security assessment.

## Remaining before CF-17 acceptance

Implement durable publication/closure notification intents and recipient snapshots, authenticated realtime updates with reconnect/catch-up, reviewed result finalization/disqualification/appeal policy, MongoDB schema/transaction migration, native device tests and full release gates. The legacy active adapter is bounded to its first authorized page and is not an exhaustive history API. Automatic deadline closure is an effective response projection until a worker persists a closure revision.
