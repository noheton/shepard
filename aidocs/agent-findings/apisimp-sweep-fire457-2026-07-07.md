---
stage: fragment
last-stage-change: 2026-07-07
---

# APISIMP sweep — fire-457 (2026-07-07)

Full scan of `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` (92 files)
and `plugins/**/*Rest.java` (20 files). Prior backlog rows updated inline.

---

## Stale-row corrections (already merged to main, rows mis-labelled)

| Row | Old status | Corrected status |
|---|---|---|
| `APISIMP-VOCAB-USED-BY-BARE-LIST` | "PR open" | ✅ merged commit `693e95f` |
| `APISIMP-COLL-TEMPLATES-ANNOT-MISSING-PARAM` | "PR open (fire-445, PR #2364)" | ✅ merged `9806b5b` |
| `APISIMP-REFANNOT-PAGE-MISSING-PARAM` | "PR open (fire-445, PR #2364)" | ✅ merged `9806b5b` |
| `APISIMP-TEMPLATE-TAGS-BARE-LIST` | "PR open (fire-447, PR #2366)" | ✅ merged `826d99a` |
| `APISIMP-TEMPLATE-IMPORT-BARE-LIST` | "PR open (fire-448, PR #2367)" | ✅ merged `d79b36f` |
| `APISIMP-ANNOTATION-ALIAS-FIELDS` | "PR open (fire-454)" | ✅ merged `4ffeca8` |
| `APISIMP-VIDEO-TOMBSTONE-DELETE` | "PR open (fire-452, PR #2373)" | ✅ merged `44a31e6` |
| `APISIMP-BUNDLE-REF-KIND-UNIFY` | "slice 2 queued" | ✅ both slices merged (`61eb8e4` + `fe2e401`); `FileBundleReferenceRest` is now a 410 tombstone |
| `APISIMP-SNAP-PINNED-IN-MEMORY-PAGING` | "queued" | ⚠️ PR #2368 open but **unstable**: spurious `CodeQL` summary-job failure (real analysis jobs PASS); operator force-merge or re-trigger needed |

---

## New findings — fire-457

### F1: `NotificationTransportRest` — bare `List<T>` (no `PagedResponseIO`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/resources/NotificationTransportRest.java:84–88`
**Path:** `GET /v2/admin/notifications/transports`
**Finding:** `service.listAll()` returns every configured notification transport in a bare
`List<NotificationTransportReadIO>` with no `page`/`pageSize` params and no `PagedResponseIO`
envelope. In practice the list is small (< 20), but the response shape is inconsistent with
every other v2 list endpoint.
**Fix:** Wrap in `PagedResponseIO<NotificationTransportReadIO>` with total=items.size(); add
optional `@QueryParam("page")`/`@QueryParam("pageSize")` defaults; no DB-side pagination
needed (list is inherently bounded — one row per transport slot).
**AC:** `GET /v2/admin/notifications/transports` returns `{items, total, page, pageSize}`;
`mvn verify -pl backend` green.
**Size:** XS

---

### F2: `InstanceAdminRest.listInstanceAdmins` — bare `List<T>` (no `PagedResponseIO`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:106–124`
**Path:** `GET /v2/admin/instance-admins`
**Finding:** `instanceAdminService.listInstanceAdmins()` returns all instance-admin grants as a
bare `List<InstanceAdminGrantIO>`. The sibling `GET /v2/admin/permission-audit/log` on line 229
uses `PagedResponseIO` correctly. No `page`/`pageSize` params exist.
**Fix:** Wrap in `PagedResponseIO`; add optional `page`/`pageSize` params.
**AC:** `GET /v2/admin/instance-admins` returns `{items, total, page, pageSize}`;
`mvn verify -pl backend` green.
**Size:** XS

---

### F3: `InstanceAdminRest.listPermissionAuditOrphans` — bare `List<T>` (no `PagedResponseIO`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:180–199`
**Path:** `GET /v2/admin/permission-audit`
**Finding:** `permissionAuditService.listOrphans()` returns orphaned permission entries as a
bare `List<PermissionAuditEntryIO>`. In a large instance this list can be substantial (one entry
per mis-linked Collection). Sibling `GET /v2/admin/permission-audit/log` is paged.
**Fix:** Add `page`/`pageSize` params; push SKIP/LIMIT to `PermissionAuditService.listOrphans(int, int)`;
add `countOrphans()` method; wrap in `PagedResponseIO`.
**AC:** `GET /v2/admin/permission-audit?page=0&pageSize=50` returns `{items, total, page, pageSize}`;
`mvn verify -pl backend` green.
**Size:** S

---

