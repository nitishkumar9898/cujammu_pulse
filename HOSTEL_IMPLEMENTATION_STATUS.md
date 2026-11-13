# Hostel implementation status

## Implemented in the current slice

- Typed owner-scoped credential issuance for approved/out passes with digest-only persistence and rotation.
- Operator-scoped scan-intent creation with two-minute expiry and a six-digit owner challenge.
- Owner confirmation with pass/credential/intent locks, lifecycle checks, append-only gate evidence and canonical `LATE_ENTRY` calculation.
- Retry-stable request keys and no-store controller responses; raw legacy scanner route remains fail-closed.
- Shared Expo web/Android source exposes owner issuance and pending-intent status behind the hostel module flag.
- Focused backend tests, full H2 Maven verification, shared TypeScript typecheck and client tests pass in this workspace.

## Remaining before CF-04 acceptance

- Guard all older hostel command/read routes with the same runtime feature policy and migrate callers safely.
- Replace the separate native Kotlin legacy screen with the real API/scanner/secure-storage adapter and prove web/native action parity (demo credentials/PII have been removed).
- Register and revoke gate devices, define offline policy, and bind operator/device identity to evidence.
- Finalize university-approved curfew, grace, extension, emergency and consent rules.
- Add durable notification outbox/provider receipts, Mongo replica-set migration/index evidence, load/concurrency/device tests, legacy token rotation and independent security/UAT review.

This status is an implementation record, not a claim of zero vulnerabilities or production readiness.
