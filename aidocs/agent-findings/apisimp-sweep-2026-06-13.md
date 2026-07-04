---
name: APISIMP sweep pass 11
stage: fragment
last-stage-change: 2026-06-13
---

# APISIMP Sweep Pass 11 — 2026-06-13

Scanned: all `*Rest.java` under `backend/src/main/java/de/dlr/shepard/v2/` and `plugins/`.
Prior sweeps (1–10) already addressed: per-kind endpoint unification, admin config registry,
numeric-id leaks in BasicEntity/BasicRef/ContainerSummary/ProjectIO IOs, pagination rename
(in-flight #1870), error envelope on most resources, and dozens of OpenAPI annotation gaps.

## Findings

### Finding 1 — APISIMP-FILEREF-V2-PROBLEM-BODIES
**Severity:** MAJOR  
**File:** `backend/src/main/java/de/dlr/shepard/v2/file/resources/FileReferenceV2Rest.java`

`FileReferenceV2Rest` was added as `REF-UNIFIED-TABLE-FR1B` after all prior error-envelope
sweeps completed. It has 18 `Response.status(UNAUTHORIZED/FORBIDDEN/NOT_FOUND).build()`
empty-body returns while its BAD_REQUEST and INTERNAL_SERVER_ERROR paths already use the
`problem()` helper defined at line 596. Example:

```java
if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build(); // line 136
if (...) return Response.status(Response.Status.NOT_FOUND).build();               // line 150
if (...) return Response.status(Response.Status.FORBIDDEN).build();               // line 153
```

**Fix:** Replace each empty-body 401/403/404 return with a `problem(PROBLEM_TYPE_*, …)` call
using the already-present `private static Response problem(…)` helper.

---

### Finding 2 — APISIMP-DO-V2-EMPTY-BODIES
**Severity:** MAJOR  
**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java`

`DataObjectV2Rest` imports `ProblemJson` but has 0 `problem()` helper calls for
auth/perm/not-found paths — 18 empty-body `UNAUTHORIZED/FORBIDDEN/NOT_FOUND` returns remain.
The prior `APISIMP-DO-V2-FIELDS-ERROR-ENVELOPE` pass only fixed the one BAD_REQUEST near-miss
at line 196. Examples:

```java
if (collectionOgmId == null) return Response.status(Response.Status.NOT_FOUND).build(); // line 209
if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();        // line 851
if (...) return Response.status(Response.Status.FORBIDDEN).build();                      // line 853
```

**Fix:** Add a `private static Response problem(…)` helper (same pattern as `FileReferenceV2Rest`)
and replace all empty-body 4xx returns.

---

### Finding 3 — APISIMP-CONTAINERS-V2-EMPTY-BODIES
**Severity:** MAJOR  
**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`

`ContainersV2Rest` has 24 empty-body 401/403/404 returns alongside 9 existing `problem()`
calls — inconsistent. The `problem()` helper already exists in the file; the remaining
empty-body paths just weren't converted. Examples:

```java
if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build(); // line 118
if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build(); // line 155
```

**Fix:** Replace remaining empty-body UNAUTHORIZED/FORBIDDEN/NOT_FOUND returns with
`problem(…)` calls.

---

### Finding 4 — APISIMP-AAS-PAGINATION-SIZE
**Severity:** MINOR  
**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:99`

`AasShellsRest` (`GET /v2/aas/shells`) uses `@QueryParam("size")` for list pagination,
not covered by APISIMP-PAGINATION-UNIFY (PR #1870) which targets 7 core v2 resources.
After #1870 merges, the AAS plugin will be the last `?size` holdout in the `/v2/` surface.

**Fix:** Rename `@QueryParam("size")` → `@QueryParam("pageSize")` in `AasShellsRest`.
Dependent: wait for APISIMP-PAGINATION-UNIFY (#1870) to merge first.

---

## No-op checks

- `@Path(Constants.SHEPARD_API + ...)` additions: **none found** ✅
- Per-kind endpoint proliferation: **none** — all plugin CRUD unified under `/v2/references?kind=` per prior sweeps ✅
- Bespoke admin `*ConfigRest` outside the generic registry: **none** — all migrated per V2CONV-A4 ✅
- Numeric ids in `@PathParam`/`@QueryParam` of type `Long`: **none** in v2 REST resources ✅
  (internal entity `Long id` OGM fields exist in entities but do not appear on the wire)

## Summary

4 findings filed:
- APISIMP-FILEREF-V2-PROBLEM-BODIES (MAJOR, S) — empty 401/403/404 bodies in `FileReferenceV2Rest`
- APISIMP-DO-V2-EMPTY-BODIES (MAJOR, S) — empty 401/403/404 bodies in `DataObjectV2Rest`
- APISIMP-CONTAINERS-V2-EMPTY-BODIES (MAJOR, S) — empty 401/403/404 bodies in `ContainersV2Rest`
- APISIMP-AAS-PAGINATION-SIZE (MINOR, XS) — `?size` in AAS plugin, dependent on #1870
