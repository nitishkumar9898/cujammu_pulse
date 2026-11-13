# Department chat membership implementation

Updated 3 October 2026. CF-09 remains partial; this continuation adds a tested explicit-membership workflow. See [decision and migration boundaries](ADR_CHAT_MEMBERSHIP.md).

## Delivered

- Admin-only, no-store department-group catalogue and bounded membership pages.
- Explicit grant/revoke using subject account ID, expected revision and a required reason. Active grants require a reviewed-source reference and an enabled eligible account. Self-grants and generic role-based access are denied.
- Unique current membership and append-only provenance; audit and safe recipient inbox notice commit with the change under current notification controls.
- Department reads/posts require explicit active grants, independent of mutable profile department. Group locking orders grant revocation with message acceptance/read authorization.
- Generic ADMIN no longer implies event/department message access. Event owner/confirmed participant access remains separately scoped. No moderator authority is conferred by department membership.
- Protected admin inspection and revocation remain available when chat is OFF; new grants and ordinary chat stay blocked. Events OFF does not suppress authorized department groups.
- Shared web/Android-source Chat access panel for administrators, with grant/revoke, stale-revision handling, source/reason fields, paging and grant-disabled state. Ordinary users do not see this administration route.
- Database-scoped 20-row group paging and bounded membership checks replace unbounded directory scans/whole registration-history loads. Private thread/draft state clears on page changes; late results cannot cross scopes.

## Verification completed

| Check | Result |
|---|---|
| Full backend suite, isolated synthetic H2 | 234 tests, 0 failures/errors/skips |
| ChatMembershipPersistenceTest, included above | 7 tests: real repositories/transactions, private grants, revocation, current account checks, concurrent same-revision decisions, revoke versus blocked read/post, provenance, 20+1 scoped directory paging |
| CommunityChatPolicyTest, included above | 21 policy tests including quiet-hour boundaries, no implicit admin authority and terminal event restrictions |
| PlatformHttpWorkflowTest, included above | Real authenticated HTTP grant/read/post/revoke with CSRF, no-store, unauthorized/anonymous rejection and OFF-state recovery |
| Client unit tests | 55 passed |
| TypeScript check and Expo web export | Passed |
| Full Edge browser suite, desktop and small viewport | 26 passed, synthetic mocked API responses |

Commands: backend uses the isolated Maven command documented in `TEAM_AND_CHAT_IMPLEMENTATION_STATUS.md`; client uses `npm test`, `npm run typecheck`, `npm run build:web` and `npm run test:web`, all invoked through `rtk proxy`. Existing database/log/upload files were not used as test data. No services were deployed and no real institutional memberships were imported.

Independent review led to protected revocation while OFF, removal of implicit administrator content access, explicit database contention tests and bounded directory checks. Tests do not establish native-device parity, MongoDB transaction correctness or absence of undiscovered defects.

## Still required

University-approved grant expiry/review cadence and non-file record retention; reviewed additive production migration; independently authorized provisioner onboarding; moderator grants, realtime revocation, secure attachments, hold-aware cleanup, and durable notification outbox. [The subsequent chat-posting slice](CHAT_POSTING_IMPLEMENTATION_STATUS.md) adds bounded acceptance and idempotent message receipts; the verification table above records the earlier membership checkpoint. The current admin catalogue has a 100-group bound and fails explicitly above it pending catalogue paging. Directory offsets may shift during concurrent changes and are not a snapshot export. Repeated/stale grant mutations return conflict and require refresh; they do not yet return a durable idempotent retry receipt.

Native authentication/device tests, MongoDB migration, remaining architecture modules, independent security assessment and university release sign-off remain open in [implementation ledger](IMPLEMENTATION_PROGRESS.md).
