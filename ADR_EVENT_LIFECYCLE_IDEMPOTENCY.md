# ADR: Idempotent event lifecycle commands

**Status:** Implemented as a legacy JPA slice; Mongo migration and production contention evidence remain open  
**Date:** 2026-10-04

Cancellation and rescheduling are retriable commands. The API accepts an optional UUID `requestKey` and optional expected schedule-history revision; the shared client creates the key before the first attempt, sends the revision visible in its history panel, and keeps the key while the request is retried. The service normalizes the key, locks the event operations row, authorizes the current event manager, and then checks a unique `(eventId, requestKey)` receipt. A stored receipt is replayed only when its command type and SHA-256 payload fingerprint (including expected revision) match. Reusing a key for a different command or replacement details fails closed. A new command with a stale revision is rejected before venue/event mutation; a legitimate retry is checked for replay before the current-revision guard.

The receipt is written in the same transaction as the event status, linked venue mutation, immutable schedule history and domain audit. This prevents a successful lifecycle change from losing its retry identity. Authorization occurs before receipt lookup so a leaked request key cannot become an ownership oracle. Client UI clears the key only after a successful response, preserving safe retry behavior after a timeout or transient transport failure.

The command receipt is an idempotency boundary, not a delivery guarantee. Notification inbox intents and after-commit channel dispatch retain their own dedupe and delivery state. Resource overlap checks and lifecycle locks remain authoritative, and a receipt never bypasses a current event or facility guard.

Remaining gates are Mongo/replica-set schema migration, unique-index and transaction verification under concurrent barriers, bounded receipt retention policy, durable notification workers/reconciliation, multi-resource recovery, native installed-client rollout compatibility and independent security/UAT evidence. No exactly-once external delivery or zero-defect claim follows from this slice.
