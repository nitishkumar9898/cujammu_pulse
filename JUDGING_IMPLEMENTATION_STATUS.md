# Judging and leaderboard implementation status

Implemented: explicit event judge assignments, approved-event and feature checks, four criteria each bounded to 25, versioned rubric marker, optimistic revision/pessimistic row lock, UUID request key plus payload hash, DTO-only scores/leaderboards, and immutable submission/correction snapshots in `judge_score_revisions`.

Verification: `EventJudgingRevisionTest` passes; Maven compile and the complete Maven suite exited 0 after this integration. No dedicated judging concurrency/security test exists yet.

Remaining: canonical participant/team binding, immutable append-only score/correction history, rubric configuration and publication, conflict-of-interest checks, appeal/reopen workflow, deterministic tie-break policy, durable notifications, web/native parity and independent security/concurrency evidence.
