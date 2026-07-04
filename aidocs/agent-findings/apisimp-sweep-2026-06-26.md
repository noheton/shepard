---
stage: deployed
last-stage-change: 2026-06-26
audience: [contributor]
---

# APISIMP Sweep — 2026-06-26 (fire-231)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.

## What I found

### Numeric ID hygiene — CLEAN

Zero v2 `@PathParam` or `@QueryParam` carry a `Long` or `int` type.
All path params are UUID v7 strings. `PermissionsIO` numeric remnants are
`@Schema(deprecated=true)` / `READ_ONLY` (APISIMP-CONTAINERS-PERMS-IO-NUMERIC, done).
`TimeseriesChannelV2IO.id` is `@Schema(deprecated=true)` (APISIMP-TSCHANNEL-INT-ID-DEPRECATE, done).
No new leaks found.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path` annotation.
ArchUnit `V2NamespaceTest` enforces this at build time.

### Bespoke admin `*ConfigRest` outside generic registry — CLEAN

`JupyterConfigPublicRest` (`GET /v2/jupyter/config`) is an intentional
bifurcation: the generic registry requires `instance-admin`; this public read
lets any authenticated user discover whether JupyterHub is available. Design
is correct and documented. No new bespoke admin configs found.

### Per-kind endpoints not unified — CLEAN

- `FileBundleReferenceRest` (`/v2/bundles/…`) — intentional: bundles have
  a group+file structure orthogonal to unified reference CRUD. Not a sprawl finding.
- `SqlTimeseriesRest` (`POST /v2/sql/timeseries`) — query facade, not CRUD.
  Intentionally separate from `ContainersV2Rest`. Not a sprawl finding.
- `FileReferenceV2Rest` (`/v2/files/*`) — all endpoints return 410 Gone
  with migration hints. Fully retired per APISIMP-FILE-PATH-RETIRE-2.

### Pagination — TWO ACTIONABLE FINDINGS

Previous fires added pagination (`page`/`pageSize`) to all unbounded list
endpoints. Two cross-cutting style/consistency gaps remain:

**Gap 1 — Annotation style inconsistency (S)**
Some endpoints use the validated form (`@DefaultValue("0") @PositiveOrZero int page`);
others use the nullable form (`@QueryParam("page") Integer page`) which relies on
null-checks in service code and silently accepts negative values.
Remaining nullable-style sites after the prior wave of fixes:
`ContainersV2Rest` (file `ContainersV2Rest.java`), `CollectionLabJournalEntriesRest`
(intentional backward-compat bulk mode — doc only).

**Gap 2 — Response envelope inconsistency (M)**
Three different envelope patterns coexist across v2 list endpoints:
- `SnapshotListRest`: structured `{items: [], total: N, page: 0, pageSize: 50}` — most discoverable
- `ContainersV2Rest`, `NotificationRest`, `ProjectsRest`, `CollectionWatchesRest`, `CollectionWatchersRest`:
  plain array + `X-Total-Count` header — partially discoverable
- `CollectionV2Rest`, some annotation-list endpoints: plain array, no total count — least discoverable

This forces clients to use different parsing patterns for functionally equivalent
list responses.

### Redundant endpoints — NONE

No pairs of endpoints doing the same thing at different paths.

### Bloated response bodies — NONE

IO classes are properly bounded. No internal OGM implementation details in
JSON shapes (neo4j `version`, `__type`, graph node IDs) found that are not
already deprecated/suppressed.

## Opportunities

1. **APISIMP-PAGINATION-PARAM-STYLE** (S): Standardize nullable-Integer pagination
   params to `@DefaultValue("0") @PositiveOrZero int` pattern. Eliminates silent
   acceptance of negative page numbers and removes null-branch service code.

2. **APISIMP-PAGINATION-ENVELOPE** (M): Converge all v2 list endpoints to the
   structured `{items, total, page, pageSize}` response envelope (same shape as
   `SnapshotListRest`). Eliminates client need to check both body and headers.
   ~8–10 endpoints affected.

## Real-world impact

Pagination envelope inconsistency is a developer-experience friction point:
a client building a generic "load more" paginator must handle three shapes.
Standardizing to a structured envelope enables generic pagination UI components
that work across all Shepard list views without endpoint-specific wrappers.

## Gaps & blockers

None. Both findings are cosmetic/DX improvements — no security, data integrity,
or upgrade-path risk.

## What surprised me

The v2 surface is remarkably clean. After 226 fires of APISIMP work:
- Zero numeric-id leaks in path/query params
- Zero forbidden `SHEPARD_API` constant uses
- Zero bespoke admin config resources outside the generic registry
- Zero per-kind endpoints that should be unified but aren't

The two remaining gaps are both about pagination *style* — the feature itself was
added in prior fires, the inconsistency is in how it was wired.
