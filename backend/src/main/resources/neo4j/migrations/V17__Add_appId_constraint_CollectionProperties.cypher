// CP1a — appId uniqueness constraint on :CollectionProperties.
// Same shape as V13 (Role) and V15 (Activity). Idempotent —
// CREATE CONSTRAINT IF NOT EXISTS.
//
// Applied AFTER V16 so the backfilled rows are already in place
// when the constraint is created.
CREATE CONSTRAINT appId_unique_CollectionProperties IF NOT EXISTS FOR (n:CollectionProperties) REQUIRE n.appId IS UNIQUE;
