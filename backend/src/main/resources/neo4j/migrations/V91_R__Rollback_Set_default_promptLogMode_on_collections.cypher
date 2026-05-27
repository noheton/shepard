// V91_R — Rollback for V91__Set_default_promptLogMode_on_collections.cypher.
//
// Removes the `promptLogMode` property from all :Collection nodes that were
// set to 'HASH_ONLY' by V91.
//
// WARNING: This rollback is destructive for any Collection where an operator
// explicitly changed the mode away from 'HASH_ONLY' after V91 ran — those
// collections will also lose their custom promptLogMode setting. The rollback
// is only safe on a freshly migrated installation where no manual changes have
// been made via PATCH /v2/collections/{appId}.
//
// To run from a shell:
//   cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//     -f backend/src/main/resources/neo4j/migrations/V91_R__Rollback_Set_default_promptLogMode_on_collections.cypher
//
// Verify after: MATCH (c:Collection) WHERE c.promptLogMode IS NOT NULL RETURN count(c);
// -- expect 0 after a successful rollback.

MATCH (c:Collection)
WHERE c.promptLogMode IS NOT NULL
REMOVE c.promptLogMode;
