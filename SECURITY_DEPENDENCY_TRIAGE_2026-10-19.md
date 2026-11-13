# Dependency security triage — 19 October 2026

This is source and public-advisory triage, not an exploitation assessment or production approval. The initial packaged Java artifact yielded 154 public Maven coordinates, 71 advisory matches across 21 package coordinates, and one unidentified JAR. An advisory match means the installed version is listed in an affected range; attack prerequisites and application reachability require separate verification.

Only public dependency coordinates were sent to OSV/Maven Central. No application source, credentials or university records were sent.

## Evidence and version verification

- Initial packaged inventory: `college-events/target/java-security-audit-initial-2026-10-19.json`.
- All 71 complete advisory summaries and affected ranges, retrieved from the official [OSV API](https://google.github.io/osv.dev/api/): `college-events/target/java-security-advisory-details-2026-10-19.json`.
- Live proposed-version query across all 21 affected coordinates: `college-events/target/java-security-planned-versions-2026-10-19.json`.
- These target files are ignored local evidence. Rebuild and scan the resulting JAR before reporting installed remediation.

The apparent Jackson/Spring findings were checked against current ranges: they are recent 2026 advisories, not merely stale Jackson 2.9 or old Spring findings. For example, [Jackson record-ignore bypass](https://osv.dev/vulnerability/GHSA-3pjw-73gf-8qr5) includes 2.19.0 through versions before 2.21.4, and [Spring JavaScriptUtils XSS](https://osv.dev/vulnerability/GHSA-3chg-m5w7-qfv5) includes 6.2.0 through versions before 6.2.19. No blanket false-positive exclusion is justified.

Live Maven Central metadata confirms Spring Boot **3.5.16** is available on the existing major/minor line. Its [published BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom) manages Spring 6.2.19, Security 6.5.11, Spring Data 2025.0.13, Jackson 2.21.4, Tomcat 10.1.55, Netty 4.1.135.Final, Logback 1.5.34 and Micrometer 1.15.12. Boot alone does not resolve every match.

## Affected package groups and proposed patches

All proposed versions below were checked in public release metadata or the Boot BOM. Version changes remain subject to build, targeted behavior and integration testing; same-major releases do not guarantee compatibility.

| Affected coordinate/group | Installed | Initial matches | Proposed version/action | Main reachability considerations |
|---|---|---:|---|---|
| logback-core | 1.5.32 | 2 | 1.5.34 through Boot | Deserialization/network logging configuration prerequisites need review. |
| jackson-core | 2.21.2 | 3 | Jackson BOM 2.21.8 | JSON parsing is reachable; specific async/DataInput/parser helper prerequisites differ. |
| jackson-databind | 2.21.2 | 16 | Jackson BOM 2.21.8 | HTTP DTO deserialization is reachable. Some findings additionally require polymorphic typing, specific annotations, InetAddress/Path types or naming strategies. No demonstrated bypass claimed. |
| micrometer-core | 1.15.11 | 2 | 1.15.12 through Boot | HTTP instrumentation DoS is relevant to the web stack; gRPC instrumentation use was not established. |
| netty-codec | 4.1.132.Final | 2 | Netty BOM 4.1.139.Final | LZ4/BZip2 decoder findings require those handlers to consume attacker-controlled frames. The HTTP server is Tomcat. |
| netty-handler | 4.1.132.Final | 5 | Netty BOM 4.1.139.Final | TLS/SNI/trust-manager/IPv6-filter prerequisites need component-level review; Netty also enters through Redis dependencies. |
| commons-lang3 | 3.17.0 | 1 | 3.21.0 | ClassUtils uncontrolled recursion requires long/untrusted inputs to the affected helper. Fixed since 3.18.0. |
| log4j-api | 2.24.3 | 1 | Log4j BOM 2.26.1 | Non-finite floating-point MapMessage JSON encoding; presence of API alone does not demonstrate a deployed logging exploit. |
| tomcat-embed-core | 10.1.54 | 10 | Tomcat 10.1.61 | HTTP parsing/server paths are reachable; AJP, WebDAV, DIGEST, container FORM and LockOutRealm findings each have distinct configuration prerequisites. |
| batik-bridge | 1.14 | 4 | Remove unused SVG-support dependency, then verify absence | SSRF/untrusted-code advisories involve SVG processing. No BatikSVGDrawer/useSVGDrawer or SVG imports found in application source. |
| batik-script | 1.14 | 1 | Same removal | SVG-related information disclosure. |
| batik-transcoder | 1.14 | 1 | Same removal | SVG external-resource SSRF. |
| spring-boot-autoconfigure | 3.5.14 | 1 | Boot 3.5.16 | Predictable temporary directory in Artemis auto-configuration; Artemis prerequisite not established and not automatically exempted. |
| spring-data-commons | 3.5.11 | 4 | 3.5.13 through Data BOM | User-controlled Sort/property-path parsing and caches require scoped reachability analysis. |
| spring-data-keyvalue | 3.5.11 | 1 | 3.5.13 through Data BOM | SpEL injection requires applicable KeyValue repository sorting; no exploit claimed. |
| spring-security-web | 6.5.10 | 2 | 6.5.11 through Boot | X.509 and CookieRequestCache prerequisites differ from this application's session/bearer configuration. |
| spring-core | 6.2.18 | 1 | 6.2.19 through Boot | AntPathMatcher DoS depends on attacker-controlled matching inputs. |
| spring-expression | 6.2.18 | 3 | 6.2.19 through Boot | Untrusted SpEL expression evaluation is a prerequisite. |
| spring-web | 6.2.18 | 1 | 6.2.19 through Boot | UriComponentsBuilder SSRF requires affected URL parsing/validation usage. |
| spring-webmvc | 6.2.18 | 9 | 6.2.19 through Boot; **2 residual matches** | New fixes cover seven matches. See unresolved conditional findings below. |
| xalan | 2.7.2 | 1 | Verify removal with SVG dependency tree | Malicious XSLT processing prerequisite. If retained, 2.7.3 is the published fix. |

Latest reviewed same-line metadata: [Jackson 2.21.8](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/jackson-bom/maven-metadata.xml), [Netty 4.1.139.Final](https://repo.maven.apache.org/maven2/io/netty/netty-bom/maven-metadata.xml), [Commons Lang 3.21.0](https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/maven-metadata.xml), [Log4j 2.26.1](https://repo.maven.apache.org/maven2/org/apache/logging/log4j/log4j-bom/maven-metadata.xml), [Tomcat 10.1.61](https://repo.maven.apache.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/maven-metadata.xml).

The live proposed-coordinate query returned **2 matches across 21 coordinates**, versus the original 71. This predicts removal of 69 matches from those coordinates; it is not a post-build packaged scan. Removing unused SVG support may remove whole coordinates instead. Scan all remaining packaged dependencies again to detect newly introduced or previously unidentified components.

## Unresolved Spring MVC conditions

1. [GHSA-j9f9-w8pj-32f8 / CVE-2026-47890](https://spring.io/security/cve-2026-47890): Spring rates this **LOW**; SSE stream corruption requires rendering view fragments and attacker-controlled streamed data. Current `NotificationService` sends JSON maps through `SseEmitter`, not rendered view fragments; no matching fragment-rendering call was found. Spring lists 6.2.20 as fixed for Enterprise Support only and 7.0.9 as fixed for OSS. This is evidence about prerequisites, not approval to suppress the installed-version match.
2. [GHSA-pc63-qcmh-9cmg / CVE-2026-47884](https://spring.io/security/cve-2026-47884): Spring rates this **MEDIUM**; XsltView SSRF/RCE requires a wildcard `/**` view-rendering mapping and no explicit view name. No XsltView configuration/use was found in current source. Spring lists 6.2.20 as fixed for Enterprise Support only and 7.0.9 as fixed for OSS. Retain the match pending documented reachability review, an eligible enterprise patch, or a tested Spring Boot 4 / Framework 7 migration.

Do not force Spring 7/Boot 4 into this patch. They require an independently planned compatibility migration. OSS Spring 6.2.20 is not available per Spring's advisory; the 6.2.20 fix requires Enterprise Support. Choose that supported patch path or plan/test a Boot 4 / Framework 7 migration; do not represent the matches as resolved based only on source reachability.

## POI and PDF rendering

POI 5.5.1 is the latest published version in its [official Maven metadata](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/maven-metadata.xml), and it produced no match in the initial query. Current source creates XSSFWorkbook objects for export; no WorkbookFactory/OPCPackage untrusted import path was found in the scoped search. This does not establish absence of every parser risk.

OpenHTMLtoPDF 1.0.10 is the latest release under the current `com.openhtmltopdf` coordinates. Its published [PDFBox module POM](https://repo.maven.apache.org/maven2/com/openhtmltopdf/openhtmltopdf-pdfbox/1.0.10/openhtmltopdf-pdfbox-1.0.10.pom) imports PDFBox/XMPBox/core/graphics2d; its [SVG-support POM](https://repo.maven.apache.org/maven2/com/openhtmltopdf/openhtmltopdf-svg-support/1.0.10/openhtmltopdf-svg-support-1.0.10.pom) imports the Batik tree. CertificateService renders the fixed `point-certificate-pdf-template` with Thymeleaf-escaped text and no installed SVG drawer. Removing unused SVG support is narrower than replacing the rendering engine; verify the final tree and real certificate PDF output.

Packaged PDFBox/FontBox/XMPBox are 2.0.24 and had no OSV matches in this scan. Latest published same-major PDFBox is 2.0.37; moving to PDFBox 3.0.8 is an API migration and should not be imposed as an untested override on OpenHTMLtoPDF 1.0.10. A separately tested 2.x maintenance update is possible, but absence of an OSV match is not a freshness or security guarantee.

## Inventory gap and client advisories

`spring-boot-jarmode-tools-3.5.14.jar` lacked embedded Maven metadata. Identify it independently through the build/plugin coordinates and rescan the replacement artifact; do not count it as cleared automatically.

The separate npm audit reports 16 High package entries cascading from two advisories: [braces <=3.0.3](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and [node-forge <=1.4.0](https://github.com/advisories/GHSA-86w9-cpqp-85rv). Live official npm metadata still lists those affected versions as latest, and neither advisory lists a patched release. No guessed override, audit suppression or Expo 44 downgrade was applied. Expo signing helpers actually call node-forge signature-verification functions, so blanket harmless-tooling classification is unjustified.

## Required validation after remediation

Rebuild the JAR, inspect all resolved versions and ensure Batik/Xalan absence if SVG support is removed. Run backend tests, certificate rendering smoke, application startup and selected authenticated workflows. Rerun the entire public-coordinate audit and preserve artifact SHA-256, unknown inventory entries and unresolved conditions. Passing a metadata scanner does not establish exploit resistance or production acceptance.

## Completed local remediation evidence

The final executable JAR was rebuilt with Java 21 and the compatible versions described above. Full backend verification passed 502 tests; actual certificate rendering produced a parseable one-page PDF. The final OSV inventory contains 131 coordinates, two conditional Spring MVC matches and zero unidentified JARs. The Boot 3.5.16 packaging helper was identified by SHA-256 against its exact Maven artifact; Batik/Xalan are absent. Nine persisted browser workflows and eight real HTTP security checks passed against an isolated loopback instance. Exact artifact SHA, workload measurements and remaining release gates are recorded in [SECURITY_RUN_EVIDENCE_2026-10-19.md](SECURITY_RUN_EVIDENCE_2026-10-19.md). Earlier proposed remediation and initial inventory text above is historical context, not an additional unresolved inventory gap.

## Superseding current candidate — 19 October 2026

Earlier recommendations against Boot 4 were based on the then-current Boot 3 artifact and are historical. Current source has completed a Boot 4.1.1 / Spring Framework 7.0.9 migration with Jackson 2.21.8 / 3.1.8 and Tomcat 11.0.27. After cleanup of duplicate property declarations, full isolated verify passed 539/539; a fresh live audit of exact artifact `college-events/target/events-boot411-clean-20261019.jar` (SHA-256 `13B789F2D66989085E44020A3139FCF8A434A68921D958C795F7F9239369EA99`) checked 158 Maven coordinates with zero advisory matches and zero unidentified JARs. Details are in [SECURITY_PATCH_VERIFICATION_2026-10-19.md](SECURITY_PATCH_VERIFICATION_2026-10-19.md). The separate npm audit still has 16 High entries; do not combine the Java scan result with or treat it as closure of npm findings.

## Superseding universal-client audit refresh — 19 October 2026

The shared client now uses Expo SDK 58.0.15 / React Native 0.88.0 / React 19.3.0, reached through SDK 57 in accordance with Expo's incremental upgrade guidance. Final shared tests pass 140/140; typecheck, Expo Doctor 20/20, web export, Android Hermes export and responsive Playwright 119/1 documented skip also pass. Current `npm audit` is 15 High / 0 Critical entries cascading from two advisories: [braces GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and [node-forge GHSA-86w9-cpqp-85rv](https://github.com/advisories/GHSA-86w9-cpqp-85rv). Both official records currently list no patched versions. The audit-fix recommendation still offers incompatible major downgrades; no such downgrade, suppression or unreviewed override was applied. Do not report the client dependency gate as cleared. Exported bundles are not native signed artifacts/device acceptance.


## Post-update browser check and audit snapshot � 23 October 2026

After the compatible Expo patch update, Playwright reported 121 passed, 1 existing desktop-only skip and zero failures. The local audit snapshot and current lockfile hashes are recorded in launch progress and security verification appendices; no-patch High dependency findings remain a release gate.

