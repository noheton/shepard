// Rollback for V97__NOOP_BasicContainer_status_additive.cypher.
//
// Data-only rollback: strip the `status` property from any container nodes
// where it was set during the deployment window. Idempotent; safe to re-run.
// The forward migration is itself a NOOP (Neo4j is schema-less), so the
// rollback only needs to clean up data written via the PATCH endpoint.
//
// Run order: stop the backend, run this Cypher, restart on the prior version.
//
//   MATCH (c)
//   WHERE (c:FileContainer OR c:TimeseriesContainer OR c:StructuredDataContainer
//          OR c:HdfContainer)
//     AND c.status IS NOT NULL
//   REMOVE c.status;
RETURN 1;
