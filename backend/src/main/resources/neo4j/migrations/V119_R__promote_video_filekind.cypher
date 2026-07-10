// V119_R — Rollback for V119__promote_video_filekind.cypher.
//
// Removes the fileKind = "video" tag from :SingletonFileReference nodes whose
// name indicates a video container format. Scoped to fileKind = 'video' AND a
// video-extension name so it only reverts what V119 set — a FileReference that
// legitimately carries some other fileKind is never touched.
//
// WARNING: on an installation that has RUN NEW VIDEO UPLOADS since V119, those
// uploads were tagged fileKind = 'video' by VideoFileKindDetector at upload
// time (not by this migration). This rollback will also clear those — it cannot
// distinguish "set by V119" from "set by a later upload". Only fully safe on a
// freshly migrated install. After rollback, re-uploading or editing the
// reference re-derives the kind.
//
// To run from a shell:
//   cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//     -f backend/src/main/resources/neo4j/migrations/V119_R__promote_video_filekind.cypher
//
// Verify after (expect 0):
//   MATCH (r:SingletonFileReference)
//   WHERE r.fileKind = 'video' AND toLower(r.name) ENDS WITH '.mp4'
//   RETURN count(r);

MATCH (r:SingletonFileReference)
WHERE r.fileKind = 'video' AND (
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
REMOVE r.fileKind;
