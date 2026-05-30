// KRL-CONFIG-1 — rollback for V96__Add_KrlInterpreterConfig_singleton.cypher
//
// Drops the uniqueness constraint on :KrlInterpreterConfigEntity.appId
// and removes any seeded singleton node(s).
//
// WARNING: This rollback removes all :KrlInterpreterConfigEntity nodes.
// Any runtime config overrides (sidecarUrl, timeoutSeconds, maxBodySizeMb)
// will be lost. The system reverts to deploy-time application.properties
// defaults after rollback.
//
// Idempotent: DROP CONSTRAINT IF EXISTS is a no-op when the constraint
// does not exist. DETACH DELETE is a no-op on an empty match.

DROP CONSTRAINT appId_unique_KrlInterpreterConfigEntity IF EXISTS;

MATCH (n:KrlInterpreterConfigEntity) DETACH DELETE n;
