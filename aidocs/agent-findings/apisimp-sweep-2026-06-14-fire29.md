---
stage: deployed
last-stage-change: 2026-06-14
---

# APISIMP Sweep — fire-29 (2026-06-14)

Scope: remaining simplification opportunities in the live `/v2/` REST surface after fire-28 (APISIMP-WATCH-DROP-OGMID). All frozen v1 `/shepard/api/` paths intentionally excluded. Known-deferred spatiotemporal plugin v1 paths (APISIMP-NUMERIC-ID-BATCH-2 / APISIMP-V1-PATH-RESIDUAL-1) skipped.

---

## Findings

### Finding 1 — `ImportV2Rest.java:146` — 422 missing `application/problem+json` content-type
**Row:** `APISIMP-IMPORTV2REST-422-CONTENT-TYPE`  
**File:** `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportV2Rest.java:146`  
**What:** `POST /v2/import/validate` returns `Response.status(422).entity(io).build()` when the manifest has hard errors. All other 4xx paths in this file use the `problem()` helper (lines 275–284) with `type("application/problem+json")`. The 422 path is the only one that bypasses this pattern — it returns `ImportPlanIO` (the validation result object with error lists) without setting the content-type header. Callers that parse `Content-Type` to decide how to handle errors will get `application/json` instead of `application/problem+json` for this path.  
**Severity:** MEDIUM  
**Fix:** Add `.type("application/problem+json")` to the 422 return (one-liner). The body is already structured (ImportPlanIO contains error details); no ProblemJson wrapping needed for now since ImportPlanIO is the canonical response shape for import validation errors. Alternatively add a `"type"` field to ImportPlanIO and set it to `"urn:shepard:error:import.validation-failed"` to make it fully RFC 7807 conformant.  
**Size:** XS

---

### Finding 2 — `CollectionIO.java:29` — shared IO exposes `defaultFileContainerId` (Long) on v2 wire
**Row:** `APISIMP-COLLECTIONIO-NUMERIC-FILECONTAINER`  
**File:** `backend/src/main/java/de/dlr/shepard/context/collection/io/CollectionIO.java:29`  
**What:** `CollectionIO` is a **shared DTO** reused by both the v1 `SearchRest.java` and the v2 `CollectionV2Rest.java`. It has a `private Long defaultFileContainerId` field (line 29) populated from `collection.getFileContainer().getId()` (the Neo4j OGM node ID, line 126). This numeric ID appears on every `GET /v2/collections/{appId}` response. No v2 frontend code was observed reading this field (`useV2ShepardApi`-generated clients would have it in the schema but the FE should be using `defaultFileContainerAppId` if it exists).  
**Caveat:** Since `CollectionIO` is shared, changing the field directly would also affect the `/shepard/api/` v1 surface — prohibited by the API-version policy. The correct fix is either: (a) add a parallel `defaultFileContainerAppId` String field (additive, v1-compat), or (b) create a `CollectionV2IO` subclass that shadows / hides the numeric field for v2 callers.  
**Severity:** MEDIUM (v1/v2 shared-IO complexity — must not break v1 wire shape)  
**Fix:** Add `@JsonProperty("defaultFileContainerAppId") private String defaultFileContainerAppId` alongside the existing `defaultFileContainerId`; populate from `collection.getFileContainer().getAppId()` at line 126. Mark the `Long` field `@Deprecated` (leave in for v1 compat). No removal until L2e deprecation window.  
**Size:** S

---

### Finding 3 — `FileBundleReferenceRest.java:431–434` — `@QueryParam("size")` without `@DefaultValue` (blocked by #1887)
**Row:** NOT FILED separately — covered by in-flight APISIMP-PAGINATION-UNIFY-RECREATE (PR #1887). When #1887 merges, it renames `?size` → `?pageSize` for `listGroupFiles()` at line 431. Adding `@DefaultValue` is a follow-up slice after #1887 lands.  
**Severity:** LOW (pre-existing; unblocked once #1887 merges)

---

### Finding 4 — `ShapesRenderRest.java:217–227` — call order (LOW, not filed)
`.entity(body).type("application/problem+json")` instead of `.type(...).entity(body)`. Functionally identical in JAX-RS; skipping as noise.

---

### Finding 5 — No new `@Path(Constants.SHEPARD_API + ...)` additions — CLEAN ✅
Scan confirmed: zero new `Constants.SHEPARD_API` usage in v2 packages or plugins outside the known-deferred spatiotemporal v1-compat paths. Gate holds.

---

### Finding 6 — Admin bespoke endpoints — CLEAN ✅
All bespoke `/v2/admin/` GET/PATCH config operations have been migrated to the generic `ConfigRegistry` pattern (V2CONV-A4 + V2CONV-A7 shipped fully). Remaining bespoke admin endpoints are credential operations, domain-specific lifecycle actions, or one-shot operations — all legitimately bespoke per CLAUDE.md.

---

### Finding 7 — Pagination standards — MOSTLY CLEAN ✅
`@QueryParam("pageSize")` with `@DefaultValue` is universal across v2 resources. The only outlier (`listGroupFiles` `?size` without default) is blocked on #1887 (see Finding 3).

---

## New APISIMP rows filed in aidocs/16

| Row | Size | Status |
|-----|------|--------|
| APISIMP-IMPORTV2REST-422-CONTENT-TYPE | XS | queued |
| APISIMP-COLLECTIONIO-NUMERIC-FILECONTAINER | S | queued |
