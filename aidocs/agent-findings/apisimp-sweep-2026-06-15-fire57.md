---
stage: deployed
last-stage-change: 2026-06-15
---

# APISIMP Sweep — fire-57 (2026-06-15)

**Scope:** post–fire-55 pass. fire-55 found APISIMP-CROSS-TS-BULK-PATH and
APISIMP-VIDEO-ANNOT-PATH, both now in READY PRs (#1952, #1953). This sweep
checks what residual sprawl remains after those land.

---

## Findings

### Finding 1 — APISIMP-USERGROUP-PAGESIZE (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java`
**Path:** `GET /v2/user-groups`

`APISIMP-PAGINATION-UNIFY-RECREATE` (PR #1887, merged 2026-06-15) fixed the
`?size` → `?pageSize` rename on 8 v2 list endpoints. `UserGroupV2Rest.listUserGroups`
was missed — it still carries:

```java
@Parameter(name = Constants.QP_SIZE)           // line 83 → "size"
@QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,   // line 88
```

`Constants.QP_SIZE = "size"` (common/util/Constants.java:157). After PR #1887,
`GET /v2/user-groups` is the only v2 list endpoint that exposes `?size` instead
of `?pageSize`, breaking the unified pagination contract.

**Frontend caller:** `frontend/composables/context/useUserGroupsV2.ts:80` calls
`listUserGroups()` without passing the size param — no frontend change needed.

**AC:** `@QueryParam("pageSize")` + `@Parameter(name = "pageSize")` on
`listUserGroups`; zero v2 list endpoints accepting `?size` for pagination
(thumbnail size param in ContainersV2Rest is semantic, not pagination);
`mvn verify -pl backend` green.

---

### Finding 2 — APISIMP-CONTAINERS-PRESIGN-EMPTY-BODIES (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`
**Endpoints:** thumbnail / presigned-upload / commit-upload / download cluster

8 empty-body 4xx responses were added as part of the BATCH series (after
`APISIMP-CONTAINERS-V2-EMPTY-BODIES` was fixed) in the presigned-upload and
thumbnail endpoints:

| Line | Status | Endpoint |
|------|--------|----------|
| 1371 | 401 UNAUTHORIZED | `getThumbnail` |
| 1373 | 404 NOT_FOUND | `getThumbnail` |
| 1405 | 401 UNAUTHORIZED | `getUploadUrl` (presigned) |
| 1407 | 404 NOT_FOUND | `getUploadUrl` |
| 1434 | 401 UNAUTHORIZED | `commitUpload` |
| 1436 | 404 NOT_FOUND | `commitUpload` |
| 1466 | 401 UNAUTHORIZED | download endpoint |
| 1468 | 404 NOT_FOUND | download endpoint |

A `problem()` helper already exists in the file (lines 111-115 define the
`PROBLEM_TYPE_*` constants; the helper method is present). These 8 sites simply
need to be wired up.

**AC:** All 8 `.status(xxx).build()` empty-body 4xx replaced with `problem()`
calls using the existing helper and constants; `mvn verify -pl backend` green.

---

## Not-a-finding notes

| Area | Verdict |
|---|---|
| `ContainersV2Rest.java:1367` `@QueryParam("size") Integer sizeParam` | Semantic "thumbnail pixel size" param — NOT pagination. Correctly named. |
| `Constants.QP_SIZE` in v1 resources (`UserGroupRest`, `SearchRest`, `FileRest`) | Frozen `/shepard/api/` v1 surface — must not be changed. |
| Error envelopes across remaining v2 resources | Confirmed clean after fire-55 sweep. |
| Pagination params on all other v2 list endpoints | Confirmed `?page` + `?pageSize` consistently after PR #1887. |
| Problem URI format | Zero `https://shepard.dlr.de/problems/` in core or plugins after APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS (PR #1919). |

---

## New rows filed in `aidocs/16`

| Row | Size | Status |
|---|---|---|
| `APISIMP-USERGROUP-PAGESIZE` | XS | queued — dispatch next fire |
| `APISIMP-CONTAINERS-PRESIGN-EMPTY-BODIES` | XS | queued |
