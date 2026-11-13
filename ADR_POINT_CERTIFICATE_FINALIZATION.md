# ADR: Evidence-backed point-attendance certificates

Status: implemented bounded SQL reference slice; production migration, native download handling, large resumable jobs, institutional approval and release acceptance remain open.

Requirements: CF-07, CF-13 boundaries, CF-26..28, CF-29. Normative point evidence and certificate rules are in `CORE_FEATURES_SPEC.md` and `EVENT_OPERATIONS_AND_ATTENDANCE_SPEC.md`.

## Decision

`RegistrationStatus.ATTENDED` and pre-existing certificate rows cannot establish eligibility. Event operations explicitly publish `POINT_ATTENDANCE` before registration; missing policy is `UNREVIEWED`. Attendance mode/subpolicy and certificate policy freeze together after registration exists. Policy revisions advance with relevant changes. Unsupported duration/manual certificate paths return a compatibility failure.

ENTRY retains the legacy ATTENDED marker as a post-admission cancellation barrier until a reviewed unified-status migration separates admission from attendance. Ledger/report states and certificate eligibility are computed from policy/evidence, never from that legacy marker alone.

An authorized event manager finalizes a completed, previously approved event with certificate generation enabled and recorded completion provenance. Scanner/manual windows must be closed. Finalization locks the operations aggregate and event lifecycle, checks expected operations revision, validates verified identifiers and reads accepted owner-confirmed QR evidence. SINGLE_QR requires ENTRY_AND_ATTENDANCE plus accepted ENTRY. DUAL_QR requires accepted ENTRY plus a later checkpoint. Evidence must belong to the registration/event, match a confirmed owner intent and occur between the event start and recorded completion. Manual/mixed evidence, incomplete evidence and existing legacy certificate collisions remain unresolved; they are never silently converted into eligibility.

One transaction persists a bounded finalization job, eligible certificates with immutable policy/evidence/identity/title snapshots, safe private inbox intent and audit. UUID request keys bind actor/event/revision/reason, and authorized identical retries return the committed receipt. The timestamp uses database-compatible microsecond precision so fresh and replayed receipts remain identical. Unique registration issuance and event finalization constraints are defense in depth. Audit failure rolls back the entire job.

The current atomic path supports up to 500 registrations. Larger events explicitly require a resumable job implementation. Unresolved quality survives in the committed job and response. Corrections require a separate reviewed revision workflow; the current endpoint cannot rewrite finalized evidence.

## Read and delivery boundary

Own lists are scoped and paginated. Downloads reauthorize the owner and stored proof and render a fixed point-attendance PDF on demand. Existing uploaded/template paths and arbitrary template selectors do not influence rendering. No generated file is persisted by this slice. The certificate expressly distinguishes point attendance from duration, grades or volunteer hours.

Opaque verification at `/certificates/verify/{opaqueNumber}` returns validity, certificate type and policy version only. It exposes no name, verified identifier, contact, event title or registration ID. Legacy predictable numbers and unproven rows return invalid. Certificate/event runtime controls are enforced server-side.

## Migration and remaining gates

Before adding the unique registration constraint to an existing database, inventory duplicate legacy rows and preserve their IDs/content/evidence for an office-reviewed migration. Automatic `ddl-auto=update` is not a reviewed production migration. This change does not delete duplicates, overwrite legacy certificates, assign proof to old rows or retroactively publish attendance policy. Existing operations receive UNREVIEWED/version 0. Legacy completed events lacking completion provenance stay blocked.

Real replica-set Mongo migration/transaction contention, bounded resumable job processing, reviewed corrections, approved duration/manual/volunteer policies, provider delivery receipts, native secure PDF handling, university UAT, artifact signing and independent release assessment remain separate work.

## Verification

`CertificatePointPersistenceTest` exercises real H2 transactions, QR evidence persistence, stable duplicate requests, synchronized duplicate finalization, SINGLE/DUAL eligibility, unresolved manual/partial/outside-window evidence, legacy denial, owner scope, public verification minimization, audit rollback and actual PDF content.

`EventOperationsCertificatePolicyTest` checks explicit compatible preregistration policy and frozen policy after registration. `EventQrAttendanceServiceTest` checks scoped credential/owner confirmation behavior and operations-first lock ordering. H2 and mocked provider controls do not constitute Mongo/device/provider production evidence.
