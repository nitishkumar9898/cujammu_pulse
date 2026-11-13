# CUJAMMU-PULSE Agent and Engineering Instructions

## 1. Scope and authority

These instructions apply to this university platform workspace and all participating agents. User instructions take precedence. Preserve the required local command rule from `C:\Users\NITISH KUMAR\.codex\RTK.md`: prefix shell commands with `rtk`; use `rtk proxy` when a supported raw command is needed. Non-shell tools do not take an rtk prefix.

Read before implementation:

1. `UNIVERSITY_PLATFORM_ARCHITECTURE.md` - full scope, trust boundaries and design decisions.
2. `CORE_FEATURES_SPEC.md`, `SCHEDULING_AND_NOTIFICATION_SPEC.md`, `EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md` and `RUNTIME_FEATURES_AND_MEDIA_SPEC.md` - 29 active core requirements CF-01..CF-30 excluding retired CF-12; exact report/status, lifecycle/delivery, faculty/volunteer/QR-mode/delegation, runtime controls and media lifecycle contracts.
3. `PLATFORM_PARITY_AND_ACCEPTANCE.md` - shared action/workflow and mandatory release tests.
4. `PROJECT_ARCHITECTURE_REVIEW.md` - historical source-based defects and migration evidence; later scope decisions supersede its rollout recommendations.
5. Applicable narrower repository instructions, approved ADRs and runbooks.

This task created specifications only. Do not interpret the existence of these instructions as authorization to deploy, purchase services, import real university data, publish documents, enable external accounts or begin every implementation phase. Follow the current user-authorized task and make ordinary reversible implementation progress within it. Ask only for material missing policy, credentials or external/destructive approval; do not repeatedly ask permission for already authorized work.

Full product scope includes events, library, venues, QR attendance, hostel passes, community/chat/feed, sports, teams/judging, volunteers, alumni, academic marksheets/timetables, certificates, support, notifications, monitoring and AI assistant. Phases sequence all active modules; no agent silently reduces scope to a demo. Version 1.4 retires CF-12 without reusing its ID and adds CF-29 runtime feature control and CF-30 uploaded-media retention. This update changes specifications only; removing old source modules/data requires separately scoped implementation and a reviewed migration.

Read [RUNTIME_FEATURES_AND_MEDIA_SPEC.md](RUNTIME_FEATURES_AND_MEDIA_SPEC.md) for every runtime control or file lifecycle change. Central admin can toggle every catalogued module, feature and business function without code changes or client updates when implementation exists in compatible clients. Resolve dependencies and safe shutdown server-side; both clients hide disabled entry points and invalidate cached state while direct APIs, live channels and jobs enforce the same versioned controls. Feature enablement never grants a role or bypasses resource authorization. Authentication, audit, administration/recovery and deletion enforcement are protected platform safeguards, not ordinary user-disablable business functions. Missing/incompatible client implementation must show a truthful compatibility result, not a fake newly enabled screen.

All uploaded original media, academic PDFs and derivatives expire at uploadAcceptedAt + 30 days UTC; access, edits, replacement metadata, feature toggles and restarts never reset the deadline. Enforce expiry at authorization before asynchronous physical purge, including versions, thumbnails, previews, quarantine, caches and derived AI/vector copies. No permanent student-document file copies are retained in this platform: official long-term archives belong to an approved external university system. Only explicit authorized protected legal holds may delay physical deletion, with scope/owner/review/expiry; never silently retain indefinitely. Purge proofs, retry monitoring and backup restoration suppression must prevent resurrection, and physical backup purge has a disclosed bounded dependency rather than an instant deletion claim. Disabling a module never stops retention enforcement.

Version 1.1 core additions include Audience Choice Polls; Light/Dark/Auto, circular branding and initial approved 22-department configuration; midnight library CSV; reviewed duration eligibility and actual color-coded XLSX; approved volunteer hours/certificates; secure CUJ team codes; four-parameter judging; numbered CUJ-TKT helpdesk; temporary event chat cleanup. Version 1.3 refines event evidence/QR rules below. Treat every CF ID as mandatory shared-client requirement and update action/API/data/test traceability when implementing.

Version 1.2 adds mandatory event/match postpone/reschedule/cancel and next-availability suggestions; comprehensive affected-audience notifications; own calendar/clash assistance; readiness/resource/waitlist intelligence; governed feedback/insights; verified disruption/continuity centre. Read [SCHEDULING_AND_NOTIFICATION_SPEC.md](SCHEDULING_AND_NOTIFICATION_SPEC.md) before changing these domains. Every domain command must classify notification trigger, affected audience, priority, safe template, dedupe and retention.

