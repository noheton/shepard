---
stage: feature-defined
last-stage-change: 2026-07-09
---

# APISIMP Sweep — fire-488 + fire-491 (2026-07-09)

fire-488 triggered because all named `APISIMP-*` rows in `aidocs/16` were ✅ shipped or ⛔ deferred.
fire-491 (second pass, same day) extended the plugin IO @Schema check that fire-488 missed.
Previous fire (fire-487) merged PR #2421 (APISIMP-SCHEMA-MISSING-IO wave 2, 15 IO classes).

## Scope

Scanned in-tree v2 (`backend/src/main/java/de/dlr/shepard/v2/`) and plugin REST
(`plugins/*/src/main/java/`). Checks run in order:

1. Forbidden `@Path(Constants.SHEPARD_API + …)` in v2/plugin new code → **0 findings**
2. `new ProblemJson(` outside `ProblemResponse.java` → **0 findings** (all migrated)
3. Missing `@Operation(operationId)` on v2 REST resources → **0 findings** (only allowlisted frozen files)
4. Bespoke `*ConfigRest` classes outside `AdminConfigRest.java` → **0 findings**
5. Per-kind container/reference endpoints outside the frozen spatiotemporal compat surface → **0 per-kind route findings**
6. Missing `X-Total-Count` response header on list/batch endpoints → **6 endpoints (F1)**
7. Bespoke per-kind stats REST classes with live callers still using old URLs → **2 classes (F2)**

---

## F1 — 6 list/batch endpoints missing `X-Total-Count` response header

**Severity:** MINOR — pagination contract gap; callers cannot tell total without a second call  
**Size:** XS (6 one-liners + 6 `@Header` OpenAPI declarations)  
**Filed as:** `APISIMP-LIST-XCOUNT-RESIDUAL`

All 6 already wrap their result in `PagedResponseIO` (so the body carries `total`), but none
chains `.header("X-Total-Count", (long) size)` on the `Response` builder, and none declares
`headers = @Header(name="X-Total-Count", ...)` in the `@APIResponse` annotation — breaking the
formal OpenAPI contract.

| File | Line | Endpoint | Current return |
|------|------|----------|----------------|
| `ContainersV2Rest.java` | 528 | `GET /v2/containers/{appId}/files/{fileName}/versions` (`listVersions`) | `Response.ok(new PagedResponseIO<>(versionList, versionList.size(), 0, versionList.size())).build()` |
| `ContainersV2Rest.java` | 609 | `GET /v2/containers/{appId}/linked-data-objects` (`getContainerLinkedDataObjects`) | `Response.ok(new PagedResponseIO<>(linkedList, linkedList.size(), 0, linkedList.size())).build()` |
| `ContainersV2Rest.java` | 792 | `POST /v2/containers/{appId}/channels/data/bulk` (`getContainerBulkChannelData`) | `Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size())).build()` |
| `CrossDoBulkDataRest.java` | 173 | `POST /v2/data-objects/cross-timeseries-bulk` (`getCrossDoBulkData`) | `Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size())).build()` |
| `DataObjectV2Rest.java` | 915 | `GET /v2/collections/{cid}/data-objects/{appId}/predecessor-chain` (`predecessorChain`) | `Response.ok(new PagedResponseIO<>(result, result.size(), 0, result.size())).build()` |
| `DataObjectV2Rest.java` | 956 | `GET /v2/collections/{cid}/data-objects/{appId}/successor-chain` (`successorChain`) | `Response.ok(new PagedResponseIO<>(result, result.size(), 0, result.size())).build()` |

**Fix:** For each: chain `.header("X-Total-Count", (long) <size-var>)` before `.build()`;
add `headers = @Header(name = "X-Total-Count", description = "Total number of items.", schema = @Schema(type = SchemaType.INTEGER))`
to the corresponding `@APIResponse(responseCode = "200")` annotation.

---

## F2 — 2 bespoke per-kind stats REST classes; live frontend caller still on old URLs

**Severity:** MINOR — dead-code risk; the unified stats endpoint already covers these kinds  
**Size:** S (frontend composable migration + 2 Java class deletions)  
**Filed as:** `APISIMP-STATS-PERKIND-COLLAPSE`

