// FAIR-1 — :DataObject (and any other :AbstractDataObject subclass) gains two
// optional properties: license (String, e.g. "CC-BY-4.0") and accessRights
// (String, COAR vocabulary term). Neo4j properties are schema-free; no DDL
// change required.
//
// Existing DataObjects without these properties keep working; the properties are
// absent until set by a PATCH /v2/collections/{cid}/data-objects/{did} call or
// a direct API write.
//
// Operator runbook: none required. To inspect DataObjects that carry FAIR metadata:
//   MATCH (d:DataObject)
//   WHERE d.license IS NOT NULL OR d.accessRights IS NOT NULL
//   RETURN d.appId, d.name, d.license, d.accessRights LIMIT 50;
//
// Rollback (data-only, no schema): strip the new properties from all DataObjects:
//   MATCH (d:DataObject)
//   WHERE d.license IS NOT NULL OR d.accessRights IS NOT NULL
//   REMOVE d.license, d.accessRights;
RETURN 1;
