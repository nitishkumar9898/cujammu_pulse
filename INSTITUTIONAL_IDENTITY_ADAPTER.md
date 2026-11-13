# Experimental institutional API identity adapter

Implemented scope: OAuth2 JWT resource-server verification for `/api/**` requests carrying an Authorization Bearer header. This is disabled by default and is not a complete OIDC login/BFF/PKCE implementation or production approval.

## Trust boundary

`OidcResourceServerConfig` uses Spring Security's Nimbus decoder, limited to RS256, with configured HTTPS issuer and HTTPS JWKS endpoint. Standard issuer and timestamp validation (including Spring's 60-second clock tolerance) is combined with required exact API audience, nonblank bounded subject, required issue/expiry times, issue-time sanity, and a maximum 15-minute token lifetime. Token email, display names and role claims confer no local authority. Use an API-specific audience, not a login client-ID audience. ES256/other provider algorithms need a separately reviewed allowlist update.

Each verified token maps the exact `(issuer, subject)` through `institutional_identities` to an existing enabled User. Links must be explicitly enabled, never inferred from email or auto-created. Java equality additionally rejects case-insensitive database matches; production schema/index collation must preserve exact identity strings. The resulting principal remains a User for the current APIs, with authorities loaded from the current database row.

Only the high-priority bearer API chain is stateless and exempt from cookie CSRF. Its security context does not read or write the HTTP session. Missing bearer headers use the existing session chain and CSRF rules. Invalid, malformed or duplicate bearer headers cannot fall back to an authenticated cookie. `/api/v1/session/csrf` is denied on the bearer chain because it serves cookie-session clients; bearer clients do not request it. Existing helpdesk-admin gates are duplicated explicitly; all domain ownership checks continue to apply. Add future URL-only restrictions to both chains or, preferably, enforce them in the shared service policy as well.

## Activation and migration requirements

No IdP was contacted or enabled, no real account imported, and no user database migrated. Configuration remains absent/disabled. Approved deployment would need all of:

```properties
app.identity.enabled=true
app.identity.issuer=https://approved-idp.example.invalid/realms/university
app.identity.jwks-uri=https://approved-idp.example.invalid/realms/university/protocol/openid-connect/certs
app.identity.audience=university-api
```

Before enabling: reviewed additive `institutional_identities` table migration (issuer/subject unique with exact collation, foreign key to users, enabled flag default false and optimistic version), authenticated provisioning service/office workflow with independent identity verification and durable audit, and tested revocation. **Provisioning is deliberately not exposed yet**; direct ad hoc linking or auto-linking email is not a supported substitute. DB migrations and mapping imports require review and institutional authorization.

The configured JWKS URL is trusted administrator configuration. Network policy should limit outbound traffic to the approved IdP, monitor failures, and validate time synchronization. Credential rotation/cache/outage, permission change, login/logout, MFA/assurance claims, provider-side access-token revocation and key-compromise recovery still need end-to-end operational tests. Short expiry and disabling a mapping/account reduce exposure; this adapter does not claim instantaneous provider logout invalidates every already-issued JWT.

Native system-browser Authorization Code + PKCE, protected token storage, refresh rotation, web BFF sessions, real browser/device walkthrough, durable audit/notification and independent deployed assessment remain release blockers. Local/password fallback behavior is unchanged when the experimental adapter is off.

## Verification

Synthetic RSA signing tests exercise signature, issuer, audience, expiry and required/lifetime claims using a local public key; no external provider call. Converter tests reject unmapped/disabled/case-mismatched identities and ignore forged token role/email claims. MockMvc uses both real security chains and tests bearer mutations, missing/invalid/malformed/multiple headers, authenticated cookie fallback with CSRF, session isolation and admin denial. A context test proves the adapter is absent by default. This verifies the adapter contract, not actual university IdP integration or final MongoDB persistence.

References: [Spring Security 6.5 JWT resource server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html), [multiple filter chains](https://docs.spring.io/spring-security/reference/6.5/servlet/configuration/java.html), [OIDC architecture requirement](UNIVERSITY_PLATFORM_ARCHITECTURE.md).
