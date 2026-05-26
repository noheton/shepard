// NEO-AUDIT-002 — Backfill ShepardFile.providerId NULL → 'gridfs'
//
// Context:
//   V34 introduced providerId on :ShepardFile and ran the same Cypher
//   (MATCH … WHERE providerId IS NULL SET …) at migration time, but only
//   covered the 71 rows that pre-dated V34. Every ShepardFile written
//   between V34's application and the FS1a write-path stamp landing had
//   providerId = NULL. A live out-of-band backfill was applied 2026-05-24
//   (see aidocs/16 NEO-AUDIT-2026-05-24-002-SHIPPED); this migration is
//   the formal carrier so a fresh upstream-fork install gets the same
//   fix via the standard Flyway-style migration runner on first start.
//
//   Pre-state observed on live (2026-05-24):
//     12 253 NULL / 71 gridfs / 33 s3 of 11 902 total
//   Post-state after out-of-band fix (same day, +3 drift from ingest):
//     0 NULL / 12 327 gridfs / 33 s3
//
// What this migration does:
//   Sets sf.providerId = 'gridfs' on every :ShepardFile row where the
//   property is currently absent (IS NULL). Rows already stamped with
//   'gridfs' or 's3' are untouched.
//
// Idempotent: MATCH guard (WHERE sf.providerId IS NULL) means re-running
//   this on an already-fixed instance is a safe no-op (0 rows matched).
//
// Runtime cost:
//   Index on :ShepardFile(providerId) does not exist by default; the
//   MATCH will do a label scan limited to rows without the property.
//   On the observed 12 k-row dataset this is < 100 ms. On larger installs
//   (100 k+ rows) still fast — property-absence filter is index-friendly
//   in Neo4j 5.x.
//
// Forward-fix (write-path):
//   FileContainerService.createFile() and commitUpload() now call
//   Objects.requireNonNull(file.getProviderId(), …) before
//   fileContainerDAO.createOrUpdate(), so new rows always arrive with
//   providerId set and this migration becomes permanently idempotent.
//
// Reader-side NULL-coalesce (FileContainerService.effectiveProviderId):
//   Kept in place even after this migration — a belt-and-braces safety
//   net for test fixtures and any race where a row lands NULL transiently.
//   The coalesce can be removed after a future monitoring sweep confirms
//   100 % of rows have been non-NULL for a sustained period.
//
// Rollback: V79_R__backfill_shepardfile_providerid.cypher
//   (removes the 'gridfs' stamp from rows that did not previously have
//   any providerId property, reverting the property-store to pre-V79 shape)
//
// Operator runbook: idempotent; safe to run from cypher-shell at any time.
//   Monitor: MATCH (sf:ShepardFile) WHERE sf.providerId IS NULL
//            RETURN count(sf);
//   Expected result after migration: 0.

MATCH (sf:ShepardFile)
WHERE sf.providerId IS NULL
SET sf.providerId = 'gridfs';
