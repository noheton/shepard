---
title: Ontology term search (reference)
description: Complete reference for the /semantic/search surface — the search-as-you-type term discovery page, its derived namespace tree/graph projection, the composable + pure helpers, and the backend endpoint it consumes.
permalink: /reference/ontology-term-search/
layout: default
audience: user
---
# Ontology term search (reference)

The **ontology term search** (`/semantic/search`) is a frontend-only discovery
surface (backlog row **L4**) that turns the flat term-suggestion endpoint into a
live, browsable view of where a matched term sits in the ontology. It consumes the
existing backend endpoint **`GET /v2/semantic/terms/search`** (N1e) — there is **no
new backend code**. The hierarchy shown is *derived client-side* from each term's IRI
namespace.

---

## 1. Why a derived hierarchy

The term-search endpoint returns a flat list of `{ uri, label, description }`
suggestions with no parent/child edges — the n10s `:Resource` nodes are queried by
label, not by hierarchy. To show *where a matched term sits* without a backend change,
the page derives a **two-level namespace → term** hierarchy from each term's IRI.

Splitting an IRI at its last `#` (hash namespaces, e.g. OWL/RDFS) or, failing that,
its last `/` (slash namespaces, e.g. dcterms) is the standard ontology-browser
fallback when an explicit broader/narrower axis is unavailable (cf. the BioPortal
usage study, arXiv:1610.09160; FAIR vocabulary URI-design guidance,
arXiv:2003.13084).

---

## 2. Backend endpoint consumed

### `GET /v2/semantic/terms/search`

| param | type | default | notes |
|-------|------|---------|-------|
| `q` | string | — | required; **min 2 chars** (400 if shorter) |
| `pageSize` | int | 20 | capped at **50** server-side |

**Response** — a flat JSON array of `TermSuggestionIO`:

```json
[
  { "uri": "http://purl.org/dc/terms/creator", "label": "Creator", "description": "An entity responsible for making the resource" },
  { "uri": "http://purl.org/dc/terms/title",   "label": "Title",   "description": null }
]
```

`200` + `[]` when nothing matches (or no ontology is seeded); `400` when `q` is
shorter than 2 chars; `401` when unauthenticated. The endpoint is unchanged from N1e —
L4 adds only a consumer.

---

## 3. Frontend surface

### 3.1 Page — `frontend/pages/semantic/search.vue`

- Route `/semantic/search`. A `v-text-field` (`role="combobox"`, autofocus) bound to a
  debounced query ref; a `v-btn-toggle` switches between **tree** and **graph** view.
- **Tree view** — a `v-list` with one `v-list-group` per namespace; each leaf links to
  the predicate detail page (`/semantic/predicates/{encodeURIComponent(uri)}`) and the
  SPARQL playground (`/semantic/sparql?query=…`).
- **Graph view** — a client-only ECharts `VChart` laid out with `@dagrejs/dagre`
  (`rankdir LR`), reusing the existing `baseGraphSeriesConfig()` shared by the lineage
  and provenance graphs. **No new graphing library was added** — `echarts`,
  `vue-echarts`, and `@dagrejs/dagre` were already in `package.json`.
- Fluid width with a `70vh` (min `420px`) graph canvas so the surface is responsive at
  4K. Prompt / empty / error / loading states are all rendered. `data-testid`s are
  present throughout for Playwright.

Entry points (per the "tool entry points are in-context first" rule): a quick-link in
`SemanticPane` (`/me#semantic`) and a **Search ontology terms** button on
`/semantic/vocabularies`.

### 3.2 Composable — `frontend/composables/semantic/useOntologySearch.ts`

Wraps the existing `useTermSearch` composable (which is the sole caller of the v2
endpoint) and adds the search-as-you-type behaviour.

**Exports:**

| name | value | meaning |
|------|-------|---------|
| `MIN_QUERY_LENGTH` | `2` | floor below which no request fires and results clear immediately |
| `SEARCH_DEBOUNCE_MS` | `300` | debounce window (matches the shared `SearchField.vue`) |

**Returns:** `{ query, results, loading, error, searched, tree, graph, total, run }`.

Behaviour:

- A watcher on `query` debounces by `SEARCH_DEBOUNCE_MS`; a rapid keystroke burst
  fires **one** request.
- A monotonically increasing `runId` **race-guards** the result write: a stale slow
  response never clobbers a fresher one.
- When `query` shrinks below `MIN_QUERY_LENGTH`, results / `searched` clear
  immediately (no request).
- `onUnmounted` clears the pending debounce timer.
- `tree = buildTermTree(results)`, `graph = buildTermGraph(tree)`,
  `total = countTerms(tree)` — all derived computeds.

### 3.3 Pure helpers — `frontend/utils/ontologyTermTree.ts`

Framework-free so they can be unit-tested without mounting Nuxt (same pattern as
`vocabBrowserUrl.ts`).

| function | contract |
|----------|----------|
| `namespaceOf(uri)` | `{ namespace, localName }` split at the last usable `#` then `/`; guarantees `namespace + localName === uri`; whole-URI fallback when no delimiter is usable |
| `shortPrefixOf(namespace)` | last meaningful path segment (e.g. `dcterms`); backs up past a purely numeric/version tail; `"(no namespace)"` on empty |
| `buildTermTree(terms)` | groups by namespace, **de-dupes by full URI**, sorts namespaces by short prefix then leaves by label — deterministic across identical inputs; blank labels fall back to local name; null/empty-URI rows skipped |
| `countTerms(tree)` | total de-duplicated leaf count |
| `buildTermGraph(tree)` | `{ nodes, edges }` — one `category:0` namespace node + one `category:1` node per term; **node id is the full IRI** (no numeric ids); one edge namespace→term |

---

## 4. Tests

28 Vitest unit tests, framework-free (no Vue mount):

- `frontend/tests/unit/ontologyTermTree.test.ts` — `namespaceOf` (hash/slash/trailing-
  delimiter/empty edge cases + round-trip guarantee), `shortPrefixOf` (version-tail
  back-up), `buildTermTree` (grouping, dedupe, sort, blank-label fallback, defensive
  skip, determinism), `countTerms`, `buildTermGraph` (node/edge counts, IRI ids,
  empty-tree).
- `frontend/tests/unit/useOntologySearch.test.ts` — min-length floor, debounce
  (one request per burst), success populating tree/graph/total, error surfacing +
  result clearing, immediate clear below floor, and the stale-response race-guard.
  `useTermSearch` is mocked at the module boundary (`vi.hoisted`); fake timers drive
  the debounce.

---

## 5. Constraints honoured

- **v2-only** — consumes `GET /v2/semantic/terms/search` via `useTermSearch`; never
  the v1 `/shepard/api` surface.
- **appId / IRI addressing** — leaves are keyed and linked by their full IRI; no
  numeric Neo4j id appears in any route, link, or graph node.
- **No path/URL input** — the only input is a free-text *term query*, not a path or
  URL.
- **Advanced mode is a superset** — nothing is hidden behind `!advancedMode`; the page
  shows the same surface in both modes.
- **Non-breaking, ungated, additive** — a new route under the existing `/semantic/*`
  namespace; no migration, no config key, no feature flag.

---

## Related reference pages

- [Semantic annotations (reference)](semantic-annotations.md)
- [Semantic repositories (reference)](semantic-repositories.md)
- [Search (reference)](search.md)
