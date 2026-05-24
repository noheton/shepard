// IMP1 — :ImportPlan nodes are additive; no schema change required.
//
// New nodes are created by POST /v2/import/validate.  No backfill needed
// because the plan-seal pattern is forward-only: existing collections do not
// need historical plans.
//
// Operator runbook: none required.  If you want to inspect existing plans:
//   MATCH (p:ImportPlan) RETURN p ORDER BY p.validatedAt DESC LIMIT 50;
//
// Rollback (data-only, no schema): delete any plans created after the
// upgrade if you need to roll back to a pre-IMP1 binary.
//   MATCH (p:ImportPlan) DELETE p;
RETURN 1;
