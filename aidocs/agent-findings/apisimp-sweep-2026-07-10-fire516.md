---
stage: fragment
last-stage-change: 2026-07-10
---

# APISIMP sweep — fire-516 (2026-07-10)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings are
confined to the fork's development surface; the frozen `/shepard/api/` surface was not
examined. Previous sweep: fire-515 (apisimp-sweep-2026-07-10.md).

---

## §F1 — Collection `?name=` text filter not yet renamed to `?q=`

**Finding: APISIMP-COLL-NAME-TO-Q** (size: XS)
- File: `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java:172`
- Problem: `GET /v2/collections` uses `@QueryParam(Constants.QP_NAME)` (`?name=`) as its text
  filter; this is not marked `@Deprecated` and has no `?q=` alias — the direct equivalent of
  APISIMP-CONTAINERS-NAME-TO-Q (fire-510) and APISIMP-DO-NAME-TO-Q (fire-510) which renamed
  `?name=` on containers and data-objects. Collections is the third endpoint still using the
  non-standard param name.
- Fix: rename the param to `@QueryParam("q") String q`, update the internal variable references,
  and add a `@Deprecated @QueryParam("name") String nameLegacy` backward-compat alias with
  `Deprecation: true` header (same pattern as ContainersV2Rest and DataObjectV2Rest).
- AC: `grep -n '"name"' backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java | grep '@QueryParam'` returns only the deprecated-alias line (or empty if the deprecation window is skipped).

---

## §F2 — Collection sub-resources use `{collectionAppId}` instead of v2-standard `{appId}`

**Finding: APISIMP-COLL-PATHPARAM** (size: S)
- Files:
  - `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java:194`
  - `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionTimelineRest.java:64`
  - `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionContainersRest.java:49`
  - `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionPublicationStateRest.java:52`
  - `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionSnapshotRest.java:60`
- Problem: Five collection-domain resource classes use `{collectionAppId}` as their path param
  under `/v2/collections/`, while five other sub-resources in the same domain already use `{appId}`
  (`CollectionPropertiesRest`, `CollectionPermissionsRest`, `CollectionExportUrlRest`,
  `CollectionSceneGraphRest`, `CollectionStreamExportRest`). The v2 standard is `{appId}` when the
  resource type is already implied by the URL prefix. Note: `SnapshotPinnedReadRest` path
  `/v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects` uses two distinct
  appId params for two different resources; renaming the outer one is a separate call (or keep
  both qualified there for clarity).
- Fix: In the five listed files rename `{collectionAppId}` → `{appId}` in both `@Path`
  declarations and `@PathParam` bindings.
- AC: `grep -rn '"collectionAppId"' backend/src/main/java/de/dlr/shepard/v2/collection/resources/ | grep '@PathParam'` returns empty (or only `SnapshotPinnedReadRest` if that nested case is kept).

---

## §F3 — `SnapshotRest` uses `{snapshotAppId}` instead of v2-standard `{appId}`

**Finding: APISIMP-SNAPSHOT-PATHPARAM** (size: XS)
- File: `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotRest.java:62`
- Problem: The single-resource endpoint `GET|DELETE /v2/snapshots/{snapshotAppId}` uses
  `{snapshotAppId}` when the resource type is fully implied by the `/v2/snapshots/` prefix —
  identical pattern to the already-merged `{bundleAppId}` → `{appId}` (fire-506) and
  `{templateAppId}` → `{appId}` (fire-507) fixes.
- Fix: Rename `{snapshotAppId}` → `{appId}` in `@Path`, the `@PathParam` binding sites, and
  update internal variable names.
- AC: `grep -n 'snapshotAppId' backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotRest.java | grep '@PathParam'` returns empty.

---

## §F4 — Channel/annotation list endpoints use `@Max(500)` instead of v2-standard 200

