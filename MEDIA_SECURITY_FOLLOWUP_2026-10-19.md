# Media security follow-up: 19 October 2026

Local synthetic verification covered the existing JPA/local-disk image upload and private delivery bridge for CF-30. It does not establish production storage/provider acceptance.

`FileStorageService` now rejects unsupported, absolute and traversal upload directories before disk I/O. Actual write directories must resolve inside the configured storage root. Purge resolves real paths as well as lexical paths; corrupt traversal metadata records `PURGE_FAILED` and leaves outside files untouched instead of marking a deletion successful. Root independently reviewed these additions.

Java 21 verification command:

```powershell
rtk proxy powershell -NoProfile -File scripts/Invoke-SecurityVerification.ps1 -Tests MediaAdversarialBoundaryTest,MediaRetentionTest,MediaDeliveryContainmentTest,MediaPublicationScopeTest
```

Result: **25 tests passed across 4 suites, zero failures/errors/skips**. Exact per-suite evidence is `college-events/target/media-followup-evidence-2026-10-19.json` and Surefire XML. Ten new adversarial cases execute actual ImageIO/filesystem boundaries: traversal and absolute directories; generated object keys; SVG/archive/truncated images and MIME mismatch; dishonest multipart length; dimension limit; removal of trailing active payload through re-encoding; metadata-write failure rollback; filesystem-error path privacy; traversal metadata purge; and authorization/expiry recheck after file read. Existing tests cover ownership/publication restrictions, holds, purge retry and immutable expiry metadata. Upload I/O failure now exposes a constant user message rather than an OS pathname.

Windows symbolic-link creation was attempted separately and unavailable under current privileges. No symbolic-link integration pass is claimed; no test silently skipped it. Real-path checks were source-reviewed. A malicious local writer swapping filesystem links between validation and I/O remains a race risk: production upload directories require restricted OS ownership/ACLs and isolated processing.

Open production requirements include malware/quarantine scanning; sandboxed parser CPU/memory/time limits; native image handling; encrypted private object storage; all derivative/version/cache/replica/backup deletion proofs; restore-resurrection tests; reviewed hold authority; multi-node concurrent finalization/purge evidence; global storage/tenant quotas; transaction-commit failure orphan reconciliation; and measured purge lag under approved load. The rollback test covers synchronous repository save failure, not a later enclosing transaction commit failure. Image format validation/re-encoding is not a malware-scanner guarantee. This follow-up does not enable PDF/archive uploads or pretend the academic-file ingestion pipeline exists.
