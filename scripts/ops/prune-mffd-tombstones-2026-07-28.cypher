// =============================================================================
// OPERATOR OPS SCRIPT — NOT a startup migration. Run MANUALLY, offline.
// DESTRUCTIVE. Snapshot + export rollback REQUIRED before running (see below).
// MFFD-GRAPH-PRUNE  (aidocs/16)  —  2026-07-28
// =============================================================================
//
// WHAT: Remove soft-deleted (`deleted=true`) DataObject tombstones left by the
//       iterative MFFD imports, plus their child References and the orphaned
//       SemanticAnnotations that point at them. LIVE data (`deleted=false`) is
//       untouched.
//
// GROUND TRUTH (measured 2026-07-28, substrate-direct):
//       Soft-deleted DataObjects ...................... 3,185   (live: 10,443)
//         by collection:
//           mffd-bridge-welding  (019ed455-6781-755e-87dd-eb3f2f3dbba3) .. 2,077
//           mffd-ndt-thermography(019ed455-6866-71f1-b0bf-0f83a3e3aaa9) .. 1,101
//           mffd-stringer-welding(019edb10-c107-7473-ae28-ffc592aba860) ..     5
//           CASCADE-GATE-fixA    (019f7e28-5342-7ece-acc3-07cd5b18bdc7) ..     1  (scratch)
//           PERF4-1784640815384  (019f84e1-c121-7afc-9b5f-e9e467f65c44) ..     1  (scratch)
//       References under dead DOs ..................... 8,108   (all have appId)
//       SemanticAnnotations to prune (subjectAppId in
//         dead-DO appIds UNION dead-ref appIds) ....... 26,445
//           = 11,400 DataObject-subject + 15,045 reference-subject.
//
// SAFETY CHECKS (read-only, PASSED 2026-07-28 — this is why the prune is GO):
//   CHECK 1  live DO <-[has_successor|has_child]-> dead DO ......... 0  (no live
//            lineage severed by DETACH DELETE of a dead DO)
//   CHECK 2  live DO whose typedPredecessorsJson CONTAINS a dead
//            DO appId ..................................... 0  (predecessors are
//            a JSON *property*, NOT an edge — DETACH DELETE cannot clean them;
//            0 means no live DO carries a dangling dead reference)
//   CHECK 3  annotations-to-prune also subject of a LIVE entity ... 0  (subjectAppId
//            is 1:1 — no live annotation is collateral)
//   Deleted DOs share the ONE per-collection HEAD :Version (isHEADVersion=true).
//            => NEVER delete the Version node. DETACH DELETE on the DO / Reference
//            drops only that node's OWN has_version edge; the shared HEAD survives
//            (feedback_never_raw_delete_shared_version).
//
// !!!! ROLLBACK REALITY — a Shepard snapshot is NOT a delete-rollback !!!!
//   A Shepard :Snapshot is a MANIFEST of (entityAppId, revision) scalar pairs
//   (SnapshotEntry.entityAppId/revision) — it does NOT copy node data, and
//   `revision` is an in-place counter (no node-per-revision archive). After a
//   hard DETACH DELETE the manifest entry is a DANGLING POINTER to a node that no
//   longer exists → the snapshot cannot reconstruct it. So the snapshot is an
//   audit BOUNDARY MARKER (fire it per project_snapshot_boundaries), NOT a data
//   backup. THE AUTHORITATIVE ROLLBACK IS THE OFFLINE DUMP BELOW.
//
// !!!! SNAPSHOT-SCOPE WARNING (boundary marker still wants the right scope) !!!!
//   The runbook task cited snapshot target 019f4bf2-176f-7f4c-b3e2-5de837bf20af
//   = "MFFD-Dropbox", which HOLDS ZERO TOMBSTONES (verified 2026-07-28). Mark the
//   boundary on the THREE real collections instead (bridge-welding,
//   ndt-thermography, stringer-welding). Snapshots are HUMAN-fired.
//
// !!!! PRIMARY ROLLBACK — full offline dump (bulletproof; Community has no online
//   backup). Take it in the paused window BEFORE deleting:
//     docker exec infrastructure-neo4j-1 neo4j stop        # or: docker stop
//     docker exec infrastructure-neo4j-1 neo4j-admin database dump neo4j \
//       --to-path=/backups   # copy the .dump OFF the container
//     docker exec infrastructure-neo4j-1 neo4j start
//   Restore = `neo4j-admin database load neo4j --from-path=... --overwrite-destination`.
//   Stopping Neo4j for the dump ALSO guarantees the single-writer precondition.
//
// HARD PREREQUISITES (in order):
//   1. INGEST PAUSED / QUIESCED.
//   2. Fresh dest JWT (the June key is expired) for the snapshot POSTs:
//        POST /v2/collections/019ed455-6781-755e-87dd-eb3f2f3dbba3/snapshots
//        POST /v2/collections/019ed455-6866-71f1-b0bf-0f83a3e3aaa9/snapshots
//        POST /v2/collections/019edb10-c107-7473-ae28-ffc592aba860/snapshots
//   3. Mechanical rollback export (STEP 0 below) written successfully.
//   4. APOC available (5.26.26 confirmed).
//
// IDEMPOTENT: every step is driven by `MATCH (:DataObject {deleted:true})`; once
//   deleted they no longer match, so re-runs are no-ops. FAIL-FAST: ON ERROR FAIL.
//
// RUN (only after STEP 0 succeeded and snapshots are fired):
//   docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "$NEOPW" \
//     -f /path/to/prune-mffd-tombstones-2026-07-28.cypher
// =============================================================================

