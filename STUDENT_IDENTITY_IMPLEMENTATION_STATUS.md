# CF-03/CF-19 office student verification

Updated 26 October 2026. This implements first-time student identifier/department binding through the shared web/Android source. It does not implement institutional SSO, automated registry validation, correction/reassignment, bulk imports or deployed/native acceptance.

## Workflow and authorization

The protected Administration > Student verification screen is available only to an administrator. The backend independently loads the actor's current enabled account/role; hiding navigation is not authorization. Identity administration is a protected safeguard, not an ordinary user-disablable business feature.

`POST /api/v1/admin/students/lookup` accepts a bounded exact email in JSON, normalizes it consistently with registration, and returns only enabled student account ID/name/email, verified identifier/department, version and binding state. It does not search arbitrary substrings or return password, reset token, phone, self-reported enrollment, source evidence or verifier identity. Email is not placed in a query URL. Both lookup and save return `no-store`; cookie requests require CSRF.

The administrator selects an active canonical department and enters the official identifier, a bounded approved source reference and review reason. No profile value is copied into authority. The UI requires explicit review confirmation and sends the displayed account version. Leading zeros remain text. This is an authorized human attestation against the official source, not an automated institutional-source verification claim.

`PUT /api/v1/admin/students/{studentId}/verified-identifier` locks the target user, requires enabled STUDENT, validates version/fields and binds only a previously unbound account. Database uniqueness remains authoritative. Any existing partial verification evidence requires reviewed reconciliation. A complete identical binding returns a no-write receipt; different identifiers/departments cannot overwrite historical authority. Full completion requires identifier, department, timestamp, verifier, source reference and reason.

The binding, redacted audit and enabled private inbox notification/IN_APP intent commit together. Initial binding is the trigger; affected audience is that student only, ordinary informational priority, safe fixed template and dashboard link without identifier/source details. Immutable locked binding deduplicates retries. Inbox behavior/retention follows the existing notification lifecycle; no external mail/push delivery is claimed. Disabled ordinary notifications may suppress the inbox item; the binding and audit still persist. Audit failure rolls back the binding. The schema adds no fields in this slice; existing unique/version columns remain required. Mongo adapter and migration evidence are pending.

## Recovery and limitations

The client clears old private account results on lookup/account change or unmount. A mismatched save receipt is rejected. An uncertain save clears the editable candidate and requires a fresh authoritative lookup; it never blindly replays or reports optimistic success. Old verified data cannot be silently reassigned through this endpoint because academic access and attendance records depend on it. Correction requires identity reconciliation, historical-record review and a separately verified workflow.

## Evidence

- Full isolated backend verify/package: **601 tests / 99 suites**, zero failures/errors/skips or source drift; 480 source/build files. Evidence: `college-events/target/student-binding-build-1026.json`. Snapshot: `C:\tools\pulse-security-build-7b4c62f602644709b8bec96a4bd71fe7`. JAR `college-events/target/events-student-binding-1026.jar`, SHA-256 `73C531C9957E5171E3431A40C8814073261F53FF41EDE3E24042C6A6320D7A09`.
- Independent real H2/JPA concurrency suite: 4/4 passed, zero failures/errors/skips/drift. Same-student conflicting requests and different-student duplicate identifiers have one winner; identical retries create one binding/audit/inbox/intent; audit failure rolls back. Evidence: `college-events/target/student-identifier-concurrency-review.json`.
- Shared typecheck and **147 tests** passed. New browser scenarios verify explicit source review, exact command/CSRF, leading zeros, confirmed receipt, private state clearing, uncertain-write recovery and student navigation denial. Six desktop/small-web cases passed after the searchable department-selection update; campus-shell cases also passed. Persistent visible field labels were added during visual review.
- Independent source review covered service/DTO/controller and client/API/decoder. Partial-provenance classification and the multiline-input mismatch were corrected. No additional authorization bypass was identified by that review.

These are synthetic local tests, not Mongo contention, physical-device or institutional release certification. Full launch gates remain open.

Exact-artifact Java advisory inventory: **158 coordinates, zero advisory matches/unidentified JARs**, `college-events/target/java-student-binding-audit-1026.json`. Release gate `college-events/target/production-release-gate-student-binding-1026.json` remains **BLOCKED, 14 gates**. Static registry inventory now contains 24 modules, 6 groups, 31 routes and 157 actions; five declared web-only attendance-report actions remain explicit native parity gaps. An implemented label does not certify deployed behavior.

The final web export is `cujammu-pulse/dist-student-binding-final-1026`. Final local review URL: http://127.0.0.1:18086/app/. Generated credentials and process identity are kept only in `college-events/target/professor-demo-student-binding-final-1026`. The synthetic applicant starts unbound and must be verified by the actual office workflow, not by accepted-state fixtures. See `MANUAL_LAUNCH_SETUP.md` for reset/review instructions.

Final actual browser/backend walkthrough: **13/13 scenarios passed** against that export and verified JAR, including office verification persisted across reload, private student notification, calendar advice, registration/withdrawal, scoped claims and independent academic publication. Report: `college-events/target/professor-demo-student-binding-final-1026/live-ui-report.json`. Earlier temporary student-binding server on port 18085 was stopped after this final candidate passed; generated evidence was preserved. The existing calendar review server was not replaced or modified.
