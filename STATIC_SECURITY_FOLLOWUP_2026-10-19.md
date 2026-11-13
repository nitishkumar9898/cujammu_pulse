# Additional static and browser security verification

Scope: current tracked source/configuration and selected universal-client source; local synthetic verification only. This is not a penetration-test certification or full-history secret scan.

## Change and regression evidence

The retained legacy scanner template interpolated browser camera identifiers and device labels into option HTML. It now creates option elements and assigns `value` and `textContent`, then replaces the selector children. These values cannot escape an HTML attribute or option through that code path. The scanner route currently redirects away from this retained template; this is defense in depth for reference code, not evidence that the old scanner is a released workflow.

`rtk proxy npx playwright test e2e/legacy-dom-security.spec.ts --grep "camera labels"` passed **2/2** cases (desktop and small viewport). The actual template script executes with hostile camera identifiers/labels, checks exact literal values, checks for injected elements/event attributes, verifies selected-camera behavior and absence of execution. Real camera hardware is intentionally stubbed; no device-camera acceptance is claimed. `rtk proxy npm run typecheck` passed. Scoped `git diff --check` passed with only the existing Windows line-ending notice.

## Read-only findings and limits

- A narrow high-confidence private-key/cloud/provider-token signature scan of tracked source/configuration produced **zero candidate files**. It does not cover arbitrary passwords, unknown credential formats, Git history, every untracked file or deployed assets; zero matches is not proof that no secrets exist.
- Universal client AsyncStorage usage inspected is theme preference. Native identity credentials use Expo SecureStore, while public build variables are identity endpoint/client configuration. This is source evidence, not device storage extraction testing.
- Current retained native `AuthScreen.kt` rejects unavailable identity flows; the former local OTP-bypass finding should not be repeated as current behavior.
- Scoped injection searches found parameterized repository JPQL and no user-controlled dynamic SQL or shell invocation. The governed certificate PDF template uses escaped text and fixed local CSS; no untrusted HTML/resource URL path was identified in that template. These checks are not exhaustive data-flow analysis.
- Navbar assistant HTML generation escapes markup before fixed markdown transformations. Other inspected innerHTML sites use constant strings. Legacy pages still depend on inline scripts and CDN resources, so strict legacy CSP migration remains open.
- The optional AI provider previously collected an unbounded response string. Root added a 64KiB byte subscriber. Independent read-only review confirmed actual chunk accounting before retention, upstream cancellation and failure on overage, exact-budget acceptance, and no reliance on Content-Length. Its tests include an actual chunked loopback response. Provider/live-model behavior is not verified by these local tests.
- Production persistence is explicitly unavailable: `ProductionPersistenceGateConfiguration` uses the `prod` profile and throws before fallback to H2. Default H2 settings are local configuration, not production acceptance. The approved Mongo adapter/migration/recovery evidence remains a release gate.

Changed files owned by this follow-up: `college-events/src/main/resources/templates/organizer/scan-qr.html` and `cujammu-pulse/e2e/legacy-dom-security.spec.ts`. Existing unrelated workspace changes were preserved.
