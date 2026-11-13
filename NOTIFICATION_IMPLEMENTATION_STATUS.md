# Notification implementation status

Implemented:

- Owner-scoped paginated inbox and acknowledgement operations.
- Runtime `MODULE_NOTIFICATIONS` guard with commit-safe local SSE shutdown.
- Durable `NotificationDeliveryIntent` for every inbox notification, unique by notification/channel and committed atomically with the inbox row.
- Stable in-app dedupe key and explicit `INBOX_CREATED` state; no false push/provider-delivery claim.
- Bounded lease-based in-app reconciliation worker: pessimistic due-row claim, 90-second lease, attempt counter, capped exponential retry and `DEAD_LETTER` after five failed claims. `RECONCILED` means only that the owner-scoped inbox row is present.

Remaining:

- Per-instance control-revision polling now closes SSE streams on every instance after a committed notification disable. The reconciliation worker also emits bounded Micrometer counters (`cujammu.notifications.intent.reconciliation` tagged by `outcome=pass|reconciled|retry_wait|dead_letter`); deployment still must bind alerts and an on-call owner before production. A reviewed pub/sub adapter can reduce the bounded polling delay later, but database state remains authoritative.
- Versioned affected-audience snapshots for event/sports/hostel/academic changes.
- Approved push/email provider adapters with signed callbacks and distinct dispatched/accepted/delivered/read/acknowledged states.
- Mongo replica-set migration, load tests, provider outage drills and independent security/privacy/UAT review.
