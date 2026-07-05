---
stage: fragment
last-stage-change: 2026-07-05
---

# APISIMP sweep — fire-413 / 2026-07-05

Sweep of the live `/v2` REST surface
(`backend/src/main/java/de/dlr/shepard/v2/**` +
`plugins/*/src/main/java/**/@Path`) for residual sprawl.

## Scan axes run

| Axis | Pattern | Result |
|---|---|---|
| Numeric `@PathParam` leak | `Long\|long @PathParam` | **0 hits** — all cleaned in prior passes |
| Fake `PagedResponseIO` wrapper | `new PagedResponseIO<>.*\.size\(\).*\.size\(\)` | 2 hits (see below) |
| Bespoke admin `*ConfigRest` | `@Path.*admin.*Config` | **0 hits** — all migrated |
| Unbounded `listAll()`/`findAll()` in REST | `\.(listAll|findAll)\(\)` in REST resources | 5 matches; 3 closed (prior PRs), 2 new (see below) |
| Missing `@PositiveOrZero` on `page` | `@QueryParam\("page"\).*int page` without guard | **0 hits** — APISIMP-PAGE-VALIDATION-GUARDS (PR #2292) closed this |

## Findings

### F1 — `NotificationTransportRest.java:84` — fake-paged wrapper on admin list

**File:** `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/resources/NotificationTransportRest.java:84`

`GET /v2/admin/notifications/transports` wraps its response in
`new PagedResponseIO<>(items, items.size(), 0, items.size())`.
The envelope always reports `page=0`, `pageSize=N`, `total=N` — i.e. the full
result in one fake page. There are no `?page=`/`?pageSize=` query params, so
callers reading `total > items.size()` as "there are more pages" have no
mechanism to retrieve them. Identical anti-pattern to APISIMP-CONTAINERS-FAKE-PAGINATION
(PR #2297, in-flight). No frontend consumers exist (grep returns empty in `frontend/`).

**Fix:** Drop `PagedResponseIO` wrapper → `Response.ok(items).build()` with
`@Schema(type = SchemaType.ARRAY, implementation = NotificationTransportReadIO.class)`.
Remove unused `PagedResponseIO` import.

**Severity:** MINOR — admin-only endpoint; the transport set is bounded (typically 1–5);
no client currently reads `.items` vs plain array. Wire-shape breakage risk is low.
Dispatched immediately as **APISIMP-NOTIFICATIONS-FAKE-PAGINATION**.

---

### F2 — `ShapesPredicatesRest.java:107` — in-memory pagination over static metadata table

**File:** `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesPredicatesRest.java:107`

`GET /v2/shapes/predicates` calls `repository.findAll()` then slices in Java —
the same in-memory paging pattern fixed in APISIMP-GIT-SOURCE-IN-MEMORY-PAGING (PR #2294)
and APISIMP-VOCAB-PREDICATES-IN-MEMORY-PAGING (PR #2293). However, the predicate
vocabulary table is a Flyway-only-writable static registry (< 200 rows in practice,
bounded by migrations). The `@Max(500)` cap on `pageSize` and the small table size
mean the real-world performance impact is negligible. `APISIMP-SHAPES-PREDICATES-FULL-PAGINATION`
(PR #2287, fire-403) already fixed the parameter naming; this finding is the "push
to DB" follow-up.

**Fix:** Add `findBySubstrate(String, int skip, int limit)` + `countAll()`/`countBySubstrate()`
to `PredicateVocabularyRepository`; delegate `SKIP/LIMIT` to Postgres. `findAll()` variant
kept only for the Java-slicing fallback if the repo layer is unwilling.

**Severity:** LOW — negligible real-world impact given table size; filed as low-priority
backlog row **APISIMP-PREDICATES-IN-MEMORY-PAGING** for a future fire.

---

## Closed matches (not findings)

| Location | Why not a finding |
|---|---|
| `HdfAdminRest.java:186` — `hdfContainerDAO.findAll()` | `POST /v2/admin/hdf/rebuild-acls` is a batch admin operation that must iterate ALL containers to rebuild HSDS ACLs. The `findAll()` is correct by design. |
| `SemanticMcpTools.java:207` — `vocabularyDAO.listAll()` | MCP tool layer, not a REST endpoint. Out of scope for REST-surface sweep. |
| `UnhideFeedService.java:122` — `collectionDAO.findAll()` | Service layer (Unhide publish feed), not a REST endpoint. Out of scope. |
| `VocabularyBrowseRest.java:104` | Already fixed by PR #2296 (merged fire-413). |
| `OntologyAlignmentRest.java:98` | Already fixed by PR #2295 (merged fire-411). |

## Dispatched this fire

| Finding | Row | Size | Action |
|---|---|---|---|
| F1 | APISIMP-NOTIFICATIONS-FAKE-PAGINATION | XS | Implemented + PR opened fire-413 |
| F2 | APISIMP-PREDICATES-IN-MEMORY-PAGING | XS (low-pri) | Filed for future fire |
