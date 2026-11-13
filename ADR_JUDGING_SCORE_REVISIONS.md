# ADR: Immutable judging score revisions

## Decision

The current judge score remains a bounded projection for fast leaderboard reads. Every first submission and every later correction also writes an immutable `JudgeScoreRevision` snapshot in the same transaction, keyed by score and the previous score revision. Retries are resolved before mutation, and optimistic/pessimistic locking prevents two corrections from silently overwriting one another.

## Safety boundary

Revision history records what was submitted; it does not by itself approve publication, resolve conflicts of interest, implement appeals, or define tie-breaks. Participant/team binding and rubric configuration remain separate gates. Revision rows contain no raw credentials or unrelated student contact fields.
