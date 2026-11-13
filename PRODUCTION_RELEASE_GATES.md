# Production release evidence

The current production profile deliberately refuses startup because the approved MongoDB persistence adapter and migration do not exist. Keep that safeguard. Successful local H2 tests, container compilation or an HTTP readiness probe cannot close this implementation gap.

`scripts/Test-ProductionReleaseGate.ps1` adds an offline evidence completeness/integrity gate. Exit 2 means blocked; exit 0 means evidence is present and still requires human review and release authorization. The script never deploys, enables production, signs a release or authenticates reviewer identity. A self-authored manifest is not independent assessment or university approval.

Run from the repository root, after producing the final isolated build and auditing those exact artifact bytes:

```powershell
rtk proxy powershell -NoProfile -File scripts/Test-ProductionReleaseGate.ps1
```

Defaults use the follow-up artifact/build evidence, `java-security-audit-followup-2026-10-19.json`, `npm-security-audit-followup-2026-10-19.json` and `client-launch-followup-evidence.json`. Supply `-ArtifactPath`, `-BuildEvidencePath`, `-JavaAuditPath`, `-NpmAuditPath`, `-ClientEvidencePath`, `-EvidenceManifestPath` and `-OutputPath` for a later candidate. The report contains gate identifiers only, no evidence contents or credentials. Keep evidence and reviewer records in approved private storage. Missing or malformed evidence blocks rather than silently skipping.

The isolated build must have no filter, failures, errors or skipped tests; its artifact hash and complete source manifest must match the current source, including added/deleted files. Java audit must be fresh, artifact-matched, complete and without unresolved advisory matches. npm audit must have integer High/Critical counts of zero; absent/null/string counts fail closed. Conditional Spring matches cannot be dismissed by relabelling a passing test. Because raw npm format lacks lockfile identity and timestamp, the gate also requires client evidence binding `npmAuditSha256` to the raw audit, `lockfileSha256` to the current lockfile, and a recent `npmAuditedAt`. Local timestamps and hashes establish consistency, not execution authenticity; reviewed client provenance remains required.

## Prioritized implementation and evidence

| Priority | Existing source/evidence | Still required |
|---|---|---|
| P0 persistence | JPA repositories/entities, local transaction locks, explicit production startup denial; `ProductionPersistenceGateTest` checks no accidental H2 schema | Mongo domain adapters, replica-set transaction manager, validators, unique/partial indexes, stable identifier/reference migration, version/contention guards, bounded retries and commit ambiguity handling |
| P0 migration | Old data/source preserved | Synthetic dual-read reconciliation, row/document/reference and authorization checks, transaction/outbox consistency, stopped-write cutover plan, verified rollback and restore suppression of deleted media; approved migration before importing institutional records |
| P0 dependencies | Current Java candidate `college-events/target/events-boot411-clean-20261019.jar` is bound to 539/539 full tests and a 158-coordinate live audit with zero matches/unknown JARs | Resolve npm High findings; rerun both Java/npm inventories on the final combined release candidate; verify artifact/lock identity, provenance, SBOM, signing and live advisories |
| P0 qualification | Local synthetic browser/API/unit evidence | Final exact-artifact full regression, all 29 active requirements and both clients, real native storage/camera/SSO/scans/downloads, installed-client compatibility and rollback |
| P0 deployed security | Source session/CSRF/JWT/header/authorization tests | Approved staging target; real TLS edge, secure cookies, header trust, CSRF/CSP, IdP/MFA/revocation, distributed quotas, authenticated role/resource abuse, independent assessment/retest and no unresolved Critical/High findings |
| P0 operations | Local media lifecycle/outbox/job implementations | Actual private object versions/derivatives/backups purge proof, legal hold controls, restore resurrection prevention, provider delivery/retry evidence, incident alert to named responder and DR drill |
| P0 institutional acceptance | Normative specifications and configurable workflows | Policy decisions, privacy review, office UAT, accessibility, trained owners/on-call coverage and university sign-off |
| P1 performance | Bounded loopback synthetic workloads | Representative authenticated writes/reads, realistic data sizes and query plans/indexes, replica-set contention/failover, sustained soak, latency/error budgets, saturation/memory/leak/CPU observations and cache isolation/invalidation |
| P1 AI | Local prompt guards and bounded transport | Approved provider terms and keys, ACL-prefilter retrieval/tool reauthorization, indirect/multilingual injection/leakage tests and model-version regression; no autonomous adverse decisions |
| P1 supply chain | Pinned Docker bases and source build commands | Actual image/container/IaC/SAST/full-history secret scans, locked dependency/SBOM provenance, signing and independently verified release artifacts |

All P0 and applicable P1 gates are mandatory before production. This priority table sequences work; it does not waive scope. The missing Mongo implementation is substantial cross-domain migration, not a connection-string setting. Do not remove `ProductionPersistenceGateConfiguration` merely to make an image boot.

## External evidence manifest contract

Schema version 1 requires `artifactSha256`, `sourceManifestSha256`, `deploymentTarget` and an `evidence` array. Each gate requires exactly one record with `id`, `status: "satisfied"`, `reviewer`, `reviewerRole`, `scope`, `version`, `authorityReference`, `reviewedAt` (ISO UTC), `validUntil`, local private `path` and SHA-256 `sha256`. Reviews and full build/audits must be no older than seven days for this gate. Re-run after any candidate/source/environment change; do not refresh timestamps without re-verification. Evidence must name the deployed configuration/image/native version where relevant, actual tools/results, residual risks, responsible owner and test data classification. University release approval must explicitly refer to the reviewed evidence set.

Required IDs:

- `mongo-migration-concurrency`: transaction/index/schema/cutover/restore evidence and independent database/security review.
- `client-web-android-parity`: exact client lockfile/export/native hashes, complete action matrix, browser/device/accessibility results and npm audit hash/time/lock identity.
- `tls-edge-identity-mfa`: deployed configuration and negative tests, verified privileged provisioning/revocation and secret-manager references.
- `load-soak-distributed-abuse`: workload/data/concurrency/duration, SLOs, server/DB resources, distributed abuse cases and failure recovery.
- `private-media-backup-restore`: expiry authorization, physical versions/derivatives, bounded backup purge, legal holds and restore suppression.
- `security-scans-sbom-signing`: full-history secrets, SCA/SAST/container/IaC results, SBOM/signatures and verifier provenance.
- `independent-deployed-assessment`: independently authored authorized assessment/retest report for the exact deployment, including closed Critical/High findings.
- `privacy-policy-office-uat`: approved institutional policies, privacy/legal applicability review, office acceptance and all active requirement IDs.
- `incident-alert-dr-owners`: actual alert receipt, named coverage, incident/restore/DR drill and measured recovery objectives.
- `ai-provider-adversarial-evaluation`: approved provider/version and privacy boundaries, indirect/multilingual prompt/tool/retrieval abuse tests; disabled AI must be explicit and must not erase required scope.
- `university-release-approval`: authorized human sign-off against the complete release candidate and evidence set.

Hashes are not signatures, and declared names/statuses cannot prove independent review. An authorized human must verify identity/authority, provenance, completeness, interpretation and actual operational acceptance before any deployment. Exit 0 is deliberately labelled evidence-present rather than production-approved.
