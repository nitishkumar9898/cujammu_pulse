# ADR: Explicit department-chat membership and protected revocation

Date: 3 October 2026. Implementation decision for the legacy JPA bridge; MongoDB target unchanged. Requirements: CF-09, CF-29, AUTH-SCOPE-01, CHAT-MEMBERSHIP-01.

## Context and decision

Profile department is self-declared and cannot authorize access to private conversations. A chat-specific membership now binds a user ID to a department group ID. It does not certify or change the user's official department, academic authority, hostel assignment or event role. An authenticated administrator must independently review an institutional source before explicitly granting access, enter a nonpersonal source reference and reason, and cannot self-grant. Enabled student/faculty/staff/organizer subjects are eligible for reviewed membership; role alone never proves affiliation. Administrator management access excludes message content. Event group content follows confirmed/attended registration or the owning faculty/organizer; generic admin status provides no implicit read/post right.

Each `(groupId,userId)` has one unique current record with optimistic revision. Changes append a separate provenance record (actor, subject, scope, revision, state, UTC instant, source reference and reason). The ordinary audit log records identifiers/state without the source evidence. Public group/message DTOs exclude membership IDs and verification references; admin member lists disclose only account ID, name, state and revision.

Grant changes, departmental message reads and posts share the group-row contention point. Event writes acquire the event operations row before the group row, after feature controls, maintaining lifecycle ordering. Grant enables acquire CHAT control then group; revoke acquires group without requiring business enablement. Reads check current principal and membership after the group lock. The tested JPA implementation serializes group reads and writes; production scale/latency must be measured and the equivalent MongoDB contention write must be designed and retested before migration.

Chat OFF denies ordinary reads/posts and new grants. Protected admin catalogue/member inspection and revoke remain available through service-level ADMIN checks, independent of the ordinary business feature interceptor. The shared client exposes a separate Chat access tab only to admins even when Chat is OFF. Department chat is independent of Events; disabling Events removes event conversations from the server directory and denies direct event content without suppressing authorized department conversations.

Group discovery uses a database-scoped EXISTS query and 20-row pages ordered by ID, followed by bounded current-scope rechecks. Offset pages may shift during concurrent grant/group changes; they are not a snapshot export. The shared screen clears thread data/drafts before paging and discards late responses from a previous scope. Legacy `/chat/groups` returns only the first bounded page; compatible clients use `/chat/groups/page` and `hasMore`.

## Notifications and retries

An effective grant/revoke records one safe recipient inbox notice in the same transaction if Notifications is enabled. Priority is normal; audience is only the affected account; text contains no source/reason/group content and links to the authenticated app. The notice follows the existing inbox retention policy and does not attach media. Stale/duplicate revisions fail without another accepted mutation, history row or notice; after a lost response the operator must refresh and review, rather than automatically resubmit a privilege change. A durable request-key retry receipt and external outbox delivery remain open. Disabled Notifications currently suppresses the inbox notice; the independent audit/provenance remains. This is not the final mandatory-delivery architecture.

## Migration, recovery and boundaries

- Add `chat_memberships` and `chat_membership_changes` tables with unique membership scope and bounded provenance columns. Existing production databases require a reviewed versioned migration; Hibernate-generated test schema is not migration approval.
- Do not infer grants from `User.department`, old account roles, group names or prior message authors. No production data backfill/import was performed. Existing conversations are retained, and users without an explicit grant remain denied.
- Apply backend authorization before enabling the compatible admin/client workflow. Review every institutional verification source and provisioner assignment. Rollback must preserve evidence and return department content to fail-closed containment, never restore the former self-declared department check.
- Membership duration/review cadence and non-file record retention require an approved university policy. This bridge currently has explicit revocation, not automated term expiry; no perpetual institutional entitlement is claimed. These are release blockers.
- Moderator grants, user/group posting limits, realtime authorization/revocation, idempotent posting, event-group provisioning and hold-aware cleanup remain separate required work. Unsafe legacy deletion stays unavailable. Uploaded media remains unavailable until CF-30 private retention is implemented.

Evidence: [chat membership implementation status](CHAT_MEMBERSHIP_IMPLEMENTATION_STATUS.md). This ADR records implementation choices and limitations; it is not university production acceptance.
