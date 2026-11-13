# ADR: Accepted-upload expiry and purge boundary

**Status:** Accepted for the first managed-media slice (3 October 2026)

Every newly accepted image upload records an object key, content type, size, SHA-256 digest, `uploadAcceptedAt` and a non-resettable `expiresAt = uploadAcceptedAt + 30 days`. Cleanup clamps configuration to the institutional 30-day maximum, ignores modification time, respects only explicit bounded legal holds and records physical purge state. Managed expired objects are denied before static resource handling; unknown legacy files remain available only pending a reviewed metadata migration.

This does not yet provide encrypted private object storage, owner/resource authorization, academic PDF/thumbnail/vector derivative tracking, backup purge proofs, quarantine malware scanning or an operator legal-hold workflow. Public legacy `/uploads` compatibility remains a production blocker.
