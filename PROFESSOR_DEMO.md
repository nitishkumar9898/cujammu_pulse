# Local professor review

This local milestone needs no purchased domain. It uses browser session login and an explicitly isolated synthetic H2 memory database. It is for review of working workflows and remaining gaps, not proof of university SSO, physical attendance or production readiness.

From the project root, run:

```powershell
rtk proxy powershell -NoProfile -File .\scripts\Start-ProfessorDemo.ps1
```

The helper selects a full Java 21 JDK from `PULSE_JDK21_HOME`, then `JAVA_HOME`, then the known Android Studio JBR locations; it checks the JDK version before building. If your JDK 21 is elsewhere, set `$env:PULSE_JDK21_HOME` to its installation folder for that PowerShell session. It uses Maven 3.9.9, builds/tests backend and shared web, generates an ephemeral demo-only password, then starts a hidden loopback-only server at port 18080. Choose another port with `-Port 18081` if occupied. `-SkipBuild` requires current verified artifacts and refuses a stale backend JAR.

Open the reported `/app/` URL. Account names and password are in the ignored generated `college-events/target/professor-demo/session-credentials.json`; they are not printed in logs or committed. Do not reuse the demo password elsewhere. Available accounts: student, finder, faculty publisher, independent reviewer, administrator and warden, all under `example.invalid` and visibly Synthetic names.

The professor-demo environment guard runs before datasource/schema creation. It rejects file/remote databases, alternate pool/JNDI/ORM connections, SQL initialization overrides, external mail/AI/identity activation, non-loopback binding, unexpected write paths and production profile mixing. Ordinary campus/bootstrap seeds are excluded. No existing `data/eventhub.mv.db`, uploads or logs are read, migrated or deleted. Restart loses the memory database and creates fresh synthetic fixtures; generated demo output remains under its dedicated target directory.

The startup helper selects a short JVM-only Unix socket directory at `C:\tools\pulse-jvm-sockets` to avoid the observed Windows TEMP/PipeImpl connection failure. It does not change firewall, antivirus or global JVM settings. See [Oracle Java 21 socket-directory guidance](https://docs.oracle.com/en/java/javase/21/core/java-networking.html).

To stop the matching demo and start fresh fixtures, run `rtk proxy powershell -NoProfile -File .\scripts\Stop-ProfessorDemo.ps1`, then the start command. The stop helper verifies the recorded JAR path and professor-demo process arguments; it does not kill an arbitrary process occupying the port or delete generated/history files.

## Suggested review journeys

1. Student: sign in → browse synthetic workshop → register → inspect persisted registration/inbox/calendar → confirm cancellation → reload. Compare empty, denied and retry states.
2. Finder: report a found item with category and harmless public description. Student: submit confidential proof. Finder: review proof and confirm handover. Another account must not see another claimant's evidence or hand over the item.
3. Faculty: inspect scoped event operations/readiness, configure supported attendance/certificate policy before registrations and create a volunteer task. Students: recruitment/application and task evidence. Unwired assignment/provider/device states stay explicit.
4. Faculty academic publisher: submit numeric synthetic scorecard or bounded batch for verified ID `000001`. Independent reviewer: review/publish. Student: own approved scorecards only. Generic admin cannot publish the scorecard; no official PDF is fabricated.
5. Admin: review runtime controls and safe reasons/revisions. Student: disabled ordinary features disappear; protected existing-session recovery remains a separate UNVERIFIED action. No library QR visit is fabricated to demonstrate successful scanning.
6. Student: helpdesk intake/thread and community membership. Server night-silence still applies at real campus time; changing the phone clock is not a workaround.

Only authenticated server-confirmed receipts count as workflow success. Test fixtures cannot prove campus presence. Attendance camera/credentials, complete library challenge/report jobs, private academic files, all remaining module operations and provider delivery must be completed and verified before presenting them as working features.

## Still open after this review

The live verifier is `cujammu-pulse/scripts/live-demo-check.mjs`. It requires an explicit loopback URL matching the generated synthetic credentials and changes only synthetic records. Its ignored JSON report records the real browser/server results, separate from mock acceptance tests. A fresh restart is required for the initial registration journey; inspect the report outcome before claiming these journeys passed. Current integrated evidence: [local review status](LOCAL_REVIEW_STATUS_2026-10-10.md).

Latest fresh run (2026-10-19): **9/9 live scenarios passed** after correcting an asynchronous email/transaction race exposed by cancellation. The running demo now contains synthetic cancelled registration, reviewed handover and published numeric scorecard. Restart via the helpers above before showing the professor a fresh end-to-end registration journey. The Windows launcher now verifies JDK 21 from the JDK release metadata, avoiding PowerShell treating java -version stderr as a terminating error. A passing review run does not prove unsupported menu operations or physical QR presence.

See `LAUNCH_READINESS_AUDIT_2026-10-10.md` and the corrected current section of `IMPLEMENTATION_PROGRESS.md`. Target Mongo implementation/migration, full module/action parity, actual Android camera/file/push/SSO, all-copy media purge/restore, durable external delivery, independent deployed assessment, institutional policies/UAT and production approval remain open. The current npm audit includes unpatched High dependency advisories; a professor demonstration is not a security release exception.
