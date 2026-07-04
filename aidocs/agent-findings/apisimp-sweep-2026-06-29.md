---
stage: deployed
last-stage-change: 2026-06-29
---

# API Simplification Sweep — fire-295 (2026-06-29)

## Context

All named APISIMP rows in `aidocs/16` are merged or blocked:

- **Last merged (fire-284)**: `APISIMP-TEMPLATE-TAGS-CAP` — PR #2149 → SHA `68655ad4`
  (LIMIT 500 cap on `ShepardTemplateDAO.listDistinctTags` Cypher).
- **Blocked**: `APISIMP-PERMISSION-AUDIT-NEO4J-ID` — depends on L2 migration
  (neo4j numeric-id elimination pass); no timeline yet.

Per pipeline instructions, fire-295 ran a fresh sweep of the `/v2/` REST
surface before moving to the next priority. MISSING-aas-ui was also fully
completed (all 13 slices merged), so this fire closes that row and adds the
one actionable finding below.

## Sweep scope

All `@Path` endpoints under `/v2/` across:

- `backend/src/main/java/de/dlr/shepard/v2/` (core v2 resources)
- `plugins/*/src/main/java/` (plugin v2 resources: aas, spatiotemporal, video, unhide, git, …)
- `backend/src/main/java/de/dlr/shepard/v2/admin/` (admin v2 resources)
- `backend/src/main/java/de/dlr/shepard/v2/notifications/` (notification resources)

## Findings

### Finding 1 (MINOR, actionable) — `NotificationTransportRest`: bespoke list envelope

**File**: `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/resources/NotificationTransportRest.java:82-88`

`GET /v2/admin/notifications/transports` returns a bespoke `NotificationTransportListIO`
wrapper:

```java
List<NotificationTransportReadIO> items = service.listAll()
    .stream()
    .map(NotificationTransportReadIO::from)
    .toList();
return Response.ok(new NotificationTransportListIO(items)).build();
```

`NotificationTransportListIO` is a one-field wrapper (`transports: List<NotificationTransportReadIO>`)
that predates the `PagedResponseIO<T>` standard established by APISIMP-PAGINATION-ENVELOPE.
It has no `total`, no `page`, no `pageSize`. In practice the transport count is small
(singleton registrations — one SMTP, one Matrix at most) but the inconsistency means
an OpenAPI client must import two different list-shape types for the admin resource
surface.

**Fix**: Migrate `list()` to return `PagedResponseIO<NotificationTransportReadIO>`.
Because `service.listAll()` returns all rows (no pagination in the service layer yet),
the count is simply `items.size()` for the `total` field; `page=0` and
`pageSize=items.size()` echo the current semantics exactly. The `NotificationTransportListIO`
class can be deleted.

**New backlog row**: `APISIMP-NOTIF-TRANSPORT-LIST-ENVELOPE` (XS, queued)

---

### Finding 2 — Spatiotemporal plugin: v1 frozen path (`SpatialDataReferenceRest.java`)

**File**: `plugins/spatiotemporal/src/main/java/de/dlr/shepard/context/references/spatialdata/endpoints/SpatialDataReferenceRest.java`

Uses `@Path(Constants.SHEPARD_API + "/" + Constants.SPATIAL_DATA_REFERENCES)` with
`Long referenceId` path params. **No action** — this is the frozen upstream-byte-compat
v1 surface, identical to `APISIMP-V1-PATH-RESIDUAL-1` (already filed, deferred). The
`/v2/spatial/...` sibling shelf work (SPATIAL-V6-003) is the fix path.

---

### Finding 3 — Spatiotemporal plugin: v1 frozen path (`SpatialDataPointRest.java`)

**File**: `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:51`

Same pattern as Finding 2. Already tracked as `APISIMP-V1-PATH-RESIDUAL-1`. No new row.

---

### Finding 4 — `ShapesPredicatesRest`: unbounded plain array

**File**: `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesPredicatesRest.java`

`GET /v2/shapes/predicates` returns a plain `List<String>` with no pagination.
**Documented-intentional**: set is administratively bounded (≤50 SHACL predicates);
count invariant enforced by `TEMPLATE_TAGS_CAP` parallel precedent. No new row.

---

### Finding 5 — `OntologyAlignmentRest`: unbounded plain array

**File**: `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/OntologyAlignmentRest.java`

`GET /v2/semantic/ontology/alignment` returns a plain unbounded array.
**Documented-intentional**: same pattern as Finding 4, same bounded-set rationale.
No new row.

---

### Finding 6 — `HdfAdminRest`: bespoke admin resource (not on ConfigRegistry)

**File**: `backend/src/main/java/de/dlr/shepard/v2/hdf/resources/HdfAdminRest.java`

`@Path("/v2/admin/hdf")` — starts/stops HDF5 sidecar process. Not on `ConfigRegistry`
because it is an **operational action** (start/stop), not a config-read/write.
This is the same intentional separation as `POST /v2/admin/aas/registrations/sync`.
No new row.

---

### Finding 7 — `DataciteAdminRest` / `EpicAdminRest`: bespoke credential endpoints

**Files**: `backend/src/main/java/de/dlr/shepard/v2/admin/datacite/DataciteAdminRest.java`,
`backend/src/main/java/de/dlr/shepard/v2/admin/epic/EpicAdminRest.java`

Both expose credential-test connection endpoints (`POST .../test-connection`) alongside
their `PATCH .../config` surface. **Documented-intentional**: credential-test is an
operational action, not a ConfigRegistry flow. Pattern mirrors `NotificationAdminRest`
which also has a bespoke `/test-delivery` endpoint (already assessed in
`APISIMP-NOTIF-TEST-DELIVERY-IO`, shipped). No new row.

---

### Finding 8 — `ContainersV2Rest`: `X-Total-Count` header in deprecation window

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`

`X-Total-Count` header still present alongside `PagedResponseIO` body total.
**Already tracked**: `APISIMP-PAGINATION-ENVELOPE` — header retained during deprecation
window; no new action needed this fire.

---

### Finding 9 — `ProjectsRest`: same deprecation-window `X-Total-Count`

**File**: `backend/src/main/java/de/dlr/shepard/v2/project/resources/ProjectsRest.java`

Same deprecation-window pattern as Finding 8. No new row.

---

## Summary

| Finding | Severity | Action |
|---|---|---|
| 1 — `NotificationTransportRest` bespoke list wrapper | MINOR | **New row** `APISIMP-NOTIF-TRANSPORT-LIST-ENVELOPE` (XS) |
| 2 — Spatiotemporal v1 frozen path (Reference) | n/a | Already tracked, frozen |
| 3 — Spatiotemporal v1 frozen path (Point) | n/a | Already tracked, frozen |
| 4 — `ShapesPredicatesRest` unbounded | MINOR | Documented-intentional |
| 5 — `OntologyAlignmentRest` unbounded | MINOR | Documented-intentional |
| 6 — `HdfAdminRest` off ConfigRegistry | MINOR | Documented-intentional |
| 7 — `DataciteAdminRest`/`EpicAdminRest` bespoke | MINOR | Documented-intentional |
| 8 — `ContainersV2Rest` X-Total-Count | MINOR | Deprecation window, tracked |
| 9 — `ProjectsRest` X-Total-Count | MINOR | Deprecation window, tracked |

**One new backlog row filed**: `APISIMP-NOTIF-TRANSPORT-LIST-ENVELOPE` (XS).
The remainder of the `/v2/` surface is clean: consistent `PagedResponseIO` envelopes,
appId-only path params, RFC 7807 4xx bodies everywhere, no redundant endpoints.
