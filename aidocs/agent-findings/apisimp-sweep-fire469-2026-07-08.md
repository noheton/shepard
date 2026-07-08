---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — Fire 469 — 2026-07-08

**Scope:** All `@Path` REST classes under `/v2/` (backend + plugins); v2 IO response
classes for unused/verbosity fields; `@APIResponse` annotation schema-type consistency;
missing `@Operation(operationId=...)`; new `Long` id fields in IO classes.

**Coverage:** Full v2 REST surface (~130 REST files). Backlog cross-checked against
all existing APISIMP entries in `aidocs/16-dispatcher-backlog.md` before reporting
each finding. Previous fire-468 coverage (SSE/streaming, fake-paged, pagination
param names, missing ProblemJson, bespoke admin `*ConfigRest`, boxed Integer params,
`@Path(Constants.SHEPARD_API + ...)` additions) is excluded from re-check.

---

## §A New Findings

### §A1 APISIMP-USERGROUP-LIST-SCHEMA-MISMATCH

**File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java`
lines 93–95

**Problem:** The `@APIResponse` annotation on `listUserGroups()` declares the 200
response as a bare array of `UserGroupV2IO`:

```java
content = @Content(schema = @Schema(implementation = UserGroupV2IO.class, type = SchemaType.ARRAY))
```

The actual wire shape on every code path — both with and without `?q=` — is
`PagedResponseIO<UserGroupV2IO>` (lines 114 and 122):

```java
return Response.ok(new PagedResponseIO<>(items, (long) items.size(), 0, items.size())).build();
// ...
return Response.ok(new PagedResponseIO<>(items, total, page, pageSize)).build();
```

The generated OpenAPI spec therefore describes `GET /v2/user-groups` as returning
`array[UserGroupV2IO]` while the actual response body is
`{items: UserGroupV2IO[], total, page, pageSize}`. Callers relying on the spec for
type-safe deserialization will fail to decode the `total`, `page`, and `pageSize`
fields.

**Root cause:** A two-PR oscillation. Fire-444 (PR #2361, SHA `4318a9d`) changed the
`?q=` path from fake-`PagedResponseIO` to a bare list and correctly updated the
annotation to `SchemaType.ARRAY`. Fire-456 (PR #2381, SHA `f17385e3`) then reverted
the `?q=` path back to `PagedResponseIO` but did not revert the annotation — leaving
the annotation permanently mismatched.

**Fix (XS):**
1. In `listUserGroups()`, replace line 95:
   ```java
   content = @Content(schema = @Schema(implementation = UserGroupV2IO.class, type = SchemaType.ARRAY))
   ```
   with:
   ```java
   content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
   ```
2. Remove the now-unused `SchemaType` import (line 39:
   `import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;`).

No runtime behaviour changes — the fix is annotation-only.

**Size:** XS

---

## §B Coverage Summary

| Angle checked | Outcome |
|---|---|
| Response verbosity — IO fields no `/v2/` caller reads | None found. DB-OPT5 + API2 already swept the main culprits. All remaining fields examined (`DataObjectDetailV2IO`, `AnnotationIO`, `PermissionAuditLogEntryIO`, `InstanceAdminGrantIO`, `AdminMetricsSummaryIO`, `TypedPredecessorSummaryIO`) are consumed by frontend, MCP tools, or backend tests. |
| `Response` return types instead of typed returns | Not a new finding class — the 99-method `Response`-return pattern is established practice for JAX-RS (type safety lives in the `@APIResponse` annotation, not the return type). No new individual deviations found beyond those in the annotation-mismatch class. |
| Missing `@Operation(operationId=...)` | None found. Fire-341/352/354 waves (129+ operationIds) plus subsequent per-PR fixes addressed all v2 REST methods. Zero methods in the current codebase lack `operationId`. |
| Inconsistent `@APIResponse` schema type — `SchemaType.ARRAY` vs `PagedResponseIO` | **1 finding filed** (§A1 above). All other `SchemaType.ARRAY` sites verified correct: `DataObjectV2Rest.list()` genuinely returns a bare array (documented divergence, tracked as `APISIMP-DO-LIST-CONTENT-RANGE`); `NotebookRest`, `AdminConfigRest`, `AdminUserGitCredentialRest`, `OntologyAlignmentRest`, `DataObjectBatchV2Rest` (array is on the `@RequestBody`, not the response), `ImportDiagnosticsV2Rest` — all annotations match their wire shapes. |
| New `Long` id fields in IO classes | None found. `DataObjectListItemV2IO.timeBoundsStart/End` are epoch-nanosecond timestamps, not IDs. `AnnotationIO.validFromMillis/validUntilMillis` are millisecond timestamps. All other `long`/`Long` fields examined are counts, metrics, or sizes — none are substrate-internal IDs beyond `PermissionAuditEntryIO.neo4jNodeId` already tracked as `APISIMP-PERMISSION-AUDIT-NEO4J-ID`. |

The `SchemaType.ARRAY`-vs-`PagedResponseIO` oscillation pattern on `UserGroupV2Rest`
was not covered by prior annotation sweeps (those targeted missing/wrong error-response
codes, not response-body schema-type mismatches on success responses), and does not
appear elsewhere in the existing backlog.

---

*Sweep run: fire-469 · 2026-07-08*
