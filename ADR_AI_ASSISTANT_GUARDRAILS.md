# ADR: Grounded campus assistant guardrails

**Status:** Accepted for the first containment slice (3 October 2026)

The assistant now bounds and normalizes user input, rejects common prompt-injection attempts, refuses credential/private-data requests, sanitizes event fields before placing them in retrieval context, and caps model output. The rule-based fallback remains available when the provider is unavailable.

This is not a complete AI safety approval. Production still needs an ACL-aware retrieval layer, authenticated scoped tool authorization, provider data-handling review, abuse rate limits, prompt/response redaction, multilingual injection evaluation, model/version pinning, audit telemetry and human escalation. The assistant must never make academic, disciplinary, attendance or access decisions.