**Finding: APISIMP-CHANNEL-PAGECAP** (size: XS)
- File: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:651,1033,1154`
- Problem: Three list endpoints have `@Max(500)` instead of the v2-standard 200: `listChannels`
  (line 651), `listChannelAnnotations` (line 1033), and `listTemporalAnnotations` (line 1154).
  All three also carry misleading OpenAPI schema descriptions saying "capped at 500". Note:
  APISIMP-PAGESIZE-CAP-UNDOCUMENTED (fire-444) added OpenAPI `schema = @Schema(maximum)` annotations
  to document the cap, but did not change the cap value itself.
- Fix: Change `@Max(500)` → `@Max(200)` on all three params and update the `@Parameter`
  description strings accordingly.
- AC: `grep -n '@Max(500)' backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java | grep pageSize` returns empty.

---

## §F5 — Snapshot manifest and pinned-read use non-standard page-size caps

**Finding: APISIMP-SNAPSHOT-PAGECAP** (size: XS)
- Files:
  - `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotRest.java:156`
  - `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotPinnedReadRest.java:133`
- Problem: `GET /v2/snapshots/{snapshotAppId}/manifest` caps `pageSize` at `@Max(1000)` and
  `GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects` caps it at
  `@Max(2000)` — both are well above the 200 standard and the 1000 bundle-files exception does
  not apply here. Note: APISIMP-SNAPSHOT-MANIFEST-IN-MEMORY-PAGING (fire-422) fixed in-memory
  paging without changing the @Max cap; APISIMP-PAGESIZE-CAP-UNDOCUMENTED (fire-444) added
  OpenAPI documentation for the undocumented cap on `SnapshotPinnedReadRest` without normalising
  the value.
- Fix: Change `@Max(1000)` → `@Max(200)` in `SnapshotRest.java:156` and `@Max(2000)` → `@Max(200)`
  in `SnapshotPinnedReadRest.java:133`; update `@DefaultValue` and description text accordingly.
- AC: `grep -n '@Max' backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotRest.java backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotPinnedReadRest.java | grep -E '1000|2000'` returns empty.

---

## §F6 — `ReferenceAnnotationRest` uses `@Max(1000)` instead of v2-standard 200

**Finding: APISIMP-REFANNOT-PAGECAP** (size: XS)
- File: `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java:165`
- Problem: `GET /v2/references/{appId}/annotations` caps `pageSize` at `@Max(1000)` (OpenAPI says
  "Items per page (1–1000). Default 200."). The 1000 exception applies only to bundle-files; this
  is a reference-annotation list. Note: APISIMP-REFANNOT-IN-MEMORY-PAGING (fire-442) fixed
  in-memory paging without changing the @Max cap.
- Fix: Change `@Max(1000)` → `@Max(200)` and update the `@Parameter` description to say "1–200".
- AC: `grep -n '@Max' backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java | grep '1000'` returns empty.

---

## §F7 — Admin permission-audit endpoints use `@Max(500)` instead of v2-standard 200

**Finding: APISIMP-ADMIN-AUDIT-PAGECAP** (size: XS)
- File: `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:210,271`
- Problem: Both `GET /v2/admin/permission-audit` and `GET /v2/admin/permission-audit/log` use
  `@Max(500)` on `pageSize` (the comments even say "Page size 1–500"). These are admin-gated
  (`@RolesAllowed("instance-admin")`) but still deviate from the v2-standard cap of 200.
- Fix: Change both `@Max(500)` → `@Max(200)` and update the `@Parameter` description strings.
- AC: `grep -n '@Max(500)' backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java` returns empty.

---

## Summary table

| Slug | Size | One-line description |
|---|---|---|
| APISIMP-COLL-NAME-TO-Q | XS | `CollectionV2Rest` `?name=` text filter not yet renamed to `?q=` |
| APISIMP-COLL-PATHPARAM | S | 5 collection sub-resources use `{collectionAppId}` instead of `{appId}` |
| APISIMP-SNAPSHOT-PATHPARAM | XS | `SnapshotRest` uses `{snapshotAppId}` instead of `{appId}` |
| APISIMP-CHANNEL-PAGECAP | XS | `ContainersV2Rest` channels/annotations list endpoints `@Max(500)` → `@Max(200)` |
| APISIMP-SNAPSHOT-PAGECAP | XS | Snapshot manifest `@Max(1000)` and pinned-read `@Max(2000)` → `@Max(200)` |
| APISIMP-REFANNOT-PAGECAP | XS | `ReferenceAnnotationRest` `@Max(1000)` → `@Max(200)` |
| APISIMP-ADMIN-AUDIT-PAGECAP | XS | `InstanceAdminRest` permission-audit endpoints `@Max(500)` → `@Max(200)` |

All 7 are safe additive/rename changes with no wire-format breakage. 6 of 7 are XS
(smallest dispatchable slice). Recommended next-fire-517 dispatch: APISIMP-COLL-NAME-TO-Q
(XS, standalone rename, no blockers).
