# Teams and chat continuation evidence

Date: 3 October 2026. Requirements: CF-09, CF-10; shared controls CF-29. Status: partial implementation with verified repairs, not module acceptance or production approval.

Subsequent continuation: [explicit chat membership status](CHAT_MEMBERSHIP_IMPLEMENTATION_STATUS.md) supersedes this snapshot's department-access and directory-paging limitations. The current full backend/client/browser totals are 234/55/26; the results below describe the earlier containment slice.

## Implemented behavior

- Team create/join/lock commands use feature-row controls and a common event-operation lock. Competing joins into different teams contend on that same row. Registration cancellation uses an owner-scoped scalar event lookup before acquiring the guard and loading the registration, avoiding a pre-lock stale entity.
- A registration with existing team membership cannot be cancelled through the ordinary cancellation path. A reviewed withdrawal/removal workflow is still required; the message does not imply such an organizer screen already exists.
- Membership changes advance the persisted team revision; duplicate same-team joins preserve membership/count. A stale lock request fails. Leader lock is one-way; requests to reopen are rejected. Returned revisions are read after flush.
- Team join commands require the selected `expectedEventId`. A valid invitation for another event is rejected without membership creation. Campus eligibility uses Asia/Kolkata consistently for event start and registration deadline.
- The private `/teams/mine?page=N` API returns bounded, principal-scoped history independently of currently joinable events. Shared web/Android source shows history, page navigation and roster details after registration closes. Only the leader receives their invitation code. Event managers keep a separate scoped endpoint.
- Chat no longer trusts self-declared profile department for membership. Department groups are unavailable until independently verified assignment and revocation exist. No records were removed to enforce this restriction.
- Legacy chat cleanup rejects requests after checking manager authority; it cannot destroy active, permanent or held records while hold-aware purge is missing.
- Event-owner/admin moderation is scoped; an unrelated faculty/organizer participant cannot bypass admin-only posting. Night Silence applies to all roles over `[22:00,07:00)` Asia/Kolkata. There is no unapproved emergency override.
- Cancelled/completed event chats remain authorized read-only history. New messages require an approved event and the shared lifecycle guard. Service reads/writes enforce module controls; chat responses use `no-store`.
- Shared chat clears private content on group changes and access errors, and ignores late reads/send receipts belonging to a previous thread. Attachments remain unavailable pending CF-30 storage.

## Executed verification

| Check | Result | What it establishes |
|---|---|---|
| Full Maven test suite | 226 passed; 0 failures/errors/skips | Existing Java sources/tests compile; synthetic backend scenarios pass |
| TeamWorkflowConcurrencyTest | 7 passed, included above | Real ephemeral H2 cross-team join race, cancellation-versus-join, capacity/retry/revision, private history and selected-event binding |
| CommunityChatPolicyTest | 21 passed, included above | Independently authored mocked-policy checks for membership, silence boundaries, scoped moderator rights, feature OFF, cleanup denial and terminal-event posting |
| Client `npm test` | 48 passed | Bounded decoders, transport and action contracts |
| Client `npm run typecheck` | Passed | Shared TypeScript compile checking |
| Client `npm run build:web` | Passed | Expo web export; not native APK verification |
| Client `npm run test:web` | 20 passed | Edge desktop/small viewport workflows with synthetic mocked APIs, including team history/feature disable and chat stale-response clearing |

Maven 3.9.9 was found at `C:\tools\apache-maven-3.9.9\bin\mvn.cmd`. Earlier ledger statements that Maven was unavailable were a PATH discovery problem. JDK 25.0.2 ran the tests with Java release 21 compilation. JDK/Mockito dynamic-agent, legacy API, Commons Logging and configuration warnings remain visible and require toolchain/dependency cleanup; passing tests do not erase them.

The final full backend run used isolated settings:

```powershell
rtk proxy C:\tools\apache-maven-3.9.9\bin\mvn.cmd -q "-Dspring.datasource.url=jdbc:h2:mem:verification;DB_CLOSE_DELAY=-1" "-Dapp.upload.dir=target/test-uploads/verification" "-Dlogging.file.name=target/test-logs/verification.log" "-Dapp.media.cleanup-cron=-" "-Dapp.email.backend=console" test
```

Run from `college-events`. Test-specific in-memory databases and target-only output paths remain in effect. No real university data, production services, physical uploaded files or workspace database were used by this verification. The first suite runs exposed stale fixture/signature assertions and missing slice dependencies; those were repaired, with privacy/concurrency assertions retained. Chatbot tests now require the truthful unavailable-secure-pass response instead of advertising the retired bearer QR screen. The foundation browser test now recognizes the already implemented hostel request screen.

## Outstanding acceptance gates

- Teams: invitation expiry/rotation/hash storage, targeted brute-force limits, approved event-owned size policy, reviewed withdrawal/reopen exceptions, durable notifications/outbox, schema migration and production database proofs. Six-character legacy invitation storage has not been promoted to a production-ready credential design.
- Chat: verified department provisioning, scoped moderator grants/revocation, paginated/scoped group repository query (current unbounded directory/N+1 remains an availability concern), realtime subscriptions, rate limits/moderation workflow, idempotent posting, lifecycle provisioning, protected holds and resumable purge. No cleanup success is claimed.
- Client parity: shared source and small browser viewport testing do not establish native sign-in, camera, physical-device or installed-client compatibility. Native identity remains incomplete.
- Architecture: MongoDB replica-set migration, owner-confirmed QR credentials, private 30-day media lifecycle, complete function-level controls, remaining modules and release gates in `IMPLEMENTATION_PROGRESS.md` remain open.
- Security: independent assessment, dependency remediation/SBOM, existing-data migration, office UAT, load/restore/incident evidence and university sign-off are still required. No zero-vulnerability or all-phases-complete claim is made.

Independent reviewers examined the team guard/ownership changes and chat containment. Their unresolved findings above remain tracked rather than being relabelled complete.
