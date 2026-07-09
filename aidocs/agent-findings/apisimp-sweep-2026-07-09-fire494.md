---
stage: deployed
last-stage-change: 2026-07-09
---

# APISIMP SWEEP — fire-494 (2026-07-09)

Scope: live `/v2` REST surface + plugin REST classes. Triggered because the APISIMP
dispatch queue was empty after merging PR #2424 (APISIMP-PLUGIN-IO-SCHEMA-MISSING) and
PR #2425 (APISIMP-MAPPINGS-FQN-ANNOTATIONS) earlier in fire-494.

## Patterns checked

| Pattern | Files checked | Findings |
|---|---|---|
| Inline FQN OpenAPI annotation bodies | All `v2/**/*.java` + `plugins/**/*.java` REST resources | 1 (§F1) |
| `SchemaType.ARRAY` on non-array endpoints | 8 usages across v2 + plugin REST | 0 — all correct |
| Wire-IO classes missing `@Schema(name,description)` | All `v2/**/*IO.java` | 2 (§F2) |
| `new ProblemJson(...)` outside `ProblemResponse.java` | All `v2/**/*.java` + `plugins/**/*.java` | 0 |
| `@Path(Constants.SHEPARD_API + ...)` in v2/plugin REST | All `v2/**/*.java` + `plugins/**/*.java` | 0 (known frozen spatiotemporal excluded) |
| Boxed Integer params needing `@Min`/`@Max` | Spot-checked v2 REST | 0 (APISIMP-MAXPOINTS-BOXED already addressed) |

## F1 — `DmpSnippetV2Rest.java:118` inline FQN `SchemaType.STRING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/fair/resources/DmpSnippetV2Rest.java:118`

**Finding:** One remaining inline FQN `org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING`
in the `@Content` annotation body on the `renderSnippet` endpoint's markdown response variant.

```java
// line 118 — current
@Content(mediaType = "text/markdown", schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING)),
```

This is the only FQN usage remaining across the entire v2 + plugin REST surface after
APISIMP-MAPPINGS-FQN-ANNOTATIONS (PR #2425). Every other `SchemaType.*` usage across
the codebase uses the short imported form.

**Backlog row:** `APISIMP-DMP-FQN-SCHEMATYPE` (XS)

**Fix:** Add `import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;` and
replace the FQN with `SchemaType.STRING`.

## F2 — `CollectionEventIO` + `RepExportIO` missing `@Schema(name,description)`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/events/CollectionEventIO.java`
- `backend/src/main/java/de/dlr/shepard/v2/export/rep/RepExportIO.java`

**Finding:** Two wire-IO classes introduced after the APISIMP-SCHEMA-MISSING-IO
sweep (fire-483) do not carry a class-level `@Schema(name=..., description=...)`.

- `CollectionEventIO` — SSE event payload record for `GET /v2/collections/{appId}/events`
  (P13 collection change-feed). Present in OpenAPI spec as an anonymous schema.
- `RepExportIO` — response body for `POST /v2/collections/{appId}/export/regulatory-evidence`
  and its GET twin (TPL14 regulatory evidence pack). Present in OpenAPI spec without name.

Both appear in the generated OpenAPI spec without stable schema names, reducing client
discoverability relative to the rest of the v2 surface.

**Backlog row:** `APISIMP-V2-IO-SCHEMA-RESIDUAL` (XS)

**Fix:** Add `@Schema(name = "CollectionEvent", description = "SSE event payload for the Collection change-feed (P13).")` to `CollectionEventIO` and `@Schema(name = "RepExport", description = "Response body for the Regulatory Evidence Pack export (TPL14).")` to `RepExportIO`.

## SchemaType.ARRAY verification results (no findings)

All `SchemaType.ARRAY` usages in v2 + plugin REST were verified against their
actual return types. All are correct — each annotates a bare `List<T>` response body
or a `@RequestBody` array input:

| File | Line | Return type | Verdict |
|---|---|---|---|
| `AdminConfigRest.java` | 83 | `List<ConfigFeatureIO>` bare array | correct |
| `OntologyAlignmentRest.java` | 92 | `List<OntologyAlignmentIO>` bare array | correct |
| `DataObjectV2Rest.java` | 196 | Serialized `List<DataObjectListItemV2IO>` (string body) | correct |
| `DataObjectBatchV2Rest.java` | 139 | `@RequestBody` array input — not a response | correct |
| `ImportDiagnosticsV2Rest.java` | 121 | `List<EventIO>` bare array | correct |
| `ImportDiagnosticsV2Rest.java` | 182 | `List<RunSummaryIO>` bare array | correct |
| `MeCredentialsRest.java` | 69 | `List<GitCredentialIO>` bare array | correct |
| `NotebookRest.java` | 113 | `List<NotebookReferenceIO>` bare array | correct |

## Summary

Two new `APISIMP-*` rows filed. Both are XS fixes suitable for the next fire's
implementation slot. The surface is otherwise clean on all six checked patterns.
