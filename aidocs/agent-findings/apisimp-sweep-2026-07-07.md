---
stage: fragment
last-stage-change: 2026-07-07
---

# APISIMP Sweep — 2026-07-07

**Scope:** Full scan of `/v2/` REST surface for residual API sprawl after the
fire-453 through fire-461 wave of merges. Cross-referenced against all 325 existing
APISIMP rows in `aidocs/16-dispatcher-backlog.md`.

**Method:** Read all v2 Java REST resource files; grepped for bare-list returns,
uncapped params, missing `@Operation(operationId=...)`, numeric id exposure, and
inconsistent error envelopes. Verified candidate findings against the backlog before
filing. Checked plugin REST files for the same patterns. Used Python-based
parenthesis-matching to confirm all `@Operation` annotations carry `operationId`
(grep-based approach produces false positives on multi-line annotations — no
`operationId` gaps found).

---

## Findings

### F1 — APISIMP-TOMBSTONE-BARE-STRING

**Files affected:**

| File | Lines | Pattern |
|---|---|---|
| `backend/src/main/java/de/dlr/shepard/v2/filecontainer/resources/FileContainerStatsRest.java` | 27–39 | `private static final String GONE_MSG = "…"; … .entity(GONE_MSG).build()` |
| `backend/src/main/java/de/dlr/shepard/v2/structureddatacontainer/resources/StructuredDataContainerStatsRest.java` | 27–39 | same bare-string pattern |
| `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminFeaturesRest.java` | 33–41 | bare string, has `Link:` header but wrong media type |
| `plugins/minter-datacite/…/DataciteAdminRest.java` | 48,69,83 | same bare-string pattern across two methods |
| `plugins/minter-epic/…/EpicAdminRest.java` | 48,69,83 | same bare-string pattern across two methods |

**What is wrong:** Five tombstone REST classes introduced by fire-453 (container
stats unify) and fire-454 (minter credential unify) and fire-460 (feature-toggle
unify) return `Response.status(GONE).entity(plainString).build()`. The
class-level `@Produces(MediaType.APPLICATION_JSON)` causes JAX-RS to set
`Content-Type: application/json`, so the response body is a quoted JSON string
`"This path has been removed…"` — technically valid JSON but not a JSON object,
not `application/problem+json`, not `ProblemJson`.

**Correct pattern** (used by `WikiWriterTombstoneRest` and `GitReferenceRest`,
introduced in fire-444 and fire-455):

```java
return Response.status(Response.Status.GONE)
  .type("application/problem+json")
  .header("Location", "/v2/…/replacement")
  .entity(new ProblemJson("urn:shepard:error:gone", "Gone", 410, GONE_MSG, null))
  .build();
```

**Impact:** A client that checks `Content-Type` to choose a JSON deserializer gets
`application/json` (bare string) instead of `application/problem+json` (RFC 7807
object). Any client using a generated OpenAPI stub that expects a problem object
will fail to deserialize the 410 body. Inconsistency also confuses future readers
scanning tombstone classes for the canonical pattern.

**Fix (per file):**
1. Add `ProblemJson` import (already present in most; for the container stats files
   it needs adding).
2. Replace `private static final String GONE_MSG = "…"` with a `ProblemJson`
   factory call in the response method.
3. Replace `.entity(GONE_MSG).build()` with
   `.type("application/problem+json").header("Location", "…").entity(new ProblemJson(…)).build()`.
4. The `FileContainerStatsRest` and `StructuredDataContainerStatsRest` tombstones
   also lack a `Location`/`Link` header pointing to the replacement
   `GET /v2/containers/{appId}/stats` path — add it.
5. `AdminFeaturesRest` has a `Link:` header (correct format per RFC 8288) — keep
   it; just swap the body to `ProblemJson`.

**Severity:** XS. All five classes are low-traffic tombstones; no functional
regression. Pure consistency/schema fix.

**Backlog row:** `APISIMP-TOMBSTONE-BARE-STRING` (filed below).

---

## What was checked (no new finding)

- `AdminConfigRest.listFeatures()` bare array: intentional (documented at backlog
  line 3912 — "already a bare list — no change needed there").
- `BundleGroupsV2Rest.listGroupFiles()` `Integer page/pageSize` without bean
  validation: intentional clamping design per fire-444 "Keep `Integer` type to
  preserve intentional clamping behavior."
- `PluginsAdminRest.list()` uses `PagedResponseIO` with bounded list — consistent
  with post-fire-458 standard.
- `InstanceRegistryRest`, `OntologyGitSourceRest`, `MffdProcessChainMappingRest`,
  `AdminConfigRest` — all clean.
- `PublicationsListRest` — pagination was already added in fire-461 (PR #2388).
- `UserGroupV2Rest` — dual-shape fixed in fire-456 (PR #2381).
- All `@Operation(operationId=...)` present across all v2 REST files (confirmed
  with parenthesis-aware parser; prior waves fire-341/352/354 already addressed
  every gap).

---

## Rows filed

1. `APISIMP-TOMBSTONE-BARE-STRING` (XS, queued) — see `aidocs/16` APISIMP section.
