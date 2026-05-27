// PROMPT-h2: backfill all existing Collections that have no promptLogMode set.
//
// Safe to re-run — the `WHERE c.promptLogMode IS NULL` guard prevents
// double-writes; a re-run on an already-migrated graph is a no-op.
//
// Effective default: HASH_ONLY — the most conservative posture; only a
// SHA-256 hash of AI conversation bodies is stored, never the raw text.
// Operators who want analytics (BODY_REDACTED) or full GPAI traceability
// (BODY_RAW) must explicitly set the mode via PATCH /v2/collections/{appId}.
//
// Operator runbook:
//   Verify before: MATCH (c:Collection) WHERE c.promptLogMode IS NULL RETURN count(c);
//   Re-run check:  MATCH (c:Collection) WHERE c.promptLogMode IS NULL RETURN count(c);
//                  -- expect 0 after a successful migration.
//
// See: aidocs/semantics/99-promptlog-design.md §10-11 (ESCALATION-PROMPT-2).
// Rollback: V91_R__Rollback_Set_default_promptLogMode_on_collections.cypher

MATCH (c:Collection)
WHERE c.promptLogMode IS NULL
SET c.promptLogMode = 'HASH_ONLY';
