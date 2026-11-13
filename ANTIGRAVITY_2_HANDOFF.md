# CUJAMMU-PULSE / College Events Platform — Antigravity 2.0 Handoff

Date: 2026-10-05 (Asia/Kolkata)

## Mission

Continue this repository from its current state and take it through the complete accepted architecture. Work in vertical slices: authoritative backend policy and persistence, shared Expo web/Android UI, audit/outbox/telemetry, tests, migration and runbooks. Preserve unrelated user changes and do not delete existing data, uploads, APKs or source without a reviewed migration.

The quality bar is principal-level engineering: default-deny authorization, privacy minimization, duplicate-safe commands, concurrency correctness, accessible shared UX, dependency review and evidence for every claim. Treat zero bugs and zero vulnerabilities as release-blocking quality targets, never as a fact that can be promised without tests and independent assessment.

## Read these files first, in order

1. `AGENTS.md` and `C:\Users\NITISH KUMAR\.codex\RTK.md`.
2. `UNIVERSITY_PLATFORM_ARCHITECTURE.md` — normative architecture, trust boundaries and data model.
3. `CORE_FEATURES_SPEC.md` — active CF-01..CF-30 requirements; CF-12 is retired and must never be reused.
4. `SCHEDULING_AND_NOTIFICATION_SPEC.md` — postpone/reschedule/cancel, availability suggestions and notification contracts.
5. `EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md` — faculty organizers, volunteers, SINGLE_QR/DUAL_QR/NO_QR and delegation.
6. `RUNTIME_FEATURES_AND_MEDIA_SPEC.md` — admin runtime controls and immutable 30-day uploaded-media expiry.
7. `PLATFORM_PARITY_AND_ACCEPTANCE.md` — shared action registry and release acceptance scenarios.
8. `PROJECT_ARCHITECTURE_REVIEW.md`, `PLATFORM_RISK_AND_RESOLUTION_REVIEW.md` and all `*_IMPLEMENTATION_STATUS.md` files.
9. `IMPLEMENTATION_PROGRESS.md` — append-only implementation evidence.

Do not replace the architecture with a smaller demo. Do not import real university records or secrets. Use synthetic fixtures only.

## Current implementation evidence

- Shared TypeScript registry currently contains 24 implemented module entries and 123 shared actions, registered for both `web` and `android`. Registry status does not mean production acceptance; inspect each status file and test evidence.
- Shared client `npm run typecheck` passes. The universal client suite currently passes 106 tests. Expo web export previously passed.
- Backend Maven regression suites previously passed with zero reported failures/errors in the isolated test environment, including QR, venue concurrency, chat, polls, sports, judging, media and runtime-control slices. Re-run after every material change.
- Android source is retained as a migration/reference client. Several fabricated/mock screens were removed and changed to fail closed. Current native Gradle compilation is unverified because this environment has a JVM/Gradle loopback connection failure. Never report the prebuilt APK as a fresh build.
- Backend architecture is still a legacy Spring/JPA bridge in important areas. The target architecture is Java 21 modular Spring backend with MongoDB replica-set as the authoritative application store; Mongo migration and replica-set proof remain open.
- No independent security assessment, university UAT, production deployment approval, real provider delivery proof, load/SLO evidence or DR restore certification has been completed.

## Implemented or partially implemented groups

### Foundation and runtime control

- Identity boundary hardening, role registration restrictions, session-state checks, password/reset containment and CSRF foundations exist. OIDC/PKCE, trusted university provisioning, MFA, full DTO migration and production identity integration remain open.
- Runtime admin feature controls, audit inventory, route eviction and service-side guards exist for many modules. Complete dependency-aware catalogue, compatible-client rollout/rollback and full job/subscription enforcement remain open.
- Media metadata, immutable 30-day deadline, digest, expiry denial and cleanup foundation exist. Encrypted private object gateway, quarantine, derivatives, backup purge evidence, hold management and legacy migration remain open.

### CF-04 Hostel passes and curfew

Implemented: typed owner-bound credential issuance, digest-only storage, short-lived scan intents, owner confirmation, append-only gate evidence, `LATE_ENTRY`, runtime guard, shared web flow and late-entry inbox notification. Native fabricated approval/reference/QR behavior was removed and the submission button now fails closed until authenticated API/session wiring exists.

Remaining: real native authentication/API/scanner adapter, registered gate devices, operator/device binding, offline policy, university curfew/grace/extension/emergency policy, full notification provider delivery, Mongo migration and independent device/security/UAT proof.

### CF-05 Venues and sports grounds

Implemented: facility catalogue, advisory availability suggestions, overlap conflict checks, transactional guard/concurrency slice, booking idempotency, pending review/cancel, runtime `MODULE_VENUES` checks at service boundary, shared web/native actions and request-key parity.

Remaining: maintenance/closure/opening-hours data model and overlays, VC-office priority preemption, full resource/accessibility buffers, Mongo transaction/index proof, native authenticated adapter, load/security/UAT evidence.

