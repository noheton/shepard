---
stage: fragment
last-stage-change: 2026-06-13
---

# APISIMP sweep pass 12 — 2026-06-13

**Scope:** Scan `/v2` REST surface for residual empty 4xx bodies, untyped Map
responses, numeric-id leaks, pagination inconsistencies, and forbidden
`@Path(Constants.SHEPARD_API + ...)` additions.

**Previous passes:** 1–11 (see `aidocs/agent-findings/apisimp-sweep-2026-06-13.md`
and prior). Passes 1–11 addressed: pagination unification, error envelope
unification, untyped Map responses, numeric-id leaks in IO classes, problem-body
consistency in the major REST resources (FileReferenceV2Rest, DataObjectV2Rest,
ContainersV2Rest, and ~20 others).

---

## Findings

### Finding 1 — APISIMP-EMPTY-BODIES-BATCH-3: four small REST resources still have empty 4xx

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationRest.java`
  — 4× empty-body 401 (`Response.status(UNAUTHORIZED).build()`, lines 55/79/101/125)
    + 2× `ApiError` 404 bodies (lines 107–109, 130–132; `ApiError` is not RFC 7807)
- `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserAvatarRest.java`
  — 4× empty-body 401 (lines 71/90/115/120); already has `private static Response
    problem(...)` helper + `PROBLEM_TYPE_*` constants — just needs
    `PROBLEM_TYPE_UNAUTHORIZED` added and calls wired up.
- `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserAvatarByAppIdRest.java`
  — 2× empty-body 404 (lines 44/51); no helper yet.
- `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/resources/CollectionWatchersRest.java`
  — 1× `unauthorized()` helper that returns empty 401 (line 168); 1× empty-body
    404 via `Optional` (line 110).

**Fix:** Add `ProblemJson` bodies to all empty 4xx returns and convert `ApiError`
bodies to `ProblemJson`. This is batch 3 of the ongoing error-envelope rollout.

**Severity:** MAJOR (inconsistent error shape across the v2 surface)

**Row filed:** APISIMP-EMPTY-BODIES-BATCH-3

---

### Not filed — PermissionAuditEntryIO.id intentionally kept

The `PermissionAuditEntryIO.id` field (`long id`, Neo4j node id) was reviewed
during APISIMP-PERM-AUDIT-LOG-APPID and explicitly kept: the `GET
/v2/admin/permission-audit` endpoint is a diagnostic tool for _entities that
have no appId yet_ (pre-L2 orphans). Without the numeric id, an admin cannot
identify which node to inspect in Cypher. The row explicitly says "decommission
after L2." This is a documented exception, not a finding.

---

### Not filed — pagination, forbidden paths, numeric @PathParams

No new violations found:
- Zero `@QueryParam("limit")` / `@QueryParam("count")` remaining in v2.
- Zero `@Path(Constants.SHEPARD_API + ...)` additions in v2.
- Zero `Long` / `long` `@PathParam` remaining in v2.
- Zero untyped `Map.of(` / `Map.entry(` response bodies remaining.

---

## Remaining empty-body scope

53 v2 resource files still have at least one
`Response.status(UNAUTHORIZED/FORBIDDEN/NOT_FOUND).build()`. This fire fixes 4
of them (batch 3). Subsequent fires should batch the remaining 49 files into
groups of 5–8 per PR to stay within the XS/S slice ceiling.

Key remaining clusters (batch 4+):
- **Import cluster** (4 files): ImportV2Rest, ImportJobsV2Rest, ImportLockV2Rest, ImportDiagnosticsV2Rest
- **Snapshot cluster** (5 files): SnapshotRest, SnapshotListRest, CollectionSnapshotRest, SnapshotDiffRest, SnapshotPinnedReadRest — note: branch APISIMP-SNAPSHOT-RESP-SIZE (PR #1870) already touches SnapshotListRest + CollectionSnapshotRest; wait for it to merge before batching those.
- **Template cluster** (5 files): ShepardTemplateRest, CollectionTemplatesRest, TemplateInstantiationRest, TemplateExcelExportRest, TemplateFormRest
- **Collection cluster** (5 files): CollectionV2Rest, CollectionContainersRest, CollectionPropertiesRest, CollectionPublicationStateRest, CollectionSceneGraphRest
- **Annotation + provenance** (2 files): SemanticAnnotationV2Rest, ProvenanceRest
- **Remaining** (28 files): labjournal, me, quality, publish, references, sql, timeseries, users/MeRest, users/MeRoleInRest, admin cluster, fair, export, bundle
