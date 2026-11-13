# ADR: Durable notification-intent reconciliation

**Status:** Implemented as a bounded legacy JPA worker; provider adapters and Mongo migration remain open  
**Date:** 2026-10-04

Every in-app notification already creates a `NotificationDeliveryIntent` in the same transaction as the inbox row. This slice adds a lease-based reconciliation worker for that durable handoff. A worker claims only bounded due rows under a pessimistic query lock, increments attempts, and records a 90-second owner lease. A crashed worker leaves an expired lease that another instance can reclaim; a live lease cannot be processed by a different worker.

The current `IN_APP` channel is reconciled only to `RECONCILED`, meaning the notification row and its owner-scoped inbox projection are present. It does not mean a push provider accepted or delivered anything. Missing references fail with bounded exponential backoff and become `DEAD_LETTER` after five attempts. Error text is capped and no notification body, contact data or provider secret is copied into the intent.

The scheduler is a process-local trigger only; database intent state, leases and unique notification/channel keys are authoritative. Disabling the ordinary notifications module prevents new reconciliation claims while preserving rows for later policy-approved recovery. The worker has a bounded batch and fixed lease so it cannot load unbounded inbox history or monopolize a transaction.

Remaining gates are provider-specific outbox/delivery adapters with signed callbacks, versioned affected-audience snapshots, cross-instance metrics/alerts, outage and dead-letter runbooks, Mongo replica-set migration, load/concurrency evidence, retention policy for receipts and independent security/privacy/UAT review.
