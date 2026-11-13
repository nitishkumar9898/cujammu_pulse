# CF-20 calendar implementation evidence

Updated 26 October 2026. Status: locally verified event-only slice; full CF-20 and production acceptance remain open.

## Implemented workflow

- Organizer create/edit accepts an optional explicit end time in campus time, strictly later than the start. Rescheduling preserves a supplied duration; it never derives duration from QR windows or venue bookings. Null historical ends remain unknown.
- The own calendar returns current event status, lifecycle revision and optional end separately from registration status. A cancelled event cannot appear approved merely because a historical registration remains REGISTERED.
- Events exposes `calendar.clashes.check` before registration. `GET /api/v1/cujammu/events/calendar/clashes/{eventId}` reauthorizes the principal and requires Events, Calendar and the dedicated action. A caller-supplied user ID cannot select another person's schedule.
- The target must be a future approved event. Candidates are only the caller's confirmed/attended registrations on approved events. Waitlisted, withdrawn, cancelled and other-user commitments are excluded.
- Known intervals use half-open overlap: touching endpoints do not clash. Unknown or malformed historical durations remain possible clashes. Known overlaps sort first so historical unknowns cannot crowd them out. At most 50 candidates are returned with a continuation warning.
- The shared UI uses IST regardless of browser timezone, clears old advice before rechecking, aborts obsolete reads and removes the result when disabled. Advice includes revision and check time. It does not reserve a seat, change eligibility or claim to cover classes, exams, matches, duties or travel.
- An API error regression discovered by the HTTP test was fixed: intentional `ResponseStatusException` retains its status and safe fixed JSON instead of becoming an HTML 500. Exception details are not returned. Independent read-only review found no further actionable issue after fixes.

## Data, notification and recovery boundaries

Private calendar data is account-scoped and `no-store`. Read requests create no registration/domain mutation or notification intent. Existing create/edit/reschedule notification classification remains in its lifecycle workflow.

The additive JPA `eventEndsAt` column is nullable. Existing records stay null unless an authorized organizer supplies an end time; never bulk-infer it. Demo H2 schema generation is not a production migration. Mongo validators, query indexes, explicit migration and rollback evidence remain required. Older binaries must not be assumed to preserve newly entered end times during rescheduling; rollback must freeze schedule writes or use a reviewed compatible build.

## Verified candidate

- Backend snapshot: `C:\tools\pulse-security-build-b9bae23d76104b35969dfe168fb6e550`, 478 source/build files, zero drift. Full Maven verify/package: **591 tests / 97 suites**, zero failures/errors/skips.
- Artifact: `college-events/target/events-calendar-1026.jar`; SHA-256 `E939B6E3C4C601D152125ABE5003B98CC78A11999287F4C21B9B3364B22F83FD`. Evidence: `college-events/target/calendar-build-1026.json`.
- Java advisory scan: 158 coordinates, zero matches, zero unidentified JARs; exact artifact-bound evidence: `college-events/target/java-calendar-audit-1026.json`. This is not a complete security assessment.
- Shared typecheck, 143 tests and separate export `cujammu-pulse/dist-calendar-1026` passed. Calendar and client-wiring E2E: 24/24 desktop/small-web cases, including America/Los_Angeles browser timezone to verify IST handling.
- Actual synthetic browser/backend run: **11/11 workflows**, including clash read without registration mutation, calendar lifecycle, persisted registration/withdrawal, private claims and independent scorecard publication. Report: `college-events/target/professor-demo-calendar-1026/live-ui-report.json`.
- Local candidate: http://127.0.0.1:18084/app/. Synthetic in-memory data resets on restart. No public deployment or native device acceptance is implied.

## Remaining scope

Fresh executable registry inventory (`node --experimental-strip-types scripts/audit-action-inventory.mjs` from `cujammu-pulse`) enumerates 24 modules, 6 navigation groups, 30 routes and 155 actions, including generated entries. All 29 active CF IDs are referenced. Declared action statuses: 103 implemented, 49 web-session-bridge, 1 legacy-map-bridge and 2 unavailable. These are source labels, not test completion counts. The audit exits 1 for five explicit web-only attendance-report actions: CSV/XLSX/scan-log downloads and export-access grant/revoke. Those native parity gaps remain open; no labels were changed to conceal them. Detailed generated report: `cujammu-pulse/target/action-inventory/inventory.md` (JSON includes source hashes). This count supersedes earlier counts based on literal registry rows.

Approved class/exam schedules, selected matches, duties, cross-domain occurrence identity, reminders, travel policy and opt-in external/device calendar adapters are pending. Production Mongo, native OIDC/device acceptance, deployed security/load/DR and institutional release evidence remain open. Current gate: `college-events/target/production-release-gate-calendar-1026.json`: **BLOCKED, 14 gates**. Owner-reported university authorization is acknowledged; candidate-specific acceptance evidence is still needed. The owner confirmed that a deployment server/domain is not yet available.
