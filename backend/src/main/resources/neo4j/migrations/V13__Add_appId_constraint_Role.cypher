// A0 — instance-admin role mechanism. Adds the same uniqueness-only
// `appId` constraint to the new `Role` label that V11 added to the
// other 28 protected node-entity labels (see aidocs/25 §2 and the
// preceding constraint additions in V11).
//
// Single statement, idempotent — safe to re-run on stacks where it
// already applied. The uniqueness check ignores nulls (Neo4j 5
// semantics), so the constraint is valid even on a graph where
// pre-A0 Role nodes (none, in practice — Role is born here)
// hypothetically exist with appId = null.
CREATE CONSTRAINT appId_unique_Role IF NOT EXISTS FOR (n:Role) REQUIRE n.appId IS UNIQUE;
