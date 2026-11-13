# ADR: Library turnstile lifecycle and concurrency guard

## Decision

Library visits are serialized with a pessimistic lock on the requesting user's active visit. Entry and exit both require the runtime `MODULE_LIBRARY_TURNSTILE` control and accept only the two approved purposes: `STUDY` and `BOOK_ISSUE_RETURN`. The dwell duration is calculated from persisted server timestamps; client timers are display-only.

## Amendment — 18 October 2026

The former midnight auto-close behavior was removed because a report cutoff is not evidence of physical exit. A prior-local-day `ACTIVE` visit remains open and blocks another entry until its owner records a safe exit explicitly marked unverified. No automatic duration is stored. Actual configured closing-hours/holiday handling and the durable previous-day report job remain unimplemented until their policy/configuration and job lifecycle are available. Library CSV text fields are neutralized against spreadsheet formulas before normal CSV quoting.

## Safety boundary

This does not create a signed facility QR challenge or prove that a physical scan occurred. The public legacy library routes therefore remain fail-closed until a reviewed challenge/confirmation protocol and scoped librarian report endpoint are implemented. No client supplied latitude, longitude, or clock is used as authority.
