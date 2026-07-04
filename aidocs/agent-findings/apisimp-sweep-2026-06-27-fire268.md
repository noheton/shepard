---
stage: deployed
last-stage-change: 2026-06-27
audience: [contributor]
---

# APISIMP Sweep — 2026-06-27 (fire-268)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.

## What I found

### Numeric ID hygiene — CLEAN

Zero v2 `@PathParam` or `@QueryParam` carry a `Long` or `int` type.
All path params are UUID v7 strings. Known tracked exceptions unchanged:
- `PermissionAuditEntryIO.neo4jNodeId` — intentional triage handle,
  tracked as APISIMP-PERMISSION-AUDIT-NEO4J-ID (blocked on L2 clean).
- `ContainersV2Rest` / `ProvenanceRest` `Long` params — epoch-nanosecond /
  millisecond timestamps; confirmed NOT Neo4j IDs.

No new leaks.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path` annotation.
Only known residual: `SpatialDataReferenceRest.java:40` (spatiotemporal plugin —
frozen upstream-compat surface, deferred per APISIMP-V1-PATH-RESIDUAL-1).
ArchUnit `V2NamespaceTest` enforces this at build time.

### Bespoke admin `*ConfigRest` outside generic registry — CLEAN

All domain-specific admin configs are on the generic `AdminConfigRest`
(`GET|PATCH /v2/admin/config/{feature}` per V2CONV-A4). The one intentional
carve-out (`JupyterConfigPublicRest` at `/v2/jupyter/config`) remains correct —
it is a public read endpoint (no `instance-admin` requirement) that the generic
registry cannot provide.

### Per-kind endpoints — CLEAN

No new per-kind paths not yet unified under `?kind=`. Confirmed intentional
carve-outs unchanged from fire-262.

### Plugin REST — CLEAN

16 plugin REST resources scanned; no new forbidden `SHEPARD_API` uses beyond
the known spatiotemporal deferred residual.

### Pagination consistency — ONE FINDING

All except one list endpoint use consistent declarative bean-validation
annotations (`@PositiveOrZero` on `page`, `@Min(1) @Max(200)` on `pageSize`)
or equivalent manual guards (SnapshotListRest clamps, SemanticAnnotationV2Rest
returns 400 from code).

**Finding 1 — `InstanceAdminRest.GET /v2/admin/permission-audit/log` (XS)**

`InstanceAdminRest.java:208` `GET /v2/admin/permission-audit/log`:

- `@QueryParam("page") @DefaultValue("0") int page` — no `@PositiveOrZero`;
  Javadoc says "default 0" but a negative page passes directly to
  `PermissionAuditLogQueryService.query()` with undefined DAO behaviour.
- `@QueryParam("pageSize") @DefaultValue("50") int pageSize` — no `@Min(1)` /
  `@Max`; Javadoc claims "Server-side cap: 500" but no enforcement visible in
  the REST method.
- Response is `Response.ok(rows)` where `rows` is a plain
  `List<PermissionAuditLogEntryIO>` — not a `PagedResponseIO` envelope —
  inconsistent with every other paginated v2 list endpoint.

Severity: LOW (admin-only endpoint; can't be exploited by regular users).
Impact: generic pagination tooling built over v2 list endpoints must special-case
this endpoint; negative-page / zero-size edge cases have undefined server behaviour.

## Opportunities

1. **APISIMP-PERMISSION-AUDIT-LOG-PAGINATION** (XS): Standardise
   `GET /v2/admin/permission-audit/log` to the v2 pagination contract.
   Add `@PositiveOrZero` to `page`; add `@Min(1) @Max(500)` to `pageSize`
   (matching the existing Javadoc claim); wrap result in `PagedResponseIO`
   with total count from the DAO. AC: `?page=0&pageSize=50` returns
   `PagedResponseIO` envelope; `page=-1` returns 400; `pageSize=0` returns 400;
   `mvn verify -pl backend` green.

## Real-world impact

LOW. The surface is genuinely stable after 268 fires of APISIMP work.
The one finding is an admin-only edge case; it does not affect research users
or third-party clients.

## Gaps & blockers

None. Surface is in excellent shape.

## What surprised me

After 268 fires:
- Zero numeric-id leaks in path/query params (excluding known tracked exceptions)
- Zero forbidden `SHEPARD_API` constant uses in v2 resources
- Zero bespoke admin config resources outside the generic registry
- Zero per-kind endpoints that should be unified but aren't
- The one finding (permission-audit/log) is the last visibly unguarded
  pagination endpoint in the entire v2 surface.
