// =============================================================================
// audit-singleton-file-bundles.cypher
//
// Identifies :FileBundleReference nodes (Neo4j label :FileReference, Java class
// FileBundleReference) that hold exactly one ShepardFile — i.e. single-file
// bundles that should be migrated to the FR1b singleton shape
// (:SingletonFileReference, /v2/files/).
//
// Background: CLAUDE.md §"Always: singleton FileReference for one-file uploads"
// Migration driver: V23__Split_singleton_bundles_to_FileReferences.java
//                   (opt-in, guarded by shepard.migration.split-singletons.enabled)
// Backlog row: SINGLETON-FILE-01-AUDIT / SINGLETON-FILE-MIGRATION arc
//
// Run in cypher-shell:
//   cypher-shell -u neo4j -p <password> < scripts/audit-singleton-file-bundles.cypher
//
// Or copy-paste individual queries into Neo4j Browser / Bloom.
//
// Operator runbook pointer: aidocs/34-upstream-upgrade-path.md §FR1b
// =============================================================================


// ---------------------------------------------------------------------------
// Query 1: Global count of single-file FileBundleReferences
//
// :FileReference is the Neo4j label used by BOTH FileBundleReference (FR1a)
// AND the legacy rows not yet carrying :FileBundleReference.  The
// :FileBundleReference label was added by the V21 migration.  We filter on
// :FileBundleReference to target only bundles (not FR1b singletons, whose
// label is :SingletonFileReference).
//
// fileOids[] is the property written by the upstream FileReference REST
// surface — it holds one entry per uploaded file OID.  A bundle with
// size(fileOids) = 1 is a single-file bundle.
//
// NOTE: nodes that have already been converted by V23 carry
// :SingletonFileReference and no longer carry :FileBundleReference, so they
// are excluded automatically.
// ---------------------------------------------------------------------------
MATCH (b:FileBundleReference)
WHERE size(b.fileOids) = 1
RETURN count(b) AS singleton_bundle_count;


// ---------------------------------------------------------------------------
// Query 2: Per-Collection breakdown
//
// Walks the graph:
//   (:Collection)-[:has_dataobject]->(:DataObject)-[:has_reference]->(:FileBundleReference)
//
// Relationship names come from Constants.HAS_DATAOBJECT = "has_dataobject"
// and Constants.HAS_REFERENCE = "has_reference".
//
// Collections that have no single-file bundles are excluded (no zero rows).
// ---------------------------------------------------------------------------
MATCH (c:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(b:FileBundleReference)
WHERE size(b.fileOids) = 1
RETURN
  c.name              AS collection_name,
  c.appId             AS collection_app_id,
  count(b)            AS singleton_bundle_count
ORDER BY singleton_bundle_count DESC;


// ---------------------------------------------------------------------------
// Query 3: Per-DataObject detail view (top 50)
//
// Lists individual bundle nodes with their names and appIds so an operator
// can identify which specific bundles need migration.
//
// Sorted by collection name, then data-object name for readable console output
// ("greppable list" acceptance criterion).
// ---------------------------------------------------------------------------
MATCH (c:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(b:FileBundleReference)
WHERE size(b.fileOids) = 1
RETURN
  c.name     AS collection,
  do.name    AS data_object,
  b.name     AS bundle_name,
  b.appId    AS bundle_app_id
ORDER BY c.name, do.name
LIMIT 50;


// ---------------------------------------------------------------------------
// Query 4: V23 migration readiness check
//
// Shows how many bundles the V23 Java migration would actually convert.
// V23 requires exactly one :FileGroup child and exactly one :ShepardFile
// reachable through that group.  Bundles with multiple groups or multiple
// files per group are NOT candidates (they are genuine multi-file bundles).
//
// Use this as a pre-flight before enabling
//   shepard.migration.split-singletons.enabled=true
// ---------------------------------------------------------------------------
MATCH (b:FileBundleReference)
WHERE NOT b:SingletonFileReference
WITH b,
     count { (b)-[:HAS_GROUP]->(:FileGroup) } AS groupCount
WHERE groupCount = 1
MATCH (b)-[:HAS_GROUP]->(g:FileGroup)
WITH b, g,
     count { (g)-[:has_payload]->(:ShepardFile) } AS groupPayloads,
     count { (b)-[:has_payload]->(:ShepardFile) } AS bundlePayloads
WHERE groupPayloads = 1 AND bundlePayloads = 1
RETURN count(b) AS v23_migration_candidate_count;