### CF-06 Smart library turnstile

Implemented: STUDY vs BOOK_ISSUE_RETURN purpose allowlist, locked active visit, mismatch evidence, server-anchored duration, midnight auto-close, runtime guards, owner history and private sorted styled XLSX service method. Legacy entry/exit/export HTTP routes intentionally return `LIBRARY_VERIFICATION_UNAVAILABLE`.

Remaining: signed facility QR challenge issuance/verification, physical scan evidence, authorized librarian/admin report route, verified CSV/XLSX endpoint wiring, private delivery and Mongo proof. Do not enable legacy routes as a shortcut.

### CF-07 Event attendance and operations

Implemented: owner-scoped opaque credentials, SINGLE_QR/DUAL_QR/NO_QR checks, scanner intents, owner confirmation, duplicate-safe evidence, volunteer recruitment/operations slices, manual fallback policy, ordered report foundations, lifecycle request-key parity and shared operations screen.

Remaining: CameraX/native scanner and authenticated identity adapter, WebAuthn/high-assurance confirmation, live second-checkpoint notification, full QR/manual delegation UI, final presence/duration policy, offline provisional reconciliation, report finalization/checksum/manifest, provider notifications, Mongo/concurrency/device proof.

### CF-08 CoE scorecards and timetable

Implemented: verified identifier mapping, individual and batch preflight, independent review/publish/return, owner-scoped student read projection, runtime guards, shared web/native registry actions and request-key parity. Fabricated Android grades/name/enrollment/semesters were removed; legacy native screen fails closed until authenticated data service exists.

Remaining: secure authenticated Android/web document adapter, official private PDF service, academic file quarantine/parser sandbox, immutable publication/reissue ledger, Mongo migration, external institutional archive policy, notification delivery, timetable integration and independent privacy/UAT review.

### CF-09 Chat and community

Implemented: explicit membership grants/revokes, bounded pages, quiet-hours policy, idempotent posting, quotas, stale-scope clearing, feature guards, shared chat screens.

Remaining: realtime subscriptions/revocation, moderator provisioning, durable outbox/provider delivery, secure attachments and hold-aware 30-day purge, cross-group abuse controls, Mongo/native/load/security evidence.

### CF-10 Teams, CF-11 judging, CF-13 volunteers

Implemented: event-scoped team codes/locks/history, judge assignment/rubric bounds/revisions, volunteer task/completion/approval foundations, shared screens and feature controls.

Remaining: reviewed withdrawal/removal, canonical participant binding, conflicts of interest, appeals/tie-breaks, supervisor evidence/hours/certificate generation, notifications, Mongo and independent concurrency/security tests.

### CF-14..CF-17 campus services

Lost & Found, Helpdesk, sports fixtures/score correction, audience polls, feedback/insights, campus feed, certificates and operations telemetry have bounded slices and shared routes. Most remain partial because private media/quarantine, realtime delivery, moderation, deterministic replay, finalization/appeals, Mongo migration and production evidence are open. Read every status file before marking one complete.

### CF-18..CF-23 lifecycle, calendar, readiness, insights and disruptions

Advisory reschedule suggestions, lifecycle history, postpone/cancel commands, calendar/readiness/insights/disruption screens and runtime controls exist in partial form. Remaining: full affected-audience snapshot/dedupe/reconciliation, resource and waitlist intelligence, verified disruption continuity, privacy-safe calendar clash logic, provider delivery and institutional policy/UAT.

### CF-24..CF-30 newer requirements

Faculty organizer workspace, volunteer recruitment, selectable QR policy, independent scanner/manual delegation, ordered exports, runtime controls and 30-day media lifecycle are specified and have partial vertical slices. The authoritative event operations contract supersedes old “QR-2 is always exit” assumptions. Do not reintroduce old behavior.

## Required execution order for Antigravity

1. Establish a clean reproducible baseline without resetting unrelated changes. Run typecheck, client tests, Maven compile/tests and inspect failures.
2. Finish CF-04 native authenticated API/device workflow and its independent tests.
3. Finish CF-05 maintenance/closure/resource policy, preemption and Mongo concurrency proof.
4. Finish CF-06 signed library facility challenge, physical evidence, scoped report endpoints and Mongo proof.
5. Finish CF-07 event QR/manual scanner parity, live checkpoint, final evidence/reporting and notification workflow.
6. Finish CF-08 academic secure document/publication/timetable workflow and native parity.
7. Complete CF-09..CF-17 domain gaps in architecture order; preserve each module's privacy and feature-control boundary.
8. Complete CF-18..CF-23 lifecycle/notification/calendar/readiness/disruption reconciliation.
9. Complete CF-24..CF-30 cross-client operations, exports, runtime controls and media purge propagation.
10. Perform Mongo migration only through reviewed additive migrations; retain rollback and reconciliation scripts.
11. Run web/Android parity, accessibility, negative authorization, duplicate/retry, conflict/concurrency, offline/provisional and lifecycle tests.
12. Produce SBOM, SAST/SCA/secret/container/IaC scans, authorized staging DAST, load results, restore/DR evidence, privacy review and independent security retest.
13. Do not call the platform production-ready until university owners sign UAT/policy, all Critical/High findings are closed, native build is reproducible/signed, and release gates are documented.

