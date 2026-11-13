# Additional security verification - 19 October 2026

The platform is **not production-approved or fully secure**. This follow-up adds concrete local adversarial checks and fixes after the earlier security pass; it does not close the whole platform's release gates. All test data is synthetic. No deployment, real university records, external model invocation or provider delivery was performed.

## Additional fixes

- OTP verification/consumption and failed-attempt counting now use atomic per-account operations. Concurrent issuance cannot exceed the five-request budget. Exact UTC expiry and locale-independent identity normalization are tested; OTP logs omit recipients and credentials. Storage remains single-instance and resets on restart.
- Upload directories are explicitly allowlisted. Writes and purges check actual filesystem containment, corrupt traversal metadata records a failed purge, and storage errors omit local filesystem paths. Tests use actual image decoding/re-encoding and filesystem writes. See [media evidence](MEDIA_SECURITY_FOLLOWUP_2026-10-19.md).
- The optional AI transport now limits actual response bytes to 64 KiB before retaining chunks, cancels excess response data and returns its existing safe fallback. The request timeout remains in place. Tests exercise exact/+1 bounds and an actual chunked loopback response without a declared length. No provider or live-model acceptance is claimed.
- Retained scanner camera identifiers/labels are assigned using DOM properties rather than HTML interpolation. Its route remains unavailable; this is defense in depth for retained code. See [static evidence](STATIC_SECURITY_FOLLOWUP_2026-10-19.md).

Security-sensitive changes received independent read-only review.

## Completed targeted evidence

- Media: 25 tests across 4 suites passed, including 10 new adversarial tests.
- Authentication/session: 21 tests across 5 suites passed, including 10 new tests for concurrency, session fixation, real saved external redirect input, CSRF/logout, password/role/email change and account deletion.
- Browser DOM attack regressions: 14/14 passed on desktop and small viewport; actual template scripts with synthetic fixtures. Client typecheck passed.
- Narrow high-confidence credential-signature scans of selected tracked files and current production source (including untracked source): zero candidate files. This is not a complete secret, Git-history, bundle or deployment audit.
- Fresh npm audit still reports 16 High package entries. Findings were retained without incompatible downgrades or suppression.

The first full regression attempt encountered Windows Java NIO loopback initialization failure in the new actual-HTTP test. A standalone selector probe failed with the host TEMP configuration and passed with an explicit short socket temporary directory. The setting must be passed at forked-JVM startup; late Surefire system-property promotion did not resolve it. The verification helper now accepts `-SocketTempDirectory`; it changes local JVM socket setup, not security assertions. A separate actual chunked-HTTP probe then passed, requiring the IOException specifically to contain the byte-budget rejection rather than accepting arbitrary network errors. The JUnit equivalent is retained in `BoundedProviderHttpIntegrationTest`; the initial failure is preserved in `college-events/target/security-followup-initial-failure.json`. The JDK documents this temporary-directory setting in its [networking properties](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/doc-files/net-properties.html).

## Final assembly limitation

**A clean final full-build result and new sustained-load result were not obtained.** Concurrent edits affected shared source/tests and Maven outputs, so shared-directory aggregate counts are not accepted as final evidence. The byte-budget HTTP check remains in a separate integration-test file rather than being removed to obtain a pass.

An isolated source-copy helper now preserves source manifests, before/after inventory differences, build logs and artifact hashes without using the shared Maven target. Its first isolated attempt failed before any tests: the JVM could not allocate native memory (`Chunk::new`). A second attempt with explicit Maven/test heap caps also failed before producing test reports. At that time a process inventory observed 11 Java processes with approximately 7029 MiB aggregate working set; a later shell process failed CoreCLR heap initialization. Other processes were preserved. These are machine resource failures, not a demonstrated application load-capacity result.

The targeted successes above remain the completed evidence. Do not substitute the earlier 502-test success or concurrent 525-test XML totals for a clean final assembled result. The new 60-second/three-route loopback workload script was prepared but **not executed**. No latest p95/CPU/memory or fresh artifact dependency result is claimed by this follow-up.

After sufficient resources are available, reproduce using:

```powershell
rtk proxy powershell -NoProfile -File scripts/Invoke-IsolatedSecurityFollowup.ps1 -Package
rtk proxy powershell -NoProfile -File scripts/Start-ProfessorDemo.ps1 -Port 18317 -SkipBuild -OutputName security-followup-01a15257 -ArtifactName events-security-followup.jar
```

The copied JAR must match the successful snapshot hash and have no source drift before using it for loopback acceptance. Run browser workflows and attack checks sequentially before the sustained workload; sample only the selected PID-owned process, then stop only that selected demo. No follow-up demo was started in this blocked run.

## Remaining mandatory evidence

Production-shaped long-duration load/soak, authenticated writes/concurrency, distributed abuse controls, real database query plans and Mongo replica-set migration, device/native storage/camera/SSO, deployed TLS/edge/cookies, live approved-model adversarial evaluation, malware/parser sandboxing, full-history secret/SAST/container/IaC scans, restore/backup/derivative deletion, university UAT and independent deployed penetration assessment remain open. Windows symbolic-link creation was unavailable; no symlink integration pass is claimed. Filesystem TOCTOU and outer-transaction upload-orphan reconciliation remain residuals.

Known dependency findings and legacy CSP migration remain as recorded in [the prior evidence](SECURITY_RUN_EVIDENCE_2026-10-19.md). More testing can find defects; no finite suite proves absence of all vulnerabilities or predicts every future attack.

## Subsequent launch follow-up

The historical failures and pending statements above are preserved. Later isolated full-build, Windows upload-rollback identity repair, notification/session-refresh regressions, exact-artifact browser replay and dependency evidence are recorded in [LAUNCH_READINESS_PROGRESS_2026-10-19.md](LAUNCH_READINESS_PROGRESS_2026-10-19.md). The new evidence supersedes only the explicitly repeated local checks; production migration, full scope/client acceptance, deployed assessment and institutional release gates remain open.
