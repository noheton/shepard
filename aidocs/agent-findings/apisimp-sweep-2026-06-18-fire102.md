---
stage: fragment
last-stage-change: 2026-06-18
---

# APISIMP surface sweep — fire-102 (2026-06-18)

**Scope:** full scan of `/v2` REST surface in `backend/src/main/java/de/dlr/shepard/v2/`
(58 REST resource files) + plugin `@Path` annotations (22 plugin REST files).
Post-fire-101 state: all V2CONV A1–A7 + B1–B8 rows complete; #1977/1978/1979 applied to main.

**Checklist:**
- [x] New `@Path(Constants.SHEPARD_API + ...)` additions — none found (spatiotemporal frozen surface unchanged)
- [x] Per-kind reference endpoints not yet unified under `?kind=` — all migrated; tombstones in place
- [x] Bespoke admin `*ConfigRest` not on generic registry — none; all features on generic registry or correct sister-endpoint pattern
- [x] Numeric Neo4j id leaks in `@PathParam`/`@QueryParam`/response bodies — 1 known blocked (APISIMP-PERMISSION-AUDIT-NEO4J-ID); no new critical leaks
- [x] Inconsistent pagination param names — `@QueryParam("size")` in `ContainersV2Rest:1336` is thumbnail pixel-size, not pagination; not a finding
- [x] Empty-body 4xx responses — none; all v2 resources use `ProblemJson` consistently
- [x] Endpoints superseded by `POST /v2/shapes/render` — none new
- [x] `/v2/bundles` per-kind surface — `GET /v2/bundles/{bundleAppId}` intentionally richer than generic reference GET (returns embedded groups+files); group CRUD sub-resources have no generic equivalent; not a finding

## What I checked

### Per-kind reference migration status (post-fire-101)
| Surface | Status |
|---|---|
| `POST /v2/files` (create) | ✅ 410 (#1966) |
| `GET|PATCH|DELETE /v2/files/{appId}*` | ✅ 410 (#1978) |
| `/v2/data-objects/{doId}/git-references` | ✅ 410 (#1967) |
| `/v2/data-objects/{doId}/video-stream-references` | ✅ 410 (#1970) |
| `TimeseriesAnnotationRest` + `VideoAnnotationRest` collision | ✅ unified (#1971) |
| `?kind=structured-data` on `/v2/references` | ✅ handler + FE composable (#1974/1975/1976) |
| `?kind=bundle` on `/v2/references` | ✅ `FileBundleReferenceKindHandler` (#1979) |

### Admin config surface
All runtime-configurable features are on `GET|PATCH /v2/admin/config/{feature}`. Bespoke admin REST
classes retain only credential-rotation and operational one-shot endpoints (the "sister endpoints"
pattern — these are intentional, not findings). User-management sub-paths
(`/v2/admin/users/{username}/git-credentials`, `/v2/admin/users/{username}/orcid`,
`/v2/admin/users/mirror`) are user-administration surfaces, not feature config toggles — correct
to be separate.

### Response IO numeric-id suppression
`CollectionV2IO`, `BasicContainerV2IO`, and `DataObjectV2IO` all carry `@JsonIgnoreProperties({"id"})`
suppressing the Neo4j internal id. Confirmed consistent.

## New findings filed this sweep

### APISIMP-PROVENANCE-STATS-ID-PARAM (MINOR, XS)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:424`
- **Symptom:** `GET /v2/provenance/stats` accepts `@QueryParam("id") String id` — a bare `id`
  parameter that serves two purposes: for `scope=collection` it is the collection's `appId` (UUID v7);
  for `scope=user` it is the caller's username. The `@Parameter` description says "Entity appId for
  scope=collection, username for scope=user" but the param name `id` is ambiguous and inconsistent
  with the v2 convention of using `appId`-suffixed names.
- **Impact:** Callers building a generic v2 client must special-case this endpoint's dual-purpose
  parameter. Minor naming friction; the description documents the overloading but the name doesn't.
- **AC:** Rename `@QueryParam("id")` to `@QueryParam("entityId")` in `ProvenanceRest.stats()`;
  update `@Parameter` description accordingly; update any frontend caller of `GET /v2/provenance/stats`.
  `mvn verify -pl backend` green. No shim needed (pre-production admin endpoint).

### APISIMP-WIKI-WRITER-COLLECTION-IN-PATH (MINOR, S)
- **File:** `plugins/wiki-writer/src/main/java/de/dlr/shepard/plugins/wikiwriter/resources/WikiWriterRest.java:50`
- **Symptom:** `POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write`
  includes `{collectionAppId}` in the path even though the DataObject already belongs to exactly one
  Collection (findable via `DataObject.getCollection()`). The implementation resolves
  `collectionOgmId = resolveOrNull(collectionAppId)` then passes it to `wikiWriterService.wikiWrite()`.
  This forces callers to know (and supply) both identifiers, and diverges from the v2 convention that
  per-DO actions live at `/v2/data-objects/{appId}/...` (or `/v2/references/{appId}/...` for reference
  actions).
- **Impact:** Minor caller friction: an agent or script invoking wiki-write must know the
  `collectionAppId` even when it already has `dataObjectAppId`. Also, the permission check
  (`isAccessAllowedForDataObjectAppId`) doesn't use `collectionOgmId` — the collection is
  redundant for the auth gate.
- **AC:** Move path to `/v2/data-objects/{dataObjectAppId}/wiki-write`; resolve `collectionOgmId`
  from `dataObjectOgmId` inside `WikiWriterService.wikiWrite()` (via
  `dataObjectDAO.findByAppId(dataObjectAppId).getCollection().getId()`); old path returns 410 Gone
  with `Location: /v2/data-objects/{dataObjectAppId}/wiki-write`; `mvn verify` (full incl. plugins)
  green.

## Conclusion

Surface is clean post-fire-101. All per-kind CRUD is unified under `/v2/references`; all admin
configs are on the generic registry or correct sister-endpoint pattern; no new numeric-id leaks;
error envelopes are consistent throughout.

Two MINOR findings filed: `APISIMP-PROVENANCE-STATS-ID-PARAM` (XS) and
`APISIMP-WIKI-WRITER-COLLECTION-IN-PATH` (S). Smallest dispatchable next fire:
`APISIMP-PROVENANCE-STATS-ID-PARAM` (XS).
