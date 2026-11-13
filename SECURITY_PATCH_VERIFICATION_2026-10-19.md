# Java dependency patch verification — 19 October 2026

This report records the current isolated backend candidate. It is local verification, not production certification.

## Changes

- Spring Boot parent remains at 4.1.1 / Spring Framework 7.0.9.
- Pinned compatible same-minor patches in `college-events/pom.xml`: Jackson 2.x BOM 2.21.8, Jackson 3.x BOM 3.1.8, and embedded Tomcat 11.0.27.
- Fixed private no-store media response headers in `MediaDeliveryController`; the complete suite covers the regression.

## Evidence

- Java 21 isolated Maven `verify`: 539 tests, 89 suites, zero failures, errors or skips; build exit 0; no source drift after the snapshot.
- Exact packaged candidate: `college-events/target/events-boot411-clean-20261019.jar`.
- SHA-256: `13B789F2D66989085E44020A3139FCF8A434A68921D958C795F7F9239369EA99`.
- Build evidence: `college-events/target/boot411-clean-20261019.json`.
- Live OSV scan: 158 Maven coordinates, zero advisory matches, zero unidentified JARs; exact artifact hash recorded in `college-events/target/java-security-audit-boot411-clean-20261019.json`.
- The Boot repackager helper JAR was matched by SHA-256 to the public Maven Central checksum for `org.springframework.boot:spring-boot-jarmode-tools:4.1.1`.

## Limits and next gates

This removes the prior Java artifact advisory matches for these exact bytes. It does not establish exploit resistance, independent review or all-dependency safety. The fresh npm audit is retained at `college-events/target/npm-audit-current-20261019.json` and reports 16 High entries through Expo/Metro build dependencies (`braces` and `node-forge`). A separate package-resolution probe using published Expo SDK 58.0.14, matching auth/crypto/secure-store/browser modules, React Native 0.88.0 and React 19.3.1 yielded 15 High plus 7 Moderate findings, with `node-forge` still in the Expo signing chain. It was not applied to the project because the major upgrade does not clear the High gate and adds compatibility and moderate-finding churn. The Java tests use the existing JPA/H2 bridge and do not prove Mongo replica-set transactions, migration or concurrency. The candidate has not received fresh browser/device, deployed staging, load, restore/DR, independent assessment or institutional UAT against these exact bytes. Do not describe the platform as production-ready or deploy this artifact until all applicable gates in `PRODUCTION_RELEASE_GATES.md` are satisfied.

The offline production gate was rerun against this exact JAR, build evidence and Java audit plus a fresh current npm audit. It reports **14 incomplete gates** in `college-events/target/production-gate-boot411-clean-20261019.json`. Full-build/source identity and Java dependencies pass; npm dependencies and production-persistence implementation fail, as do the external-evidence manifest and its 11 required review/acceptance records. The reported university-release-approval record is an artifact-specific evidence gate: the user's approval message authorizes this work but is not a reviewed, hash-bound institutional sign-off record for this release candidate.
