---
stage: deployed
last-stage-change: 2026-06-29
---

# API Simplification Sweep — fire-311 (2026-06-29)

## Context

All named APISIMP rows in `aidocs/16` (through fire-304/305) are merged, blocked, or
deferred. The fire-295 sweep (earlier today, same date) already filed
`APISIMP-NOTIF-TRANSPORT-LIST-ENVELOPE` (shipped fire-297), `APISIMP-PLUGINS-LIST-ENVELOPE`
(shipped fire-299), `APISIMP-FEATURES-LIST-ENVELOPE` (shipped fire-300),
`APISIMP-GIT-CRED-LIST-ENVELOPE` (shipped fire-301), `APISIMP-CONTAINERS-LIST-ENVELOPE`
(shipped fire-302), `APISIMP-INSTANCE-ADMIN-LIST-ENVELOPES` (shipped fire-302), and several
others — all resolved. Immediate prior merged slice: PR #2188 `b1f7883f`
(MCP-COV-02-2-REF-CRUD — `ReferencesMcpTools`: 5 reference CRUD tools + 27 tests).

Per pipeline instructions, fire-311 ran a fresh sweep before moving to the next priority.

## Sweep scope

- `backend/src/main/java/de/dlr/shepard/v2/` (all v2 resources)
- `plugins/*/src/main/java/` (all plugin v2 resources — aas, spatiotemporal, video, unhide,
  git, v1-compat, wiki-writer, imagebundle)
- Focus: per-kind endpoints outside `?kind=`; bespoke `*ConfigRest` not on generic
  registry; numeric id leaks in `@PathParam`/`@QueryParam`/response bodies; pagination
  inconsistencies; superseded/tombstone endpoints; forbidden `@Path(Constants.SHEPARD_API...)`.

## Findings

### Finding 1 — Spatiotemporal plugin: v1 paths + numeric container IDs (SpatialDataPointRest)

**File**: `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:51,117,155`

Uses `@Path(Constants.SHEPARD_API + "/" + Constants.SPATIAL_DATA_CONTAINERS)` with
`@PathParam("spatialDataContainerId") Long containerId` path parameters (e.g., `GET /spatial-data-containers/42`).

**No new action** — already tracked as:
- `APISIMP-V1-PATH-RESIDUAL-1` (line 3722, ⛔ deferred — frozen upstream-compat surface)
- `APISIMP-NUMERIC-ID-BATCH-2` (line 3721, ⛔ deferred — frozen upstream-compat surface)

Fix path: `PLUGIN-V2-001` ships the `/v2/spatial-containers/{appId}` sibling shelf; the
v1 resource stays frozen indefinitely for upstream-compat.

---

### Finding 2 — Spatiotemporal plugin: bare array response (SpatialDataPointRest)

**File**: `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:98`

`GET /spatial-data-containers` returns `Response.ok(result).build()` with a bare
`List<SpatialDataContainerIO>` — no `PagedResponseIO` envelope.

**No new action** — this endpoint is on the frozen v1 surface (`APISIMP-V1-PATH-RESIDUAL-1`,
deferred). The `/v2/` sibling shelf (`PLUGIN-V2-001`) ships with the standard
`PagedResponseIO<SpatialContainerV2IO>` envelope from day one, per the v2 pagination contract.

---

### Finding 3 — `ProvenanceRest`: epoch-ms `since`/`until` numeric query params

**File**: `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:110-112,167-169` (and 7 additional occurrences)

Multiple `listActivities`-family endpoints accept `@QueryParam("since") Long` and
`@QueryParam("until") Long` as millisecond epoch timestamps. While these are **timestamps**
(not Neo4j internal IDs), they are `Long` numerics on the v2 REST surface, which callers
must know to interpret correctly (epoch-ms, not ISO 8601).

`APISIMP-PROVENANCE-CURSOR-UNDOCUMENTED` (shipped fire-217/PR #2076) documented the
**cursor pagination design** as intentional. It did not address the numeric-vs-string
representation of the time bounds.

**Assessment**: `Long` epoch-ms timestamps are technically exempt from the "appId/UUID
only" numeric-id rule (they are not database IDs). However, ISO 8601 strings
(`?since=2026-01-01T00:00:00Z`) are more ergonomic, REST-idiomatic, and consistent with
the `InstanceAdminRest.permissionAuditLog()` `from`/`to` params (which already document
ISO-8601 per `APISIMP-INSTANCE-ADMIN-AUDIT-FILTER-PARAMS-UNDOCUMENTED`, shipped). The
inconsistency between two provenance-adjacent endpoints — one numeric, one ISO 8601 — is
worth resolving.

**New backlog row**: `APISIMP-PROV-ISO8601-TIMESTAMPS` (XS, queued).

---

### Finding 4 — All other v2 `@Path` endpoints

Full scan of `backend/src/main/java/de/dlr/shepard/v2/` and all plugin v2 resources confirms:

- **Per-kind endpoints**: None found outside `?kind=`. References fully unified under
  `GET /v2/references?kind=…`; containers already use `?kind=` param; AAS uses distinct
  entity-type paths (intentional — Shells vs Submodels are different resource types).
- **Bespoke `*ConfigRest` outside ConfigRegistry**: `AdminConfigRest.java` is the generic
  registry; operational-action endpoints (`HdfAdminRest` start/stop, `AasAdminRest`
  import-templates, notification test-delivery) are correctly NOT on the registry — they
  are actions, not config. No new row.
- **Numeric id leaks in v2 response bodies**: None beyond already-tracked rows
  (`APISIMP-PERMISSION-AUDIT-NEO4J-ID` blocked, spatiotemporal frozen).
- **Superseded endpoints**: No remaining endpoints superseded by `POST /v2/shapes/render`.
  Lab journal render, git preview, and similar are special-purpose operational surfaces.
- **Tombstone endpoints**: Four tombstones have `Location` headers (APISIMP-TOMBSTONE-REMOVAL-WINDOW,
  shipped). No new tombstones observed.
- **Forbidden `@Path(Constants.SHEPARD_API...)`**: Only in `de.dlr.shepard.*` (v1, correct)
  and `plugins/spatiotemporal/` (frozen upstream-compat, tracked). No v2 namespace violations.

## Summary

| Finding | Severity | Action |
|---|---|---|
| 1 — Spatiotemporal v1 path + numeric ids | n/a | Already tracked (APISIMP-NUMERIC-ID-BATCH-2 + APISIMP-V1-PATH-RESIDUAL-1), deferred, fix via PLUGIN-V2-001 |
| 2 — Spatiotemporal bare array response | n/a | Frozen v1 surface; `/v2/` sibling (PLUGIN-V2-001) ships with PagedResponseIO |
| 3 — ProvenanceRest epoch-ms Long params | XS | **New row** `APISIMP-PROV-ISO8601-TIMESTAMPS` (XS, queued) |
| 4 — All other v2 surface | n/a | Clean: consistent PagedResponseIO envelopes, appId-only path params, RFC 7807 4xx bodies, no redundant endpoints |

**One new backlog row filed**: `APISIMP-PROV-ISO8601-TIMESTAMPS` (XS, queued).
The v2 surface is otherwise conformant. Remaining open APISIMP items are either blocked
on L2 migration (APISIMP-PERMISSION-AUDIT-NEO4J-ID) or deferred to the spatial v2
sibling shelf (APISIMP-NUMERIC-ID-BATCH-2, APISIMP-V1-PATH-RESIDUAL-1).
