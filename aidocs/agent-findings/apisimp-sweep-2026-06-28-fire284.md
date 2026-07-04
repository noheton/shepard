---
stage: deployed
last-stage-change: 2026-06-28
---

# API Simplification Sweep — fire-284 (2026-06-28)

## Context

All named APISIMP rows in `aidocs/16` are merged or blocked:

- **Last merged**: `APISIMP-TEMPLATE-TAGS-CAP` — PR #2149 → SHA `68655ad4`,
  merged fire-284 (LIMIT 500 cap on `listDistinctTags` Cypher).
- **Blocked**: `APISIMP-PERMISSION-AUDIT-NEO4J-ID` — depends on L2 migration
  (neo4j numeric-id elimination pass); no timeline yet.

Per pipeline instructions, fire-284 ran a fresh sweep of the `/v2/` REST
surface before moving to the next priority (UI-GAP → MISSING-aas-ui).

## Sweep scope

All `@Path` endpoints under `/v2/` across:

- `backend/src/main/java/de/dlr/shepard/v2/` (core v2 resources)
- `plugins/*/src/main/java/` (plugin v2 resources: aas, spatial, video, unhide, git, …)
- `backend/src/main/java/de/dlr/shepard/v2/admin/` (admin v2 resources)

## Findings

| Category | Verdict |
|---|---|
| Redundant endpoints (two paths doing the same thing) | ✅ CLEAN |
| Inconsistent pagination envelopes | ✅ CLEAN — all list endpoints use `PagedResponseIO{items,total,page,pageSize}` |
| Numeric Neo4j ID leaks in path params or response bodies | ✅ CLEAN — all path params are `appId` (UUID v7); numeric `id` stays in entity beans but is not surfaced as a routing key |
| Leaky abstraction (DB internals in response IO shapes) | ✅ CLEAN |
| Per-kind bespoke sub-paths instead of `?kind=` | ✅ INTENDED — plugin-namespaced paths (`/v2/aas/`, `/v2/spatial/`, `/v2/video/`) are plugin namespaces, not per-kind sprawl within a shared resource |
| Missing CRUD operations for existing entities | ✅ CLEAN within scope of shipped features |
| Cross-cutting header naming consistency (`X-Shepard-*`) | ✅ CLEAN — no new mismatches found |
| Optional: X-Total-Count header vs envelope total field | ✅ INTENDED — total is in the `PagedResponseIO` body; no redundant header |

## Conclusion

No new APISIMP rows warranted. The `/v2/` surface is minimal and consistent.

Dispatcher proceeds to next priority: **MISSING-aas-ui** (UI-GAP, M-sized).
Slice 1 implements the AAS Shell list composable + page + Vitest tests + nav entry.