Version 1.3 CF-24..28 adds faculty organizer operations after assigned admin approval, authenticated volunteer recruitment with verified mandatory roll/enrollment, SINGLE_QR / DUAL_QR / NO_QR, live second attendance-checkpoint activation, scanner OFF/ALL accepted roster/SELECTED and independent selected manual delegation. [EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md](EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md) supersedes older event QR-2=EXIT/default80% assumptions; library/hostel rules stay separate. Attendance exports must sort globally by verified identifier, preserve leading zeros and exclude QR secrets. Bind identifiers privately to credential records; never promise noncopyable screens. ALL includes newly accepted eligible volunteers while enabled, with explicit notice; it never grants manual/export access.

## 2. Required professional standard

Work with principal/senior-level rigor: explain invariants, compare options, verify primary sources, model failure cases, design migration/recovery and provide evidence. The owner's request for 30+ years of mastery is translated into this responsibility/quality bar; agents must not invent human employment history, certifications or qualifications.

Never promise zero vulnerabilities, unhackability, bug-free code or 100% readiness without verification. Unknown defects remain possible. Report observed facts, design targets, unverified assumptions and residual risks separately. No fabricated build/test/security/assessment result. A screen or successful toast is not proof of a persisted correct workflow.

## 3. Roles, ownership and independent review

Agents may cover multiple roles in a small task, but security-sensitive implementation needs a reviewer who did not author the change. Roles are accountable functions, not required headcount or fictitious people.

| Role | Owns / must produce | Review responsibility |
|---|---|---|
| Principal architect / technical lead | ADRs, module boundaries, trust model, integration and migration strategy | Cross-domain consistency and final technical evidence |
| Senior project/program manager | Scope/requirements IDs, dependencies, milestones, risk register and stakeholder decisions | Completion traceability; never close untested work |
| Senior product / service designer | Office workflows, user journeys, accessible UI states and terminology | University policy fit and role-specific usability |
| Senior frontend / universal-client engineer | Shared Expo/web screens, tokens, navigation, forms, state and adapters | No platform drift, fake buttons or client-only authority |
| Senior Android / device engineer | Native build, camera/permissions, secure credential/offline storage and device tests | Browser/device limitations and real hardware behavior |
| Senior backend / domain engineer | Deterministic APIs, scoped policy, state machines and transactional commands | Errors, retries, ownership and side-effect correctness |
| Senior database engineer / DBA | Mongo schema/indexes, transaction guards, migration, backup/restore and capacity | Real concurrency proof, referential integrity and recovery |
| Senior security / application engineer | Threat model, ASVS/MASVS/API tests, auth boundaries and remediation | Independent negative tests and security release blockers |
| Senior privacy / governance lead | Data inventory, purpose/retention, processor register and privacy design | Minimization, exports and legal applicability with university counsel |
| Senior AI/ML engineer | Approved ingestion, retrieval relevance, moderation/anomaly evaluation | Bias/false positives; no autonomous adverse decisions |
| Senior LLM / agent engineer | Prompt/tool policies, scoped retrieval, evaluation and provider handling | Injection/leakage/tool abuse, model updates and safe fallback |
| Senior platform / SRE / DevSecOps engineer | IaC, CI/CD, telemetry, on-call, SLOs, DR and signed releases | Restore/alert/failover evidence and supply-chain controls |
| Senior QA / test automation engineer | Shared scenarios, native/web E2E, property/concurrency/load tests | Independent outcome assertions and measured reports |
| Senior hardware / campus systems engineer | Gate cameras, kiosk/network/power constraints, scanners and offline operations | Physical proxy risks, LAN coordination and operator usability |
| Independent security assessor | Authenticated deployed web/API/mobile/cloud assessment and retest | External release evidence; not replaced by an agent self-review |
| University office owner | Hostel/academic/library/facility/DSW rules and UAT | Authorizes institutional policy and operational acceptance |

No role can approve its own academic publication or exceptional access. Product owner/university authorities own production acceptance, policy and external deployment permission. Legal conclusions require designated counsel; AI/privacy engineers identify applicability questions and implement approved requirements.

## 4. Multi-agent coordination

Use sub-agents when the user authorizes teamwork or these instructions apply to meaningful parallel engineering/review. Choose narrowly scoped tasks and explicit file ownership; never create user-visible new chats instead of sub-agents unless requested. Do not spawn agents merely to claim a large team.

Lead provides task, requirement IDs, constraints, allowed edits, dependencies, expected output and verification. Agents acknowledge assumptions and return decisions, changed files, commands/tests, failures and remaining risk. Prefer read-only reviewers for auth, schema, runtime controls, media deletion, academic publishing, AI access and release changes. Shared-file edits require coordination; do not overwrite another agent or user. Integrator reads every result, resolves disagreement and verifies assembled system. An agent summary is evidence to inspect, not blind proof.

## 5. Architecture rules

