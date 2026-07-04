---
stage: deployed
last-stage-change: 2026-07-04
---

# APISIMP Sweep — fire-409 (2026-07-04)

Five in-memory-pagination and unbounded-list findings across the semantic and
container resource families. All are XS-sized annotation-or-DAO changes with no
wire-shape impact on existing callers.

---

## F1 — In-memory pagination: `OntologyGitSourceRest.list()`

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/OntologyGitSourceRest.java:113`

`GET /v2/admin/semantic/ontology/git-sources` accepts `?page=` and `?pageSize=`
via `@QueryParam` but calls `gitSourceDAO.listAll()` — loading every row — and
slices in Java with `all.subList(from, to)`.  The DAO round-trip scales linearly
with total rows even when `pageSize=10`.

**Fix:** push `SKIP page*pageSize LIMIT pageSize` to `OntologyGitSourceDAO`; add
a paired `count()` Cypher query for the `total` envelope field.  No wire change.

**Backlog row:** `APISIMP-GIT-SOURCE-IN-MEMORY-PAGING`

---

## F2 — In-memory pagination: `VocabularyBrowseRest.listPredicatesForVocabulary()`

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/VocabularyBrowseRest.java:151`

`GET /v2/semantic/vocabularies/{vocabId}/predicates` accepts `?page=`/`?pageSize=`
but calls `predicateDAO.listByVocabulary(vocabId)` (no args) then slices in Java.
Large vocabularies (e.g. PROV-O, IAO) have hundreds of predicates; loading all
before slicing wastes DB I/O on every page-2+ request.

**Fix:** add `listByVocabulary(String vocabId, int skip, int limit)` to
`PredicateDAO`; delegate `count(vocabId)` to a `COUNT(*)` Cypher query.  No wire
change.

**Backlog row:** `APISIMP-VOCAB-PREDICATES-IN-MEMORY-PAGING`

---

## F3 — Unbounded list: `OntologyAlignmentRest.list()`

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/OntologyAlignmentRest.java:94`

`GET /v2/semantic/ontology/alignment` calls `ontologyAlignmentDAO.findAll()` and
returns a bare `List<OntologyAlignmentIO>` — no pagination params, no `?limit=`,
no size cap.  The alignment registry is small today (≤ ~50 rows per design) but
carries no enforcement preventing a migration from seeding thousands.

**Fix (preferred):** the set is architecturally bounded; replace the bare list
with a `List<OntologyAlignmentIO>` response annotated
`@Schema(type = SchemaType.ARRAY, implementation = OntologyAlignmentIO.class)` —
already present on the `@APIResponse` but the return is currently `Response.ok(body)`.
Add a server-side cap (`MAX = 500`; return first 500 + `X-Truncated: true` if
exceeded) so the contract is explicit even if the registry grows unexpectedly.

**Backlog row:** `APISIMP-ALIGNMENT-UNBOUNDED`

---

## F4 — Unbounded list: `VocabularyBrowseRest.listVocabularies()`

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/VocabularyBrowseRest.java:101`

`GET /v2/semantic/vocabularies` calls `vocabularyDAO.listAll()` with no cap.
An operator who has seeded 300 vocabularies (e.g. full OBO Foundry slice) gets
all 300 in one response; no way for callers to page.

**Fix:** same pattern as F3 — add `?page=`/`?pageSize=` (default 50, max 200)
and push `SKIP/LIMIT` to `VocabularyDAO`.  `listVocabularies()` is already
annotated with `@Schema(implementation = VocabularyIO.class)` (not array); the
`@APIResponse` needs to wrap in `PagedResponseIO`.

**Backlog row:** `APISIMP-VOCAB-LIST-UNBOUNDED`

---

## F5 — Fake-paged wrapper: `CollectionContainersRest.list()`

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionContainersRest.java:78`

`GET /v2/collections/{collectionAppId}/containers` accepts no `?page=`/
`?pageSize=` params but wraps the result in
`new PagedResponseIO<>(containers, containers.size(), 0, containers.size())`.
The envelope `page=0`, `pageSize=N`, `total=N` implies a navigable page set
that doesn't exist.  Callers reading `total > items.size()` as "there are more
pages" have no mechanism to retrieve them.

**Fix:** drop the `PagedResponseIO` wrapper; return `Response.ok(containers).build()`
with `@Schema(type = SchemaType.ARRAY, implementation = ContainerV2IO.class)`.
Paired frontend caller (`CollectionContainersPane.vue`) already unpacks the raw
array from `.items` — swap to reading the array directly.

**Backlog row:** `APISIMP-CONTAINERS-FAKE-PAGINATION`

---

## Summary

| Row ID | File | Line | Size |
|--------|------|------|------|
| APISIMP-GIT-SOURCE-IN-MEMORY-PAGING | `OntologyGitSourceRest.java` | 113 | XS |
| APISIMP-VOCAB-PREDICATES-IN-MEMORY-PAGING | `VocabularyBrowseRest.java` | 151 | XS |
| APISIMP-ALIGNMENT-UNBOUNDED | `OntologyAlignmentRest.java` | 94 | XS |
| APISIMP-VOCAB-LIST-UNBOUNDED | `VocabularyBrowseRest.java` | 101 | XS |
| APISIMP-CONTAINERS-FAKE-PAGINATION | `CollectionContainersRest.java` | 78 | XS |
