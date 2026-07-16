---
stage: deployed
last-stage-change: 2026-07-16
fire: fire-622
---

# APISIMP Sweep — 2026-07-16 (fire-622)

Automated sweep of the live `/v2` REST surface in
`backend/src/main/java/de/dlr/shepard/v2/**` run as part of fire-622,
after confirming no named APISIMP row was dispatchable (all 197 rows either
done, decision-blocked, or blocked on PLUGIN-V2-001 / SPATIAL-V6-003 / L2e).

Complements the previous sweeps:
- `apisimp-sweep-2026-07-14-fire619.md`
- `apisimp-sweep-2026-07-15-fire620.md`
- `apisimp-sweep-2026-07-15-fire621.md`

---

## Scope

Checked for:
- Per-kind endpoints not yet unified under `?kind=`
- Bespoke `*ConfigRest` not on generic registry
- Numeric Neo4j IDs leaking into `@PathParam`/`@QueryParam`/response bodies
- Inconsistent pagination param names or error envelopes
- Response fields no caller reads
- Endpoints superseded by `POST /v2/shapes/render`
- New `@Path(Constants.SHEPARD_API + ...)` additions (forbidden)
- Missing `X-Total-Count` response header on paged list endpoints

---

## What I Found

### No violations in the clean-gate categories

- **Forbidden `SHEPARD_API` constants**: zero hits in `v2/`
- **Numeric Long `@PathParam` or `@QueryParam`**: zero hits in `v2/` REST resources
- **Inconsistent pagination param names**: all paged endpoints consistently use
  `page` (0-based) and `pageSize` (1–200). The single `@QueryParam("size")` outlier
  in `ContainersV2Rest` (line 1495) is a thumbnail pixel-size param, not a
  pagination param — not a violation.
- **Superseded endpoints**: none found; `POST /v2/shapes/render` coexists cleanly
  with `ShapesRenderRest` (it IS that endpoint); no legacy shape-render paths remain.
- **Per-kind endpoints not unified**: the `?kind=` pattern is consistently used in
  `ContainersV2Rest.list()` and `ReferencesV2Rest.list()`. No per-kind endpoint
  proliferation detected.

### Finding F1 (MAJOR): ~30 paged list endpoints missing `X-Total-Count` (WAVE4)

Every endpoint below uses `PagedResponseIO` in the response body but **neither**
chains `.header("X-Total-Count", ...)` on the `Response` builder **nor** declares
`headers = @Header(name = "X-Total-Count", ...)` in `@APIResponse(responseCode="200")`.

The pattern is established by XCOUNT-WAVE3 (fire-622, commit `ee009fb`) and the
AdminConfigRest precedent (APISIMP-ADMIN-CONFIG-LIST-ENVELOPE, fire-563). All
paged list endpoints must carry the header for frontend paging controls and for
`useV2ShepardApi` callers that read `X-Total-Count`.

**Affected endpoints:**

