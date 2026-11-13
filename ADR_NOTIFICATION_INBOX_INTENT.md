# ADR: Durable in-app notification intent

**Status:** Implemented as an inbox/outbox foundation; external delivery is not enabled  
**Date:** 2026-10-03

Every persisted in-app notification now creates a unique `IN_APP` delivery-intent row in the same transaction. The intent records a stable dedupe key, `INBOX_CREATED` status and attempt metadata. The university inbox remains the source of truth; SSE publication still happens only after commit and is best effort. A later push/email adapter can reconcile these rows without guessing whether an inbox record existed.

The intent intentionally does not report provider acceptance, device delivery or read acknowledgement. Those states require separate channel delivery rows, signed provider callbacks and approved contact policy. Runtime notification disablement still blocks new inbox mutations and closes local live streams after commit.

The repository field is optional only for isolated legacy unit tests; the production Spring context injects it and schema startup is covered by the H2 suite. Mongo migration, worker leases/retry/dead-letter processing, cross-instance stream invalidation, provider integration and independent load/security evidence remain open.
