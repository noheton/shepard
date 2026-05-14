// FS1a — backfill `providerId` on every existing `:ShepardFile` row.
//
// Operator runbook
// ----------------
// All pre-FS1a file payloads live in MongoDB GridFS via FileService
// (de.dlr.shepard.data.file.services.FileService). The new FileStorage
// SPI (aidocs/45 §3.2) routes by the per-row `providerId`; pre-FS1a
// rows have no such property. This migration stamps every existing
// `:ShepardFile` node with `providerId = 'gridfs'` so the
// FileStorageRegistry dispatches correctly for legacy reads.
//
// Idempotent
// ----------
// Re-running is a no-op: `WHERE f.providerId IS NULL` filters out
// already-stamped rows. Safe to re-run from `cypher-shell` against a
// healthy cluster.
//
// Failure shape
// -------------
// Fail-fast: the MigrationsRunner (post-A1e per CLAUDE.md "always
// maintain the upstream upgrade path" rule 3) propagates any error
// from this script. If the stamp fails partway, restart re-runs the
// script and finishes the remaining rows — no rollback needed.
// Operator-runnable rollback ships at V34_R for symmetry with V12_R
// + V14_R.
//
// Cost
// ----
// Single `MATCH … WHERE … SET` over the `:ShepardFile` label. On a
// 1M-row file deployment expect single-digit-seconds on NVMe. The
// label is bounded (one row per uploaded file) so the cost is
// linear in the file count, not the entire graph.

MATCH (f:ShepardFile)
WHERE f.providerId IS NULL
SET f.providerId = 'gridfs';
