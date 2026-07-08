---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — fire-471 · 2026-07-08

**Scope:** v2 REST surface — `@APIResponse` 200 response bodies missing `content = @Content(schema = @Schema(...))` on list endpoints that return `PagedResponseIO`.

**Method:** Cross-referenced all v2 REST files that return `Response.ok(new PagedResponseIO<>(...))` against their `@APIResponse(responseCode = "200", ...)` annotations; identified cases where the 200 response is annotated as a bare description string without a `content` schema declaration.

---

## §A1 — APISIMP-REFANNO-LIST-NO-SCHEMA (XS, 🔄 in-flight fire-471, PR #2399)

**Finding:** `ReferenceAnnotationRest.listReferenceAnnotationsV2()` (`GET /v2/references/{appId}/annotations`) declares its 200 `@APIResponse` with only a description string — no `content = @Content(schema = @Schema(implementation = PagedResponseIO.class))`:

```java
// Before (line 151 in main):
@APIResponse(responseCode = "200", description = "Paged envelope: items + total + page + pageSize. Header X-Total-Count = total count before paging (kept during deprecation window, APISIMP-PAGINATION-ENVELOPE).")
```

But the method body at line 165 returns:

```java
return Response.ok(new PagedResponseIO<>(slice, total, page, pageSize))
    .header("X-Total-Count", total)
    .build();
```

The OpenAPI spec therefore describes the 200 body as an untyped/empty response while the wire shape is a `PagedResponseIO` envelope. The file also lacked the `Content` and `Schema` imports needed to add the annotation.

**Fix:** Expand the `@APIResponse` to include `content`; add the two missing imports.

**Files legitimately having bare 200 responses (verified, not touched):**
- `ReferenceAnnotationRest.java:209` — single annotation GET returns `Map<String, Object>` (no schema to declare)
- `ReferenceAnnotationRest.java:237` — single annotation PATCH returns `Map<String, Object>` (no schema)
- `AdminConfigRest.java:101,129` — GET/PATCH config endpoints return kind-specific shapes; per-kind shapes are not declared here (APISIMP-CONFIG-SCHEMA is a separate potential finding)
- `LedgerAnchorRest.java:137` — endpoint always returns 501 (not implemented); aspirational annotation (no fix needed)
- `ContainersV2Rest.java:213` — binary file download, not a JSON schema
- Various `ContainersV2Rest` timeseries/channel annotations — binary/streaming responses

**Files correctly annotated with `PagedResponseIO` schema (verified):**
- `LabJournalHistoryRest.java:99` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `CollectionContainersRest.java:77` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `InstanceAdminRest.java:116,239` — both paged endpoints annotated ✅
- `PluginsAdminRest.java:121` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `ShapesPredicatesRest.java:88` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `SnapshotListRest.java:122` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `SemanticAdminRest.java:256` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `CollectionV2Rest.java` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `OntologyGitSourceRest.java` — `@Schema(implementation = PagedResponseIO.class)` ✅
- `ReferencesV2Rest.java` list endpoint — `@Schema(implementation = PagedResponseIO.class)` ✅
- `ContainersV2Rest.java` listVersions/getLinkedDataObjects — both annotated ✅

---

## Summary

| ID | Severity | Status |
|----|----------|--------|
| APISIMP-REFANNO-LIST-NO-SCHEMA | XS | 🔄 in-flight (fire-471, PR #2399) |

Previous fire-470/471 context:
- PR #2397 (fire-470): `APISIMP-USERGROUP-LIST-SCHEMA-MISMATCH` ✅ merged
- PR #2398 (fire-470): `APISIMP-DEAD-SCHEMATYPE-IMPORTS` — CI in progress at time of fire-471 sweep