### F4: `AdminUserGitCredentialRest.list` — bare `List<T>` (no `PagedResponseIO`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/users/AdminUserGitCredentialRest.java:202–236`
**Path:** `GET /v2/admin/users/{username}/git-credentials`
**Finding:** Lists all git credentials for a target user as a bare `List<AdminGitCredentialListItemIO>`
with no pagination. A power user could accumulate many rotated credentials.
**Fix:** Wrap in `PagedResponseIO`; add optional `page`/`pageSize` params with pass-through to DAO.
**AC:** response is `{items, total, page, pageSize}`; `mvn verify -pl backend` green.
**Size:** XS

---

### F5: `PublicationsListRest` — hardcoded `LIMIT 1000` in DAO without wire pagination

**File:** `backend/src/main/java/de/dlr/shepard/v2/publish/resources/PublicationsListRest.java:147`
**Path:** `GET /v2/{kind}/{appId}/publications` (deprecated alias, kept for compat)
**Finding:** `publicationDAO.findByEntityAppId(appId, 0, 1000)` uses a hardcoded SKIP 0 LIMIT 1000
with no wire-exposed `page`/`pageSize` params. The flat alias `GET /v2/publications` (already paged
via `APISIMP-FLAT-PUBS-NO-PAGINATION` and `APISIMP-PUBLICATIONS-IN-MEMORY-PAGING`) is correct.
This deprecated alias still has the hardcoded cap.
**Fix:** Since this path is already deprecated (OpenAPI annotation at line 97), the lowest-friction fix
is to forward to `FlatPublicationsRest` internally or to add `page`/`pageSize` params matching the
flat alias's shape. AC: `?page=0&pageSize=50` works; `mvn verify -pl backend` green.
**Note:** Low urgency since path is deprecated; bundle with a future tombstone of `{kind}/{appId}/publications`.
**Size:** XS

---

### F6: `NotebookRest.listNotebooks` — internal Neo4j numeric ID (`ogmId`) at line 126

**File:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/NotebookRest.java:124–163`
**Path:** `GET /v2/lab-journal/{dataObjectAppId}/notebooks`
**Finding:** `entityIdResolver.resolveLong(dataObjectAppId)` at line 126 materialises a numeric
Neo4j internal ID `ogmId`, which is then passed to `fileBundleReferenceDAO.findByDataObjectNeo4jId(ogmId)`
at line 163 to find legacy bundle notebooks. The numeric ID does not appear on the wire (responses
use `appId`), so this is not a client-facing leak — but it couples the handler to the pre-appId
bundle DAO, and the bundle pattern is itself being phased out (SINGLETON-FILE-MIGRATION). The
singleton path at line 141 correctly uses `singletonService.listByDataObject(dataObjectAppId)` with
no numeric ID. **This finding is informational** — the fix is to extend `SingletonFileReferenceService`
to cover this lookup (as FR1a bundles are migrated to FR1b singletons per SINGLETON-FILE-MIGRATION).
**Size:** XS (informational, deferred to SINGLETON-FILE-MIGRATION completion)

---

### F7: `AdminFeaturesRest` — bespoke toggle surface parallel to generic `AdminConfigRest`

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminFeaturesRest.java`
**Path:** `GET /v2/admin/runtime-toggles`, `PATCH /v2/admin/runtime-toggles/{name}`
**Finding:** Feature toggles live at `/v2/admin/runtime-toggles` while the generic config registry
lives at `GET/PATCH /v2/admin/config/{feature}`. An operator must learn two admin surfaces for
feature-flag management. The toggle patch is `PATCH /v2/admin/runtime-toggles/{name}` (body:
`{enabled: bool, reason: string}`) while the config patch is `PATCH /v2/admin/config/{feature}`
(RFC 7396 merge-patch). Divergent API shapes.
**Fix option A (preferred):** Register `FeatureToggleRegistry` as a `ConfigDescriptor` provider in
`AdminConfigRegistry`; expose toggle state via `GET /v2/admin/config/feature-toggles` and individual
toggle PATCH via `PATCH /v2/admin/config/feature-toggles/{name}`; tombstone `runtime-toggles` path.
**Fix option B (minimal):** Alias `/v2/admin/config/feature-toggles` → `AdminFeaturesRest` with
an HTTP 302 and a deprecation header; no logic change.
**AC:** `PATCH /v2/admin/config/feature-toggles/jupyter` enables Jupyter; old path returns 301/410;
`mvn verify -pl backend` green.
**Size:** S

---

### F8: Minter plugins — `GET/DELETE /credential` paths alongside generic config (residual after APISIMP-MINTER-CRED-CONFIG-UNIFY)

