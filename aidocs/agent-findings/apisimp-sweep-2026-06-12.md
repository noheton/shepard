---
stage: concept
last-stage-change: 2026-06-12
---
# APISIMP Sweep — 2026-06-12

**Date:** 2026-06-12
**SSOT:** `aidocs/platform/191-v2-surface-convergence.md`
**Previous sweep:** `aidocs/agent-findings/apisimp-sweep-2026-06-11.md`

Fourth incremental audit of the `/v2` REST surface. Run after the 2026-06-11
sweep actioned all third-pass rows (THERMO-ADMIN-CONFIG, FC-SDC-LINKED-DO-APPID,
CHANNEL-ANNOT-APPID, PROJECT-IO-DROP-LEGACY-ID all shipped or in-progress).
This pass checks the residual surface and surfaces three genuinely new findings.

Scope: `backend/.../v2/**` + `plugins/*/.../v2/**` + `frontend/` callers.
Research-and-backlog only; no production Java/Vue touched.

## Findings

### Finding 1 — `ContainerAnnotated` class calls deleted container-SA endpoints (CRITICAL live bug)

**Severity:** CRITICAL
**Location:** `frontend/composables/annotated.ts:152–203`
**Row filed:** `APISIMP-CONTAINER-ANNOTATED-FE-DEAD-ENDPOINTS`

`annotated.ts` (lines 152–203) defines an abstract `ContainerAnnotated` class
with three concrete subclasses:

```ts
export class AnnotatedTimeseriesContainer extends ContainerAnnotated {
  readonly basePath = "timeseries-containers";
}
export class AnnotatedFileContainer extends ContainerAnnotated {
  readonly basePath = "file-containers";
}
export class AnnotatedStructuredDataContainer extends ContainerAnnotated {
  readonly basePath = "structured-data-containers";
}
```

The class builds URLs like:

```ts
`${v2BaseUrl()}/v2/${this.basePath}/${this.containerId}/annotations`
```

These three URL patterns map to the bespoke container-SA resources
(`TimeseriesContainerSemanticAnnotationRest`, `FileContainerSemanticAnnotationRest`,
`StructuredDataContainerSemanticAnnotationRest`) that were **deleted** in
APISIMP-SA-CONT-DELETE (merged 2026-06-10). Any attempt to GET/POST/DELETE
annotations via this class returns 404.

Six live call sites (all on container detail pages):

| File | Lines | What fails |
|---|---|---|
| `pages/containers/timeseries/[containerId]/index.vue` | 280, 284 | Annotation panel on TS container detail — empty + silent 404 |
| `pages/containers/files/[containerId]/index.vue` | 147, 151 | Annotation panel on File container detail |
| `pages/containers/structureddata/[containerId]/index.vue` | 198, 202 | Annotation panel on SDC detail |

Additionally, `ContainerAnnotated.deleteAnnotation(annotationId: number)` takes a
numeric annotation id (line 172), which was the old endpoint's interface. The
correct replacement is `DELETE /v2/annotations/{annotationAppId}`.

**Target:** Rewrite `ContainerAnnotated` (or the three subclasses) to call
`GET /v2/annotations?subjectAppId={containerAppId}` (list),
`POST /v2/annotations` (create, with `subjectAppId` + `subjectKind` in body),
`DELETE /v2/annotations/{annotationAppId}` (delete by appId).
The constructor must accept `containerAppId: string` instead of `containerId: number`.
Six call sites in three container pages update to pass the container's `appId`
(resolved from the container accessor) rather than the legacy numeric route param.

---

### Finding 2 — `Trace3DEditChannelsDialog` and `ViewRecipeBuilderDialog` use numeric `containerId` on appId-keyed channel-annotation POST (MAJOR live bug)

**Severity:** MAJOR
**Location:**
- `frontend/components/container/timeseries/Trace3DEditChannelsDialog.vue:50`
- `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue:76`
**Row filed:** `APISIMP-TRACE3D-AXIS-ANNOT-APPID-FIXUP`

