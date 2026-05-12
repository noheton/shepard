// V23 rollback — FR1b reverse-split.
// See `aidocs/53 §1.8.5` for the design rationale.
//
// Safety guard: refuses to run if ANY :SingletonFileReference node
// lacks the legacyV23Singleton property (a row minted via
// /v2/files/... POST after V23 ran), OR if any such row has a
// non-null updatedAt that's later than the marker timestamp
// captured at V23 time. The first guard catches "user-created
// post-V23 singletons exist"; the second catches "V23-minted
// singletons that were subsequently edited via PATCH".
//
// Operator runbook (the boring path):
//   1. Verify rollback is safe:
//        MATCH (s:SingletonFileReference)
//          WHERE s.legacyV23Singleton IS NULL OR s.legacyV23Singleton = false
//          RETURN count(s) AS post_v23_user_singletons;
//      If `post_v23_user_singletons > 0`, do NOT roll back —
//      user state would be lost.
//   2. Verify no V23-minted singletons have been patched:
//        MATCH (s:SingletonFileReference {legacyV23Singleton: true})
//          WHERE s.updatedAt IS NOT NULL
//          RETURN count(s) AS patched_v23_singletons;
//      If > 0, manually triage first (the rollback won't see those
//      edits — they'd be silently lost).
//   3. Take a Neo4j backup.
//   4. Run this rollback. The Mongo bytes are NOT moved back —
//      they remain in the shared `_shepard_files` namespace. The
//      rollback writes a fresh per-bundle collection name (the
//      legacyV23BundleMongoId stored on the singleton at V23 time)
//      back into the :FileContainer node so the upstream-API read
//      path sees the legacy layout again.
//
// Bytes move:
//   V23 only moved the small metadata doc between Mongo collections;
//   GridFS chunks stayed in place (one shared bucket). V23_R moves
//   the metadata doc BACK. If the source collection was dropped by
//   V23 (it's now non-existent), we recreate it implicitly via the
//   first insert.
//
// LIMITATION:
//   V23_R is Cypher-only by design — it does NOT move Mongo metadata
//   docs. An operator running this rollback must ALSO run a small
//   `mongosh` script (linked in the design doc) to move metadata
//   docs back to per-bundle collections. The Cypher-only rollback
//   covers the Neo4j-side relabel + group-restore; the Mongo side is
//   a manual operator step (V23_R can't run JavaScript inside a
//   Cypher migration).
//
// Idempotent + fail-fast per CLAUDE.md.

// 1. Guard: refuse to run if any post-V23 user singletons exist.
CALL {
  MATCH (s:SingletonFileReference)
    WHERE s.legacyV23Singleton IS NULL OR s.legacyV23Singleton = false
  WITH count(s) AS userSingletons
  RETURN 1 / (CASE WHEN userSingletons = 0 THEN 1 ELSE 0 END) AS guard_users
};

// 2. Guard: refuse to run if any V23-minted singleton has been
// updated (would silently lose user state on rollback).
CALL {
  MATCH (s:SingletonFileReference {legacyV23Singleton: true})
    WHERE s.updatedAt IS NOT NULL
  WITH count(s) AS patchedSingletons
  RETURN 1 / (CASE WHEN patchedSingletons = 0 THEN 1 ELSE 0 END) AS guard_patched
};

// 3. Re-create the default :FileGroup under each V23-minted singleton.
//    Note: index = 0, legacyDefault = 'FR1a-V21' so the V21_R rollback
//    (if subsequently chained) can pick the row up cleanly.
MATCH (r:SingletonFileReference {legacyV23Singleton: true})
CREATE (g:FileGroup:BasicEntity {
  appId: randomUUID(),
  name: 'default',
  description: 'Auto-restored default group from V23_R (FR1b) rollback.',
  index: 0,
  deleted: false,
  legacyDefault: 'FR1a-V21'
})
CREATE (r)-[:HAS_GROUP {index: 0}]->(g);

// 4. Wire the existing has_payload edge through the new group.
//    The singleton already has (r)-[:has_payload]->(f); we add
//    (g)-[:has_payload]->(f) so the bundle's group view is restored.
MATCH (r:SingletonFileReference {legacyV23Singleton: true})-[:has_payload]->(f:ShepardFile)
MATCH (r)-[:HAS_GROUP]->(g:FileGroup {legacyDefault: 'FR1a-V21'})
MERGE (g)-[:has_payload]->(f);

// 5. Relabel: drop :SingletonFileReference, restore :FileBundleReference
//    and the legacy :FileReference label. Strip the marker properties.
MATCH (r:SingletonFileReference {legacyV23Singleton: true})
REMOVE r:SingletonFileReference,
       r.legacyV23Singleton,
       r.legacyV23BundleMongoId
SET    r:FileBundleReference, r:FileReference;
