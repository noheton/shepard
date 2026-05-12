// A0 / C3 — Backfill default Permissions nodes for legacy entities
// that lack a `:has_permissions` edge. Bundled with the C3 fail-closed
// flip in `PermissionsService.getRoles` (aidocs/51 §8 / aidocs/07 C3).
//
// The migration is **idempotent**: it only creates Permissions for
// entities that don't already have one, so re-running is a no-op on a
// healthy graph.
//
// Configuration mechanism:
//   * The Java side (`OrphanPermissionsBackfillContext`) writes a
//     singleton `(:_ShepardMigrationContext {defaultOwner: '<u>'})`
//     node before migrations run, sourced from
//     `shepard.permissions.default-owner` config.
//   * If that config is unset and orphan entities exist, the Java
//     pre-migration hook **aborts startup with a clear error** before
//     this file ever runs (post-A1e fail-fast). Operators who want to
//     bypass the backfill on a healthy graph (no orphans) can leave
//     the config unset; only orphans-exist + config-unset is fatal.
//   * If the config is set but the named owner doesn't exist as a
//     `:User`, the Java hook also aborts before this runs.
//
// Operator runbook (the boring path):
//   1. Set `shepard.permissions.default-owner=<existing-username>` in
//      your environment / `.env` / properties file.
//   2. Restart shepard. The Java hook seeds the context node, this
//      migration runs, attaches default Permissions to every orphan.
//   3. Verify via `GET /v2/admin/permission-audit` (post-A0): list
//      should be empty.
//
// Rollback: V14_R__Rollback_Backfill_orphan_permissions.cypher (ships
// alongside; deletes Permissions nodes whose `createdBy = 'A0-V14'`).
//
// MIGRATION BODY:

// 1. Resolve the default-owner from the seeded context node. If the
//    context is absent, $owner stays null.
MATCH (ctx:_ShepardMigrationContext) WITH ctx.defaultOwner AS owner LIMIT 1

// 2. For every BasicEntity without a :has_permissions edge, attach a
//    new Permissions node owned by the configured default owner.
//    `OPTIONAL MATCH` lets the migration succeed when there are zero
//    orphans (the typical, healthy case) — it's literally a no-op
//    in that situation.
OPTIONAL MATCH (e:BasicEntity)
WHERE NOT (e)-[:has_permissions]->(:Permissions)
WITH owner, collect(e) AS orphans

// 3. If the JVM-seeded owner is set, attach Permissions to every
//    orphan. If it isn't set, skip — the Java pre-hook should have
//    already aborted; this guards against a manual-run scenario where
//    an operator invokes neo4j-migrations directly.
CALL {
  WITH owner, orphans
  UNWIND orphans AS e
  MATCH (u:User {username: owner})
  MERGE (e)-[:has_permissions]->(p:Permissions {legacyBackfill: 'A0-V14'})
  ON CREATE SET
    p.appId = randomUUID(),
    p.permissionType = 'Private'
  MERGE (p)-[:owned_by]->(u)
  RETURN count(p) AS backfilled
}
RETURN backfilled;
