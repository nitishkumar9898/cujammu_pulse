# CUJAMMU-PULSE / College Events Platform — OpenCode 2.0 Handoff

This file is the OpenCode execution handoff. The complete normative architecture remains in `UNIVERSITY_PLATFORM_ARCHITECTURE.md`, `CORE_FEATURES_SPEC.md`, `SCHEDULING_AND_NOTIFICATION_SPEC.md`, `EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md`, `RUNTIME_FEATURES_AND_MEDIA_SPEC.md` and `PLATFORM_PARITY_AND_ACCEPTANCE.md`. Read `AGENTS.md` and `C:\Users\NITISH KUMAR\.codex\RTK.md` first.

## Mission

Continue the existing repository from its current state and implement every remaining architecture group in order. Use complete vertical slices: authoritative backend policy and persistence, shared Expo web/Android UI, action registry, audit/outbox/telemetry, tests, migration and runbooks. Preserve unrelated work, synthetic fixtures and existing data. Do not replace the platform with a smaller demo.

The quality bar is principal-level engineering. Default-deny authorization, privacy minimization, duplicate-safe commands, concurrency correctness, accessible shared UX, dependency review and evidence are mandatory. Zero bugs and zero vulnerabilities are release gates to verify, never facts that can be promised without independent testing.

## Current implemented evidence

- Shared TypeScript registry: 24 module entries and 123 actions registered for web and Android; inspect status files before treating these as accepted production features.
- Universal client typecheck and 106 tests pass; Expo web export previously passed.
- Relevant isolated backend Maven suites previously passed, including QR, venues, chat, polls, sports, judging, media and runtime-control slices. Re-run after each material change.
- Several fabricated/mock native surfaces were removed and now fail closed. Android Gradle compilation is currently unverified because of a JVM/Gradle loopback failure; never call the prebuilt APK a fresh build.
- Important backend areas still use a legacy Spring/JPA bridge. Target authority is Java 21 modular Spring with MongoDB replica-set transactions, validators, indexes and recovery proof.
- Independent security assessment, university UAT, production approval, provider delivery proof, load/SLO evidence and DR restore certification are still open.

## Implemented versus remaining groups

- Foundation/runtime: identity, role restrictions, session checks, CSRF foundations, runtime guards, audit inventory and media deadline foundations exist. OIDC/PKCE, MFA, trusted provisioning, full DTO migration, dependency-aware rollout, encrypted object gateway, quarantine, derivative/backup purge and hold management remain.
- CF-04 Hostel: owner-bound credentials, digest storage, short-lived intents, owner confirmation, gate evidence, `LATE_ENTRY`, guards and shared flow exist. Native authenticated adapter, devices, offline policy, institutional curfew policy, provider delivery, Mongo and device/security/UAT proof remain.
- CF-05 Venues: catalogue, advisory availability, overlap guards, idempotency, review/cancel, runtime checks and shared actions exist. Maintenance/closures, opening-hours/buffers, VC priority preemption, Mongo concurrency, native adapter, load/security/UAT remain.
- CF-06 Library: STUDY/BOOK_ISSUE_RETURN rules, mismatch evidence, server-anchored duration, midnight close, guards, history and private sorted XLSX service exist. Signed facility challenge, physical evidence, scoped report routes, private delivery and Mongo proof remain.
- CF-07 Attendance/events: scoped credentials, SINGLE_QR/DUAL_QR/NO_QR, intents, confirmation, duplicate-safe evidence, volunteer/manual foundations and shared operations screen exist. Native scanner/auth adapter, high-assurance confirmation, live checkpoint, delegation UI, final evidence policy, offline reconciliation, reports, notifications and Mongo/device proof remain.
- CF-08 Academics: verified mapping, batch preflight, review/publish/return, owner-scoped reads, guards and shared actions exist. Secure document adapter/PDF service, quarantine/parser sandbox, publication ledger, archive policy, timetable, notifications, Mongo and privacy/UAT proof remain.
- CF-09 Chat: membership grants/revokes, bounded pages, quiet hours, idempotent posts, quotas and guards exist. Realtime revocation, moderators, outbox/provider delivery, secure attachments, purge, abuse controls, Mongo/native/load/security proof remain.
- CF-10/11/13: teams, judging/rubrics and volunteer foundations exist. Withdrawals, participant binding, judge conflicts, appeals/ties, supervisor evidence, certificates, notifications, Mongo and concurrency/security proof remain.
- CF-14..CF-17: lost/found, helpdesk, sports, polls, feedback/insights, feed, certificates and telemetry have bounded slices. Private media, realtime delivery, moderation, deterministic replay, finalization/appeals, Mongo migration and production evidence remain.
- CF-18..CF-23: lifecycle, calendar, readiness, insights and disruption slices exist. Audience snapshots/dedupe/reconciliation, resource/waitlist intelligence, continuity, privacy-safe clash logic, provider delivery and UAT remain.
- CF-24..CF-30: faculty organizer, volunteer recruitment, QR policy/delegation, ordered exports, runtime controls and media lifecycle are partial. Finish cross-client operations, exports, compatibility rollback, expiry propagation and all acceptance tests.

## Required execution order

