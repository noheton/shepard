// V2 — NOOP placeholder for chain-order validation.
//
// This migration was applied long ago but the original file was removed from
// the repo during refactoring. The neo4j-migrations chain validator requires
// every applied DB migration to also exist on disk at the same index, even if
// the file is a NOOP. Adding this placeholder so the chain aligns and newer
// migrations (V99+, V100+) can apply.
//
// Surfaced 2026-05-31 when V99 + V100 deploy failed with
//   'Unexpected migration at index 97: 99'
// because V2 + V23 + V62 were missing from disk.

RETURN 1 AS noop;
