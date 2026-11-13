# ADR: Owner-confirmed hostel gate credentials

**Status:** Implemented as a backend and shared-client slice; not a release approval  
**Date:** 2026-10-03  
**Scope:** CF-04 hostel gate-pass credential verification

## Decision

The legacy raw `HostelGatePass.qrToken` scanner remains fail-closed. A new credential is issued only to the authenticated pass owner for the next permitted action (`EXIT` or `ENTRY`). The server stores a SHA-256 digest and a short validity window; the raw value is returned only in the owner-scoped issuance response. Rotation deactivates the previous credential.

An authenticated hostel operator creates a short-lived scan intent from the presented credential. The intent contains a digest of a six-digit challenge, a request key and the assigned hostel scope. The owner confirms the intent in the authenticated app using the challenge. Confirmation locks the intent, pass and credential, reauthorizes current approval/lifecycle/operator scope, consumes the credential, appends immutable gate evidence and advances `APPROVED -> ACTIVE_OUT -> RETURNED_ON_TIME/LATE_ENTRY`. An EXIT confirmation issues the next ENTRY credential. Repeated request keys and confirmations are stable; changed payload reuse is rejected.

This provides owner confirmation and replay resistance at the server boundary without claiming that a displayed QR is non-copyable. A copied credential alone cannot complete the workflow, but device compromise, owner account compromise and supervised manual fallback remain separate threats requiring policy and independent assessment.

## Invariants

- No raw credential, challenge or secret is persisted, logged or included in DTOs other than the owner issuance response.
- Every command checks the runtime hostel feature, authenticated principal, assigned-hostel scope, pass lifecycle, expiry and expected action.
- Evidence is append-only and uniquely constrained by pass/action; credential and intent rows are contention-locked.
- EXIT must precede ENTRY; late status is derived from the approved return deadline and configured Asia/Kolkata curfew policy.
- The old token field is retained only for non-destructive migration planning and is not a usable scanner authority.

## Open release gates

Real MongoDB replica-set transaction/index migration, operator device registration and revocation, approved curfew/extension policy, durable outbox/push delivery, native Android API/scanner integration, legacy-data rotation, load/concurrency evidence and an independent security assessment are still required. The slice is therefore not CF-04 acceptance or production approval.
