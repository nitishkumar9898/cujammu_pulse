# Identity foundation implementation status

Date: 2026-10-02. Scope: legacy Spring/JPA application hardening toward the approved IDENTITY architecture. This is not the completed OIDC, MongoDB or shared-client migration.

## Implemented boundaries

- Public registration accepts only STUDENT and ALUMNI; faculty, staff, organizer and other privileged roles require trusted provisioning. Controller rejects forged roles before sending OTP; service independently rejects them before persistence. Email uniqueness lookup uses the same normalized value as persistence.
- Existing session principals are checked against persisted account state before request authorization. Missing/disabled accounts and changed role, email or password invalidate the session and receive HTTP 401. This protects subsequent requests, not commands already in flight. Database errors fail the request rather than accepting stale authority.
- Password and reset-token fields are excluded from User JSON and generated string output. Registration DTO string output excludes plaintext passwords. Reset service no longer logs token values.
- H2 console is disabled in configuration and denied by the security chain.
- Helpdesk global listing and ticket status mutation are restricted to ADMIN at the HTTP boundary pending scoped support grants.
- Alumni routes require authentication. This alone does not resolve all member-to-member data visibility issues.
- Faculty sign-in redirects to the organizer dashboard, consistent with existing route authorization.

## Verification and independent review

`IdentityBoundaryTest` covers rejection of every privileged registration role without database writes, nested credential serialization, disabled/deleted/role-changed/password-changed sessions and valid-session continuation. Independent read-only reviewer identified H2 exposure, helpdesk access, DTO logging and remaining issues below. Build/test outcomes are recorded in the task response; no production dataset is used by these isolated tests.

## Remaining release blockers and next sequence

1. Configure university OIDC/PKCE, audience/issuer validation, MFA and trusted role provisioning. Native client still needs real authentication; do not restore broad API CSRF bypass as a shortcut.
2. Replace entity API responses with scoped DTOs. Authenticated alumni responses still expose nested User personal fields; authenticated access is not sufficient privacy authorization.
3. Alumni membership verification cannot be inferred from self-registration or profile edits; existing default verified flags and migration policy need correction and an approved review of existing records.
4. Password reset requires hashed token storage, atomic single-use consumption, uniform password policy and a complete secret-log audit including email/OTP paths.
5. Move ownership checks into every domain service, including scorecards, passes, exports and helpdesk. Route guards are defense in depth, not the final resource policy.
6. Verify full security-chain integration, native/web flows, concurrency and database migrations before release. Existing users granted roles through the old registration path require institutional review; this change does not silently revoke or delete historical accounts.
7. Continue event operation/scanner grants only after identity/resource authorization is verified. Remaining UI, MongoDB, outbox, runtime feature catalogue and media lifecycle work remains tracked in the architecture specifications.

No zero-vulnerability, complete-platform or production-readiness claim is made.
