# Certificate implementation status

Verified locally on 2026-10-10; this is a bounded point-attendance slice and does not close the full certificate/attendance requirements or production release gates.

Implemented:

- Explicit preregistration POINT_ATTENDANCE policy, UNREVIEWED legacy default, frozen versioned mode/policy.
- Completed approved event and recorded completion provenance, closed attendance windows, scoped manager authorization and expected operations revision.
- Persisted owner-confirmed ENTRY for SINGLE_QR ENTRY_AND_ATTENDANCE; chronological ENTRY plus checkpoint for DUAL_QR. No certificate from legacy ATTENDED alone, entry-only, NO_QR/manual/duration or unresolved mixed evidence.
- Atomic bounded event finalization, unique registration issuance, stable scoped retries, persisted unresolved quality, evidence/policy/identity/title snapshots, safe private inbox intent and audit.
- Scoped paginated own lists, reauthorized on-demand point PDF download, opaque verification without private identity/event fields, runtime feature guards and removal of lazy GET issuance.

Evidence:

- Targeted CertificatePointPersistenceTest, EventQrAttendanceServiceTest and EventOperationsCertificatePolicyTest: 11 tests passed, including actual PDF generation/text inspection, unauthorized render denial, H2 persisted proof, concurrent identical finalization and rollback on audit outage.
- Fresh integrated Maven `clean verify` under the available OpenJDK 21.0.10 compiler/runtime: 354 tests across 60 suites; zero failures, errors or skips. This includes the isolated professor-demo guard/smoke tests and packages the executable backend JAR.

Remaining:

- Reviewed duplicate-preserving migration before production constraints; no retroactive proof or deletion of legacy rows.
- Resumable jobs for events larger than 500 registrations and reviewed correction/reissuance flows.
- Approved adequate duration/manual/volunteer-hour evidence and independently governed eligibility; institution-approved certificate/signature policy.
- Native secure PDF handling, provider delivery receipts, real Mongo replica-set migration/concurrency, device evidence, office UAT and independent release assessment.

The professor demonstration may use isolated synthetic local accounts and records. A domain purchase or institutional identity provider is not required for that local milestone. Public deployment and real university acceptance remain separate decisions.

See `ADR_POINT_CERTIFICATE_FINALIZATION.md` for invariants, privacy boundaries and migration constraints.
