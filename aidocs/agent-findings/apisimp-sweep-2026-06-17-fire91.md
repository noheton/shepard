---
stage: deployed
last-stage-change: 2026-06-17
---

# APISIMP sweep — fire-91 (2026-06-17)

**Context:** fire-91 dispatch sweep. All named V2CONV/APISIMP rows are either merged,
blocked on #1966 (FILE-PATH-RETIRE-2, B8), or already in-flight with READY PRs
(#1966/#1967/#1968/#1970/#1971/#1972). This sweep scans for net-new findings.

**Scope:** `backend/src/main/java/de/dlr/shepard/v2/**` + `plugins/*/src/main/java/**`.

**Checks:** per-kind unmerged endpoints; numeric id leaks; SHEPARD_API path usage;
inconsistent `?size` pagination; bare String error bodies; superseded endpoints.

---

## Finding 1 — MINOR/XS: bare String body on 503 in FileContainerKindHandler thumbnail path

**ID:** `APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING`  
**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/handlers/FileContainerKindHandler.java:183`  
**Severity:** MINOR  
**Size:** XS  

`getThumbnail()` returns a 503 with `.entity(sue.getMessage())` — a plain string body
missing `Content-Type: application/problem+json`. All other 4xx/5xx in `ContainersV2Rest`
use the `problem()` helper (constants at lines 111–115). The handler returns `Optional<Response>`;
`ContainersV2Rest` propagates it verbatim.

Fix: replace `.entity(sue.getMessage())` with a `ProblemJson` record:
```java
return Optional.of(problem(
  "/problems/files.thumbnail-unavailable",
  "Thumbnail Service Unavailable",
  Response.Status.SERVICE_UNAVAILABLE,
  sue.getMessage()
).header("Retry-After", "5").build());
```
`problem()` helper is defined in `ContainersV2Rest` — expose it as a package-private
static method or duplicate it in the handler (10 LoC, same pattern).

AC: `GET /v2/containers/{appId}/files/{oid}/thumbnail` 503 response has
`Content-Type: application/problem+json`; `mvn verify -pl backend` green.

---

## Findings skipped (already tracked or deferred)

| Candidate | Why skipped |
|---|---|
| SpatialDataReferenceRest numeric @PathParam/SHEPARD_API path | Already filed as APISIMP-NUMERIC-ID-BATCH-2 + APISIMP-V1-PATH-RESIDUAL-1 (both ⛔ deferred — frozen upstream-compat surface) |
| ContainersV2Rest `@QueryParam("size")` at :1367 | Thumbnail *pixel-size* param, not a pagination param — intentional use of `?size` |
| UserGroupV2Rest variable `size` vs wire `pageSize` | Java variable naming only; wire param is correct `@QueryParam("pageSize")` — no bug |
| FileBundleReferenceRest at `/v2/bundles` | Already tracked as B8 (`V2CONV-B8-VIEW-SHAPE-SEEDING` → kind=bundle); blocked on #1966 merge |

---

## Net-new rows filed in aidocs/16

1. `APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING` — XS, MINOR