// =============================================================================
// STEP 0 — SECONDARY MECHANICAL ROLLBACK EXPORT (the offline dump above is
// PRIMARY). A hand-written V(N)_R__ twin CANNOT reconstruct DETACH DELETE'd
// nodes/appIds/edges, and the Shepard snapshot is only a pointer manifest — so
// this apoc.export of the exact delete-scope subgraph is the lightweight backup.
//
// CRITICAL: the query MUST return the SURVIVING NEIGHBOUR nodes (the live
// :Collection, the shared HEAD :Version, the surviving :ShepardFile blobs) AND
// the connecting relationships as EXPLICIT variables — apoc.export.cypher only
// emits a relationship when BOTH endpoints are in the returned set. Returning
// just (d, r, s) would DROP the has_dataobject / has_version / has_payload edges
// (their other endpoint survives the prune) and a replay would restore orphaned,
// versionless, collection-less DataObjects. `cypherFormat:'updateAll'` writes
// nodes+rels as MERGE, so re-MERGE of the surviving Collection/Version/ShepardFile
// is idempotent (matches existing) while the deleted d/r/s are re-created.
// VALIDATE the export by replaying it into a scratch DB before trusting it.
// -----------------------------------------------------------------------------
//   CALL apoc.export.cypher.query(
//     "MATCH (c:Collection)-[hd:has_dataobject]->(d:DataObject {deleted:true})
//      OPTIONAL MATCH (d)-[dv:has_version]->(v:Version)
//      OPTIONAL MATCH (d)-[dcb:created_by]->(dcbu:User)
//      OPTIONAL MATCH (d)-[hr:has_reference]->(r)
//      OPTIONAL MATCH (r)-[rv:has_version]->(rvv:Version)
//      OPTIONAL MATCH (r)-[hp:has_payload]->(f:ShepardFile)
//      OPTIONAL MATCH (r)-[ha:has_annotation]->(sr:SemanticAnnotation)
//      OPTIONAL MATCH (sd:SemanticAnnotation) WHERE sd.subjectAppId = d.appId
//      RETURN c, d, v, dcbu, r, rvv, f, sr, sd, hd, dv, dcb, hr, rv, hp, ha",
//     'prune-mffd-tombstones-rollback-2026-07-28.cypher',
//     {format:'cypher-shell', cypherFormat:'updateAll', useOptimizations:{type:'UNWIND_BATCH'}}
//   );
//   -- file lands at <neo4j-import-dir>/prune-mffd-tombstones-rollback-2026-07-28.cypher
//   -- COPY IT OFF THE CONTAINER before deleting. Note: subjectKind=DataObject
//   -- annotations (sd) have no incoming edge; they are re-created as standalone
//   -- nodes keyed by their own appId, matching how the app resolves them.
// =============================================================================