## Non-negotiable invariants

- Backend is authoritative; UI flags, cache, Redis, notifications and AI output never grant access or decide seats, grades, reservations or attendance.
- Every read/write/export/file/subscription/job/AI tool checks principal + capability + scope + resource state + current feature control.
- All retries use request keys and payload binding; all external side effects use committed outbox/inbox, never inside retried transactions.
- MongoDB transactions, replica-set majority writes, unique/partial indexes and shared contention guards protect seats, venue intervals, active library visits, grades, QR actions and feature controls.
- QR bearer values are short-lived, owner-bound and never logged/exported. A displayed QR is copyable; never promise noncopyability.
- Manual attendance is independently granted, supervised and audited; it does not imply QR authority or certificate eligibility.
- Academic records are never published from OCR/AI alone. Official long-term originals belong in an approved university archive.
- Uploaded originals, PDFs, derivatives, thumbnails, caches and AI/vector copies expire at `uploadAcceptedAt + 30 days UTC`; access denial precedes physical purge. Legal holds are explicit, scoped and expiring.
- Runtime disabling must hide compatible client routes, invalidate cached state and enforce API/live/job shutdown server-side. Re-enabling never resurrects expired media or grants roles.
- Suggestions are advisory, never reservations. Confirmation revalidates current lifecycle/resource versions.
- Notification states distinguish intent, dispatch, provider acceptance, delivery, read and acknowledgement. Never claim device delivery from an inbox row.
- AI receives minimum authorized context, ACL-prefiltered retrieval and server-reauthorized tools. No arbitrary shell/DB/HTTP tools and no autonomous adverse decisions.

## Exact Antigravity 2.0 operating prompt

You are the lead principal engineer for CUJAMMU-PULSE in `C:\Users\NITISH KUMAR\OneDrive\Desktop\Proect Folder\college-events-project`. Read `AGENTS.md`, this handoff and every linked normative specification before editing. Continue from the repository state; do not restart or discard existing work. Implement every remaining group in the required order, using vertical slices that include backend policy/persistence, shared Expo web/Android UI, action registry, audit/outbox, tests, migration and runbooks. Preserve unrelated files and synthetic-only test data.

Use the target architecture: shared TypeScript Expo/React Native/Web clients, Java 21 modular Spring backend, MongoDB replica-set authority, private encrypted object storage, Redis only for ephemeral concerns, transactional outbox/inbox, authenticated WebSocket/SSE and an ACL-filtered AI gateway. Existing JPA/H2 code is migration/reference material, not proof that Mongo architecture is complete.

For each task: state requirement IDs, actors, scopes, classification, invariants, failure cases, migration/rollback plan and test plan; implement the smallest complete vertical slice; run positive/negative authorization, duplicate/retry, conflict/concurrency, offline/provisional and both-client parity tests; update status and `IMPLEMENTATION_PROGRESS.md`; report concrete evidence and residual risks. Never fabricate UI records, success toasts, QR scans, grades, certificates, notifications or delivery. If a native/API integration is missing, fail closed and record the blocker.

Treat zero vulnerabilities and zero defects as mandatory release gates. Do not claim those outcomes unless independent security testing and measured verification prove them. Never weaken security or tests to make a feature appear complete. Do not deploy, publish, import real data, install services or use production credentials without explicit authorization.

Do not stop after a summary: continue through all groups until every normative acceptance scenario is either implemented and verified or has a clearly documented external policy/credential blocker. At the end, provide a machine-readable completion matrix, changed files, commands/results, remaining blockers and exact release-gate status. No production-ready claim is allowed while any required gate remains open.

## Handoff acceptance checklist

- [ ] All 29 active IDs traced to both clients, APIs, scopes, collections and tests.
- [ ] No retired CF-12 functionality or identifier reintroduced.
- [ ] No fabricated data remains in native/web production paths.
- [ ] Mongo replica-set migration, indexes, transaction/concurrency and restore evidence complete.
- [ ] Android build reproducible, signed and device-tested; web export and browser/device parity pass.
- [ ] QR, manual attendance, library, hostel and academic workflows pass negative/security tests.
- [ ] Runtime disable/enable and client compatibility rollback pass.
- [ ] Media expiry blocks access and purges all approved copies without resurrection.
- [ ] Notification intent/audience/dedupe/retry/provider states and alerts verified.
- [ ] AI ACL, injection, leakage, tool authorization and multilingual regression tests pass.
- [ ] SBOM, supply-chain scans, load/accessibility/DR/privacy/UAT and independent security retest complete.
- [ ] University owners approve policy, operations, incident response and production release.
