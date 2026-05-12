// CP1a — :CollectionProperties side-node backfill. Designed in
// aidocs/58 §5. For every Collection that doesn't already have a
// :HAS_PROPERTIES edge, attach a new :CollectionProperties node with
// default-shape fields (webdavVisible=true).
//
// Idempotent: the WHERE-NOT-EXISTS guard makes re-runs no-ops.
// Greenfield deployments hit zero matches and the migration is a
// no-op there too.
//
// Each created row gets the marker `legacyBackfill: 'CP1a-V16'` so
// the V16_R rollback can find them; production-grown rows from the
// CollectionPropertiesDAO.ensureFor() path won't carry the marker.
CALL {
  MATCH (c:Collection)
  WHERE NOT (c)-[:HAS_PROPERTIES]->()
  CREATE (c)-[:HAS_PROPERTIES]->(p:CollectionProperties {
    appId: randomUUID(),
    webdavVisible: true,
    legacyBackfill: 'CP1a-V16'
  })
  RETURN count(*) AS backfilled
}
RETURN backfilled;
