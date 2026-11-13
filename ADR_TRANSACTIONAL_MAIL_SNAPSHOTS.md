# ADR: immutable mail snapshots at the transaction boundary

Date: 10 October 2026. Applies to legacy JPA event/registration mail during the bridge migration.

The real local browser test exposed intermittent registration withdrawal failure. The asynchronous cancellation renderer received a managed Registration and traversed its lazy Event/User references while the request transaction used the same Hibernate state. The server reported `Illegal pop() with non-matching JdbcValuesSourceProcessingState`; the withdrawal did not persist. Isolated mocked browser tests had not exercised this interaction.

All entity-taking email methods now render recipient, subject and HTML synchronously on the owning transaction thread. They publish an immutable strings-only snapshot; an asynchronous AFTER_COMMIT transactional event listener handles delivery. A rollback discards the snapshot. Transactionless callers retain explicit fallback execution. Event lifecycle callers publish snapshots before commit instead of nesting publication inside an existing after-commit callback. The worker receives no entity, lazy proxy, persistence context or mutable audience collection.

Mail HTML escapes user-controlled names, event fields and announcement content. The legacy attendance template no longer equates a status update with verified duration or an issued certificate. Logs do not claim provider delivery from snapshot preparation.

This is race containment, not durable notification delivery. In-memory events can be lost on process failure after commit; executor saturation and provider retry/reconciliation need the specified transactional outbox. Console demo delivery is suppressed and does not prove SMTP delivery. No external effect is inserted into retried domain transactions. Independent proxy-based rollback/commit/immutability tests and a fresh real browser persistence run are required before closing this regression.
