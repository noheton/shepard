// V90 — Remove Permissions nodes that V14 incorrectly attached to
// DataObject or BasicReference nodes.
//
// Background (V14-WHERE-CLAUSE-TIGHTEN):
//   V14__Backfill_orphan_permissions.cypher lines 44-45 match ALL
//   BasicEntity nodes without a :has_permissions edge:
//
//       OPTIONAL MATCH (e:BasicEntity)
//       WHERE NOT (e)-[:has_permissions]->(:Permissions)
//
//   But DataObject and BasicReference ARE BasicEntity nodes that are
//   intentionally designed to have NO direct Permissions — they inherit
//   from the parent Collection via
//   PermissionsService.isAccessAllowedForDataObjectAppId (lines 321-338).
//
//   If an operator ever ran V14 on a graph that contained DataObjects or
//   BasicReferences (the common post-bootstrap case), those nodes would
//   receive spurious Permissions. The PermissionsService.isAccessTypeAllowedForUser
//   3-arg fallback (line 287) short-circuits on the first non-null return
//   from permissionsDAO.findByEntityNeo4jId — so a directly-attached
//   Permissions node silently BYPASSES the Collection-level inheritance
//   walk, breaking the intended access-control model.
//
// What this migration does:
//   1. Find DataObject and BasicReference nodes that have a :has_permissions
//      edge to a Permissions node carrying legacyBackfill = 'A0-V14'
//      (the exact marker V14 stamps on every node it creates).
//   2. Delete the :has_permissions edge.
//   3. If the Permissions node now has no other incoming :has_permissions
//      edges, DETACH DELETE it (which also removes the :owned_by edge to
//      the User node that V14 wired up).
//
// Idempotency: The MATCH returns zero rows on a healthy graph or on
// re-run — safe to re-run without side effects.
//
// Operator runbook:
//   1. No config required — this migration is automatic and unconditional.
//   2. Verify via:
//        MATCH (e)-[:has_permissions]->(p:Permissions {legacyBackfill: 'A0-V14'})
//        WHERE e:DataObject OR e:BasicReference
//        RETURN count(p);
//      — should return 0 after migration.
//   3. Access to existing DataObjects remains correct: the
//      Collection-level Permissions nodes are untouched.
//
// Rollback: V90_R__Rollback_Tighten_orphan_permissions_backfill.cypher
//   (no-op by design — see that file for rationale).

// Step 1 + 2: Remove the :has_permissions edge from DataObject and
//   BasicReference nodes where the Permissions node was created by V14.
MATCH (e)-[r:has_permissions]->(p:Permissions {legacyBackfill: 'A0-V14'})
WHERE e:DataObject OR e:BasicReference
DELETE r
WITH DISTINCT p

// Step 3: If the Permissions node now has no remaining incoming
//   :has_permissions edges, it is unreachable — delete it along with
//   its :owned_by edge.
WHERE NOT (p)<-[:has_permissions]-()
DETACH DELETE p;