Both components have a `saveAnnotations()` function that POSTs axis-role
annotations for Trace3D channel assignment. After APISIMP-TSCONT-APPID-KEY (all
4 slices shipped), the backend `TimeseriesChannelAnnotationRest` accepts
`containerAppId` (String UUID). Both components have a `containerAppId: string`
prop available — but the annotation POST URL still uses the numeric `containerId`:

```ts
// Trace3DEditChannelsDialog.vue:50 — wrong
`/v2/timeseries-containers/${props.containerId}/channels/${ch.shepardId}/annotations`

// ViewRecipeBuilderDialog.vue:76 — wrong
`/v2/timeseries-containers/${props.containerId}/channels/${ch.shepardId}/annotations`
```

Both components already carry both `containerId: number` and `containerAppId: string`
props. The fix is one-line each: replace `props.containerId` with `props.containerAppId`.
These POST calls will silently 404 today whenever a user saves Trace3D axis annotations.

Note: the APISIMP-TSCONT-APPID-KEY-3 slice (PR #1845) notes "Trace3DEditChannelsDialog
migrated to `containerAppId: string`" in its commit description, but the actual
`saveAnnotations` function call at line 50 still uses `props.containerId`. The prop
was added; the URL string was missed.

---

### Finding 3 — `PinnedChannelTile` uses numeric Postgres serial `containerId` on appId-keyed data endpoint (MAJOR live bug)

**Severity:** MAJOR
**Location:**
- `frontend/components/container/timeseries/PinnedChannelTile.vue:108`
- `frontend/composables/container/usePinnedChannels.ts:20`
**Row filed:** `APISIMP-PINNED-CHANNEL-TILE-APPID`

`PinnedChannelTile.vue:108` calls:

```ts
`${v2Base()}/v2/timeseries-containers/${props.channel.containerId}/channels/${props.channel.shepardId}/data`
```

The `PinnedChannel` interface (composable `usePinnedChannels.ts:20`) stores
`containerId: number` documented as "Postgres serial id of the owning
TimeseriesContainer." After APISIMP-TSCONT-APPID-KEY, the backend endpoint
`GET /v2/timeseries-containers/{containerAppId}/channels/{shepardId}/data`
calls `timeseriesContainerService.getContainerByAppId(containerAppId)` — which
does a `findByAppId(appId)` lookup. A Postgres serial integer passed as the
`containerAppId` URL segment will never match a UUID v7 and the call returns 404.

The UX-PIN1 feature (pinned channel tiles on PersonalDigest) is completely broken.

**Target:**
1. Add `containerAppId: string` to `PinnedChannel` interface in `usePinnedChannels.ts`.
2. Wherever `PinnedChannel` entries are created (the pin action in
   `TimeseriesMeasurementsTable.vue` or wherever the UX-PIN1 pin button lives),
   populate `containerAppId` from the loaded container's appId.
3. Update `PinnedChannelTile.vue:108` to use `props.channel.containerAppId`.
4. `containerId` may be kept temporarily for the navigation `containerPath` string
   (which is a V1-EXCEPTION per `HeaderBar.vue:646-650` — until CONTAINER-V2-ROUTE
   ships) but should be clearly documented as nav-only, not API-addressing.

---

### Finding 4 — `NotificationAdminRest` 200 OK transport-test response returns plain string (MINOR)

**Severity:** MINOR
**Location:** `backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationAdminRest.java:153`
**Row filed:** `APISIMP-NOTIF-TEST-RESP-ENVELOPE`

```java
return Response.ok().entity("delivered via " + transport.getKind()).build();
```

APISIMP-ERROR-ENVELOPE-UNIFY (shipped 2026-06-11, all 8 slices) fixed plain-string
4xx/5xx bodies across all v2 resources. This single 200 OK response was out of scope
(the task brief targeted 4xx bodies). The result is an inconsistency: the 200 uses a
plain string while the `problem()` helper on the same method returns a typed `ProblemJson`.

**Target:** Wrap in a typed response object, e.g.:
```java
return Response.ok(Map.of("status", "delivered", "transport", transport.getKind())).build();
```
Produces a JSON object instead of a bare string. No FE caller reads this body
(the test endpoint is admin-only, result observed in logs/UI toast). Backend-only XS fix.

---

### Finding 5 — `DataObjectSummaryIO.long id` exposed in `/v2/` predecessor/successor chain responses (MINOR)

**Severity:** MINOR
**Location:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectSummaryIO.java:17`
**Row filed:** `APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID`

`DataObjectSummaryIO` carries `private long id` alongside `private String appId`.
This IO class is used in:
- `GET /v2/collections/{appId}/data-objects/{appId}/predecessors` → list response
- `GET /v2/collections/{appId}/data-objects/{appId}/successors` → list response
- `GET /v2/collections/{appId}/data-objects/{appId}/predecessor-chain` → list response

The FE interface `DataObjectChainItem` in `useFetchPredecessorChain.ts:13` declares
`id: number` matching the wire field, but no call site in `frontend/` navigates or
resolves via this numeric id — the composable only reads `appId`, `name`, `status`.

The predecessor/successor endpoints are fork-only (not in upstream `openapi-5.4.0.json`).
The "upstream callers" justification from `DataObjectSummaryIO.java:10` does not apply.

**Target:** Drop `private long id` from `DataObjectSummaryIO` (and the constructor
parameter + getter). Remove `this.id = d.getShepardId() != null ? d.getShepardId() : -1L`
from the constructor. Drop `id: number` from `DataObjectChainItem` in `useFetchPredecessorChain.ts`
and from the test fixture `useFetchPredecessorChain.test.ts:28-38`. Wire break on
`/v2/.../predecessors` + `/v2/.../successors` + `/v2/.../predecessor-chain` (pre-prod).
No FE navigation change needed.

---

### Finding 6 — `placeholderRegistry.ts` stale `{containerId}` in channel-annotation endpoint description (MINOR)

**Severity:** MINOR
**Location:** `frontend/components/common/placeholder/placeholderRegistry.ts:225-226`
**Row filed:** `APISIMP-PLACEHOLDER-REGISTRY-STALE-PATH`

```ts
endpoint: "/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations",
```

After APISIMP-TSCONT-APPID-KEY, the correct path segment is `{containerAppId}` not
`{containerId}`. The placeholder is documentation/UI only (no live API call), but
a developer reading the UI stub will be sent to the wrong path shape.

**Target:** Update line 226 to `{containerAppId}`. One-line XS fix.

---

## Summary table

| ID | Finding | Severity | Size | Files |
|---|---|---|---|---|
| APISIMP-CONTAINER-ANNOTATED-FE-DEAD-ENDPOINTS | `ContainerAnnotated` calls deleted container-SA endpoints — live 404 on all 3 container annotation panels | CRITICAL | S | `annotated.ts:152–203`; 3 container detail page call sites |
| APISIMP-TRACE3D-AXIS-ANNOT-APPID-FIXUP | `Trace3DEditChannelsDialog` + `ViewRecipeBuilderDialog` POST axis annotations to numeric-containerId URL — live 404 | MAJOR | XS | `Trace3DEditChannelsDialog.vue:50`; `ViewRecipeBuilderDialog.vue:76` |
| APISIMP-PINNED-CHANNEL-TILE-APPID | `PinnedChannelTile` fetches channel data with numeric Postgres `containerId` — live 404, UX-PIN1 broken | MAJOR | XS–S | `PinnedChannelTile.vue:108`; `usePinnedChannels.ts:20` |
| APISIMP-NOTIF-TEST-RESP-ENVELOPE | `NotificationAdminRest` 200 OK delivers plain string, not typed JSON object | MINOR | XS | `NotificationAdminRest.java:153` |
| APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID | `DataObjectSummaryIO.long id` leaks Neo4j OGM id in predecessor/successor responses | MINOR | XS | `DataObjectSummaryIO.java:17`; `useFetchPredecessorChain.ts:13` |
| APISIMP-PLACEHOLDER-REGISTRY-STALE-PATH | `placeholderRegistry.ts` references obsolete `{containerId}` path segment for channel annotations | MINOR | XS | `placeholderRegistry.ts:225-226` |

---

## What already shipped (not re-filed)

- **APISIMP-SA-CONT-DELETE** ✅ (3 bespoke container-SA REST resources deleted 2026-06-10)
- **APISIMP-SA-CONT-PROBE-REPOINT** ✅ (e2e probe repointed 2026-06-10)
- **APISIMP-PV-UNIFY** ✅ (payload-version resources collapsed 2026-06-11)
- **APISIMP-TSCONT-APPID-KEY** ✅ (all 4 slices shipped, `bdf4090` #1846 2026-06-11)
- **APISIMP-PAGINATION-UNIFY** ✅ slice 1 (PR #1847 pending CodeQL race) + slice 2 merged
- **APISIMP-ERROR-ENVELOPE-UNIFY** ✅ (all 8 slices merged 2026-06-11)
- **APISIMP-THERMO-ADMIN-CONFIG** ✅ (deleted 2026-06-11)
- **APISIMP-FC-SDC-LINKED-DO-APPID** ✅ (merged PR #1852 `df8b242e` 2026-06-11)
- **APISIMP-SNAPSHOT-RESP-SIZE** ⏳ queued (depends on #1847)
- **APISIMP-PROJECT-IO-DROP-LEGACY-ID** ✅ confirmed merged (ProjectIO, SubCollectionItemIO, ProjectByAnnotationItemIO no longer carry `Long id`)
- **APISIMP-CHANNEL-ANNOT-APPID** ✅ (annotationId → annotationAppId String 2026-06-11)

---

## Patterns verified clean this pass

| Check | Status |
|---|---|
| `@PathParam.*Long` in `v2/**/*.java` | **Clean** — ProvenanceRest `?since`/`?until` are temporal epoch-millis (not Neo4j ids); `start`/`end` in TS data endpoint are also epoch-millis timestamps. No Neo4j id leaks. |
| `@QueryParam("size")` on pagination | **7 pending** — covered by APISIMP-PAGINATION-UNIFY-1 (PR #1847). `ThumbnailRest.?size` is an image-dimension param — intentional, not pagination. |
| `@QueryParam("limit")` without `offset` | **Clean** — zero hits. |
| Bespoke `*ConfigRest` or `*ConfigAdminRest` outside generic registry | **Clean** — `ThermographyConfigAdminRest` was the last one; deleted by APISIMP-THERMO-ADMIN-CONFIG. `JupyterConfigPublicRest` is intentionally public/non-admin. Plugin configs (`AasConfigDescriptor`, `LegacyV1ConfigDescriptor`) use the descriptor SPI correctly. |
| `@Path(Constants.SHEPARD_API + …)` in `/v2/` or plugin code | **Clean for plugins we author** — only `SpatialDataPointRest` + `SpatialDataReferenceRest` in `plugins/spatiotemporal/` remain, correctly marked frozen upstream-byte-compat surfaces (PLUGIN-V2-001). |
| Per-kind endpoints not yet unified | **Clean** — `/v2/timeseries-containers`, `/v2/file-containers`, `/v2/structured-data-containers` coexist with `/v2/containers` (generic). The per-kind paths carry domain-specific operations (stats, thumbnail, presigned URL) that the generic surface doesn't expose — intentional specialisation, not redundancy. |
| Long id in IO classes | **4 remaining (see below)** — ContainerSummaryIO (blocked by CONTAINER-V2-ROUTE), DataObjectSummaryIO (new F5), PermissionAuditEntryIO (intentional: Neo4j orphan-triage tool showing `id(e)`), PermissionAuditLogEntryIO (intentional: Postgres serial PK). |
| RFC-7807 error envelopes | **Near-clean** — one remaining 200 OK plain string (F4 above). |

### Intentional legacy debt (not filed, previously documented)

| Surface | Reason not filed |
|---|---|
| `ContainerSummaryIO.long id` (`CollectionContainersRest`) | Blocked by CONTAINER-V2-ROUTE — frontend `CollectionContainersPanel.vue:19` uses `c.id` for navigation; will clear when container routes accept appId. Already noted in apisimp-sweep-2026-06-11 §scan-observations. |
| `WatchIO.containerOgmId` (nullable Long) | FE `WatchedContainersPanel.vue:103` uses it for the `/containers/{kind}/{ogmId}` route link. V1-EXCEPTION per code comment (`watches/io/WatchIO.java:31-37`); clears with CONTAINER-V2-ROUTE. |
| `ContainerRefIO.containerId` + `referenceId` (long) | DataObject detail's `ContainerRefIO` carries both numeric ids for upstream v1 payload resolution. Javadoc at `ContainerRefIO.java:38-42` explicitly describes the upstream-compat reason. |
| `PermissionAuditEntryIO.long id` | Neo4j `id(e)` for orphan-triage — admin-only diagnostic tool; not used for addressing. |
| `PermissionAuditLogEntryIO.long id` | Postgres serial PK of the audit log table — appropriate surrogate key for a log record, not a Neo4j OGM id. |
| Spatial plugin v1 frozen surface | PLUGIN-V2-001 already queued. |

---

## Priority order for dispatch

1. **APISIMP-CONTAINER-ANNOTATED-FE-DEAD-ENDPOINTS** (CRITICAL, S) — container annotation panels 404 silently. Rewrite `ContainerAnnotated` + 3 subclasses to call `/v2/annotations?subjectAppId=`. Six call sites update to pass `containerAppId`.
2. **APISIMP-TRACE3D-AXIS-ANNOT-APPID-FIXUP** (MAJOR, XS) — one-line each in two components. Fix `props.containerId` → `props.containerAppId` in `saveAnnotations()`.
3. **APISIMP-PINNED-CHANNEL-TILE-APPID** (MAJOR, XS–S) — UX-PIN1 pinned channels 404. Add `containerAppId` to `PinnedChannel` interface; populate at pin time; update tile URL.
4. **APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID** (MINOR, XS) — clean drop of dead `long id` from DataObject predecessor/successor responses.
5. **APISIMP-NOTIF-TEST-RESP-ENVELOPE** (MINOR, XS) — one-line backend fix.
6. **APISIMP-PLACEHOLDER-REGISTRY-STALE-PATH** (MINOR, XS) — one-line doc fix.

---

## What surprised me

- All three CRITICAL/MAJOR frontend bugs (F1, F2, F3) are a direct consequence of
  the APISIMP-TSCONT-APPID-KEY + APISIMP-SA-CONT-DELETE migrations: the backend was
  fully migrated in all 4 slices, but several frontend call sites that were NOT in
  the main migration path (the `ContainerAnnotated` abstract class, the
  `saveAnnotations` helper in Trace3D dialogs, the `PinnedChannelTile`) were missed.
  The 2026-06-11 sweep's "third-pass summary" table correctly noted these as shipped,
  but the actual `saveAnnotations` URL string at `Trace3DEditChannelsDialog.vue:50`
  was not updated by slice 3.
- The `ContainerAnnotated` class comment at line 124 explicitly states "the
  `/v2/{type}-containers/{id}/annotations` endpoints are new on this fork, so we hit
  them directly until the OpenAPI client is regenerated." This comment is now
  incorrect — the endpoints don't exist at all.
- F1 and F2 together mean that ANY user who opens a timeseries/file/structured-data
  container detail page and tries the annotation panel, AND any user who saves Trace3D
  axis assignments, gets a silent 404. These are user-visible regressions introduced
  by the migration.

## Cross-references

- `aidocs/platform/191-v2-surface-convergence.md` (V2CONV SSOT)
- `aidocs/agent-findings/apisimp-sweep-2026-06-11.md` (prior sweep)
- `aidocs/16` APISIMP section rows
- Files:
  - `frontend/composables/annotated.ts:152-203`
  - `frontend/components/container/timeseries/Trace3DEditChannelsDialog.vue:50`
  - `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue:76`
  - `frontend/components/container/timeseries/PinnedChannelTile.vue:108`
  - `frontend/composables/container/usePinnedChannels.ts:20`
  - `backend/.../v2/notifications/resources/NotificationAdminRest.java:153`
  - `backend/.../v2/dataobject/io/DataObjectSummaryIO.java:17`
  - `frontend/components/common/placeholder/placeholderRegistry.ts:225-226`
