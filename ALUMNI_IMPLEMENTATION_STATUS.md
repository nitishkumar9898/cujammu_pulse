# Alumni privacy implementation evidence

Date: 2026-10-02. Requirements: ALUMNI action family, architecture section 6.12, CF-29 server enforcement and scheduling/notification alumni audience rules. This is a legacy Spring/JPA vertical slice, not the completed MongoDB/universal-client production migration.

## Implemented

- `/alumni` and `/api/v1/cujammu/alumni/**` use a dedicated controller and DTO service. Every service operation resolves the persisted enabled account through `DomainAccess` and checks `MODULE_ALUMNI`.
- Explicit response projections never include nested User entities, account email/phone/enrollment, password/reset information or reviewer identity. Job contact links are explicitly entered for the opportunity, not copied from account contact data.
- Directory and mentor queries enforce verified provenance, independent reviewer, enabled owner and explicit opt-in before rows leave the database. Pages contain at most 24 records. Company/designation, location, bio and LinkedIn visibility are separately controlled; new and legacy-null flags are private.
- Self profile edits do not grant alumni membership or verification. Degree/year changes after verification require review. Unsafe automatic date-based graduation promotion was removed; historical records remain stored.
- Administrator verification rejects self-approval, records reviewer/time, uses expected revision and updates the legacy alumni indicator only through that authorized decision. An admin review desk exposes pending and existing profile review. ADMIN is a transitional provisioned reviewer role; institutional scoped verification capabilities remain required for production.
- Legacy `verifiedAlumni=true` without reviewer/time is treated as unverified, including for job posting. No blanket data rewrite or deletion occurs. Such records enter the review queue and remain absent from the directory until independently verified and opted in.
- Verified members can publish jobs; request keys converge retries under an owner row lock. Owners/admin can close/reopen listings with version checks. Queries suppress jobs after verification revocation or owner disable, and department filters include ALL listings. Private directory opt-out does not withdraw separately published opportunities.
- URLs accept HTTPS, LinkedIn host allowlisting for profile links, or plain job email converted to mailto. Legacy unsafe links are suppressed. Text is rendered with textContent, and external links use noopener/noreferrer.
- Mutations validate commands inside services, use READ_COMMITTED transactions, owner contention and optimistic profile/job versions. Actor authorization is based on persisted accounts. All state mutations append audit and a safe owner-only protected inbox notification in the same transaction. There are no precommit provider/SSE effects.
- The legacy web module provides real API-backed profile/preferences, directory/mentor filters, job creation/listing closure, verification review, CSRF, server errors, session expiry and pagination. It includes responsive layout, keyboard controls and reduced-motion support.

## Tests performed

`AlumniPrivacyTest`: 16 tests, zero failures/errors. Anonymous/disabled accounts, disabled module service bypass, self-awarded membership, legacy verification, unrelated private reads, forged session role, self-review, independent verification/audit/inbox, stale revisions, DTO minimization, unsafe links, verified fact editing, duplicate job creation, job ownership and invalid commands.

`AlumniRepositoryPrivacyTest`: 3 tests, zero failures/errors against isolated synthetic H2 database. Actual JPQL visibility exclusions, mentor/department filtering, current verification required for job reads, ALL department listings, legacy preservation/review queue.

`AlumniTemplateTest`: 2 tests, zero failures/errors. Actual Thymeleaf render, CSRF token, private defaults and member/admin controls. `node --check` passed for `static/js/alumni.js`.

Command: `rtk proxy <local-maven>/mvn.cmd -q "-Dtest=Alumni*" test`. Java runtime 25.0.2, compiler release 21. Tests do not read university datasets. Build warnings remain about future Mockito dynamic-agent/JDK behavior, commons-logging and redundant H2 dialect; these are not silently suppressed.

Independent root review found a REPEATABLE_READ snapshot hazard and malformed self-verification queue omission. Write transactions now use READ_COMMITTED and the queue includes self-review anomalies. These revisions were included in the passing test run.

## Remaining release gates

- Institutional graduation evidence, reviewer capability provisioning, account linking/recovery and personal-contact verification policies; full mentorship request/accept/decline workflow is not implemented by a LinkedIn link.
- Shared TypeScript web/native screen migration and Android feature/action parity; this legacy API now uses paged DTO contracts, so older clients need compatibility handling before rollout.
- Reviewed additive migration for nullable privacy/provenance fields and version columns. Current prototype auto-DDL is not a production migration. Review old records before trusting them; do not silently backfill verification or opt-in.
- MongoDB replica-set migration, real database concurrent revocation/posting tests and DB isolation/rollback acceptance. H2 query tests do not prove MySQL or MongoDB concurrency behavior.
- Notification fan-out/outbox, delivery retry/dedupe, live channel revocation and provider delivery tracking. This slice persists an owner inbox notice; it does not claim instant push/email delivery. Job posting deliberately does not broadcast to every student without approved audience/subscription policy.
- Full runtime control revision/shutdown contention, profile-update retry dedupe, anti-scraping/rate limits, listing moderation/report handling, approved structured-record retention and audit separation.
- Real browser/device workflows, accessibility assessment, university UAT and independent security assessment/retest before production approval.

No claim of zero vulnerabilities, feature-complete alumni network or launch approval follows from these tests.
