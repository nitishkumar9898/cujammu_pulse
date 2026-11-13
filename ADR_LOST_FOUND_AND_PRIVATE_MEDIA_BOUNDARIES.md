# ADR: principal-bound lost/found claims and private media bridge

Date: 10 October 2026. Status: implemented local SQL bridge; not a MongoDB or production release certification.

Requirements: CF-14, CF-30, LOST-CLAIM-01, FILE-01, MEDIA-01. Data: item display text is internal; claim evidence and contact/identifier fields are confidential. Objects have an explicit authenticated owner and immutable acceptance/expiry.

## Decisions

Replace full-entity lost/found endpoints with validated DTO commands and bounded pages. A claimant is always the fresh authenticated principal; body names, roll numbers and phone numbers confer no ownership. Claims remain pending without reserving the item. Only its linked finder reviews private proof and records a verified handover. Generic ADMIN/STAFF roles do not acquire private claim authority. Office recovery needs separately provisioned scopes and remains outstanding.

Serialize claims/handovers on the item row, use optimistic item versions and actor/payload-bound request receipts. One successful handover closes competing claims as NOT_SELECTED. Bound a single review queue to 100 claims; this is an implementation capacity bound, not an institutional entitlement policy. Reason, evidence and contact content are excluded from audit and inbox notification templates. The audit and in-app notification intent commit with domain changes; external delivery is not claimed.

Replace `/uploads/**` static serving with a private image delivery gateway. Metadata and authenticated resource authorization are required; untracked/null-owner objects return unavailable without deletion. Expired, purged and legally held bytes are denied ordinary delivery. Holds preserve physical evidence under their separate reviewed custody; they do not extend ordinary access. Responses are no-store, image-only and nosniff. Recheck current ownership/resource/state and expiry after disk I/O.

New image uploads receive owner metadata, bounded actual byte/content/dimension validation and clean re-encoding. PNG/JPEG use installed decoders; any format with no safe decoder is rejected honestly. An explicit authorized event-banner binding permits sharing only the current canonical banner of an approved/completed ALL-audience event. Self-declared profile department is not an authorization source. Other assets remain owner-only; scoped department sharing needs trusted effective institutional membership.

## Migration and recovery

No historical bytes or records are deleted by this change. Existing textual CLAIMED/HANDED_OVER rows without verified claim linkage are displayed as LEGACY_REVIEW_REQUIRED. Do not infer ownership from names or roll strings. Review historical free text for identifying proof/contact content before publishing it broadly; DTO allowlisting alone cannot sanitize historic prose.

Schema additions: item version/request receipt/accepted-claim linkage, separate claim records, media owner/resource fields and verified institutional department binding. Numeric academic records no longer manufacture uploaded-file acceptance/expiry timestamps. Existing values are preserved. Before a running installation is updated, use an additive migration with backup, null/default/version inventory, duplicate-key/claim reconciliation and rollback; do not rely on `ddl-auto=update` for production migration. No migration was executed against historical local databases.

Legacy objects stay inaccessible until an office-reviewed ownership/resource mapping is supplied. Do not derive ownership from filesystem modification timestamps, filename alone or a guessed uploader. Existing legal holds without expiry need governance review; automatic deletion must not destroy unknown protected evidence. Backup/replica/derivative deletion proof remains unfinished.

## Verification

Independent negative service and media guard tests plus real H2 transaction tests cover foreign handover/self-claim, caller-scoped evidence, stale state, two concurrent handovers, retry receipts and rollback on audit failure. New HTTP tests exercise real Spring sessions/CSRF and private delivery. Current run results are recorded in IMPLEMENTATION_PROGRESS.md after integration. These do not establish Mongo replica-set, provider storage, malware sandbox or Android hardware acceptance.

Primary references: [OWASP upload guidance](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html), [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html). Target architecture remains the normative university specifications.
