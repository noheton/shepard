// V120_R — Rollback for V120__promote_image_filekind.cypher.
//
// Removes the fileKind = "image" tag from :SingletonFileReference nodes whose
// name indicates a raster-image format. Scoped to fileKind = 'image' AND an
// image-extension name so it only reverts what V120 set — a FileReference
// that legitimately carries some other fileKind is never touched.
//
// WARNING: on an installation that has RECEIVED NEW IMAGE UPLOADS since V120,
// those uploads were tagged fileKind = 'image' by ImageFileKindDetector at
// upload time (not by this migration). This rollback will also clear those —
// it cannot distinguish "set by V120" from "set by a later upload". Only
// fully safe on a freshly migrated install. After rollback, re-uploading or
// editing the reference re-derives the kind.
//
// To run from a shell:
//   cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//     -f backend/src/main/resources/neo4j/migrations/V120_R__promote_image_filekind.cypher
//
// Verify after (expect 0):
//   MATCH (r:SingletonFileReference)
//   WHERE r.fileKind = 'image' AND toLower(r.name) ENDS WITH '.tiff'
//   RETURN count(r);

MATCH (r:SingletonFileReference)
WHERE r.fileKind = 'image' AND (
  toLower(r.name) ENDS WITH '.png'  OR
  toLower(r.name) ENDS WITH '.jpg'  OR
  toLower(r.name) ENDS WITH '.jpeg' OR
  toLower(r.name) ENDS WITH '.gif'  OR
  toLower(r.name) ENDS WITH '.bmp'  OR
  toLower(r.name) ENDS WITH '.webp' OR
  toLower(r.name) ENDS WITH '.tif'  OR
  toLower(r.name) ENDS WITH '.tiff'
)
REMOVE r.fileKind;
