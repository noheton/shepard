// FS1a — operator-runnable rollback for V34.
//
// Removes the `providerId` property from every `:ShepardFile` row
// that carries the FS1a default stamp `'gridfs'`. The FileStorageRegistry
// will fall back to the in-code default
// (`GridFsFileStorage.ID == "gridfs"`) for unstamped rows, so removing
// the stamp doesn't break GridFS reads — it merely reverts the schema
// surface.
//
// **Not auto-run.** This file lives under `neo4j/migrations/` for
// discoverability but is named with the `_R` suffix Neo4j-Migrations
// recognises as operator-run (mirrors V12_R + V14_R). An operator
// chooses to run this from `cypher-shell` if they need to dial FS1a
// back; the next backend start re-applies V34 if `providerId IS NULL`
// on any row.
//
// Use case: an operator wants to revert to a pre-FS1a backend image
// without dropping the database. After running this rollback the
// older image can come up cleanly (no `providerId` property exists,
// the old image's OGM ignores it).

MATCH (f:ShepardFile)
WHERE f.providerId = 'gridfs'
REMOVE f.providerId;
