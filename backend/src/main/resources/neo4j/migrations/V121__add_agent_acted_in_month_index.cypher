// NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — Range index for the time-bucketed
// agent edge on the `:Activity` → `:User` supernode.
//
// A single service `:User` carries ~2.87M incoming `WAS_ASSOCIATED_WITH` edges
// (~28× Neo4j's dense-node threshold). `ActivityDAO.writeAgentActedInMonth()`
// now writes `(:User)-[:agent_acted_in_month {ym:"YYYYMM"}]->(:Activity)` on
// every new Activity (via an O(1) guarded CREATE — see the DAO), so agent+time
// provenance queries can label-scan this bounded, ym-indexed rel instead of
// walking the supernode. This migration creates that index.
//
// !!! The one-time historical backfill of the ~2.87M existing edges is
//     DELIBERATELY NOT here. !!! A multi-million-row backfill must never be a
//     fail-fast, synchronous startup migration — the original in-CALL
//     `MATCH…MERGE` form did not stream and MERGE on the supernode is O(degree)
//     per row, which hung backend startup on 2026-07-19. The historical backfill
//     is deferred to a separate, tuned, monitored operation (streaming
//     `MATCH (u)<-[:WAS_ASSOCIATED_WITH]-(a) … CALL { WITH u,a CREATE … } IN
//     TRANSACTIONS`, run offline against a paused-ingest window). Tracked as
//     ACTIVITY-SUPERNODE-BACKFILL in aidocs/16. New edges (from the DAO) get the
//     index immediately; historical agent+time queries wait on the backfill.
//
// Idempotent (`IF NOT EXISTS`). Rollback: V121_R__ (drops the index).

CREATE RANGE INDEX agent_acted_in_month_ym_idx IF NOT EXISTS
FOR ()-[r:agent_acted_in_month]-()
ON (r.ym);
