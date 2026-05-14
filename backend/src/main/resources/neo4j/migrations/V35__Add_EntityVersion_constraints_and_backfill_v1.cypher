// ENT1a — Entity-versioning baseline.
//
// Two responsibilities in one migration (kept together so the
// post-migration graph is internally consistent — every existing
// Collection + DataObject ends with at least one :EntityVersion row
// whose appId honours the constraint that's freshly applied above):
//
//   1. Constraints on the new :EntityVersion label.
//   2. Backfill a v1 :EntityVersion + per-version :Permissions for
//      every existing Collection + DataObject so the post-ENT1a
//      `MATCH (e {appId: X})-[:HAS_ENTITY_VERSION]->(:EntityVersion)`
//      pattern always succeeds.
//
// V## ordering: V34 is reserved for FS1a (in-flight); ENT1a takes
// V35. If FS1a hasn't merged by rebase time the orchestrator
// handles the V## collision check — both are independently rebasable
// (constraint-only + idempotent backfill).
//
// Idempotent: every CREATE statement is guarded by
// `WHERE NOT (parent)-[:HAS_ENTITY_VERSION]->()` (no parent gets
// a duplicate v1) or by `IF NOT EXISTS` (constraints). The
// per-relationship-type ACL clones are guarded by `WHERE NOT EXISTS`
// on the target edge so a re-run after a partial failure picks up
// where the previous run left off.
//
// Fail-fast: MigrationsRunner (post-A1e) propagates any exception so
// a partial backfill aborts startup; operators see the failure rather
// than half-migrated data.
//
// MigrationsRunner runs the script in PER_STATEMENT transaction mode
// (one transaction per `;`-terminated statement) — every statement in
// this file is therefore self-contained and order-tolerant within the
// constraints above.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row ENT1a +
// docs/reference/entity-versions.md.
// Verify post-migration:
//   cypher-shell> MATCH (c:Collection) WHERE c.appId IS NOT NULL AND NOT (c)-[:HAS_ENTITY_VERSION]->() RETURN count(c);
//   → must return 0.
//   cypher-shell> MATCH (d:DataObject) WHERE d.appId IS NOT NULL AND NOT (d)-[:HAS_ENTITY_VERSION]->() RETURN count(d);
//   → must return 0.
//   cypher-shell> MATCH (v:EntityVersion) RETURN count(v);
//   → roughly equals (Collection count + DataObject count).
//
// Rollback (rare; see docs/reference/entity-versions.md):
//   cypher-shell> MATCH (v:EntityVersion)<-[r:HAS_ENTITY_VERSION]-()
//                 OPTIONAL MATCH (v)-[:has_permissions]->(vp:Permissions)
//                 OPTIONAL MATCH (vp)-[gr]->()
//                 DELETE gr, vp, r, v;
//   cypher-shell> DROP CONSTRAINT appId_unique_EntityVersion;
//   cypher-shell> DROP CONSTRAINT parentAppId_label_unique_EntityVersion;

// --- 1. constraints ---------------------------------------------------

CREATE CONSTRAINT appId_unique_EntityVersion IF NOT EXISTS FOR (n:EntityVersion) REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT parentAppId_label_unique_EntityVersion IF NOT EXISTS FOR (n:EntityVersion) REQUIRE (n.parentEntityAppId, n.versionLabel) IS UNIQUE;

// --- 2. backfill v1 :EntityVersion for every Collection / DataObject ---

MATCH (c:Collection) WHERE c.appId IS NOT NULL AND NOT (c)-[:HAS_ENTITY_VERSION]->() CREATE (c)-[:HAS_ENTITY_VERSION]->(:EntityVersion {appId: c.appId + '-v1', versionLabel: 'v1', versionOrdinal: 1, createdAt: coalesce(c.createdAt, timestamp()), createdBy: '<backfill>', parentEntityKind: 'collection', parentEntityAppId: c.appId, note: 'Auto-backfill by ENT1a (V35)'});

MATCH (d:DataObject) WHERE d.appId IS NOT NULL AND NOT (d)-[:HAS_ENTITY_VERSION]->() CREATE (d)-[:HAS_ENTITY_VERSION]->(:EntityVersion {appId: d.appId + '-v1', versionLabel: 'v1', versionOrdinal: 1, createdAt: coalesce(d.createdAt, timestamp()), createdBy: '<backfill>', parentEntityKind: 'data-object', parentEntityAppId: d.appId, note: 'Auto-backfill by ENT1a (V35)'});

// --- 3. clone parent :Permissions onto each backfilled v1 (Collection) ---

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(v:EntityVersion {versionLabel: 'v1'}) WHERE v.parentEntityKind = 'collection' AND NOT (v)-[:has_permissions]->(:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions) CREATE (v)-[:has_permissions]->(:Permissions {appId: c.appId + '-v1-perms', permissionType: coalesce(p.permissionType, 'Private')});

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) WHERE NOT (vp)-[:owned_by]->(:User) MATCH (c)-[:has_permissions]->(p:Permissions)-[:owned_by]->(o:User) CREATE (vp)-[:owned_by]->(o);

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions)-[:readable_by]->(u:User) WHERE NOT (vp)-[:readable_by]->(u) CREATE (vp)-[:readable_by]->(u);

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions)-[:writeable_by]->(u:User) WHERE NOT (vp)-[:writeable_by]->(u) CREATE (vp)-[:writeable_by]->(u);

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions)-[:manageable_by]->(u:User) WHERE NOT (vp)-[:manageable_by]->(u) CREATE (vp)-[:manageable_by]->(u);

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions)-[:readable_by_group]->(g:UserGroup) WHERE NOT (vp)-[:readable_by_group]->(g) CREATE (vp)-[:readable_by_group]->(g);

MATCH (c:Collection)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (c)-[:has_permissions]->(p:Permissions)-[:writeable_by_group]->(g:UserGroup) WHERE NOT (vp)-[:writeable_by_group]->(g) CREATE (vp)-[:writeable_by_group]->(g);

// --- 4. clone parent :Permissions onto each backfilled v1 (DataObject) ---

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(v:EntityVersion {versionLabel: 'v1'}) WHERE v.parentEntityKind = 'data-object' AND NOT (v)-[:has_permissions]->(:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions) CREATE (v)-[:has_permissions]->(:Permissions {appId: d.appId + '-v1-perms', permissionType: coalesce(p.permissionType, 'Private')});

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) WHERE NOT (vp)-[:owned_by]->(:User) MATCH (d)-[:has_permissions]->(p:Permissions)-[:owned_by]->(o:User) CREATE (vp)-[:owned_by]->(o);

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions)-[:readable_by]->(u:User) WHERE NOT (vp)-[:readable_by]->(u) CREATE (vp)-[:readable_by]->(u);

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions)-[:writeable_by]->(u:User) WHERE NOT (vp)-[:writeable_by]->(u) CREATE (vp)-[:writeable_by]->(u);

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions)-[:manageable_by]->(u:User) WHERE NOT (vp)-[:manageable_by]->(u) CREATE (vp)-[:manageable_by]->(u);

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions)-[:readable_by_group]->(g:UserGroup) WHERE NOT (vp)-[:readable_by_group]->(g) CREATE (vp)-[:readable_by_group]->(g);

MATCH (d:DataObject)-[:HAS_ENTITY_VERSION]->(:EntityVersion {versionLabel: 'v1'})-[:has_permissions]->(vp:Permissions) MATCH (d)-[:has_permissions]->(p:Permissions)-[:writeable_by_group]->(g:UserGroup) WHERE NOT (vp)-[:writeable_by_group]->(g) CREATE (vp)-[:writeable_by_group]->(g);
