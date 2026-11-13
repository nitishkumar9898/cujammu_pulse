# ADR: Immutable event schedule-change history

**Status:** Implemented as a legacy JPA slice; production migration and outbox evidence remain open  
**Date:** 2026-10-03

Effective event cancellation and rescheduling now append an immutable `EventScheduleChange` record after the existing event/venue guards and lifecycle command succeed. Each record stores a per-event sequence, change type, previous/replacement date, bounded participant-safe reason and actor identity snapshot. Actor email is never returned to clients; the manager-scoped history DTO exposes only role and timestamp. Duplicate cancellation is stable and a duplicate reschedule (same date/reason) does not create another history row.

The schedule history is advisory evidence of a committed domain change. It is not a reservation authority: confirmation still revalidates the linked facility interval under its existing lock, and suggestions never reserve a slot. Notification delivery remains governed by the existing post-commit flow; this slice does not claim a durable provider outbox.

Request-key idempotency is now implemented for lifecycle commands; see [ADR_EVENT_LIFECYCLE_IDEMPOTENCY.md](ADR_EVENT_LIFECYCLE_IDEMPOTENCY.md). Remaining gates are Mongo/replica-set migration, multi-resource workflow recovery, immutable audit custody, notification worker reconciliation and independent concurrency/security/UAT evidence.