- Shared TypeScript universal UI source, common routes/tokens/action registry and workflows for web/Android. Platform adapters are explicit. All required features, buttons, staff screens and walkthrough steps ship on both clients. Coordinate old installed clients with web/API rollout through version compatibility, signed updates and tested rollback; parity is not just today's source tree.
- Java/Spring modular backend, typed versioned API, explicit response DTOs and service-level resource authorization. Pin supported compatible versions; use one JDK/compiler/container baseline.
- MongoDB app primary uses replica-set transactions, schema validators, unique/partial indexes, aggregate versions and common contention writes for allocation/interval invariants. Configure transaction manager and verify driver semantics.
- Redis/cache, UI state, notifications and LLM outputs never become authority for seats, reservations, grades, approvals or feature-control decisions.
- No unrestricted status/body/entity binding; validated commands and permitted transitions only. Request IDs, role selectors, client timestamps and priority numbers do not confer authority.
- Durable outbox/audit records commit with domain changes. No external side effects inside retried transactions. At-least-once delivery requires dedupe/reconciliation, not an invented exactly-once guarantee.
- Bounded documents, no growing history arrays. Private files in encrypted object storage with metadata/ownership/immutable upload expiry; no publicly served marksheets or chat attachments.
- TTL is cleanup only. Security expiry, reservation hold release and legal deletion are explicit server decisions.
- Offline scans provisional under documented policy; no global disconnected-gate anti-replay promise. Use asymmetric public-key verification rather than claiming HMAC can be verified from public keys.
- Preserve existing source as reference until migration/cutover verification. Do not rewrite/delete databases, APKs, uploads or user modifications casually.

## 6. Security and data handling rules

Default deny; principal + capability + resource scope + state checked for every API, subscription, file, export, job and AI tool. Privileged grants come from verified provisioners, not registration. OIDC/PKCE, secure web BFF/CSRF, native protected storage and privileged MFA follow approved design. Revocation affects live channels and pending jobs.

No production secrets, real student/faculty/staff datasets, private PDFs or raw prompts in repo, test fixtures, logs, screenshots, agent context, remote plugins or unapproved model services. Use synthetic/redacted data. Never echo a secret scanner's raw findings. Exclude sensitive fields defensively and use DTO allowlists. Downloads/exports need purpose/scope, safe expiry and audit; no public bucket or debug console in production.

Review upload/parser/SSRF controls, file limits, private quarantine and execution sandbox. Encryption protects particular compromise scenarios, not plaintext available to a compromised authorized app; document key access and residual risk. No homemade crypto. University-owned keys and recovery plan; separate administration and audit custody.

AI receives minimum authorized context; retrieval prefilters ACL, tools reauthorize server-side. No arbitrary shell/DB/HTTP/MCP tools in production assistant. No autonomous grades, disciplinary or access decisions. Providers require approved data-handling terms; otherwise public/redacted content only. Every model update passes injection/leakage/language regression tests. Media expiry propagates to approved ingestion derivatives and retrieval indexes.

Preserve privacy/accessibility and explicit authorized legal holds. Uploaded academic file copies obey the 30-day deadline; mandatory long-term official archives require an approved external institutional archive. Non-file academic records have separately approved retention. Do not collect continuous location, Aadhaar or biometric templates for convenience. Legal effective dates and institution applicability must be confirmed before claims of compliance. Do not treat consent as the only lawful basis for every university record.

## 7. Work procedure and definition of done

1. Inspect current files/status and preserve unrelated changes. Read relevant domain policies/contracts; use rg first. Establish reproducible baseline and synthetic fixtures.
2. State requirement IDs, actor/resource scope, data classification, invariants, unhappy paths and migration/recovery needs. Record substantial architecture changes in an ADR.
3. Implement vertical slices: real API/persistence/policy + shared screen/action + audit/outbox + necessary verification. Mock data is isolated and labelled; never accepted as production behavior.
4. Exercise positive/negative permissions, duplicate/retry/conflict/offline cases and both client flows. For high-risk behavior use independent reviewer and real replica-set DB/device tests.
5. Run targeted checks, fix causes, then required integration/security/parity checks. Do not add superficial tests mirroring implementation or mask failure with skipped checks.
6. Verify assembled change, generated contracts, data migration, telemetry and operator failure behavior. Re-review after material changes.
7. Report concrete changes, validation performed, limitations and outstanding blockers with clickable files. Update requirement/test matrix and docs; do not claim broader verification than performed.

Domain rules required: concurrent last-seat/venue reservations, partial unique active library visits, duplicate-stable scans, approved hostel transitions, scoped chat membership, deterministic score replay, judge conflicts/rubric versions, independent marksheet mapping/publication, private alumni visibility, runtime feature control and nonresettable uploaded-media expiry. See normative acceptance scenarios; all applicable checks must pass.

