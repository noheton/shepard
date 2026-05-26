// V84_R__FAIR2_FAIR3_fields.cypher — rollback for V84
//
// Drops the embargoEndDate index introduced in V84.
// Note: createdByOrcid and embargoEndDate properties on existing :DataObject
//       nodes are NOT removed by this rollback — the schema-free Neo4j model
//       means removing the property would require a data-mutating migration.
//       The properties are nullable and backward-compatible; leaving them in
//       place on a rollback is safe.

DROP INDEX data_object_embargo_end_date IF EXISTS;
