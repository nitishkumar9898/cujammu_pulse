# ADR: Volunteer completion evidence and approval

**Status:** Accepted for the first hardened volunteer slice (3 October 2026)

Task creation and assignment now require an enabled volunteer module, an approved event and event-manager authority. Assignments require a verified student enrollment identity. A volunteer submits bounded hours as `COMPLETION_REQUESTED`; only an event manager can lock and approve that request, mark the task complete and unlock a certificate. Row locks and optimistic revisions prevent concurrent reassignment/approval from silently overwriting state.

This does not yet implement supervisor delegation, approved hour policies, attendance-backed evidence, certificate rendering, notification outbox or independent client/security evidence.