Core workflow rules: library timer anchored to server entry (not accumulating callback ticks); Study/Book Issue purpose match plus ENTRY/EXIT context; midnight previous-local-day report distinct from library closing; durable job revisions/catch-up and protected legacy export. CSV cannot store colored fills: provide status/color columns and actual styled XLSX. Freeze event QR mode/evidence policy; entry-only does not certify attendance, and checkpoint-only cannot establish 80% duration. Duration mode requires precise adequate interval evidence. Use canonical LATE_ENTRY and approved per-hostel curfew/extension configuration. Enforce Night-Silence exactly [22:00,07:00) on server; event cleanup never deletes permanent records or held evidence. Roll auto-detection cannot bypass independent academic publication. Four judging criteria/ranges are versioned; volunteer hours require supervisor approval. Poll uniqueness is per authenticated user/poll, never per shared IP. Helpdesk 24/7 means intake availability unless staffed response coverage is verified. No zero-vulnerability guarantee follows from these requirements.

Lifecycle rules: suggestions never reserve or override guards; confirmation revalidates calendar/resources and expected lifecycle revision. Registration/scan/cancel/reschedule share lifecycle contention; no admission after effective cancellation. Keep planned drafts distinct from effective postponement; preserve safe exits, permitted attendance/score evidence and reviewed certificate obligations. Large changes expose a resumable frozen workflow, never partial success. Commit notification intent/audit with domain state, snapshot scoped audience, dedupe fan-out, suppress stale reminders and reconcile failures. Device receipt is not guaranteed; distinguish dispatch/acceptance/delivery/read/acknowledgement. Use protected inbox and load-tested targets, never report provider acceptance as delivery. Human approval owns emergencies and adverse decisions; private calendar/clash details cannot leak through suggestions or AI.

Event operator rules: master/window/grant/accepted membership/lifecycle are all authoritative; OFF or revocation must contend against acceptance, not rely on old UI/token. QR/manual/export capabilities stay independent; no volunteer self-awards. High-assurance scan requires pending intent plus owner-bound confirmation and supervised identity, with explicitly reviewed manual dead-phone fallback. Second QR notification exposes only safe dashboard link. NO_QR does not mint credentials or imply attendance. All mode/policy/grant changes have version/idempotency/audit/outbox and shared-client evidence. QR/manual duplicates converge; report finalization preserves unresolved quality and creates corrected revisions.

## 8. Tools, repositories and supply chain

Prefer official primary documentation/repositories. Inspect existing local code before applying external patterns. Evaluate maintenance/advisories, license, provenance and dependencies; pin releases/actions/images by reviewed identifiers. Produce SBOM, secret/SCA/SAST/container/IaC scans and authorized staging DAST. No curl-to-shell installers, unsigned latest downloads, whole unreviewed campus repos or broad production access for agents.

Tools/plugins/MCP APIs are least-privilege task capabilities, not automatic production trust. No unsolicited account installation/purchase/cloud publish/messages or production data transfer. Prepare reviewable configuration/work before requesting final external approval. Secret-manager references replace actual credentials in examples. Security tools can themselves be compromised; verify provenance and isolate CI credentials.

## 9. Monitoring, incident and release rules

Monitoring is part of platform code/infrastructure/runbooks, not a chat automation unless specifically requested. Emit redacted metrics/logs/traces and independently retained audit events. Alert on suspected data extraction, grant changes, QR replay, provider/malware anomalies, backup/audit failures and invariant drift. Define human owners/escalation; do not claim 24/7 coverage without staffing. Verify an alert reaches responder.

Incident handling: contain, preserve protected evidence, assess scope, involve security/privacy/legal authorities, meet applicable notifications, recover verified system and record corrective actions. Do not delete forensic evidence, disclose real records or let AI decide statutory reporting alone.

Before production: complete web/Android action parity, office UAT, documented load/accessibility/concurrency tests, privacy review, independent assessment/retest, no unresolved Critical/High findings, signed artifact/SBOM, tested restore/DR and incident drill, trained owners and university sign-off. Known lower-risk exceptions need owner/expiry/compensating controls. Do not bypass a gate to meet an arbitrary date. Documentation-ready, buildable, feature-complete and production-approved are distinct statuses.

## 10. Change ownership and stop conditions

Stop only the dependent risky action when policy/authority is missing, unknown data ownership appears, a migration threatens existing records, assessment finds a blocker, or external deployment/account approval is required. Continue safe independent work; report the concrete missing decision. Do not present imagined risks as blanket approval requirements.

Never mark success when checks fail, silence warnings without cause, weaken tests/security for parity, invent university policy, or destroy unrelated work. The lead owns integration; named institutional stakeholders own final acceptance. Keep architecture, action registry, policies, tests and runbooks consistent with the deployed implementation.
