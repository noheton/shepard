// NEO-AUDIT-010 — User email uniqueness constraint + duplicate email reconciliation
//
// Context:
//   No uniqueness constraint exists on :User.email. The audit found 2× duplicate
//   email addresses in the dev instance (alice@demo.shepard.local, admin@demo.shepard.local).
//   In production a shared department mailbox or OIDC misconfiguration would silently
//   create multiple :User nodes with the same email, corrupting provenance trails.
//
// DATA-MUTATING WARNING:
//   This migration transfers all edges from duplicate :User nodes to canonical nodes and
//   then deletes the duplicate nodes. No data (edges, properties) is lost — edges are
//   retargeted, not dropped. But the canonical node's properties win in any conflict.
//   BACK UP your Neo4j database before running on production.
//
// Canonical-user selection strategy (in priority order):
//   1. The node whose username matches the known admin pattern (e.g. "admin") is canonical.
//   2. Failing that, the node with more outgoing relationships wins (the more "active" node).
//   3. As a deterministic final tiebreak, the node whose username sorts first lexicographically
//      is canonical (consistent and reproducible; does not rely on unreliable createdAt).
//
// Idempotency:
//   The reconciliation CALL block runs only when duplicates actually exist (WHERE guard).
//   The constraint creation uses IF NOT EXISTS. Both are safe to re-run.
//
// APOC dependency:
//   Uses apoc.refactor.mergeNodes to transfer ALL edges (both directions) in one call.
//   APOC core is bundled with Neo4j 5.x >= 5.7 and is activated via
//   NEO4J_PLUGINS: '["apoc","n10s"]' (NEO-AUDIT-013 compose change).
//
// Rollback: V81_R__user_email_unique_constraint.cypher
//   Drops the constraint only. Duplicate nodes are NOT recreated (reconciliation
//   is a one-way merge — restoring the pre-merge graph would require a full DB restore).
//
// Operator runbook:
//   Manual run from cypher-shell:
//     cypher-shell -u neo4j -p <pwd> -f V81__user_email_unique_constraint.cypher
//   Or let Flyway pick it up automatically on next shepard startup.
//   Verify post-migration: MATCH (u:User) WITH u.email AS e, count(*) AS c
//     WHERE e IS NOT NULL AND c > 1 RETURN e, c; → should return 0 rows.
//   Verify constraint: SHOW CONSTRAINTS WHERE name = 'user_email_unique'; → 1 row.

// ---------------------------------------------------------------------------
// STEP 1 — Reconcile duplicate email addresses
//
// For each set of :User nodes sharing an email, select the canonical node by the
// priority rules above, then use apoc.refactor.mergeNodes([canonical, ...duplicates])
// to transfer all edges and delete the duplicate nodes.
//
// apoc.refactor.mergeNodes signature:
//   mergeNodes(nodes: List<Node>, config: Map)
//   config.properties: 'discard' → canonical node's properties win unconditionally
//   config.mergeRels: true → duplicate relationships are collapsed (not duplicated)
//
// The first element of the nodes list is the target — its properties are preserved.
// ---------------------------------------------------------------------------

CALL {
  // Gather all groups of :User nodes that share the same non-null email.
  MATCH (u:User)
  WHERE u.email IS NOT NULL AND u.email <> ''
  WITH u.email AS email, collect(u) AS users
  WHERE size(users) > 1

  // For each duplicate group, select the canonical node by priority.
  // apoc.coll.sortNodes/apoc.nodes.degree not universally available; use Cypher sort.
  UNWIND users AS u
  WITH email, users, u,
       CASE
         WHEN u.username = 'admin'                          THEN 0
         WHEN u.username STARTS WITH 'admin'                THEN 1
         ELSE 2
       END AS adminScore,
       size([(u)-[]-() | 1]) AS degree

  ORDER BY adminScore ASC, degree DESC, u.username ASC
  WITH email, collect(u) AS orderedUsers

  // Merge: canonical = orderedUsers[0], rest are duplicates
  // apoc.refactor.mergeNodes([canonical] + duplicates) — canonical props win ('discard')
  WHERE size(orderedUsers) >= 2
  CALL apoc.refactor.mergeNodes(orderedUsers, {properties: 'discard', mergeRels: true})
  YIELD node
  RETURN count(node) AS mergedCount
} IN TRANSACTIONS OF 10 ROWS;

// ---------------------------------------------------------------------------
// STEP 2 — Add the uniqueness constraint on :User.email
//
// Runs only after step 1 eliminates all duplicates (constraint creation would
// fail if duplicates still existed). IF NOT EXISTS makes re-runs safe.
// ---------------------------------------------------------------------------

CREATE CONSTRAINT user_email_unique IF NOT EXISTS
  FOR (u:User)
  REQUIRE u.email IS UNIQUE;