`ContainersV2Rest.getStats()` (`GET /v2/containers/{appId}/stats`) already delegates to
`FileContainerKindHandler.getStats()` and `StructuredDataContainerKindHandler.getStats()`,
making `FileContainerStatsRest` and `StructuredDataContainerStatsRest` dead code — but they
are still live because `useContainerCardinality.ts` (lines 38 and 40) still calls the
old per-kind URLs:

```typescript
// frontend/composables/containers/useContainerCardinality.ts:38
case "FILE":
  return `${getV2BaseUrl()}/v2/file-containers/${containerAppId}/stats`;
// :40
case "STRUCTUREDDATA":
  return `${getV2BaseUrl()}/v2/structured-data-containers/${containerAppId}/stats`;
```

The unified endpoint `GET /v2/containers/{appId}/stats` covers all kinds (TIMESERIES,
FILE, STRUCTUREDDATA, SCENEGRAPH, …). Migrating the composable to use the unified
path allows deleting both bespoke REST classes and their associated tests.

**Fix:**
1. Change both `case` branches in `useContainerCardinality.ts` to `return \`${getV2BaseUrl()}/v2/containers/${containerAppId}/stats\``.
2. Delete `FileContainerStatsRest.java` + its test.
3. Delete `StructuredDataContainerStatsRest.java` + its test.
4. Update `useContainerCardinality.test.ts` to assert the unified URL.

**AC:** `GET /v2/file-containers/*/stats` → 404; `GET /v2/structured-data-containers/*/stats` → 404;
`useContainerCardinality.ts` uses the unified path for all kinds; FE lint + test + typecheck green.

---

---

## F3 — 34 plugin wire-IO classes missing class-level `@Schema(description)` (fire-491)

**Severity:** MINOR — OpenAPI spec gap for plugin endpoints; same class as APISIMP-SCHEMA-MISSING-IO (waves 1+2) which was scoped to `de.dlr.shepard.v2.*.io` only  
**Size:** M (34 files across 10 plugins)  
**Filed as:** `APISIMP-PLUGIN-IO-SCHEMA-MISSING`