**File:** `plugins/minter-datacite/.../DataciteAdminRest.java:40–89`; `plugins/minter-epic/.../EpicAdminRest.java:40–89`
**Finding:** Per the backlog row `APISIMP-MINTER-CRED-CONFIG-UNIFY` (✅ merged fire-454), the
credential sub-resource was merged into the config descriptor. Verify the bespoke `POST/DELETE
/credential` sub-paths are tombstoned (410) in the merged code, or if they were silently dropped
(not tombstoned). If the old paths are simply gone (no tombstone), clients hitting them will get
405/404 with no migration guidance.
**Fix:** Verify `DataciteAdminRest.java` and `EpicAdminRest.java` currently have tombstone stubs
for `/credential` POST/DELETE. Add 410 stubs if missing.
**AC:** `POST /v2/admin/minters/datacite/credential` → 410 Gone with `Location: /v2/admin/config/minter-datacite`;
`mvn verify -pl plugins/minter-datacite` green.
**Size:** XS

---

### F9: `ContainersV2Rest.getThumbnail` — `@QueryParam("size")` non-standard param name

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1463`
**Path:** `GET /v2/containers/{appId}/files/{oid}/thumbnail?size=`
**Finding:** The thumbnail endpoint uses `@QueryParam("size")` for the pixel dimension (64/200/400).
Every other query param in the v2 surface uses `page`/`pageSize`/`pageIndex` for numeric params.
`size` is semantically correct for a pixel dimension but introduces a non-standard name that could
confuse clients checking query-param naming conventions. This is low priority since thumbnail
`size` is clearly not pagination.
**Note:** Informational; not a blocker. Document in OpenAPI with `@Parameter(description="Thumbnail
pixel size…")` if not already present.
**Size:** XS (informational; resolved by existing `@Parameter` doc at line 1462)

---

### F10: `CollectionDQRRest.evaluate` — `@QueryParam("limit")` non-standard for list truncation

**File:** `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:198`
**Path:** `POST /v2/collections/{collectionAppId}/dqr/evaluate`
**Finding:** The `evaluate` endpoint uses `@QueryParam("limit")` (1–5000, default 5000) for list
truncation, while all other v2 list endpoints use `pageSize`. The response is wrapped in a custom
`DQRResultsIO` (not `PagedResponseIO`). This is a post-operation analysis endpoint that evaluates
all DQR rules and truncates for display, rather than a paginated list — so `limit` is arguably
appropriate. However, OpenAPI clients encountering this after `pageSize`-normalised endpoints will
notice the inconsistency.
**Fix:** Either rename `limit` → `maxResults` (explicit about truncation semantics, not pagination)
or keep `limit` but add `@Parameter(description="Maximum number of DQR results returned (truncation,
not pagination). Default 5000, capped at 5000.")` for clarity.
**Note:** `APISIMP-DQR-ORPHAN` already tracks whether this entire endpoint should be retained.
If the endpoint is retained, normalise the param name.
**Size:** XS (informational; bundle with APISIMP-DQR-ORPHAN resolution)

---

## Summary

| Finding | Row ID | Size | Priority |
|---|---|---|---|
| `NotificationTransportRest` bare list | `APISIMP-NOTIF-TRANSPORT-BARE-LIST` | XS | P3 |
| `InstanceAdminRest.listInstanceAdmins` bare list | `APISIMP-INSTANCE-ADMINS-BARE-LIST` | XS | P3 |
| `InstanceAdminRest.listPermissionAuditOrphans` bare list | `APISIMP-PERMISSION-AUDIT-BARE-LIST` | S | P3 |
| `AdminUserGitCredentialRest.list` bare list | `APISIMP-GIT-CRED-BARE-LIST` | XS | P3 |
| `PublicationsListRest` deprecated alias hardcoded LIMIT 1000 | `APISIMP-PUBS-ALIAS-HARDCODED-LIMIT` | XS | P4 (deprecated path) |
| `NotebookRest` internal numeric ID (informational) | informational | XS | deferred |
| `AdminFeaturesRest` bespoke toggle surface | `APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY` | S | P2 |
| Minter credential tombstone verification | `APISIMP-MINTER-CRED-TOMBSTONE-VERIFY` | XS | P3 |
| Thumbnail `size` param (informational) | informational | XS | N/A |
| DQR `limit` param name | informational | XS | bundle with DQR-ORPHAN |

**Overall assessment:** The v2 surface is in good shape. The remaining findings are small, consistent
bare-list-vs-PagedResponseIO gaps on admin endpoints (F1–F4), one bespoke admin toggle surface that
should unify with the generic config registry (F7), and minor informational notes (F6, F9, F10).
No new per-kind per-DataObject path sprawl was found — the kind-discriminated pattern is now
consistently applied. The NotebookRest numeric-id concern (F6) is informational and resolves
naturally as SINGLETON-FILE-MIGRATION progresses.