1. Establish a clean reproducible baseline without resetting unrelated changes; run typecheck, client tests, Maven compile/tests and inspect failures.
2. Finish CF-04 through CF-08 in order, including native authenticated adapters and independent negative/concurrency/privacy tests.
3. Complete CF-09 through CF-17 in architecture order.
4. Complete CF-18 through CF-23 lifecycle, notification, calendar, readiness and disruption reconciliation.
5. Complete CF-24 through CF-30 cross-client operations, exports, runtime controls and media purge propagation.
6. Perform reviewed additive Mongo migration with rollback/reconciliation scripts.
7. Run parity, accessibility, authorization, retry, conflict, concurrency, offline/provisional and lifecycle tests on web and Android.
8. Produce SBOM, SAST/SCA/secret/container/IaC scans, authorized staging DAST, load results, restore/DR evidence, privacy review and independent security retest.
9. Do not claim production readiness until policy owners sign UAT, all Critical/High findings are closed, native build is reproducible/signed and release gates are documented.

## Non-negotiable invariants

- Backend is authoritative. UI, cache, Redis, notifications and AI never grant roles or decide seats, grades, reservations or attendance.
- Every read/write/export/file/subscription/job/AI tool checks principal, capability, scope, resource state and current runtime feature control.
- Retries require request-key and payload binding; external effects use committed outbox/inbox, never inside retried transactions.
- Mongo replica-set majority writes, transactions, validators, unique/partial indexes and contention guards protect seats, venue intervals, library visits, grades, QR actions and feature controls.
- QR values are short-lived, owner-bound and excluded from logs/exports. Never promise that a displayed QR is noncopyable.
- Manual attendance is independently granted, supervised and audited; it does not imply QR authority or certificate eligibility.
- Academic records are never published from OCR/AI alone; official long-term originals belong in an approved university archive.
- Every uploaded original, PDF, derivative, thumbnail, cache and AI/vector copy expires at `uploadAcceptedAt + 30 days UTC`; access denial precedes physical purge. Legal holds are explicit, scoped and expiring.
- Runtime disable hides compatible routes, invalidates cached state and enforces API/live/job shutdown server-side. Re-enable never restores expired media or grants roles.
- Scheduling suggestions are advisory; confirmation revalidates lifecycle/resource versions.
- Notification intent, dispatch, provider acceptance, delivery, read and acknowledgement are separate states.
- AI uses minimum authorized context, ACL-prefiltered retrieval and server-reauthorized tools. No arbitrary shell/DB/HTTP tools or autonomous adverse decisions.

## Exact OpenCode operating prompt

You are the lead principal engineer for CUJAMMU-PULSE in `C:\Users\NITISH KUMAR\OneDrive\Desktop\Proect Folder\college-events-project`. Read `AGENTS.md`, `OPENCODE_2_HANDOFF.md` and every linked normative specification before editing. Continue from the current repository state; never restart, reset or discard existing work. Implement every remaining group in the required order using vertical slices that include backend policy/persistence, shared Expo web/Android UI, action registry, audit/outbox, telemetry, tests, migration and runbooks.

Use the target architecture: shared TypeScript Expo/React Native/Web clients, Java 21 modular Spring backend, MongoDB replica-set authority, private encrypted object storage, Redis only for ephemeral concerns, transactional outbox/inbox, authenticated WebSocket/SSE and an ACL-filtered AI gateway. Existing JPA/H2 code is migration/reference material, not proof that Mongo is complete.

For each task, state requirement IDs, actors, scopes, data classification, invariants, failure cases, migration/rollback plan and test plan. Implement the smallest complete vertical slice, then run positive and negative authorization, duplicate/retry, conflict/concurrency, offline/provisional and both-client parity tests. Update the relevant status file and append evidence to `IMPLEMENTATION_PROGRESS.md`. Never fabricate records, success toasts, QR scans, grades, certificates, notifications or delivery. If an integration is missing, fail closed and document the exact blocker.

Treat zero vulnerabilities and zero defects as mandatory release gates. Do not claim those outcomes without independent security testing and measured verification. Never weaken security or tests to make a feature appear complete. Do not deploy, publish, import real university data, install services or use production credentials without explicit authorization.

Continue through all groups, not merely one summary, until every normative acceptance scenario is implemented and verified or has a clearly documented external policy/credential blocker. Finish with a machine-readable completion matrix, changed files, commands/results, remaining blockers and exact release-gate status. No production-ready claim is allowed while any required gate is open.

## Final acceptance checklist

- [ ] All 29 active IDs traced to both clients, APIs, scopes, collections and tests; retired CF-12 is not reused.
- [ ] No fabricated production data remains.
- [ ] Mongo replica-set migration, indexes, transactions/concurrency and restore evidence complete.
- [ ] Android build reproducible/signed/device-tested; web and Android parity passes.
- [ ] QR, manual attendance, library, hostel and academic workflows pass negative/security tests.
- [ ] Runtime disable/enable and compatibility rollback pass.
- [ ] Media expiry blocks access and purges approved copies without resurrection.
- [ ] Notification audience, dedupe, retry, provider states and alerts are verified.
- [ ] AI ACL, injection, leakage, tool authorization and multilingual regression tests pass.
- [ ] SBOM, supply-chain scans, load/accessibility/DR/privacy/UAT and independent retest complete.
- [ ] University owners approve policy, operations, incident response and release.