The fire-488 "What was clean" section stated "Zero IO classes missing `@Schema`" — that assertion was correct for `de.dlr.shepard.v2.*.io` (the scope of APISIMP-SCHEMA-MISSING-IO waves 1+2, PRs #2420/#2421), but the prior sweep explicitly excluded `plugins/`. The fire-491 pass extended coverage to `plugins/*/src/main/java/*/io/` and found 44 files missing `@Schema`, of which 10 are CLI-only types (not wire response types) and 34 are genuine wire IO types appearing in the plugin OpenAPI spec.

**Total plugin IO files (main only, excluding test):** 66  
**Already annotated:** 22  
**Missing @Schema:** 44  
**Excluded as CLI-only (not wire types):** 10  
**Genuine wire types needing @Schema:** 34

**CLI-only exclusions** (not wire types — no @Schema required):  
`HdfCliConfig.java`, `DataciteConfig.java`, `DataciteCredentialSet.java`, `DataciteTestConnection.java`, `EpicConfig.java`, `EpicCredentialSet.java`, `EpicTestConnection.java`, `HarvestKeyMinted.java` (cli/io), `UnhideConfig.java` (cli/io), `VideoConfigCli.java`

**Wire types missing @Schema by plugin:**

| Plugin | Files |
|--------|-------|
| `aas` | `AasRegistrationIO`, `AasSyncResultIO`, `AasConfigIO`, `AasConfigPatchIO`, `AasShellDescriptorIO` (5) |
| `ai` | `AiCapabilityConfigIO` (1) |
| `git` | `CheckUpdateResultIO` (1) |
| `hdf5` | `HdfConfigIO` (1) |
| `minter-datacite` | `DataciteCredentialIO`, `DataciteCredentialSetIO`, `DataciteMinterConfigIO`, `DataciteMinterConfigPatchIO`, `DataciteTestConnectionIO` (5) |
| `minter-epic` | `EpicCredentialIO`, `EpicCredentialSetIO`, `EpicMinterConfigIO`, `EpicMinterConfigPatchIO`, `EpicTestConnectionIO` (5) |
| `spatiotemporal` | `FilterCondition`, `Operator`, `SpatialDataQueryParams` (3 — frozen v1 surface, lowest priority) |
| `unhide` | `FeedEntryIO`, `FeedIO`, `HarvestKeyMintedIO`, `UnhideConfigIO`, `UnhideConfigPatchIO`, `UnhideValidationReportIO` (6) |
| `v1-compat` | `LegacyV1ConfigIO`, `LegacyV1ConfigPatchIO`, `LegacyV1StatsIO` (3) |
| `video` | `VideoConfigIO`, `VideoConfigPatchIO` (2) |
| `wiki-writer` | `WikiWriteRequestIO`, `WikiWriteResponseIO` (2) |

**Fix:** Apply the same pattern as APISIMP-SCHEMA-MISSING-IO: add `@Schema(description = "<one-liner>")` at the class level on each of the 34 files. Batch as XS PRs per plugin. The 3 `spatiotemporal` files are on the frozen v1 surface — annotate for spec completeness but treat as lowest priority; they do not break anything unannotated.

**AC:** `find plugins -path "*/io/*.java" -not -path "*/test/*" -not -path "*/cli/*"  | xargs grep -L '@Schema'` returns only the 10 excluded CLI-only files; plugin OpenAPI specs declare `description` on all wire types; `mvn verify` green.

---

## F4 — `MappingsMaterializeRest` uses FQN OpenAPI annotation names (fire-493)

**Severity:** MINOR — cosmetic consistency; doesn't break compilation or runtime, but diverges from every other REST class in the v2 surface  
**Size:** XS (add 3 imports; replace ~7 FQN annotation references with short names)  
**Filed as:** `APISIMP-MAPPINGS-FQN-ANNOTATIONS`

`MappingsMaterializeRest.java` uses fully-qualified annotation names without import statements — the only REST class in the entire v2 surface with this pattern:

```java
// Current state:
@org.eclipse.microprofile.openapi.annotations.tags.Tag(name = "Mappings")
public class MappingsMaterializeRest {
  @org.eclipse.microprofile.openapi.annotations.Operation(...)
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "200", ...)
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "401", ...)
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "404", ...)
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "422", ...)
  public Response materialize(...) { ... }
}
```

Every other `*Rest.java` in `de.dlr.shepard.v2.*` imports `Tag`, `Operation`, and `APIResponse` from `org.eclipse.microprofile.openapi.annotations.*` and uses short names. This is purely a style divergence introduced when `MappingsMaterializeRest` was added (V2CONV-B3); the FQN workaround avoids any import name shadowing concern but is now inconsistent.

**Fix:** Add three imports and switch to short names:
```java
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Replace all 7 FQN usages with `@Tag`, `@Operation`, `@APIResponse`.

**AC:** `grep -n "org.eclipse.microprofile.openapi.annotations" backend/src/main/java/de/dlr/shepard/v2/mappings/resources/MappingsMaterializeRest.java` returns zero lines; `mvn -q test-compile` passes; no `import` conflicts (no other `Tag`, `Operation`, or `APIResponse` in the class imports).

---

## What was clean (fire-488)

- Zero `new ProblemJson(` constructions outside `ProblemResponse.java` — APISIMP-PROBLEM-DEDUP waves complete
- Zero **v2** IO classes missing `@Schema` — APISIMP-SCHEMA-MISSING-IO waves complete (`de.dlr.shepard.v2.*.io` scope; plugin IO was out of scope, now filed as F3)
- Zero v2 endpoints with `@QueryParam("size")` — APISIMP-PAGINATION-UNIFY-RECREATE complete
- Zero missing `operationId` on v2/plugin REST resources (only frozen spatiotemporal v1 compat surface, allowlisted)
- Zero bespoke `*ConfigRest` classes — V2CONV-A4 complete
- Zero per-kind container/reference routes outside frozen compat surface

## Filed rows

| Row | Size | Status | Fire |
|-----|------|--------|------|
| `APISIMP-LIST-XCOUNT-RESIDUAL` | XS | 🔄 PR open (fire-489) | fire-488 |
| `APISIMP-STATS-PERKIND-COLLAPSE` | S | 🔄 PR open (fire-490) | fire-488 |
| `APISIMP-PLUGIN-IO-SCHEMA-MISSING` | M | queued | fire-491 |
| `APISIMP-MAPPINGS-FQN-ANNOTATIONS` | XS | queued | fire-493 |

Next fire dispatches `APISIMP-MAPPINGS-FQN-ANNOTATIONS` (XS — 3 imports, 7 FQN replacements in one file) then begins `APISIMP-PLUGIN-IO-SCHEMA-MISSING` wave 1.
