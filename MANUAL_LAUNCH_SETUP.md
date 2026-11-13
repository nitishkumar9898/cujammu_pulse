# Manual setup and launch handoff

Updated 26 October 2026. Hosting/server and approved domain are not yet available, as confirmed by the owner. Local implementation can continue without purchasing either. University authorization has been reported; remaining acceptance evidence must identify the actual candidate and deployment.

## Use the current local review

1. Open http://127.0.0.1:18089/app/ on this PC.
2. Open `college-events/target/professor-demo-assigned-review-1026/session-credentials.json` locally in your editor. Use its synthetic account and generated password. Do not paste the password into chat, documentation or source control.
3. Automated review has submitted a synthetic event and verified explicit admin assignment, approval and catalog visibility. For original fresh-data journeys use the matching stop/start commands below. Restart resets only this demo's in-memory data.
4. Follow `PROFESSOR_DEMO.md` for calendar, registration, private claims and independent academic review. Actual library/camera presence and official PDF storage remain incomplete; do not present them as verified.

From the project root, only when fresh demo fixtures are wanted:

```powershell
rtk proxy powershell -NoProfile -File .\scripts\Stop-ProfessorDemo.ps1 -OutputName professor-demo-assigned-review-1026
rtk proxy powershell -NoProfile -File .\scripts\Start-ProfessorDemo.ps1 -Port 18089 -SkipBuild -OutputName professor-demo-assigned-review-1026 -ArtifactName events-assigned-review-1026.jar -ClientDistPath "C:\Users\NITISH KUMAR\OneDrive\Desktop\Proect Folder\college-events-project\cujammu-pulse\dist-proposal-review-final-1026"
```

If source has changed, the launcher may reject an old artifact; rebuild and verify before using it. No global Java, firewall or antivirus change is needed for this currently working local review.

## Repeat local client verification

- Android build and unit tests: from the project root run `.\scripts\Build-Android.ps1`. It temporarily configures a short JDK socket temp path for Gradle on affected Windows hosts and restores the prior environment afterward. Latest local result: debug APK built, 4 unit tests passed. A connected device/emulator is still required for camera, sign-in, protected storage and lifecycle acceptance.
- Mongo foundation test: start a disposable local MongoDB replica set named `pulseFoundation`, listening only on `127.0.0.1:27027`, then run `.\scripts\Test-MongoFoundation.ps1 -Port 27027`. The suite provisions a random synthetic database prefix and drops only databases with that prefix. Never point it at a production or data-bearing Mongo instance.

Student verification demo: sign in as the synthetic admin, open Administration > Student verification, and look up `applicant@example.invalid`. After a fresh restart it is unbound: select Synthetic Engineering, enter synthetic identifier `000003` and a synthetic source/reason, confirm the review, then save. Reload and look up again to verify persistence. The automated walkthrough already completes this example; the existing binding cannot be overwritten. Sign in as the synthetic applicant to view the private verification notice.

Proposal demo on the same local candidate: sign in as synthetic faculty and open `/organizer/events/create` to submit a future synthetic event. In the admin app, open Administration > Event proposal review; explicitly assign an active admin, review the submitted fields and confirm before approving or rejecting. The automated walkthrough has already submitted and approved one synthetic example. Approval currently makes the event public and queues announcements; separate publication and operational readiness checks remain open. See `EVENT_PROPOSAL_IMPLEMENTATION_STATUS.md`.

## Arrange these with university IT

1. **Hosting ownership:** select a university-owned staging server/cloud project, administrator, budget and region. Share its name and runbook location, not credentials. An approved university subdomain can be used; a personally purchased domain is not a prerequisite.
2. **DNS and HTTPS:** assign the staging hostname and DNS owner. Exact DNS/TLS commands depend on the chosen target. Do not expose the localhost demo or point public DNS at it.
3. **Identity:** identify the approved IdP, issuer, API audience, client registrations, redirect URIs and privileged MFA owner. Secrets belong in the approved secret manager. `INSTITUTIONAL_IDENTITY_ADAPTER.md` describes the experimental JWT adapter; complete web/native SSO remains pending.
4. **Storage and delivery:** provide approved Mongo replica-set, private object storage, backup and mail service ownership/configuration references. A connection string alone cannot replace pending Mongo adapters/migration or retention verification.
5. **Android:** provide an approved test handset or emulator with synthetic accounts. Use USB debugging only on a test device. Camera, permissions, protected storage, interrupted network, upgrades and sign-in need device verification; release keys need university-controlled custody.
6. **Office review:** nominate separate academic submitter/reviewer, library, hostel, event, DSW/support and IT owners. Record approved policies and actual scenario results; keep real student records out of synthetic tests.
7. **Security/operations:** arrange an authorized staging assessment/retest, incident responder, alert receipt test and backup/restore exercise. Bind reviewed results to the candidate using `PRODUCTION_RELEASE_GATES.md`.

Code migration, vulnerability remediation and verification remain engineering work. Do not disable safeguards or fabricate evidence to unblock a release. Public launch requires passing software, native/device and operational gates; infrastructure setup alone is insufficient.
