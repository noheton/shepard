// INTENTIONALLY EMPTY — no schema change required.
//
// FS1e3 ships four optional nullable properties on `:ShepardFile`:
//   - previousProviderId (String, nullable)
//   - previousLocator    (String, nullable)
//   - migratedAt         (Instant, nullable)
//   - migrationHmac      (String, nullable)
//
// Neo4j is schema-less, so additive nullable properties need no
// migration: existing `:ShepardFile` nodes simply lack the property and
// Spring Data Neo4j OGM reads the absence as `null`. The fields are
// populated by `FileMigrationService.migrateOne()` in a single Cypher
// SET that stamps the previous{ProviderId,Locator} BEFORE swapping
// providerId, so a partial-failure mid-statement is impossible.
//
// Operator runbook: no action required.
//   - Existing `:ShepardFile` rows continue to work unchanged.
//   - Pre-FS1e3 rows that were never migrated stay with all four
//     fields = null.
//   - Rows touched by a future migration get the four fields stamped
//     atomically with the providerId swap.
//   - `POST /v2/admin/files/migrate/rollback/{appId}` refuses to roll
//     back a row whose previousProviderId is null (nothing to revert).
//
// Idempotency: this migration is empty and re-running is a no-op.
//   Fail-fast: an empty migration cannot fail.
//
// Per `CLAUDE.md` — admin-facing change documented in
// `aidocs/34-upstream-upgrade-path.md` row "FS1e3a — per-file rollback
// fields on :ShepardFile".
