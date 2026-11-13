# Explicit event proposal review assignment

Date: 26 October 2026. Scope: CF-24 assigned review, shared administration and legacy compatibility. Status: implementation decision; production acceptance remains open.

## Decision and invariants

An active administrator explicitly assigns an active administrator to a pending proposal. The dispatcher may select themselves when they are not the organizer; the organizer cannot assign or decide their own proposal, including after gaining an administrator role. Assignment does not create a workspace, publish the event or grant unrelated event capabilities. Only the currently assigned active administrator may approve/reject, including a retry of an earlier decision. There is no automatic reviewer selected from directory order.

Assignment and decisions contend on the same event row and lifecycle revision. Reassignment invalidates earlier review forms. Exact assignment retry requires matching target, input revision and assigning actor; it does not repeat audit or inbox intent. Selecting the same valid reviewer with the current revision is a no-write result. A proposal edit/resubmission retains its explicit reviewer but advances the proposal revision, so the reviewer must read the new version. Rejected proposals must be resubmitted before reassignment.

The transaction commits assignment provenance, a private audit record containing old/new reviewer IDs and revision, and an enabled private notification/inbox intent together. Notification disabling does not block the assignment or audit. Assignment has no public announcement. Existing approval/rejection delivery behavior is unchanged in this slice; separate approval/workspace and public publication remains required work.

## Schema and compatibility

Four additive nullable JPA fields: assignedReviewerId, reviewerAssignedByUserId, reviewerAssignedAt and lastReviewerAssignmentRevision. Null/partial legacy provenance confers no decision authority. No bulk assignment, historical data rewrite or deletion occurs. Existing approved operations continue; old decision retries without provenance are denied. Database migrations and Mongo equivalents still need independent review and verification.

Legacy list quick-approve buttons now lead to review detail. Detail decision controls appear only for the current assigned reviewer; the server independently enforces the same rule. The shared web/Android source provides a paginated queue, reviewer selection and decisions without manually entered IDs. An old client cannot bypass assignment by calling the old MVC endpoint.

## API and private data

All routes require a fresh active administrator and enabled MODULE_EVENTS. Requests retain normal session/CSRF protections; outputs and errors are no-store. Lists are deterministic pages of 20 with hasNext, bounded page index and explicit DTOs.

| Operation | Contract |
|---|---|
| GET /api/v1/admin/event-proposals?page=0 | Pending proposal page; revision, assignment availability, decision capabilities and proposal review fields |
| GET /api/v1/admin/event-proposals/reviewers?page=0 | Active administrator ID/name page; no email, password or enrollment identity |
| PUT /api/v1/admin/event-proposals/{id}/reviewer | expectedRevision + reviewerId; returns authoritative proposal DTO |
| POST /api/v1/admin/event-proposals/{id}/approve | expectedRevision; 204 after committed decision |
| POST /api/v1/admin/event-proposals/{id}/reject | expectedRevision + bounded reason; 204 after committed decision |

Proposal DTOs include title/description, venue/interval, organizer name, capacity/fees/audience/category/certificate setting and attachment-presence indicators. File keys/URLs, student rosters and account credentials are not included. Complete shared attachment inspection remains open. A malformed/uncertain client response clears private selection and requires authoritative reload; it never becomes optimistic approval.

## Concurrency limits and verification

No reviewer/actor user lock is added after the feature/event locks: existing mail delivery locks recipient user before feature/event, so the inverse would introduce a deadlock. Fresh account checks deny accounts already disabled/demoted when the command reads them, but this slice does not claim linearization of concurrent account revocation. A common lock/revocation design and real Mongo multi-instance proof remain release work.

Independent real H2/MVC tests cover authorization, null/partial legacy assignment, current assignee, retries/reassignment, audit/notification rollback, flag shutdown, pagination/projection and assignment-versus-approval contention. Shared decoder, transport and desktop/small-web UI tests complement actual synthetic browser/backend review. Exact final verification results belong in EVENT_PROPOSAL_IMPLEMENTATION_STATUS.md; no completion is inferred from this ADR.
