// Operator-run rollback for V16. Deletes only the :CollectionProperties
// nodes V16 created (those tagged `legacyBackfill = 'CP1a-V16'`) and
// their :HAS_PROPERTIES edges. Production-grown rows (created via
// CollectionPropertiesDAO.ensureFor() in normal CRUD flow) DO NOT
// carry the marker and are NOT touched.
//
// Use case: operator wants to re-run V16 from clean after a partial
// failure. Run this, then re-apply V16.
MATCH (p:CollectionProperties { legacyBackfill: 'CP1a-V16' })
DETACH DELETE p
RETURN count(*) AS rolledBack;
