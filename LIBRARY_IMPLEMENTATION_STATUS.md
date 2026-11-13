# Smart library turnstile implementation status

Implemented in this slice:

- Purpose allowlist for `STUDY` and `BOOK_ISSUE_RETURN`.
- Pessimistic lock around a user's active visit during entry and exit, plus a visit revision for optimistic persistence safety.
- Server timestamp based dwell calculation and explicit mismatch evidence.
- CSV export now prefixes spreadsheet-formula-leading text fields before RFC-style CSV quoting.
- A reporting date boundary no longer closes an active visit or creates an estimated duration. A prior-day active visit blocks a new entry until the owner records an explicitly unverified safe exit.
- The legacy CSV/XLSX download routes now fail closed for all roles until durable snapshots and assigned-library report grants are implemented. The student screen no longer offers fake entry/exit buttons without a camera-backed facility challenge.
- Runtime `MODULE_LIBRARY_TURNSTILE` guard on all service commands and reports.

Verification after the export-boundary slice: Maven 384/384, shared client 126/126, TypeScript typecheck, web export, and desktop+narrow Playwright 76/76 passed; the latest HTTP workflow rerun passed 5/5 after expanding export denial coverage to all 10 authenticated roles. It also checks unauthenticated denial and no-store history; the browser scenario confirms there are no fake scan/exit buttons. These checks remain synthetic/H2 and do not prove real QR devices, assigned-library reporting, Mongo, policy or institutional acceptance.

Still blocked: configured library opening/closing hours and holidays, reviewed forgotten-exit policy, durable midnight report snapshot/revision/catch-up/lease workflow, signed facility QR challenge issuance/confirmation, physical scan evidence, private librarian/admin report authorization and verified styled XLSX delivery. The legacy CUJammu library routes remain fail-closed; this slice must not be presented as a deployable QR turnstile.

## Additional service-boundary closure — 19 October 2026

The compatibility HTTP export routes already returned an explicit unavailable response, but three internal service methods (`getTodayLogs`, CSV export and XLSX export) still queried the complete private visitor table after checking only the broad ADMIN/LIBRARIAN role. They now fail before repository access until an assigned-library grant and durable report service exist. `LibraryReportRenderer` keeps sorted CSV/XLSX projection, formula-neutralization and credential-free columns as a pure projection over already-selected rows; it performs no database reads or authorization. `LibraryExportOrderingTest` now tests both the fail-closed service boundary/no-query invariant and renderer ordering/formula handling. Targeted Maven verification passed 10/10 across `LibraryExportOrderingTest` and `LibraryTurnstilePolicyTest` using H2/JPA fixtures and synthetic rows. Independent review found no remaining path in the audited library report methods, while confirming that catalog/grant persistence, snapshots and actual report delivery remain absent. This does not implement library report authorization or turnstile admission.

## Verified identifier boundary — 19 October 2026

An independent review found the legacy visit snapshot could store an account email in the `Roll Number/ID` field when `enrollmentNo` was absent. New visits now require the office-verified `verifiedEnrollmentNo` and reject before persistence when it is missing; unverified profile input and account email are never used as report identifiers. The report service remains fail-closed. Historical rows may already contain email values and must be reviewed/redacted under an approved retention/correction policy before reports are enabled. Targeted `LibraryExportOrderingTest` and `LibraryTurnstilePolicyTest` passed 11/11 after the change; H2/JPA synthetic tests do not validate production identity mapping.
