# Native identity adapter foundation

The shared client now has an opt-in native Authorization Code + S256 PKCE adapter. Missing configuration keeps native campus records unavailable. No issuer/account was activated or contacted for this implementation. Android Metro/Hermes export is a bundle check, not an APK, device, SSO or production acceptance result.

## Required approved deployment inputs

Set public build configuration only after the university identity owner approves the provider and registration:

| Variable | Required value |
|---|---|
| `EXPO_PUBLIC_NATIVE_OIDC_ISSUER` | Exact HTTPS issuer; no credentials, query or fragment |
| `EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID` | Approved public native client; never a client secret |
| `EXPO_PUBLIC_NATIVE_API_ORIGIN` | HTTPS API origin with no path; same backend audience configuration |
| `EXPO_PUBLIC_NATIVE_API_AUDIENCE` | Approved API-specific audience, distinct from the login client ID |
| `EXPO_PUBLIC_NATIVE_OIDC_SCOPES` | Explicit space-delimited approved scopes including `openid`; include API scope and `offline_access` only when approved |

Register exactly `in.ac.cujammu.pulse://oauth/callback` as the native redirect. The installed build must contain scheme `in.ac.cujammu.pulse`; Expo Go is not a delivery test. Discovery must advertise the exact issuer, code response type, public token authentication `none`, and S256. Authorization/token/revocation endpoints must share the approved HTTPS issuer origin. Providers using other endpoint origins are intentionally unsupported pending a reviewed allowlist change.

The API audience input binds stored sessions to deployment configuration; it does not force a provider to mint a particular JWT claim. The provider must configure API audience/scopes correctly. The backend verifies signature/issuer/audience/timestamps and maps the exact enabled `(issuer, subject)` to a provisioned local account. Token role/email/display claims never grant authority, and ID tokens are neither used as API credentials nor stored. Existing backend provisioning/migration and privileged assurance requirements remain launch gates.

## Behavior and limits

Expo AuthSession generates random state and S256 PKCE; the client additionally checks returned state and code verifier before exchanging the code. Token exchange and refresh use an explicit Expo WinterCG fetch adapter, rejecting redirects, omitting cookies, aborting after 12 seconds and reading at most 64 KiB before JSON decoding. AuthSession token HTTP helpers were not used after source review found redirect-following, unbounded defaults. No secret is embedded in the app. Expo SecureStore protects one config-bound token bundle; browser cookies, AsyncStorage and WebView password entry are not native authentication fallbacks. Tokens must be bearer tokens with an explicit lifetime of 30–900 seconds. Refresh is single-flight and requires a different rotated refresh token. Providers without rotation or refresh support require reauthentication. Storage failure blocks acceptance.

Native API calls use the configured HTTPS origin, `Authorization: Bearer`, omitted cookies, rejected redirects and no cookie CSRF endpoint. Backend 401 clears local tokens. Logout clears the local credential bundle; it does not claim to end the provider browser session or revoke already-issued JWTs globally. Requests capture an account generation and discard old-account responses after local logout/account change. Domain authorization and backend short expiry remain authoritative.

Protected storage availability, keystore behavior, Android backup exclusion, clock changes, interrupted auth/refresh, provider revocation, rotation/crash recovery, browser redirect interception, privileged MFA, real device screenshots/accessibility, connectivity and installed-client rollout must be tested before acceptance. SecureStore deletion failures can leave persisted bytes; sign-out reports failure rather than claiming successful deletion. No sensitive tokens or identity documents belong in logs, fixtures or screenshots. Private PDF/camera/push adapters remain separately unavailable; bearer API access does not prove those workflows.

## Dependency review

Installed exact SDK56 versions: `expo-auth-session@56.0.19`, `expo-secure-store@56.0.4`, `expo-web-browser@56.0.6`. The installed Expo `bundledNativeModules.json` and versioned docs match these versions. npm metadata reports MIT licenses; integrity values are recorded in the lockfile. Install used `--ignore-scripts`; no unreviewed installer was executed.

Fresh npm audit on 10 October 2026 reports 16 high dependency entries stemming from two advisories. Both official advisories list no patched release; registry latest remains node-forge 1.4.0 and braces 3.0.3, both affected. No safe patched override exists in the reviewed registry. npm suggestions to downgrade Expo/RN across major versions are not an accepted SDK56 remediation. This remains a release blocker; no force fix or security exception was applied.

Primary references: [Expo SDK56 streaming fetch](https://docs.expo.dev/versions/v56.0.0/sdk/expo/#expofetch-api), [Expo SDK56 AuthSession](https://docs.expo.dev/versions/v56.0.0/sdk/auth-session/), [SecureStore](https://docs.expo.dev/versions/v56.0.0/sdk/securestore/), [WebBrowser](https://docs.expo.dev/versions/v56.0.0/sdk/webbrowser/), [Expo authentication](https://docs.expo.dev/guides/authentication/), [node-forge advisory](https://github.com/advisories/GHSA-86w9-cpqp-85rv), [braces advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
