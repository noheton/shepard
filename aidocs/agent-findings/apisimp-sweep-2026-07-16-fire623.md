---
stage: deployed
last-stage-change: 2026-07-16
fire: fire-623
---

# APISIMP Sweep — 2026-07-16 (fire-623)

Post-WAVE4-merge sweep of the live `/v2` REST surface. WAVE4 (PR #2591, sha `41eb6f6`)
closed the X-Total-Count gap on 34 endpoints across 22 files. This pass finds the
remaining tail, plus notes three stale backlog entries whose "✅ merged" status
contradicts the current code.

## Scope

97 v2 REST resource files checked against:
- Per-kind endpoint sprawl
- Bespoke `*ConfigRest` outside generic registry
- Numeric Neo4j id leaks in `@PathParam` / `@QueryParam` / IO body
- Inconsistent pagination param names
- Response fields no caller reads
- Missing `X-Total-Count` on paged list endpoints
- Forbidden `@Path(Constants.SHEPARD_API + …)` additions
- Fake `PagedResponseIO` (no real paging params)

## Clean-gate result (no new violations)

- **Forbidden v1 paths**: zero new `@Path(Constants.SHEPARD_API + …)` in the v2 namespace.
- **Numeric Long `@PathParam`/`@QueryParam`**: zero new leaks. `PermissionAuditEntryIO.neo4jNodeId` is pre-existing, deprecated, tracked under APISIMP-PERM-AUDIT-NEO4J-ID.
- **Bespoke admin ConfigRest**: none. `AdminConfigRest` + `PublicConfigRest` is the only pair and follows the generic-registry pattern.
- **Pagination param names**: all paged endpoints use `page` + `pageSize` consistently. The single `@QueryParam("size")` in `ContainersV2Rest:1500` is a thumbnail pixel-size, not a pagination param.
- **Per-kind endpoint sprawl**: `ContainersV2Rest` and `ReferencesV2Rest` consistently use `?kind=`. `GET /v2/references/urdf` is a per-kind path but serves a distinct use case (instance-wide cross-collection picker) with documented justification.

---

## Finding F1 — APISIMP-XCOUNT-WAVE5 (size: S)

**6 paged list endpoints still missing `X-Total-Count` response header**

WAVE1–WAVE4 closed 37+11+10+34 endpoints. Six remain. All have `total` in scope and
use `PagedResponseIO`. Three have stale "✅ merged" backlog entries; the code
contradicts those claims.

| Endpoint | File (v2-relative) | Line | Backlog note |
|----------|-------------------|------|-------------|
| `GET /v2/collections/{appId}/dqr` | `quality/resources/CollectionDQRRest.java` | 88, 101 | APISIMP-PAGINATION-LIST-DQR claimed header included — code says otherwise |
| `GET /v2/publications` | `publish/resources/FlatPublicationsRest.java` | 89, 137 | APISIMP-FLAT-PUBS-NO-PAGINATION status ✅ merged PR #2130 — not in code |
| `GET /v2/semantic/vocabularies` | `semantic/resources/VocabularyBrowseRest.java` | 105, 114 | no existing backlog row |
| `GET /v2/semantic/vocabularies/{appId}/predicates` | `semantic/resources/VocabularyBrowseRest.java` | 148, 166 | no existing backlog row |
| `GET /v2/semantic/vocabularies/used-by/{appId}` | `semantic/resources/VocabularyBrowseRest.java` | 205, 220, 228 | no existing backlog row |
| `GET /v2/semantic/terms/search` | `semantic/resources/SemanticTermSearchRest.java` | 205, 244 | APISIMP-TERMSEARCH-NO-XCOUNT status ✅ merged fire-475 — not in code |

**Fix pattern (same as WAVE1–WAVE4):**

1. Add `import org.eclipse.microprofile.openapi.annotations.headers.Header;` (if absent).
2. On `@APIResponse(responseCode="200")`: add `headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))`.
3. Chain `.header("X-Total-Count", total)` on the `Response` builder before `.build()`.

Special case — `listVocabulariesUsedBy()` has an early-return path (line 220):
```java
return Response.ok(new PagedResponseIO<>(List.<VocabularyIO>of(), 0L, page, pageSize))
    .header("X-Total-Count", 0L).build();
```

Special case — `SemanticTermSearchRest.search()`: total = `results.size()` (fulltext index
cannot return a count-only query, so total reflects the current window — same as the
`PagedResponseIO.total` field. Still useful to carry as header.)

**AC:** All 6 endpoints return `X-Total-Count`. `@APIResponse(200)` blocks formally declare
the header. `mvn verify -pl backend` green.

---

## Finding F2 — APISIMP-CROSSDO-PAGED-MISUSE (size: XS)

**`CrossDoBulkDataRest` wraps a non-paged result in `PagedResponseIO`**

`POST /v2/data-objects/cross-bulk` (file: `timeseries/resources/CrossDoBulkDataRest.java`,
line 201) returns:

```java
return Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size())).build();
```

There are no `page`/`pageSize` query params; all results are returned in one call.
The `PagedResponseIO` envelope is misleading — callers see `{items, total=N, page=0,
pageSize=N}` as if paging were available, but there is no way to request page 2.

The endpoint description notes "future kinds will extend without a new path" — real paging
(option A) fits the forward direction:

**Option A (preferred):** Add `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize`; slice `out` accordingly; add
`X-Total-Count` header. Size: S.

**Option B (minimal):** Return plain `List<CrossDoSeriesIO>` + update `@APIResponse(200)`
schema. Size: XS.

**AC:** `@APIResponse(200)` schema matches the actual JSON shape returned. The envelope is
not degenerate `{total=N, page=0, pageSize=N}` when no pagination is offered. `mvn verify -pl backend` green.

---

## Stale backlog entries to correct

| Row ID | Claimed status | Reality | Action |
|--------|---------------|---------|--------|
| APISIMP-TERMSEARCH-NO-XCOUNT | ✅ merged (fire-475, PR #APISIMP-XCOUNT-BATCH-2) | Header absent from `SemanticTermSearchRest.java:244` | Fix via WAVE5 |
| APISIMP-FLAT-PUBS-NO-PAGINATION | ✅ merged fire-264 (PR #2130) | X-Total-Count absent from `FlatPublicationsRest.java:137` | Fix via WAVE5 |
| APISIMP-PAGINATION-LIST-DQR | ✅ done — fix "included X-Total-Count header" | Header absent from `CollectionDQRRest.java:101` | Fix via WAVE5 |

---

## Finding F3 — APISIMP-REFANNOT-GET-PATCH-CREATE-SCHEMA (size: S)

**`ReferenceAnnotationRest` POST/GET/{id}/PATCH all lack `@Content(schema=…)` on success responses**

`ReferenceAnnotationRest.java` (file: `references/resources/ReferenceAnnotationRest.java`):

| Method | Line | Annotation | Gap |
|--------|------|------------|-----|
| `create()` | 188 | `@APIResponse(responseCode = "201", description = "Annotation created; body is the new annotation map.")` | No `content = @Content(...)` |
| `get()` | 213 | `@APIResponse(responseCode = "200", description = "Annotation map.")` | No `content = @Content(...)` |
| `patch()` | 241 | `@APIResponse(responseCode = "200", description = "Post-patch annotation map.")` | No `content = @Content(...)` |

The `list()` method at line 150 already has `content = @Content(schema = @Schema(implementation = PagedResponseIO.class))` (added in WAVE3). The three other success responses have no typed schema, so the OpenAPI spec documents them as `{}` (any).

All three return `Map<String, Object>` — there is no dedicated IO class. Options:

**Option A (preferred):** Introduce `ReferenceAnnotationIO` wrapping the map fields with explicit `@Schema` properties. Declare `@Content(schema = @Schema(implementation = ReferenceAnnotationIO.class))` on all three.  
**Option B (minimal):** Add `@Content(mediaType = "application/json", schema = @Schema(type = SchemaType.OBJECT))` so the spec at least documents the media type rather than leaving the response body blank.

**AC:** All three `@APIResponse` success blocks carry a `@Content` clause; the generated OpenAPI spec does not show blank response bodies for POST/GET/{id}/PATCH. `mvn verify -pl backend` green.

---

## Finding F4 — APISIMP-NOTEBOOK-INMEM-PAGE (size: M)

**`NotebookRest.listNotebooks()` loads ALL notebooks into memory before slicing**

`NotebookRest.java` (file: `labjournal/resources/NotebookRest.java`, lines 156–215):

```java
List<NotebookReferenceIO> result = new ArrayList<>();
// accumulates all singletons + all bundle .ipynb entries for the container
long total = result.size();
int skip = (int) Math.min((long) page * pageSize, total);
int end = (int) Math.min((long) skip + pageSize, total);
List<NotebookReferenceIO> pageItems = result.subList(skip, end);
return Response.ok(new PagedResponseIO<>(pageItems, total, page, pageSize))
    .header("X-Total-Count", total).build();
```

This is an O(N) in-memory pagination pattern: the DAO fetches all singleton `FileReference` nodes in the container, then all `FileBundleReference` nodes, then filters `.ipynb` entries — materialising the full list regardless of requested `pageSize`. A container with 5,000 notebooks loads all 5,000 per page request.

The pattern is identical to the known `PersonalVocabularyRest` debt (APISIMP-PERSONAL-VOC-INMEM). Fix requires a DAO-level SKIP/LIMIT query for each kind, then merging at the page boundary. Medium complexity because the two sources (singleton vs. bundle entries) need separate count queries and a combined offset strategy.

**AC:** `listNotebooks()` issues at most two DAO queries (one count + one page-slice) regardless of total notebook count. No full list materialised. `mvn verify -pl backend` green.

---

## Summary

| ID | Title | Severity | Size |
|----|-------|----------|------|
| APISIMP-XCOUNT-WAVE5 | 6 paged list endpoints missing X-Total-Count header | MAJOR | S |
| APISIMP-CROSSDO-PAGED-MISUSE | CrossDoBulkDataRest wraps non-paged response in PagedResponseIO | MINOR | XS |
| APISIMP-REFANNOT-GET-PATCH-CREATE-SCHEMA | ReferenceAnnotationRest POST/GET/{id}/PATCH missing @Content schema | MINOR | S |
| APISIMP-NOTEBOOK-INMEM-PAGE | NotebookRest O(N) in-memory pagination anti-pattern | MAJOR | M |

**Smallest dispatchable slice next fire:** APISIMP-XCOUNT-WAVE5.
Fix pattern is identical to WAVE4 — mechanical across 6 files.
