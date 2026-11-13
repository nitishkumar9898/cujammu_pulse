# Local password recovery hardening

This is an interim hardening of the existing Spring/JPA local identity implementation. The approved OIDC/PKCE identity migration remains required; this change does not assert production readiness or MongoDB verification.

## Invariants and migration

- Generate a 256-bit bearer secret with Java SecureRandom and URL-safe Base64. Only its version-prefixed SHA-256 digest is persisted in the existing reset-token column. Raw values are owner-delivery data and must not enter logs or response DTOs.
- Expiry is 30 minutes, stored and compared as UTC. Expiry is exclusive: equal-to-expiry is rejected. Disabled accounts cannot issue, validate or consume credentials.
- One conditional database UPDATE changes the password and clears digest/expiry. Concurrent consumers must have one winner; replay fails. Issuance replaces prior credentials. An authenticated password change also revokes outstanding reset credentials.
- A shared `User.credentialVersion` optimistic version advances for ordinary entity changes and every bulk credential update. Stale detached/profile writes fail with an optimistic-lock conflict rather than restoring old passwords or reset tokens. Callers must reload and retry only their explicitly authorized changes.
- Old UUID/plaintext links fail validation after rollout. Existing user/password rows are preserved. Users request a fresh link; no legacy-token fallback. Do not roll back to a raw-token implementation after issuing digest credentials.
- Existing local password complexity policy is shared across registration, password change and recovery, with an explicit 72-byte UTF-8 BCrypt limit. A future identity-provider password policy is separately reviewed.
- Console email no longer exposes OTP values or claims OTP delivery. Missing SMTP configuration fails credential delivery truthfully. SMTP exceptions in credential-specific handlers record only a failure class, not provider exception content. Credential messages bypass the generic in-memory HTML retry queue, whose lifetime does not enforce token expiry; a fresh request is required after delivery failure.
- OTP resend requires a valid session CSRF token; the web form sends the configured header. Password change errors are displayed without a server error, and success invalidates the current session before redirecting to login. Stale console instructions no longer direct users to logs for credentials.

## Verification and outstanding work

Synthetic unit tests cover secret/digest separation, normalization, disabled accounts, legacy/digest rejection, password validation and revocation on password change. Credential-mail tests capture logs with synthetic provider errors and prove no bearer/OTP or exception stack escapes, no credential messages enter the retry queue, and console mode does not claim delivery. Isolated embedded H2 JPA tests verify actual digest storage and rotation, run eight concurrent consumers and check one success, no replay, expiry boundary and disabled-account rejection. They never start the application against the workspace database; test logs go to target/test-logs.

Controller-level anti-enumeration, abuse throttling, safe link headers and full browser flows require integration review. Durable recovery delivery/audit and final database migration remain architecture work. H2 concurrency evidence does not prove the intended MongoDB deployment or external mail delivery. No real account data is needed for verification.

## Version-column rollout

No workspace database is modified by this task. Fresh test schemas create `credential_version BIGINT DEFAULT 0 NOT NULL`. Existing local developer databases configured with Hibernate `ddl-auto=update` are expected to add the defaulted column at next authorized startup; take a restorable snapshot and verify schema/data first. Production must use an explicitly reviewed migration, not automatic DDL:

```sql
ALTER TABLE users ADD COLUMN credential_version BIGINT DEFAULT 0 NOT NULL;
```

Verify dialect support, lock impact, row counts and all existing rows have version zero before enabling new writers. Drain old application writers before cutover: an old binary unaware of optimistic versions could overwrite credentials. Rollback requires stopping new credential issuance and controlled restoration of a compatible application version; do not drop the version column or revert to plaintext recovery. Existing user/password values are preserved by the additive migration. A synthetic stale-profile regression test proves a previously loaded user cannot overwrite a consumed/reset credential in H2.

Primary references: [OWASP recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Spring Data JPA modifying queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html), [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
