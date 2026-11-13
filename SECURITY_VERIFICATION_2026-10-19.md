# Security and performance verification - 19 October 2026

Status: local remediation and verification in progress. This is not production certification.

Scope: existing Spring/JPA backend, shared Expo web/native source and retained Kotlin reference. Synthetic data and isolated loopback services only. Existing unrelated workspace edits are preserved. No deployment, campus-data import, external email, paid service or model-provider call was authorized or performed.

Requirement traceability: event-manager/form/export boundaries support CF-24/25 and the event-registration architecture; notification commands/streams support CF-19; source-controlled feature and media safeguards remain CF-29/30; the regression suite also exercises CF-04..11, CF-13..23 and CF-26..28 where implemented. This security pass does not close any unfinished functional requirement. Authentication, privacy, parser/error handling and dependency controls are cross-cutting architecture requirements.

## Implemented remediations

| Finding | Change | Evidence |
|---|---|---|
| Stored DOM XSS in feed comments, sports commentary and OTP response rendering | Render untrusted values with DOM text nodes; share values use escaped data attributes | `cujammu-pulse/e2e/legacy-dom-security.spec.ts`: 12 browser cases passed across two viewport sizes; synthetic API fixtures, not deployed Thymeleaf proof |
| Legacy notification GET requests changed state; redirects trusted Referer/persisted URLs | POST-only commands, CSRF forms, fixed relative destination allowlist, escaped inbox | `LegacyNotificationSecurityTest` |
| Any organizer could replace another event's form or export its participant answers | Service-level event-manager authorization; typed field commands; persistence IDs and ordering assigned by server | `DynamicFormSecurityTest` |
| Form answer binding accepted arbitrary shapes and excessive data | Typed scalar answers, known schema keys, bounds before persistence; minimal private submission response | `DynamicFormSecurityTest` |
| CSV escaping did not prevent spreadsheet formula execution | Shared literal-cell sanitizer for dynamic-form and registration exports; leading zeros preserved | `CsvExportSafetyTest`, `DynamicFormSecurityTest` parse produced CSV |
| Broad script CSP, missing framing/base/object/form controls | `/app` scripts restricted to self; remove unsafe-eval everywhere; deny frames/plugins/base changes and cross-origin form actions; no-referrer policy | `SecurityHeaderRegressionTest`, `SessionApiSecurityTest`, live blackbox browser bootstrap |
| Exception messages/parser details could expose internal data | Generic MVC errors and explicit private REST validation responses; rejected values not logged by these handlers | `SecurityHeaderRegressionTest`, `ApiValidationPrivacyTest` |
| Unbounded notification streams retained private event delivery | 3 connections/account and 256/instance, 60s lifetime; current-account scalar revalidation; only generic invalidation hints on live channel | `NotificationStreamSecurityTest`, `NotificationStreamAccountPersistenceTest` |
| Client response parsing allocated unbounded JSON before decoder checks | Streamed decompressed byte budget, bounded growing buffer, dishonest Content-Length defense, cancellation and strict UTF-8 | `bounded-json.test.ts`; native account-switch regression uses a delayed real stream |
| Assistant fetched unbounded event rows before limiting context | Database paging, stable date/id ordering, dedicated today/free predicates and aggregate counts | `ChatbotQueryBoundsTest`, `AssistantEventQueryPersistenceTest` |
| JWT algorithm-confusion coverage missing | Unsigned JWT and RSA-public-key-as-HMAC tokens rejected in real decoder tests; existing signature/issuer/audience/lifetime cases retained | `InstitutionalTokenTest`; experimental provider adapter remains disabled by default |
| Old POI parser dependency | POI OOXML 5.2.5 upgraded to 5.5.1 | Fresh backend build and export tests; [Apache release](https://poi.apache.org/download.html), [security guidance](https://poi.apache.org/security.html) |
| Patchable Java dependency advisories | Boot 3.5.16; compatible Jackson/Netty/Tomcat/Log4j/lang3 patches; unused SVG/Batik dependency removed | `SECURITY_DEPENDENCY_TRIAGE_2026-10-19.md`, final artifact-tied OSV scan, full verification and `CertificatePdfRenderingTest` |
| Real servlet CSRF denial forwarded to a GET-only page and appeared as 405 | Direct private JSON 403 response without an error-page forward | Security-chain regression and real loopback blackbox POST |
| Speaker/sponsor browser URLs accepted unsafe schemes through direct organizer POSTs | Bounded HTTP(S) validation before persistence; safe getters suppress historical unsafe links/images; external links isolate opener/referrer | `EventExternalUrlSecurityTest`: service negatives, boundary values, raw record preservation and actual Thymeleaf section rendering |
| Certificate compatibility smoke exposed two-page overflow | Reduced fixed-template padding/margins while retaining point-attendance disclaimer and leading-zero identifier | Real PDF renderer/parser smoke, not a mocked success toast |
| Native forwarded-header handling implicitly trusted private/loopback peers | Forwarded headers ignored by default; native mode requires an explicit operator-reviewed proxy regex | Real HTTP rotating-account plus forged-XFF quota check; production edge/TLS configuration remains a deployment gate |
| Department directory returned a JPA entity containing private HOD/contact fields | `/departments` now returns an explicit `DirectoryItem` projection; creation response uses the same projection | `DepartmentDirectoryProjectionTest` serializes the response and asserts HOD, email, phone and metadata are absent |
| Facility catalogue returned JPA maintenance and operational fields | `/facilities` now returns an explicit `CatalogItem` projection containing only client-required public catalogue data | `CUJammuDirectoryHttpProjectionTest` checks exact department GET/POST and facility GET JSON contracts; `SessionApiSecurityTest` covers anonymous/student denial of department creation |
| Idempotency keys fell back to `Math.random()` or a fixed UUID in helpdesk | Shared key generation now uses UUIDv4 from Web Crypto or Expo's native cryptographic `randomUUID`; missing secure randomness fails closed; helpdesk triage retains the same immutable command across retries | `request-key.test.ts` checks UUIDv4 shape and uniqueness; shared API tests, web typecheck/export and Android Metro/Hermes export |

Independent read-only review was performed across the authorization, header/JWT, stream and browser changes. This supplements tests; it does not replace an external deployed assessment.

## Requested test coverage and its limits

| Requested category | Local evidence / remaining boundary |
|---|---|
| Unit and white box | Fresh Java suite plus 133 shared-client unit tests; source review of authorization, queries, headers and rendering. No coverage percentage or complete path coverage claimed. |
| Integration | Spring security-chain MockMvc and isolated JPA/H2 persistence fixtures. H2 is not Mongo replica-set or MySQL transaction proof. |
| Black box | Loopback `security-blackbox.mjs`, real browser application bootstrap and synthetic persisted-workflow browser checks. |
| Boundary and input validation | Form field/answer limits, response byte budget, paging, time/day bounds, invalid JSON, CSV formulas and permission negatives. Not an exhaustive fuzzer campaign. |
| State machine / business logic | Existing lifecycle, attendance, hostel, venue, team, voting, publication and retry/conflict tests run with the suite. Domain-specific test success is not proof of every state combination. |
| Authentication attacks / mass account creation | Existing password recovery, OTP, account revocation and per-account/IP throttle tests; blackbox forged-forwarding-header attempts. Distributed bot enrollment and mail-provider abuse remain staging tests. |
| BOLA / IDOR | Dynamic forms, notifications and existing ownership/department/event scope tests. Exhaustive actor-resource-endpoint inventory and deployed retest remain required. |
| SQL/NoSQL injection | Parameterized JPQL/derived repository queries in inspected code; malformed/scalar command negatives and literal CSV output. No real Mongo query-plan or NoSQL injection claim while primary Mongo migration remains incomplete. |
| Mass assignment | Dynamic form DTOs ignore persistence identity; registration authority and existing identity-boundary tests retained. All entity-binding endpoints still require inventory review. |
| CSRF / headers / clickjacking | POST without CSRF rejected; notification GET mutations removed; framing, object/base/form policy and nosniff checked. TLS HSTS/Secure-cookie deployment behavior requires HTTPS staging. |
| XSS / sensitive frontend data | Real-browser adversarial DOM fixtures, explicit session DTO fields, private response cache controls and bounded decoders. Legacy inline CSP and CDN scripts remain a limitation. |
| Open redirect | Notification destination allowlist and malformed/external/encoded path negatives; client transport rejects redirects. IdP callback acceptance requires configured provider testing. |
| JWT algorithm confusion | Actual decoder rejects alg=none/HS256, wrong signature, issuer, audience and expiry. This does not exercise a deployed JWKS endpoint or key rotation. |
| Rate limiting / DoS | Local bounded caches, account/IP throttle, bounded streams and parser budgets; controlled read workload. Multi-instance limits, slow clients, volumetric DDoS and edge behavior are unverified. |
| Information disclosure / errors | Sanitized MVC/parser/validation errors and DTO negatives. Full log/heap/backup/monitoring redaction assessment remains required. |
| Prompt injection | `AssistantAdversarialBoundaryTest` checks recognized override/credential requests avoid DB/model calls. Provider disabled in local runs; keyword rejection is not a general jailbreak defense. |
| Future AI attack vectors | Threat hypotheses: indirect injection in event metadata, multilingual/encoded instructions, provider/model drift, output link abuse, cross-user retrieval and tool escalation. No prediction or prevention guarantee. Evaluate approved live model changes with adversarial datasets before enabling. |
| Database query analysis | Bounded assistant queries plus real H2 predicate/cardinality/order fixtures. Production EXPLAIN plans, indexes, contention and N+1 profiling at campus-scale volumes are pending. |
| Load / response time / memory / CPU | Bounded synthetic loopback workload and process sampling scripts. No campus capacity, soak, leak-free or production latency claim. |
| Caching opportunities | Public immutable hashed assets can use long-lived caching at a reviewed edge. Department/facility catalogues need invalidation-aware ETags. Keep session, grades, private files and permission decisions no-store; caching never supplies authority. No unreviewed private-data cache introduced. |
| Dependency audit | Live npm audit and artifact-tied Java public-coordinate OSV scan. Unknown JAR coordinates and service errors must fail closed; vulnerability results are advisory matches requiring reachability review. OS/container/Gradle/SAST/secret/IaC/SBOM signature assessment remains separate. |

## Release blockers and residual risks

1. Live npm audit reports 16 High package findings cascading from two root advisories: [braces](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and [node-forge](https://github.com/advisories/GHSA-86w9-cpqp-85rv). At verification time upstream lists no patched versions. Do not apply npm's suggested incompatible Expo/React Native downgrade. `node-forge` reaches Expo certificate verification, so this is not dismissed as harmless tooling. Package/lock versions were preserved.
2. Legacy templates still require inline handlers and third-party CDN scripts. `/app` has a stricter script policy. Finish nonce/external-script migration and resource integrity/self-hosting before asserting strict CSP platform-wide.
3. Stream limits are per instance. Logout-only existing streams may emit generic timing hints until the 60s timeout; private content/identifiers are omitted and authorized inbox refetch is required.
4. Real university IdP, privileged MFA, native secure storage/camera/device checks, Mongo replica-set migration/concurrency, private-object storage/parser quarantine/purge/restore, durable external delivery, privacy review and independent deployed assessment remain release gates.
5. A broader production-shaped dataset/workload, office UAT, accessibility, restore/DR and incident drills are still required. No unresolved Critical/High finding may be silently treated as accepted.
6. Compatible proposed Java versions retain two conditional Spring MVC matches (view-fragment SSE and XSLT rendering). The required view-fragment/XsltView use was not found in scoped source review; neither is automatically suppressed. See the dependency triage report for exact IDs/ranges and unavailable Spring 6.2 backports.

Reverse-proxy deployment must explicitly select `PULSE_FORWARD_HEADERS_STRATEGY=native` and set `PULSE_TRUSTED_PROXY_REGEX` to the reviewed proxy addresses; the default regex `(?!)` matches no peer. Never trust the entire campus/private network or casually switch to the framework strategy, which bypasses Tomcat's proxy allowlist. Validate edge stripping of incoming forwarding headers, restricted backend access, HTTPS detection, Secure/HttpOnly/SameSite cookies and HSTS at that deployment. The local HTTP demo does not prove those TLS properties.

## Reproduction

Run from the workspace in PowerShell; every shell entry uses RTK:

```powershell
rtk proxy powershell -NoProfile -File scripts/Invoke-SecurityVerification.ps1 -Package
rtk proxy powershell -NoProfile -File scripts/Invoke-SecurityVerification.ps1 -Tests AssistantAdversarialBoundaryTest
rtk proxy powershell -NoProfile -File scripts/Invoke-SecurityVerification.ps1 -DependencyInventory
rtk proxy powershell -NoProfile -File scripts/Audit-JavaDependencies.ps1
```

From `cujammu-pulse`, run `rtk npm test`, `rtk npm run typecheck`, `rtk npm run build:web`, and `rtk proxy node node_modules/@playwright/test/cli.js test --workers=1`.

Use a separate isolated demo folder/port to preserve any existing local demo:

```powershell
rtk proxy powershell -NoProfile -File scripts/Start-ProfessorDemo.ps1 -Port 18081 -SkipBuild -OutputName security-review-2026-10-19
rtk proxy node cujammu-pulse/scripts/security-blackbox.mjs
rtk proxy powershell -NoProfile -File scripts/Measure-SecurityResources.ps1 -Port 18081
rtk proxy powershell -NoProfile -File scripts/Stop-ProfessorDemo.ps1 -OutputName security-review-2026-10-19
```

Run resource sampling concurrently with the bounded workload. The provisional local catalogue-read target is p95 <=500ms at concurrency 8 over 80 measured requests; it is not a university SLO. Define approved route-specific latency/error/resource budgets and sustained production-shaped workloads before acceptance.

Machine-readable evidence is stored under ignored `college-events/target/`; credentials remain there and must not be published. The final result section below records actual completed checks only.

## Completed results

- Shared client: 134/134 unit tests passed after the delegated-report paging decoder was added; typecheck passed; fresh web export passed.
- Fresh Android Metro/Hermes bundle export passed. This is not an APK/Gradle build or device acceptance.
- Browser DOM attack regression: 12/12 passed across desktop and small viewport, using synthetic fixtures.
- Backend fresh Java 21 `verify`: 502 tests across 77 suites, zero failures/errors/skips; executable JAR produced. Machine-readable evidence: `college-events/target/security-verification-evidence.json`.
- Complete mocked browser suite: the 100-case run returned 97 passed, one existing desktop-only skip, and two small-screen teardown timeouts under parallel load. Both timed-out cases passed when rerun serially. The delegated report discovery passed on desktop and small-screen projects. This is distinct from real backend acceptance.
- Targeted deterministic assistant adversarial guards: 10 passed, zero failures/errors/skips; no model-provider calls.
- Final packaged-JAR scan at `college-events/target/java-security-audit-2026-10-19.json`: 131 public Maven coordinates checked against OSV; 2 advisory matches remain, both Spring Web MVC 6.2.19 conditional findings documented above; no unidentified JARs. Artifact SHA-256: `80981C33285AB2B8987555C077B7300F3FCF326354C3DF80A4414B2553E511DE`. Batik and Xalan are absent from the packaged dependency inventory.
- Current rebuilt Java 21 package and full test lifecycle pass. Live HTTP/performance and remaining production gates are still pending.

## Latest verification delta after facility projection - 19 October 2026

- The controller-level projection/security selection passed 10/10 with zero failures/errors/skips.
- The first full Java 21 run completed 534 tests but failed one bounded HTTP test during Windows JDK loopback initialization (`Unable to establish loopback connection`). Repeating the complete suite with the supported short socket temp directory `C:\tmp` passed **534/534**, zero failures/errors/skips. This H2/JPA suite is not Mongo replica-set acceptance.
- Fresh source package: `college-events/target/events-security-review-2026-10-19.jar`, SHA-256 `79BA73B3D2BF07EBC9A4E81F0406167306D6D62F03FE02EE8D089E3875EBD2AC`. Fresh artifact-bound scan checked 131 public Maven coordinates, found 2 Spring MVC advisory matches and 0 unidentified JARs (`college-events/target/java-security-audit-postfix-2026-10-19.json`). These findings remain release-blocking until patched or independently triaged against a reviewed fixed version; they are not suppressed.
- The prior C: disk-full/default-JAR packaging block is superseded. An attempted temporary POM final-name change was rejected due the existing `<build>` element and reverted; it did not alter the POM configuration or artifact.
- Required npm high-severity dependency findings remain open as recorded above. Synthetic local performance, UI and all historical checks retain their original scopes and limitations.

## Verification delta after department directory privacy fix

- Java 21 full test phase on the updated source completed with **524 tests, zero failures, zero errors, zero skips**. The existing JDK `HttpServer` duplicate test was removed after repeated `HttpServer.create()` failure in this environment (`Unable to establish loopback connection`); the bounded publisher test still exercises chunked over-budget cancellation, and the separate real-browser loopback blackbox completed successfully.
- Maven could not complete executable-JAR repackaging: the pre-existing local demo holds `target/events-1.0.0.jar` open; an isolated repackage attempt then failed because the Windows C: volume had zero free bytes. No existing demo was stopped and no user data was removed. Therefore the current 524-test source run is **not** represented as a freshly packaged/scanned artifact; the earlier hash above refers to the earlier successful package only.
- The isolated loopback security blackbox passed all 8 checks. Its bounded workload returned 80/80 HTTP 200 responses at concurrency 8, but p95 was 702 ms against the provisional 500 ms target. It is one synthetic local sample, not a capacity result.
- Shared client remains at 134/134 unit tests and TypeScript typecheck pass. A clean post-fix dependency artifact scan remains pending with the package.
- Follow-up review requested HTTP-level coverage for the directory response. `CUJammuDirectoryHttpProjectionTest` now exercises the real controller mappings and exact serialized projections, while the existing security-chain test covers denied write roles. A related facility entity response was converted to an allowlisted catalogue DTO. The 10 targeted projection/session tests pass after both changes; this targeted run compiles all test sources but is not a post-fix full-suite run.

## Latest shared-client remediation - 19 October 2026

- Removed the weak/constant helpdesk request-key fallbacks. Web uses cryptographic Web Crypto bytes with UUIDv4 version/variant bits; the Expo entry point registers `expo-crypto`'s native `randomUUID()` before UI actions; no secure source means the command is rejected locally rather than reusing a key. Helpdesk triage preserves the exact command/key after an uncertain response.
- Fresh shared-client tests: **135/135**; TypeScript typecheck passed; Expo web export passed; Expo Android bundle export passed (Hermes bundle). No installed Android device test was performed, so device runtime availability and camera/auth flows remain open.
- Expo's documented `randomUUID()` is available on Android, iOS and web and uses cryptographic randomness. The installed SDK 56 package's declarations/source were inspected; bundle generation confirms inclusion, while hardware-level acceptance remains pending.

## CF-15 helpdesk retry follow-up - 19 October 2026

- Requester ticket creation now stores the entire pending command (request UUID, subject, description, category and priority) in account-scoped process memory, restores it after route remount, locks the form after uncertain outcome, and permits a clearly labelled retry with the same payload. Reply/reopen/escalate store ticket ID, version, action kind, body/reason and request UUID; retries replay that snapshot. A changed reply draft remains intact after the old command is confirmed. The private payload is not written to browser storage; logout/expired session clears it, feature OFF clears the affected account's state, and process restart discards it.
- `e2e/helpdesk-retry.spec.ts` passed **4/4** across desktop and small-screen browsers: route-remount create retry with identical body/key and CSRF, plus reply retry body/version/key equality and preservation of a newer unsent draft. The complete responsive suite then passed **113 tests with one documented desktop-only skip**.
- Fresh shared tests passed **136/136**, TypeScript typecheck, web export and Android Hermes bundle export. The complete responsive suite passed **113/113 runnable cases with one documented skip** after the session and feature-control invalidation changes. Android output replaces the shared `dist/` web export, so the static browser harness was run after a fresh web export. Browser tests use synthetic fixtures and do not prove server persistence or production acceptance.


### Recheck artifacts

A raw `npm audit --omit=dev` report is captured at `cujammu-pulse/target/security/npm-audit-omit-dev-2026-10-23.json` (SHA-256 `0df7e3a67c00ecbe1163d6c3dcb5740716fb483769664add59ec70a9f83c3a0c`) against lockfile SHA-256 `2237ba5c56d321a2adeb47913aa89b7ff1c707b8388654ddd987b9bd34cb3d3f`. Counts: 15 High, zero Critical. These are local consistency identifiers only; no signature/provenance claim.



## Exact candidate verification refresh — 23 October 2026

A checksum-verified isolated snapshot of 475 Java build/source files completed unfiltered Maven `verify/package` with 576 tests, 95 suites, zero failures/errors/skips and zero source drift. Exact artifact SHA-256: `31F890DCFD9B3C36E69D00981F99B401B4C79D8FF515DF8CBD93B90CD4D4F527`. A fresh OSV live query checked 158 coordinates extracted from the packaged artifact plus Maven dependency inventory; zero matches and zero unidentified JARs. Audit SHA-256: `74dc5da757706c00b0f28e7f584ad30bf2215ccdf3b802c0de3050ba42ccbb4f`. This closes only the Java public-coordinate advisory gate for these artifact bytes; it is not a deployed assessment or whole-platform security certification. The offline release gate binds this artifact/source identity and reports 14 incomplete checks, including the separate npm High findings and missing production evidence.