// NOTE ON BATCHING: the driving MATCH is OUTSIDE each CALL so `CALL {} IN
// TRANSACTIONS` gets N input rows and commits every batch (not one giant tx).
// These steps are small (<=15k rows) so a single tx would also be fine, but the
// batched form is used for correctness of the claim + design-consistency with
// the agent-edge backfill.

// --- STEP 1: prune SemanticAnnotations (subjectAppId-indexed, O(1)/row) -------
// PROFILE 2026-07-28: 200 dead DOs -> 795 DbHits via SemanticAnnotation_subjectAppId_idx.
// 1a) DataObject-subject annotations (11,400).
MATCH (d:DataObject {deleted:true})
MATCH (s:SemanticAnnotation {subjectAppId: d.appId})
CALL { WITH s DETACH DELETE s } IN TRANSACTIONS OF 1000 ROWS ON ERROR FAIL;

// 1b) Reference-subject annotations under dead DOs (15,045).
MATCH (d:DataObject {deleted:true})-[:has_reference]->(r)
MATCH (s:SemanticAnnotation {subjectAppId: r.appId})
CALL { WITH s DETACH DELETE s } IN TRANSACTIONS OF 1000 ROWS ON ERROR FAIL;

// --- STEP 2: DETACH DELETE the References under dead DOs (8,108) --------------
// DETACH DELETE drops r's own edges (has_reference, has_payload, has_annotation,
// has_version). The shared HEAD :Version node survives. ShepardFile payload
// blobs are LEFT IN PLACE (see note) — only the has_payload edge is removed.
MATCH (d:DataObject {deleted:true})-[:has_reference]->(r)
CALL { WITH r DETACH DELETE r } IN TRANSACTIONS OF 1000 ROWS ON ERROR FAIL;

// --- STEP 3: DETACH DELETE the soft-deleted DataObjects (3,185) ---------------
// Drops d's own edges (has_dataobject, has_version, created_in_month, any
// has_successor/has_child). CHECK 1 proved 0 live<->dead lineage edges, so no
// live DataObject loses a relationship. HEAD :Version survives.
MATCH (d:DataObject {deleted:true})
CALL { WITH d DETACH DELETE d } IN TRANSACTIONS OF 1000 ROWS ON ERROR FAIL;

// --- POST-CHECKS (all expect 0) ----------------------------------------------
MATCH (d:DataObject {deleted:true}) RETURN count(d) AS deletedDOsRemaining;
MATCH (d:DataObject {deleted:true})-[:has_reference]->(r) RETURN count(r) AS deletedRefsRemaining;
// Verify no HEAD Version was orphaned (every live DataObject still resolves a HEAD):
MATCH (d:DataObject) WHERE coalesce(d.deleted,false)=false AND NOT (d)-[:has_version]->(:Version)
RETURN count(d) AS liveDOsMissingHEAD;   // MUST be 0

// -----------------------------------------------------------------------------
// NOTE — ShepardFile blob garbage collection is DEFERRED, on purpose.
// A ShepardFile blob can be SHARED (same md5/oid) across multiple references
// (feedback_referenced_data_infinite_retention: delete only TRUE orphans). This
// prune removes the has_payload EDGES from dead refs but never a ShepardFile
// node. True-orphan blob GC (a ShepardFile with 0 remaining has_payload) belongs
// to the storage orphan-resolver, not this graph prune. Those surviving blobs
// are also part of the CHILD-APPID-BACKFILL population.
// =============================================================================
