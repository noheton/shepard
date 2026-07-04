---
stage: fragment
last-stage-change: 2026-06-28
audience: [contributor]
---

# APISIMP Sweep — 2026-06-28 (fire-280)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` (core REST resources) +
`plugins/*/src/**` (plugin REST resources). Date: 2026-06-28.

Prior fire: fire-279 merged `APISIMP-UNBOUNDED-FILEGROUPS` (PR #2145). This
pass targeted the plugin REST surfaces, particularly the AAS plugin.

## Category Sweeps — No New Findings in Core

**Category A (Per-kind list endpoints not unified under `?kind=`):** Clean.
All per-kind reference list endpoints retired as HTTP 410 Gone (video tombstone
APISIMP-VIDEO-STREAMREF-PATH verified in this pass — `VideoStreamReferenceV2Rest`
serves only 410 Gone stubs).

**Category B (Bespoke `*ConfigRest` not on generic `/v2/admin/config/{feature}`):** Clean.
All plugin admin surfaces delegate via `AdminConfigRest` or are IDTA-protocol-specific.

**Category C (Numeric Neo4j IDs leaking into the wire):** Clean in v2 core and
non-spatiotemporal plugins. `APISIMP-PERMISSION-AUDIT-NEO4J-ID` tracked separately.

**Category D (Pagination inconsistency):** Core endpoints clean after fire-276 (PR #2140)
and fire-277 (PR #2142). Plugin surfaces checked this pass — see Finding 1 below.

**Category E (Error envelope inconsistency):** Clean in v2 core. `PublicationsListRest`
`GET /v2/{kind}/{appId}/publications` marked deprecated; its 4xx responses use
`problem()` — not a finding.

**Category F (Endpoints superseded by canonical surface):** Clean.

**Category G (Forbidden `@Path(Constants.SHEPARD_API + ...)` in v2):** Clean.
Residual spatiotemporal plugin already tracked APISIMP-V1-PATH-RESIDUAL-1.

**Category H (Response verbosity — Long id fields no caller reads):** Clean.
No v2 IO class exposes a bare Neo4j node id.

## New Findings

### Finding 1 — APISIMP-AAS-SHELLS-LIST-ENVELOPE

**Category:** D — Pagination inconsistency (plugin REST surface)
**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:82-118`

`AasShellsRest.listShells()` (`GET /v2/aas/shells`) accepts `page` and `pageSize`
query params — both wired into `QueryParamHelper` and passed to
`collectionDAO.findAllCollectionsByShepardId(params, username)` at the DAO level.
However, the response is a plain `List<AasShellIO>`:

```java
List<AasShellIO> shells = collectionDAO
    .findAllCollectionsByShepardId(params, username)
    .stream()
    .map(mappingService::toShell)
    .toList();
return Response.ok(shells).build();  // line 117 — plain array, no envelope
```

The pagination filtering works at the DAO level, but the caller receives a plain
JSON array with no `total`, no `page`, no `pageSize` echo — no way to detect that
there are more pages. This contradicts the v2 pagination contract established by
APISIMP-PAGINATION-ENVELOPE (PR #2102-2105): all v2 list endpoints return
`PagedResponseIO<T>` with `{items, total, page, pageSize}`.

An operator with hundreds of Collections would receive a silently truncated list
at the default `pageSize=100` with no signal that pages 1+ exist.

Note on IDTA AAS v3 spec: IDTA-01002-3-2 defines its own pagination shape
(`GetAssetAdministrationShellsResult` with `result` array + `paging_metadata`).
However `GET /v2/aas/shells` is Shepard's own REST surface (not a compliant AAS
Repository API server), so `PagedResponseIO<AasShellIO>` is the correct shape for
v2 consistency. A future strict-AAS compliance milestone would need a separate
path (e.g. `/aas/shells` without the `/v2/` prefix and its own response shape).

**Fix:** Retrieve `collectionDAO.countAllCollectionsByShepardId(username)` for the
total; wrap the response in `new PagedResponseIO<>(shells, total, safePage, safeSize)`.
The `page`/`pageSize` params already flow to the DAO via `QueryParamHelper`; only
the response builder needs updating. No DAO changes needed if a
`count(QueryParamHelper, String)` overload (or naked count) is added.

**Acceptance criteria:** `GET /v2/aas/shells?page=0&pageSize=10` returns
`{items:[...], total:N, page:0, pageSize:10}` envelope; `aasDisabled()` still
returns 501; `mvn verify -pl plugins/aas` green.

**Size:** S
**Filed as:** `APISIMP-AAS-SHELLS-LIST-ENVELOPE`

---

### Finding 2 — APISIMP-AAS-SUBMODELS-UNBOUNDED

**Category:** D — Pagination inconsistency (plugin REST surface, minor)
**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:153-185`

`AasShellsRest.listSubmodels()` (`GET /v2/aas/shells/{aasId}/submodels`) returns
one `AasReferenceIO` per top-level DataObject in the Collection. A Collection with
thousands of top-level DataObjects returns an unbounded list with no server cap or
pagination params. The DAO call is `dataObjectDAO.findTopLevelByCollectionAppId(appId)`
— unbounded.

```java
List<DataObject> dataObjects = dataObjectDAO.findTopLevelByCollectionAppId(appId);
return Response.ok(mappingService.toSubmodelRefs(dataObjects)).build();  // unbounded
```

In practice top-level DataObjects are typically < 100 per Collection, but there is
no enforced cap. The fix is lighter than Finding 1: add optional `page`/`pageSize`
params (default 0/50, cap 200) and a `countTopLevelByCollectionAppId` DAO overload.
An alternative is a server-side cap of 500 with no pagination params (simpler for
IDTA API clients that don't expect `PagedResponseIO`).

**Size:** XS
**Filed as:** `APISIMP-AAS-SUBMODELS-UNBOUNDED`

## Summary

Swept all core v2 REST classes + plugin REST surfaces. The core surface is stable.
Plugin surfaces are mostly clean; the AAS plugin has two pagination findings.

Found 2 new actionable findings, both in `plugins/aas/`:
- **APISIMP-AAS-SHELLS-LIST-ENVELOPE** (S) — `GET /v2/aas/shells` accepts pagination
  params but returns a plain `List<AasShellIO>` — no `PagedResponseIO` envelope, no
  `total`. Silently truncates when `pageSize=100` is the default and an operator has
  more than 100 Collections.
- **APISIMP-AAS-SUBMODELS-UNBOUNDED** (XS) — `GET /v2/aas/shells/{aasId}/submodels`
  returns an unbounded list of Submodel references with no server cap or pagination.

Both can be resolved in a single micro-PR touching only `AasShellsRest.java` and
the `CollectionDAO`/`DataObjectDAO` count methods.

Dispatch next fire: `APISIMP-AAS-SUBMODELS-UNBOUNDED` (XS) or
`APISIMP-AAS-SHELLS-LIST-ENVELOPE` (S) — implement as one combined PR.
