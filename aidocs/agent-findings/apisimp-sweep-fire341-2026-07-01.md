---
stage: deployed
last-stage-change: 2026-07-01
---

# API Simplification Sweep — fire-341 (2026-07-01)

## Context

Fire-341 merged PR #2213 (`APISIMP-MISSING-OPERATIONID`, S) — added `operationId` to
129 v2 REST endpoint methods across 60 files, eliminating auto-derived fragile names
from the generated OpenAPI spec.

After that merge, all named APISIMP rows in `aidocs/16` are either merged or blocked:

| Row | Status |
|---|---|
| `APISIMP-MISSING-OPERATIONID` | ✅ merged (fire-341, PR #2213, sha: f73f7cab) |
| `APISIMP-PERMISSION-AUDIT-NEO4J-ID` | ⏳ blocked — needs L2 migration clean confirmation |
| `APISIMP-NUMERIC-ID-BATCH-2` | ⛔ deferred — frozen upstream-compat surface |
| `APISIMP-V1-PATH-RESIDUAL-1` | ⛔ deferred — frozen upstream-compat surface |

Per pipeline rule, a fresh sweep was run before declaring nothing to dispatch.

## Sweep scope

All files under:
- `backend/src/main/java/de/dlr/shepard/v2/` — core v2 REST resources + IO
- `plugins/*/src/main/java/` — plugin v2 REST resources
- `backend/src/main/java/de/dlr/shepard/v2/admin/` — admin v2 resources

Axes checked:
1. Numeric Neo4j id leaks in IO classes
2. Bespoke admin `*ConfigRest` not on the generic registry
3. Per-kind endpoint fragmentation vs. `?kind=` unification
4. Inconsistent pagination param names
5. Error envelope consistency
6. Response fields leaking internal details
7. New forbidden `@Path(Constants.SHEPARD_API + ...)` in v2

## Findings — all clean

### Finding 1 — Numeric Neo4j id leaks: **NONE**

All v2 IO classes use `appId` (UUID v7) as entity identifiers. The three field names
that triggered the initial grep (`OntologyBundleIO.id`, `StorageAdapterIO.id`,
`ProvenanceStatsIO.id`) are configuration-metadata string slugs and optional scope
identifiers — not Neo4j node ids.

`TimeseriesChannelV2IO.int id` and `long containerId` are already deprecated and tracked:
- `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` (deprecated in-wire via `@Schema(deprecated=true)`)
- `APISIMP-TSCHANNEL-CONTAINER-ID` (slice 1 done; slice 2 gated on TS-IDb migration)

### Finding 2 — Bespoke admin `*ConfigRest`: **NONE beyond tracked**

Only two config-related REST classes remain:
- ✅ `AdminConfigRest.java` — the V2CONV-A4 generic registry
- `JupyterConfigPublicRest.java` — intentional public-read endpoint (auth: any
  authenticated user), distinct from the admin-gated `AdminConfigRest` access.
  Not a violation — documented in J1e design as a public discovery endpoint.

All feature-specific admin config endpoints (semantic, unhide, etc.) have been
collapsed into the generic registry.

### Finding 3 — Per-kind endpoint fragmentation: **NONE**

References are unified under `/v2/references?kind=` (V2CONV-A2). Containers are
unified under `/v2/containers?kind=` (V2CONV-A3). Kind-specific operations
(e.g. git preview at `/v2/git-references/{appId}/preview`) are intentionally kept
separate per the "operations, not CRUD, stay kind-specific" decision.

### Finding 4 — Pagination param inconsistency: **NONE**

The one remaining `@QueryParam("size")` in v2 is `ContainersV2Rest.getThumbnail():1444`
— thumbnail pixel-size parameter (`64`, `200`, `400`), not a pagination param. This
is intentionally named `size` (fixed from `sizeParam` in APISIMP-THUMBNAIL-SIZE-PARAM-NAME,
fire-114). Not a finding.

All pagination list endpoints use `@QueryParam("page")` + `@QueryParam("pageSize")`
consistently after APISIMP-PAGINATION-UNIFY-RECREATE (PR #1887).

### Finding 5 — Forbidden `@Path(Constants.SHEPARD_API + ...)` in v2: **NONE**

Clean. No v2 resource uses the frozen v1 path constant.

### Finding 6 — Error envelopes: **NONE new**

All v2 resources use `problem()` helper or the exception mapper. No new plain-string
4xx bodies introduced since APISIMP-ERROR-ENVELOPE-UNIFY (shipped 2026-06-11).

## New APISIMP rows filed

**None.** The v2 surface is clean on all checked axes.

## Next dispatchable slice

`APISIMP-PERMISSION-AUDIT-NEO4J-ID` (XS) — unblocks once L2 migration clean
confirmation arrives (operator action: verify no null-appId rows on deployed instance).
No code work dispatchable this fire.
