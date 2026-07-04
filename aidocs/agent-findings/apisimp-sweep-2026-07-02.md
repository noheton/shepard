---
stage: fragment
last-stage-change: 2026-07-02
author: fire-360-apisimp-sweep-agent
---

# APISIMP Sweep — 2026-07-02 (fire-360)

Fresh sweep of v2 REST resource files NOT covered by fire-355 through fire-359.
Criteria applied: per-kind sprawl (#1), bespoke admin config (#2 — inconsistency),
numeric-id leaks (#3), pagination inconsistency (#4), missing ProblemJson (#5),
superseded endpoints (#6), v1-path usage (#7), uncapped list (#8/#9), redundant
implementation (#10).

---

## Findings

### APISIMP-SEARCH-DO-UNCAPPED

**File:** `backend/src/main/java/de/dlr/shepard/v2/search/resources/SearchV2Rest.java`
**Criterion:** #9 (uncapped list endpoint)
**Severity:** MAJOR

`GET /v2/search` paginates Collection results correctly but returns DataObject
search results in full, with no cap. The call to `dataObjectSearchService.search(body)`
returns every matching DataObject and the resource loops over the full result set
and appends every item inline to the response:

```java
ResponseBody doResult = dataObjectSearchService.search(body);
// ...loops over doResult.getResults() entirely without limiting
```

A large corpus (100k DataObjects) with a broad query returns all matches in one
unbounded payload. This will OOM the JVM on a loaded instance and saturate the
client's network buffer.

**Proposed fix:** Add `@QueryParam("doPage") @DefaultValue("0") @PositiveOrZero int doPage`
and `@QueryParam("doPageSize") @DefaultValue("50") @Min(1) @Max(200) int doPageSize`
alongside the existing `page`/`pageSize` (which are for Collections). Slice
`doResult.getResults()` the same way the Collection results are sliced. Return
`doTotal` in the envelope. Alternatively, unify under a single `page`/`pageSize`
if the contract can treat Collections and DataObjects as a merged list.
**Size:** S

---

### APISIMP-SHAPES-PREDICATES-BARE-LIST

**File:** `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesPredicatesRest.java`
**Criterion:** #9 (uncapped list endpoint)
**Severity:** MINOR

`GET /v2/shapes/predicates` returns all entries from the `predicate_vocabulary`
table as a raw JSON array with no pagination envelope, no `X-Total-Count` header,
and no size cap:

```java
return Response.ok(entries).build();
```

Today the table is small (tens of entries). As the vocabulary grows and plugins
register additional predicates, this becomes unbounded. It is also inconsistent
with every other list endpoint in v2 that uses `PagedResponseIO`.

**Proposed fix:** Wrap in `PagedResponseIO` and accept `?limit=` (max 500, default 200)
since this table is read-only metadata unlikely to require cursor navigation. Or add
standard `?page=`/`?pageSize=` if growth is expected. At minimum, add
`Content-Range`/`X-Total-Count` to signal pagination capability.
**Size:** XS

---

### APISIMP-ONTOLOGY-ALIGNMENT-FAKE-PAGED

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/OntologyAlignmentRest.java`
- `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java` (method `listOntologies`)

**Criterion:** #4 (pagination inconsistency — fake-paged envelope)
**Severity:** MINOR

Both endpoints return a `PagedResponseIO` wrapper but accept no `?page=` or
`?pageSize=` query parameters. The wrapper is always constructed as
`new PagedResponseIO<>(body, body.size(), 0, body.size())`, which signals to
callers that the response IS already a paged envelope — implying that `page > 0`
would produce a different slice. It will not.

- `OntologyAlignmentRest.listAlignment()` line ~57: `new PagedResponseIO<>(body, body.size(), 0, body.size())`
- `SemanticAdminRest.listOntologies()` line 275: `new PagedResponseIO<>(rows, rows.size(), 0, rows.size())`

Callers (MCP tools, UI composables) checking `total > items.size()` to decide
whether to show a "load more" button will never trigger pagination — but callers
that check `pageSize` against `items.size()` to pre-page will be surprised.

**Proposed fix (option A — preferred):** Remove the `PagedResponseIO` wrapper; return
a plain JSON array. These lists are naturally small (handful of ontology bundles,
handful of alignment rows). Document `List<T>` in `@Schema`.
**Proposed fix (option B):** Add real `?page=`/`?pageSize=` params and slice
accordingly — needed only if the lists can grow to hundreds of items.
**Size:** XS per endpoint

---

### APISIMP-VOCAB-BROWSE-FAKE-PAGED

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/VocabularyBrowseRest.java`
**Criterion:** #4 (pagination inconsistency) + #9 (uncapped list, predicate method)
**Severity:** MINOR / MAJOR (predicate list is the larger concern)

Three methods each wrap in `PagedResponseIO` without accepting pagination params:

1. `GET /v2/semantic/vocabularies` — `new PagedResponseIO<>(out, out.size(), 0, out.size())`
2. `GET /v2/semantic/vocabularies/used-by/{entityAppId}` — same pattern
3. `GET /v2/semantic/vocabularies/{vocabId}/predicates` — returns `VocabularyPredicatesIO`
   directly (not wrapped), but has no cap at all. A full QUDT ontology has ~1000 predicates;
   SOSA/SSN has ~200. This is an uncapped list (criterion #9) and is the more serious concern.

**Proposed fix:**
- Methods 1 and 2: same as APISIMP-ONTOLOGY-ALIGNMENT-FAKE-PAGED — either drop the wrapper
  or add real pagination params.
- Method 3 (predicates): add `?page=`/`?pageSize=` capped at 200 per page. The vocabulary
  predicate list is the one that will actually scale badly.
**Size:** S (covers all three methods)

---

### APISIMP-REF-ANNOTATION-LIST-NO-PAGINATION

**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java`
**Criterion:** #4 (pagination inconsistency — fake-paged envelope)
**Severity:** MINOR

`GET /v2/references/{appId}/annotations` (`list()` method, line ~151) wraps the
full annotation list in `PagedResponseIO` without accepting `?page=` or `?pageSize=`
params:

```java
return Response.ok(new PagedResponseIO<>(rows, rows.size(), 0, rows.size())).build();
```

A densely-annotated timeseries reference (AI-generated annotations at 1-Hz cadence
over a 24-hour window = 86 400 rows) returns all rows in one response. The wrapper
falsely signals pagination support.

**Proposed fix:** Add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page`
and `@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize` to
the `list()` method signature. Slice `rows.subList(from, to)` identically to the
pattern in `CollectionWatchersRest.list()`.
**Size:** XS

---

### APISIMP-PROJECT-BY-ANNOTATION-IRI-PATH

**File:** `backend/src/main/java/de/dlr/shepard/v2/project/resources/ProjectsRest.java`
**Criterion:** #2 (inconsistency in IRI-in-path-param encoding strategy)
**Severity:** MINOR

`GET /v2/projects/{appId}/by-annotation/{predicate}/{value}` accepts IRI predicate
and value as raw URL-encoded path segments. The Javadoc acknowledges this explicitly:
`"URL-encoded — e.g. urn%3Ashepard%3Amffd%3Alayer"`.

This is inconsistent with the established pattern in `SemanticPredicateStatsRest`
(`GET /v2/semantic/predicates/{predicateIriBase64}/stats`), which uses Base64
URL-safe encoding to avoid ambiguity with path separators in IRIs.
IRIs can legitimately contain `/` characters (e.g. `http://schema.org/name`), which
are valid IRI characters but are structurally significant in path templates.
URL percent-encoding of `/` (`%2F`) is rejected by some reverse proxies (nginx,
Caddy by default) even when the path is otherwise valid.

**Proposed fix (option A — preferred):** Move `predicate` and `value` to
`@QueryParam` — `?predicate=<iri>&value=<iri>`. This sidesteps both the encoding
issue and the path-separator ambiguity entirely.
**Proposed fix (option B):** Adopt the Base64 URL-safe path encoding from
`SemanticPredicateStatsRest` — `{predicateBase64}/{valueBase64}` — and update
the Javadoc and OpenAPI description accordingly.
**Size:** S

---

### APISIMP-GIT-SOURCE-DUAL-AUTH

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/OntologyGitSourceRest.java`
**Criterion:** #10 (redundant implementation)
**Severity:** LOW — may be intentional (see note)

The class carries `@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)` at the class level,
but every method also calls `guardAdmin(securityContext)` which manually re-checks
`securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)`. Under normal JAX-RS
filter execution, `@RolesAllowed` has already blocked non-admin callers before any
method body runs, making the per-method guard dead code.

**Cross-reference — reconsideration note:** `SemanticAdminRest` uses the identical
pattern and explicitly documents its rationale at lines 136–145:
> "Defence-in-depth role check. Mirrors HdfAdminRest — @RolesAllowed catches the
> canonical paths, the manual check guards against test-only paths that bypass the
> JAX-RS filter chain."

This means the dual-auth is **deliberate** across admin resources in this codebase —
it defends against Quarkus test harness configurations that bypass the security filter
chain. If `OntologyGitSourceRest` follows the same convention, this finding is a
**false positive** and should be parked.

**Proposed action:** Verify whether `OntologyGitSourceRest` has the same test-harness
concern as `SemanticAdminRest`. If yes, close as "by design." If no comment exists and
no test bypasses the filter, add the comment and keep the guard for clarity, or remove
the guard if it genuinely serves no purpose.
**Size:** XS (if real); park if by-design

---

### APISIMP-INSTANCE-CAPABILITIES-DUPLICATE-200

**File:** `backend/src/main/java/de/dlr/shepard/v2/instance/InstanceCapabilitiesRest.java`
**Criterion:** documentation inconsistency (minor OpenAPI doc bug)
**Severity:** LOW

The `getCapabilities()` method carries two `@APIResponse(responseCode = "200", ...)`
annotations on the same method. OpenAPI does not allow duplicate status codes on one
operation; SmallRye OpenAPI will silently overwrite one entry. The second annotation
attempts to document that this is a public endpoint, but that detail belongs in the
`@Operation` description, not as a duplicate response code.

**Proposed fix:** Remove the second `@APIResponse(responseCode = "200", ...)` and
move its descriptive content to the `@Operation(description = ...)` field.
**Size:** XS

---

## Files Scanned — Clean

The following files were read and raised no findings:

| File | Status |
|------|--------|
| `v2/export/rep/RepExportV2Rest.java` | Clean — proper auth gates, proper ProblemJson, GET /latest deliberately stubs 404 (TPL14b) |
| `v2/collection/resources/CollectionCrossTimelineRest.java` | Clean — proper auth, proper ProblemJson, no uncapped lists |
| `v2/publish/resources/PublicationsListRest.java` | Clean — self-documents APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT deprecation (pre-filed); no new finding |
| `v2/publish/resources/PublishRest.java` | Clean — proper kind-registry dispatch, proper ProblemJson, proper auth |
| `v2/collectionwatchers/resources/CollectionWatchersRest.java` | Clean — properly capped pagination, proper ProblemJson |
| `v2/shapes/resources/ShapesBuildRest.java` | Clean — stateless, pure function |
| `v2/shapes/resources/ShapesRenderRest.java` | Clean — SPI dispatch, content negotiation |
| `v2/shapes/resources/ShapesValidateRest.java` | Clean — stateless SHACL validation |
| `v2/shapes/resources/ShapesApplicableRest.java` | Clean — fail-soft, proper ProblemJson |
| `v2/vocabularies/resources/PersonalVocabularyRest.java` | Clean — proper pagination on GET |
| `v2/watches/resources/CollectionWatchesRest.java` | Clean — proper pagination on GET |
| `v2/dataobject/resources/DataObjectRdfRest.java` | Clean — proper auth, content negotiation |
| `v2/dataobject/resources/DataObjectCollectionScopedRdfRest.java` | Clean — documented alias; collectionAppId not validated against parent (deliberate, per Javadoc) |
| `v2/collection/resources/CollectionStreamExportRest.java` | Clean — streams RO-Crate ZIP, proper auth |
| `v2/publish/resources/FlatPublicationsRest.java` | Clean — properly paginated, proper ProblemJson |
| `v2/instance/InstanceIdentityRest.java` | Clean — authenticated read of ROR config |
| `v2/instance/InstanceRegistryPublicRest.java` | Clean — confirms APISIMP-INSTANCE-REGISTRY-BESPOKE (fire-355) is fixed |
| `v2/semantic/resources/SemanticPredicateStatsRest.java` | Clean — Base64 IRI encoding, capped params |
| `v2/admin/resources/AdminMetricsRest.java` | Clean — instance-admin gated, single snapshot |
| `v2/admin/resources/AdminStorageOverviewRest.java` | Clean — instance-admin gated, aggregated stats |
| `v2/admin/mffd/resources/MffdProcessChainMappingRest.java` | Clean — admin-only, proper skip-capture |
| `v2/admin/semantic/SemanticAdminRest.java` | Clean on dual-auth (explicitly justified); `listOntologies` extends APISIMP-ONTOLOGY-ALIGNMENT-FAKE-PAGED |

---

## Previously Known — Not Re-Filed

- **APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT** — superseded `PublicationsListRest` endpoint is already self-deprecated with `deprecated=true` in OpenAPI; `FlatPublicationsRest` is the canonical replacement. No new filing needed.
- **APISIMP-INSTANCE-REGISTRY-BESPOKE** — confirmed fixed: `InstanceRegistryPublicRest` now lives at `/v2/instance/registry`.
- All APISIMP-* rows listed in the sweep brief as previously filed were not re-examined.

---

## Summary Table

| Slug | File | Criterion | Severity | Size |
|------|------|-----------|----------|------|
| APISIMP-SEARCH-DO-UNCAPPED | `SearchV2Rest` | #9 uncapped list | MAJOR | S |
| APISIMP-SHAPES-PREDICATES-BARE-LIST | `ShapesPredicatesRest` | #9 uncapped list | MINOR | XS |
| APISIMP-ONTOLOGY-ALIGNMENT-FAKE-PAGED | `OntologyAlignmentRest`, `SemanticAdminRest` | #4 pagination inconsistency | MINOR | XS×2 |
| APISIMP-VOCAB-BROWSE-FAKE-PAGED | `VocabularyBrowseRest` (3 methods) | #4 + #9 | MINOR/MAJOR | S |
| APISIMP-REF-ANNOTATION-LIST-NO-PAGINATION | `ReferenceAnnotationRest` | #4 pagination inconsistency | MINOR | XS |
| APISIMP-PROJECT-BY-ANNOTATION-IRI-PATH | `ProjectsRest` | #2 inconsistency | MINOR | S |
| APISIMP-GIT-SOURCE-DUAL-AUTH | `OntologyGitSourceRest` | #10 redundant (may be by-design) | LOW | XS |
| APISIMP-INSTANCE-CAPABILITIES-DUPLICATE-200 | `InstanceCapabilitiesRest` | doc bug | LOW | XS |
