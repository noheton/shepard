// MP4-PROMOTE-VIDEO — backfill fileKind = "video" on existing singleton
// FileReferences whose filename indicates a video container format.
//
// WHY: before this change no FileKindDetector claimed .mp4 (and friends), so
// every pre-existing :SingletonFileReference carrying a video file has
// fileKind = NULL and the UI renders it as a plain file rather than an inline
// player. New uploads are handled automatically by
// de.dlr.shepard.spi.payload.VideoFileKindDetector; this migration promotes the
// ~178 already-ingested MP4 references (nuclide live-box estimate).
//
// ADDITIVE + REVERSIBLE: Neo4j is schema-less, so setting a nullable property
// needs no DDL. Only the metadata tag changes — no bytes, containers, or
// relationships are touched.
//
// FIELD NOTE: this migration keys on the Neo4j `.name` property of the
// :SingletonFileReference (the human-facing reference name). The runtime
// detector keys on the uploaded file's original filename (stored in
// Mongo/GridFS, invisible to Cypher). In practice reference names carry the
// original extension (e.g. "P01_2.Bahn.MP4"), so `.name` is the pragmatic
// match; a video ref whose `.name` lacks the extension is simply left NULL
// (harmless — it can be re-tagged via a future upload/edit).
//
// IDEMPOTENT: `WHERE ... fileKind IS NULL` guards against clobbering an
// already-set kind, so a re-run matches nothing (0 rows). `toLower(...)` makes
// the extension test case-insensitive so uppercase `.MP4` (the flagship
// P01_2.Bahn.MP4) is caught.
//
// Operator runbook:
//   Verify before:
//     MATCH (r:SingletonFileReference)
//     WHERE r.fileKind IS NULL AND (
//       toLower(r.name) ENDS WITH '.mp4'  OR toLower(r.name) ENDS WITH '.mov' OR
//       toLower(r.name) ENDS WITH '.m4v'  OR toLower(r.name) ENDS WITH '.avi' OR
//       toLower(r.name) ENDS WITH '.mkv'  OR toLower(r.name) ENDS WITH '.webm' OR
//       toLower(r.name) ENDS WITH '.mpg'  OR toLower(r.name) ENDS WITH '.mpeg' OR
//       toLower(r.name) ENDS WITH '.wmv')
//     RETURN count(r);
//   Re-run check (expect 0 after a successful migration):
//     MATCH (r:SingletonFileReference)
//     WHERE r.fileKind IS NULL AND toLower(r.name) ENDS WITH '.mp4'
//     RETURN count(r);
//   Run from a shell:
//     cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//       -f backend/src/main/resources/neo4j/migrations/V119__promote_video_filekind.cypher
//
// Rollback: V119_R__promote_video_filekind.cypher

MATCH (r:SingletonFileReference)
WHERE r.fileKind IS NULL AND (
  toLower(r.name) ENDS WITH '.mp4'  OR
  toLower(r.name) ENDS WITH '.mov'  OR
  toLower(r.name) ENDS WITH '.m4v'  OR
  toLower(r.name) ENDS WITH '.avi'  OR
  toLower(r.name) ENDS WITH '.mkv'  OR
  toLower(r.name) ENDS WITH '.webm' OR
  toLower(r.name) ENDS WITH '.mpg'  OR
  toLower(r.name) ENDS WITH '.mpeg' OR
  toLower(r.name) ENDS WITH '.wmv'
)
SET r.fileKind = 'video';
