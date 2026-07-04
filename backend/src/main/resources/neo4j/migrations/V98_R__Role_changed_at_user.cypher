// Rollback for V98__Role_changed_at_user.cypher.
//
// Data-only rollback: strip the `roleChangedAt` property from every :User
// node where it was set during the deployment window. Idempotent; safe to
// re-run. The forward migration is itself a NOOP (Neo4j is schema-less),
// so the rollback only needs to clean up data written by the runtime.
//
// Run order: stop the backend, run this Cypher, restart on the prior
// version. After rollback, all :User nodes are treated as never-changed
// (every JWT passes the gate) — i.e. the pre-feature behaviour is
// restored.
//
//   MATCH (u:User) WHERE u.roleChangedAt IS NOT NULL
//   REMOVE u.roleChangedAt;
RETURN 1;