| File | Method / operationId | Route | Line |
|------|---------------------|-------|------|
| `ContainersV2Rest` | `list()` | `GET /v2/containers?kind=…` | 475 |
| `ContainersV2Rest` | `getLinkedDataObjects()` | `GET /v2/containers/{appId}/linked-data-objects` | 621 |
| `ContainersV2Rest` | `listChannels()` | `GET /v2/containers/{appId}/channels` | 666 |
| `DataObjectV2Rest` | `listChildren()` | `GET /v2/collections/{appId}/data-objects/{doAppId}/children` | 684 |
| `DataObjectV2Rest` | `listPredecessors()` | `GET /v2/data-objects/{appId}/predecessors` | 793 |
| `DataObjectV2Rest` | `listSuccessors()` | `GET /v2/data-objects/{appId}/successors` | 838 |
| `DataObjectV2Rest` | `listRecentlyAccessed()` | `GET /v2/data-objects/recently-accessed` | 880 |
| `DataObjectV2Rest` | `listByProject()` | `GET /v2/data-objects?projectAppId=…` | 922 |
| `SnapshotRest` | `list()` | `GET /v2/snapshots/{appId}/entries` | 172 |
| `SnapshotListRest` | `list()` | `GET /v2/snapshots` | 176 |
| `CollectionSnapshotRest` | `list()` | `GET /v2/collections/{appId}/snapshots` | 208 |
| `InstanceAdminRest` | `listInstanceAdmins()` | `GET /v2/admin/instance-admins` | 130 |
| `InstanceAdminRest` | `listOrphans()` | `GET /v2/admin/permission-audit` | 213 |
| `InstanceAdminRest` | `listAuditLog()` | `GET /v2/admin/permission-audit/log` | 281 |
| `PluginsAdminRest` | `list()` | `GET /v2/admin/plugins` | 148 |
| `BundleGroupsV2Rest` | `listGroups()` | `GET /v2/references/{appId}/groups` | 154 |
| `BundleGroupsV2Rest` | `listGroupFiles()` | `GET /v2/references/{appId}/groups/{groupAppId}/files` | ~391 |
| `CollectionWatchersRest` | `list()` | `GET /v2/collections/{appId}/watchers` | 93 |
| `NotificationRest` | `list()` | `GET /v2/notifications` | 81 |
| `NotificationTransportRest` | `list()` | `GET /v2/notifications/transports` | 104 |
| `UserGroupV2Rest` | `list()` | `GET /v2/user-groups` | 109, 118 |
| `UserSearchV2Rest` | `search()` | `POST /v2/users/search` | 88 |
| `ShapesPredicatesRest` | `list()` | `GET /v2/shapes/predicates` | 130 |
| `ProjectsRest` | `list()` | `GET /v2/projects` | 98 |
| `PersonalVocabularyRest` | `list()` | `GET /v2/vocabularies/personal` | 195, 202 |
| `CollectionWatchesRest` | `list()` | `GET /v2/collections/{appId}/watches` | 109 |
| `ProvenanceRest` | `listActivities()` | `GET /v2/provenance/activities` | 224 |
| `ProvenanceRest` | `listEntityActivities()` | `GET /v2/data-objects/{appId}/provenance/activities` | 365 |
| `ShepardTemplateRest` | `list()` | `GET /v2/templates` | 102 |
| `ShepardTemplateRest` | `listUsed()` | `GET /v2/templates/used` | 315 |
| `CollectionTemplatesRest` | `listAllowed()` | `GET /v2/collections/{appId}/templates` | 105 |
| `CollectionTemplatesRest` | `listUsed()` | `GET /v2/collections/{appId}/templates/used` | 131 |
| `SemanticAnnotationV2Rest` | `list()` | `GET /v2/annotations` | 205 |
| `SemanticAnnotationV2Rest` | `find()` | `GET /v2/annotations/find` | 262 |

**Total: 34 call sites across 19 files.**

**Special case — `ContainersV2Rest.listChannels()` (line 666):** The handler returns
`Optional<PagedResponseIO<…>>` and the REST resource calls
`Response.ok(result.get()).build()`. The header must be extracted from the
`PagedResponseIO` record's `total()` field:
```java
var paged = result.get();
return Response.ok(paged).header("X-Total-Count", paged.total()).build();
```

**Special case — `TimeseriesContainerKindHandler` (lines 551, 587):** Two handler
methods (`listChannelAnnotations`, `listTemporalAnnotations`) build the full
`Response` inside the handler and return `Optional<Response>`. These need
`.header("X-Total-Count", total)` added before `.build()` in those two handler
methods.

**Special case — non-paged list endpoints (DataObjectV2Rest lines 880, 922):**
These hardcode `page=0, pageSize=result.size()` (return all results, no DB
pagination). Adding `X-Total-Count` is still useful (header = body total field),
but note these endpoints already self-document their non-paged nature in the
OpenAPI description.

**Fix pattern per file (for paged, REST-level build):**
1. Add `import org.eclipse.microprofile.openapi.annotations.headers.Header;`
   (if not already imported).
2. Add to `@APIResponse(responseCode="200")`:
   `headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))`
3. Chain `.header("X-Total-Count", total)` on the `Response` builder before `.build()`.

**Filed as:** `APISIMP-XCOUNT-WAVE4` in `aidocs/16-dispatcher-backlog.md`.

---

## Findings Not Filed (Acknowledged Existing Debt)

- **`DataObjectV2Rest.list()` uses `Content-Range` instead of `PagedResponseIO`**:
  Already tracked as `APISIMP-DO-LIST-CONTENT-RANGE` in aidocs/16. Intentional
  divergence for backwards compatibility.
- **`PermissionAuditEntryIO.neo4jNodeId`**: Already `@Schema(deprecated = true)`
  with justification (triage handle for pre-L2 entities with null appId). Will be
  removed post-L2 migration. No new row needed.
- **In-memory pagination (`items.size()` as total)**: `NotificationTransportRest`,
  `PluginsAdminRest`, `InstanceAdminRest.listInstanceAdmins()`, `SemanticAdminRest`
  load the full list then slice in Java. Acceptable for admin endpoints with small
  expected cardinality (< 200 items). `total` correctly reflects the full list size.
  Noted but not filed.

---

## Next Dispatch

File the APISIMP-XCOUNT-WAVE4 row as the next dispatchable slice. Size M
(~20 files, mechanical). Can be broken into per-file sub-PRs or batched; recommend
batching in groups of 6-8 files per PR to keep diffs reviewable.
