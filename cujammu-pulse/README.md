# CUJAMMU-PULSE universal client foundation

This additive Expo/React Native application is the shared source for web and Android. The existing Thymeleaf and Kotlin prototypes remain reference implementations. This foundation is buildable, **not feature complete or approved for production**.

## Implemented

- One responsive screen tree, navy/gold circular monogram, Light/Dark/Auto tokens and device-local theme persistence. The monogram is a placeholder brand treatment, not an approved university seal.
- Four-step introduction; browser session, expired session, denied/offline/error, empty and retry states. No demo accounts or operational fixtures are shipped.
- Web uses same-origin session cookies; reads `/api/v1/session` as `{id,name,role}`. Sign-in uses `/app/sign-in` to return to the shared client. Sign-out gets `/api/v1/session/csrf` and sends a CSRF-protected `POST /logout`. Tokens/session data are never put in AsyncStorage.
- Live `/api/v1/cujammu/departments` and `/facilities` catalogue reads. Responses are decoded into minimal allowlists, and contact/private extra fields are discarded. Directory rendering does not claim venue availability or expose booking controls.
- `/api/v1/cujammu/features` map rechecked at startup, foreground/focus, manual refresh and every 30 seconds while active. Unknown/disabled keys hide routes; refresh failure clears private in-memory state. Disabled venue data is evicted. API requests timeout after 12 seconds. Enabled but unsupported modules show truthful non-interactive availability labels.
- Module delivery inventory covers all 29 active CF IDs plus existing alumni/feed/assistant/certificates/operations. The action registry explicitly marks session/legacy-map bridges.
- Alumni shared screens: opt-in directory and mentors with server pagination/department filters; own profile and granular visibility; verified-alumni job posting and own listing close/reopen; administrator evidence review/confirmation and independent verification/revocation. All writes use CSRF and expected record versions; job retries reuse the same request UUID and command. Server authorization remains authoritative. These screens share Android source but authenticated native execution still waits for its identity adapter.

## Run and verify

Use Node 24 LTS or another Expo-supported version. This workspace verification used Node 26.1.0 on Windows. All direct dependencies and the full npm lockfile are pinned. Do not run blanket `npm audit fix --force`.

```powershell
rtk proxy npm ci --ignore-scripts
rtk npm run typecheck
rtk npm test
rtk npm run build:web
rtk npm run test:web
rtk proxy npx --no-install expo install --check
rtk proxy npx --no-install expo export --platform android --output-dir dist-android
```

Web output uses `/app` base paths. Configure the Spring application to serve `dist` at `/app/**` on the **same origin** as `/api` and `/logout`; no permissive CORS or cross-origin credential workaround is included. `npm run web` is useful for shell preview but does not supply authenticated API proxying. The browser tests use a loopback-only static server and route-scoped synthetic fixtures; they do not prove backend integration.

`test:web` uses installed Microsoft Edge at desktop and 390px viewports. Change the Playwright browser channel for another approved CI browser. No browser binary or cloud account is downloaded. Screenshot outputs under `test-results` contain only explicitly synthetic test identities.

## Verification, 2 October 2026

| Check | Result |
|---|---|
| TypeScript strict typecheck | Passed |
| Node contract/transport/tooling tests | 23 passed |
| Edge browser workflows at two widths | 12 passed |
| Expo dependency compatibility | Passed |
| Web Metro export | Passed, `/app` bundle |
| Android Metro/Hermes export | Passed; this is not an APK/device test |
| npm dependency audit | **Blocked: 4 high dependency entries from one node-forge advisory** |

Browser checks cover onboarding, persisted dark preference, catalogue filtering without focus loss, disabled-route/data eviction and expired session privacy clearing. Screenshot inspection found and corrected narrow-screen header wrapping. Tests use synthetic data only.

Alumni checks additionally cover absent-profile 204, privacy defaults and exact CSRF/version commands, unsafe external-link rejection, denied admin verification, confirmation before status changes and identical job retry after a simulated service failure. The source contract test reads the actual Java DTO response record fields and checks the client decoder fields against them. This detects source contract drift; it is not a live backend integration test. Browser HTTP mocks remain isolated in `e2e/`. Drafts/request keys are memory-only; after uncertain result and leaving/reloading a screen, review own listings before composing a new post. Durable cross-restart draft recovery is pending.

## Required next work and release blockers

1. Native OIDC/PKCE/protected-token transport is deliberately unavailable. Android renders the same introduction/theme shell with a clear identity-connection message; it does not pretend browser cookies authenticate a native app. Authenticated native/web parity is not complete.
2. Beyond the alumni slice, domain mutation workflows, staff/admin controls, scan/camera adapters, role-scoped actions, notifications and event workflows remain pending verified vertical slices. Alumni mentor discovery and explicit profile willingness are implemented; private mentorship request/messaging is not. A module inventory entry is not implementation or proof of native authenticated parity.
3. Current feature map has no authoritative revision, dependencies or client-compatibility signature. This is a read-only migration bridge, not acceptance of CF-29's full manifest contract. Runtime enforcement remains the backend's responsibility. Foreground polling is not instantaneous delivery.
4. Theme is saved per device, not synchronized to an authenticated account. Final official logo, account-level preference API, accessibility audit, real Android/device tests and full walkthrough/parity acceptance remain pending.
5. No real backend E2E/SSO, launch assessment, signed APK, office UAT, production deployment or real university data was used in this directory's tests.
6. `npm audit` still reports node-forge via Expo CLI/code-signing tooling. [GHSA-86w9-cpqp-85rv](https://github.com/advisories/GHSA-86w9-cpqp-85rv) affects through 1.4.0; the advisory and registry have no published patched version as reviewed on 2 October 2026. Do not accept untrusted signing certificates or enable release/update signing using this toolchain until a vetted fix/replacement is validated. This is an unresolved high release blocker, not silently waived as development-only.

The targeted `xcode -> uuid 11.1.1` override resolves [GHSA-w5hq-g745-h8pq](https://github.com/advisories/GHSA-w5hq-g745-h8pq). Xcode's actual `uuid.v4()` call is compatible; a regression test verifies its uppercase 24-character project identifier contract. Eight moderate dependency entries cleared after this change. The override must be reviewed again when Expo updates.

Version evidence: [official Expo SDK reference](https://docs.expo.dev/versions/latest/) and installed `expo/bundledNativeModules.json`; selected SDK 56.0.23 / React 19.2.3 / React Native 0.85.3 / RN Web 0.21.2. Exact lockfile integrity records identify package artifacts. Existing source is preserved for controlled migration.
