# Local security remediation evidence — 19 October 2026

This records the completed local security pass requested by the owner. It is not production approval or a guarantee against future attacks. Synthetic records, loopback HTTP and isolated in-memory persistence were used. Existing workspace changes were preserved; nothing was deployed or published.

## Changes and coverage

The detailed requested-category matrix and implementation references are in [SECURITY_VERIFICATION_2026-10-19.md](SECURITY_VERIFICATION_2026-10-19.md). Fixes cover stored DOM XSS, event-form object authorization, typed/bounded commands, spreadsheet formula injection, notification CSRF and redirect safety, bounded/revalidated notification streams, private errors, strict shared-app script CSP and framing controls, unsafe external URLs, streamed JSON limits, bounded assistant queries and forwarded-header trust. JWT algorithm-confusion regression tests reject unsigned and RSA-public-key-as-HMAC tokens. Independent agents reviewed security-sensitive changes.

Dependency changes upgrade compatible Java patches and POI, remove unused SVG/Batik/Xalan dependencies, and verify actual certificate PDF rendering. [Dependency triage](SECURITY_DEPENDENCY_TRIAGE_2026-10-19.md) records reachability conditions and upstream limitations.

## Completed checks

| Check | Observed result | Boundary |
|---|---|---|
| Java 21 full verify | 502 tests, 80 reported suites, zero failures/errors/skips | Local JPA/H2, not production database concurrency |
| Final package targeted regressions | 17 tests / 5 suites, zero failures/errors/skips | Headers, session, rate limits, actual PDF and URL validation |
| Shared client unit/typecheck | 134 tests passed; typecheck passed | Current shared workspace snapshot |
| Browser regression run from this pass | 99 passed, 1 existing conditional viewport skip | 100 enumerated fixture cases; separate concurrent runs are documented in the detailed report |
| DOM attack cases | 12 passed | Real browser with synthetic API fixtures |
| Android export | Metro/Hermes export passed | Not Gradle/APK/device acceptance |
| Actual browser/backend workflows | 9/9 passed on isolated port 18193 | Registration/withdrawal persistence, private claims and independent publication; synthetic only |
| Real HTTP attack checks | 8/8 passed | Anonymous denial, headers, cross-origin/no-CSRF denial, role/header spoofing, mutation/console denial, account and peer quotas, strict-CSP browser startup |
| Bounded catalogue workload | 80/80 HTTP 200; concurrency 8; p50 26 ms, p95 123 ms, p99 177 ms; approximately 198 requests/s | Warm public catalogue endpoint only; provisional p95 500 ms target met, not a production SLO |
| Final Java process sample | 29.46 s; 0.219 CPU seconds (0.74% of one logical core); sampled peak working set 256.12 MiB / private bytes 750.54 MiB | Predominantly workload tail/idle; not sustained-load CPU, Java heap usage or memory-leak proof |
| Java artifact OSV inventory | 131 coordinates, 2 advisory matches, no unidentified JARs | Public-coordinate metadata scan, not exploit proof |

Java artifact SHA-256: `80981C33285AB2B8987555C077B7300F3FCF326354C3DF80A4414B2553E511DE`.

Machine evidence: `college-events/target/security-verification-evidence.json`, `security-verification-targeted-evidence.json`, `java-security-audit-2026-10-19.json`, `npm-security-audit-2026-10-19.json`. Run-specific live evidence is under `college-events/target/security-owner-01a15257/`. Credentials are deliberately excluded from this report and must remain private.

The first rotating-account blackbox attempt sent password-hashing requests serially and did not observe the peer quota before its 60-second window expired. The corrected test sends a bounded eight-worker burst and asserts completion within that same window; the rerun passed. This changes the test timing, not the application quota. An earlier port collision also motivated launcher checks that verify the exact Java listener owner before publishing generated credentials. Final live results above use the separate PID-verified port 18193.

## Outstanding release gates

- npm audit has 16 High package entries cascading from two unpatched root advisories (braces and node-forge). No incompatible downgrade or suppression was applied.
- Two conditional Spring MVC advisory matches remain: GHSA-j9f9-w8pj-32f8 and GHSA-pc63-qcmh-9cmg. Required view-fragment/XSLT use was absent in scoped review; that does not clear the findings automatically.
- Legacy template inline scripts/CDNs still prevent a strict CSP across every legacy page. The shared `/app` has a stricter policy.
- Production-shaped sustained load, distributed throttling, real database query plans/concurrency, HTTPS/proxy/cookie behavior, deployed IdP/MFA, native devices, private media purge/restore, university UAT and an independent deployed security assessment remain required.
- Prompt-override guard tests are deterministic local checks with the model provider disabled. Multilingual/indirect injection, retrieval isolation and model-update adversarial evaluation remain necessary; future attack vectors cannot be predicted with certainty.

No campus capacity, production latency SLO, exhaustive attack coverage, zero-vulnerability or fully-secure claim follows from these results.
