# Volunteer task and hours implementation status

## Implemented local foundation

- Event managers create tasks and assign only enabled, verified students with an accepted event membership. Event lifecycle/runtime feature checks, optimistic task revisions and UUID command receipts guard mutations.
- Assigned volunteers can submit bounded past-shift intervals and evidence. The server rejects future/over-24-hour intervals, hours above interval duration, shifts predating assignment, shifts beyond a completed event, removed roster members and self-approval.
- A distinct authorized event manager reviews completion. The server serializes credited-hour approvals on a common volunteer row, rejects half-open interval overlap across events, and commits task state, audit, notification and immutable command receipt in the transaction.
- The shared TypeScript client provides manager assignment, volunteer submission and review states. It displays submitted hours separately from approved hours and explicitly says approval does not automatically issue a certificate. DTOs expose capability booleans rather than entities.

## Verification completed

- `VolunteerWorkflowPersistenceTest`: **10/10 passed** on the synthetic H2/JPA profile using Maven 3.9.9, Oracle JDK 25.0.2 with Java release target 21. Tests include accepted-roster authorization, stable retries/conflicts, stale revisions, removal revocation, shift bounds, audit rollback, one-winner assignment concurrency, sequential overlap rejection, and a new concurrent cross-event overlapping-approval test.
- The cross-event test now asserts one success, one overlap-specific rejection, exactly one persisted `COMPLETED` task with 0.5 credited hours, and the rejected task remaining `COMPLETION_REQUESTED` with zero credited hours. An independent read-only review found no source/test assertion defect after tightening; the review confirmed this remains H2/JPA-only evidence. It does not force both calls to rendezvous at the shared lock itself.
- Shared client `npm test`: **140/140 passed**; `npm run typecheck` passed in the current turn. The volunteer-specific decoder/action tests passed within that suite. No browser-to-real-backend volunteer workflow was run.

## Still open

- H2/JPA is not the approved Mongo persistence path. Replica-set transaction behavior, common-writer contention, durable audit/notification/receipt rollback and real concurrent conflict recovery remain unverified; no Mongo volunteer adapter/cutover exists.
- University policy has not set cumulative-hours eligibility, approved attendance/evidence rules, corrections/reversals, or certificate criteria. Certificates remain unavailable and `certificateAwarded` stays false.
- A separate supervisor/delegation provisioner and institution-specific reviewer policy are not implemented. Event ownership authorizes the current review flow; this does not establish a university staffing model.
- Installed native Android sign-in and matching volunteer workflows/device acceptance remain open. The standalone Kotlin app is still closed behind the pending OIDC/PKCE boundary.
- Production acceptance still requires authenticated browser/device UAT, load/concurrency tests on the selected production database, privacy/security review and university sign-off.
