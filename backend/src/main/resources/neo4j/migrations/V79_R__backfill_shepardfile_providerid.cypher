// NEO-AUDIT-002 — operator-runnable rollback for V79.
//
// Removes the `providerId` property from every :ShepardFile row that
// carries exactly the V79 default stamp 'gridfs'.  Rows that were already
// stamped 'gridfs' before V79 ran (the original 71 rows from V34) are
// also removed — this is intentional: after running this rollback the
// instance is back to the pre-V34/V79 state where providerId IS NULL for
// most rows, and the read-side NULL-coalesce in FileContainerService
// continues to route those reads to the GridFS adapter safely.
//
// **Not auto-run.** This file is named with the `_R` suffix that
// Neo4j-Migrations recognises as operator-run (mirrors V34_R).
// Run via cypher-shell if you need to revert V79 to install a
// pre-FS1a backend image:
//   cypher-shell -u neo4j -p <pwd> -f V79_R__backfill_shepardfile_providerid.cypher
//
// NOTE: rows stamped with 'gridfs' by V79 are indistinguishable from rows
// that arrived with 'gridfs' via the write-path stamp (post-FS1a).  This
// rollback removes both.  After rollback, restart with the older backend
// image; the read-side coalesce (FileContainerService.effectiveProviderId)
// covers the NULL rows transparently.
//
// DO NOT run this rollback if you have :ShepardFile rows with
// providerId = 's3' and those files genuinely live on S3 — the rollback
// does not touch 's3' rows, but reverting to a pre-FS1a image that does
// not know about s3 routing will cause reads of those rows to 404.

MATCH (sf:ShepardFile)
WHERE sf.providerId = 'gridfs'
REMOVE sf.providerId;
