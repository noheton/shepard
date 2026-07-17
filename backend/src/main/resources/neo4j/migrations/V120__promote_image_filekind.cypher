// TIFF-PREVIEW-SUPPORT — backfill fileKind = "image" on existing singleton
// FileReferences whose filename indicates a raster-image format (including
// TIFF, the motivating case: MFFD tapelaying "TPS intermediate evaluation
// files" + stringer-welding camera frames).
//
// WHY: before this change no FileKindDetector claimed .png/.jpg/.../.tiff
// (mirrors the MP4-PROMOTE-VIDEO gap for video), so every pre-existing
// :SingletonFileReference carrying a raster image has fileKind = NULL and
// renders as an opaque file rather than the inline quick-look preview. New
// uploads are handled automatically by
// de.dlr.shepard.spi.payload.ImageFileKindDetector; this migration promotes
// already-ingested image references.
//
// ADDITIVE + REVERSIBLE: Neo4j is schema-less, so setting a nullable property
// needs no DDL. Only the metadata tag changes — no bytes, containers, or
// relationships are touched.
//
// FIELD NOTE: this migration keys on the Neo4j `.name` property of the
// :SingletonFileReference (the human-facing reference name), same caveat as
// V119 — a reference whose `.name` lacks the original extension is simply
// left NULL (harmless — re-tagged on next upload/edit).
//
// IDEMPOTENT: `WHERE ... fileKind IS NULL` guards against clobbering an
// already-set kind (e.g. a reference some other detector already tagged), so
// a re-run matches nothing (0 rows). `toLower(...)` makes the extension test
// case-insensitive.
//
// Operator runbook:
//   Verify before:
//     MATCH (r:SingletonFileReference)
//     WHERE r.fileKind IS NULL AND (
//       toLower(r.name) ENDS WITH '.png'  OR toLower(r.name) ENDS WITH '.jpg' OR
//       toLower(r.name) ENDS WITH '.jpeg' OR toLower(r.name) ENDS WITH '.gif' OR
//       toLower(r.name) ENDS WITH '.bmp'  OR toLower(r.name) ENDS WITH '.webp' OR
//       toLower(r.name) ENDS WITH '.tif'  OR toLower(r.name) ENDS WITH '.tiff')
//     RETURN count(r);
//   Re-run check (expect 0 after a successful migration):
//     MATCH (r:SingletonFileReference)
//     WHERE r.fileKind IS NULL AND toLower(r.name) ENDS WITH '.tiff'
//     RETURN count(r);
//   Run from a shell:
//     cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//       -f backend/src/main/resources/neo4j/migrations/V120__promote_image_filekind.cypher
//
// Rollback: V120_R__promote_image_filekind.cypher

MATCH (r:SingletonFileReference)
WHERE r.fileKind IS NULL AND (
  toLower(r.name) ENDS WITH '.png'  OR
  toLower(r.name) ENDS WITH '.jpg'  OR
  toLower(r.name) ENDS WITH '.jpeg' OR
  toLower(r.name) ENDS WITH '.gif'  OR
  toLower(r.name) ENDS WITH '.bmp'  OR
  toLower(r.name) ENDS WITH '.webp' OR
  toLower(r.name) ENDS WITH '.tif'  OR
  toLower(r.name) ENDS WITH '.tiff'
)
SET r.fileKind = 'image';
