---
stage: fragment
last-stage-change: 2026-05-23
---

# Search — Improvements & Unification Proposal

**Scope.** Forward-looking design note for the search surface in shepard.
The current search layer accumulated organically across the storage
backends (Neo4j, MongoDB, TimescaleDB/Postgres, PostGIS) and now exposes
several similar-but-different endpoints, no fulltext, and no first-class
way to search by semantic annotation. This document proposes one unified
search API, a richer query syntax that opens up RDF/SPARQL and SQL where
they fit, and a path to fulltext.

This is a companion to `12-timescaledb-performance-analysis.md` (read-path
performance) and `../archive/semantics/14-semantic-improvements.md` (annotation model). All three
share a single dependency: §11.B of the performance analysis — moving
clients toward numeric `timeseries_id` as the canonical identifier.

**Status legend.** Same as the perf doc: 🟥 HIGH / 🟧 MED / 🟨 LOW /
🟪 ARCH / ✅ DONE.

---

## 1. Why unify

### 1.1 The current surface is fragmented

Five entry points today, each with subtly different shape:

| Endpoint | Purpose | Pagination | Backing store |
|---|---|---|---|
| `POST /search` | Generic — dispatches by `queryType` (Collection / DataObject / StructuredData / Reference) | None at top level | Neo4j (+ MongoDB for StructuredData) |
| `POST /search/collections` | Collection list with sort/page | `pageNumber`, `pageSize` | Neo4j |
| `POST /search/containers` | Container list | optional `page`, `size` | Neo4j |
| `POST /search/users` | User list | None | Neo4j |
| `POST /search/usergroups` | UserGroup list | None | Neo4j |

`SearchRest.java:44-204`. The bodies are similar (`SearchBody`,
`CollectionSearchBody`, `ContainerSearchBody`) and all carry the same
JSON predicate shape (`{property, value, operator}` plus AND/OR/NOT/XOR
compounds), but the response envelopes diverge
(`ResponseBody` vs `CollectionSearchResult` vs `ContainerSearchResult`),
the pagination contract diverges, and the sort contract diverges.

A frontend developer who wants to "find collections + their containers
matching X" makes two calls with two different bodies and merges. The
search composables on the frontend reflect this — one
`useCollectionSearch`, one `useContainerSearch`, one `useDataObjectSearch`
— each with its own minor variations.

### 1.2 The query language is implicit Cypher with a JSON skin

`Neo4jQueryBuilder.java` translates the JSON predicate into Cypher
fragments (`Neo4jQueryBuilder.java:18-796`). The vocabulary is fixed by
the builder — see the special-case handling for `createdBy`, `createdAt`,
`hasAnnotation`, `hasAnnotationIRI`, `successorIds`, `childrenIds`, etc.
(`Neo4jQueryBuilder.java:42-243`).

This works, but:

- It is neither full Cypher (users can't traverse arbitrary patterns)
  nor a clean abstract query language (the supported keys are
  Cypher-shaped, not domain-shaped).
- Adding a new searchable property means editing the builder, not
  declaring a property.
- It is the source of the C5 Cypher injection finding — a switch to
  parameter binding is being made independently (Phase 1 security
  work) but the structural risk of a hand-rolled translator remains.
- `MongoDBQueryBuilder.java` is a parallel translator for the
  StructuredData branch with the same operator set minus a few; not
  reused, not aligned.

### 1.3 Capability gaps

- **No native fulltext** anywhere. `contains` is substring-matching
  via Cypher `CONTAINS`; case-insensitive in some places, case-
  sensitive in others. No tokenisation, no stemming, no ranking.
  No use of Neo4j's fulltext indexes or Postgres `tsvector`.
- **Semantic annotation search is buried.** It exists — `hasAnnotation`
  and `hasAnnotationIRI` predicates with `propertyName::valueName`
  syntax (`Neo4jQueryBuilder.java:162-243`) — but it is unreachable
  from the GUI without writing JSON by hand, and it is only available
  on Collection / DataObject / Reference (not on Timeseries, Files,
  StructuredData, Spatial — see `../archive/semantics/14-semantic-improvements.md` §2).
- **No SPARQL.** Even though every annotation has `propertyIRI` /
  `valueIRI`, there is no path from "I want every entity annotated
  with `dcterms:creator` of `…/JoeBloggs`" to a result; the user has
  to know the exact IRIs and use the `hasAnnotationIRI` predicate.
  Federated queries against external ontology endpoints (the
  `SemanticRepository` registry) are read-only via `SparqlConnector`
  and only used for *label fetching*, not for *querying*.
- **No raw SQL** path for advanced users on the structured/timeseries
  side. Power users analysing data through a Python notebook today
  have to scrape via the REST API; a SQL escape hatch (read-only,
  scoped) would be a force multiplier — and is exactly the kind of
  thing the GitLab issue #763 / MR !763 ("TS payload search") is
  reaching for.
- **No cursor pagination, no streaming.** All search responses are
  built in heap and serialised at once. Same heap-blow-up vector as
  §11.A in the performance analysis, just on a different table.

### 1.4 Search-related open work

From `aidocs/archive/03-issues-status.md:106-114`:

- #683, #688, #722, #758 — fresh search bugs, scattered across the
  query path. Coverage is 0.38 (HIGH risk).
- #763 + !763 — search annotated timeseries inside a container.
- !80 — old timeseries-payload search draft, very stale, likely
  superseded by #763.

The bug velocity is itself a signal that the current surface is too
complex for its size.

---

## 2. Target design — one search API

### 2.1 Endpoint shape

A single `POST /search/v2` (or `POST /query`, name TBD) with a body
shaped like:

```jsonc
{
  "select": ["Collection", "Container", "Timeseries"],   // entity kinds
  "where": { /* predicate tree, see §2.2 */ },
  "order": [{ "field": "updatedAt", "dir": "desc" }],
  "page":  { "cursor": null, "limit": 100 },             // cursor pagination
  "include": ["annotations"]                             // expand options
}
```

- `select` lists the entity kinds the result should contain. Any
  combination is allowed; the result envelope is uniform regardless.
- `where` is the predicate tree. Same JSON shape as today (so the
  in-flight client work isn't wasted), with a few additions
  documented in §2.2.
- `page.cursor` is opaque (server-encoded); clients don't construct
  cursors by hand. For incremental fetch the client passes back the
  last `cursor` value seen.
- `include` opts into expansions that would otherwise be N+1 calls
  (e.g. fetch all annotations for matching DataObjects in one round
  trip).

The response is one envelope:

```jsonc
{
  "results": [
    { "kind": "Collection", "data": { "id": 42, ... } },
    { "kind": "Container",  "data": { "id": 17, ... } },
    ...
  ],
  "page": { "next": "opaque-cursor", "hasMore": true, "estimatedTotal": 1234 },
  "facets": { /* §2.5 */ }
}
```

`estimatedTotal` is best-effort (uses `count_estimate(…)` /
APOC analogues); accurate counts are expensive on Neo4j and TS.

The five existing endpoints stay alive, marked deprecated, for at
least two minor versions (same backwards-compat plan as
performance §11.B.3). The frontend composables collapse into
`useUnifiedSearch(body)`.

### 2.2 Query language — three layers

The proposal is an explicit three-layer query language:

1. **Default (predicate JSON, today's shape).** Same `{property,
   value, operator}` plus AND/OR/NOT/XOR. The vocabulary is the
   union of:
   - Common metadata: `id`, `name`, `description`, `createdAt`,
     `updatedAt`, `createdBy`, `updatedBy`.
   - Per-kind fields documented in OpenAPI rather than discovered
     by reading the Cypher builder.
   - Semantic predicates: `annotation.property`, `annotation.value`,
     `annotation.propertyIRI`, `annotation.valueIRI` — replaces the
     current `hasAnnotation` / `hasAnnotationIRI` with proper
     property paths instead of `::`-separated strings.
   - Relationship predicates: `successorOf`, `predecessorOf`,
     `child`, `parent`, `referencedBy`, `references` — symmetric
     and uniformly named (today they are mixed: `successorIds`
     vs `parentIds` vs `childrenIds`).
2. **Fulltext (`fulltext` operator).** A new operator value:
   `{ "property": "*", "operator": "fulltext", "value": "calibration ramp" }`.
   `*` means "any indexed text field on the kind"; a specific
   field is also accepted (`"description"`). See §2.4 for the
   index plan.
3. **Escape hatches — opt-in, scoped.**
   - **`raw.cypher`**: a `where` term that takes a Cypher pattern
     fragment plus parameter bindings. Restricted by role (admin
     only, off by default). Useful for ad-hoc analytics; saves
     having to grow the predicate vocabulary for one-off queries.
     Parsed and validated against an allowlist of patterns
     before execution. **Mutually exclusive with `fulltext` /
     predicate JSON in the same request** — keeps the optimiser
     surface small.
   - **`raw.sparql`**: a SPARQL `SELECT ?entity WHERE {…}` over the
     in-memory annotation graph (or a federated query against a
     registered `SemanticRepository`). Returns a list of entity
     IRIs (= IDs in shepard's namespace) which then feed back
     into `select` + page. Requires the triplestore step proposed
     in `../archive/semantics/14-semantic-improvements.md` §6.
   - **`raw.sql`**: read-only `SELECT` against a curated set of
     views over the timeseries hypertable and StructuredData
     mirror. Roles + statement timeout + row cap enforced by
     the backend. Direct path for #763 / !763 — clients can
     `SELECT timeseries_id, time_bucket('1 hour', time), avg(double_value)
     FROM ts_view WHERE container_id = ? GROUP BY 1, 2` without
     the API needing to grow knobs for every aggregation
     pattern.

The three layers are not meant to be layered (i.e. mixed in one
query). Default is what 95% of clients use; fulltext is an
operator inside default; the raw forms are alternatives.

### 2.3 Identifier discipline

Every search result row carries the **numeric ID** (Neo4j id for
graph entities, `bigint` PK for timeseries / structured / spatial
data points), and every reference between rows uses the same.

This is the crossover with the perf doc §11.B: search returns
`Timeseries(id=12345)`; that id is directly callable on
`/timeseries-containers/{cid}/payload?timeseriesId=12345`. No
5-tuple round-trip. The 5-tuple becomes a property exposed in
the result for human display; not the routing key.

### 2.4 Fulltext — a pragmatic plan

Three options, in increasing order of effort:

1. **Neo4j fulltext indexes (recommended start).** Neo4j has had
   built-in fulltext indexes (Lucene-backed) for years.
   Define one composite index per kind:

   ```cypher
   CREATE FULLTEXT INDEX collection_text
     FOR (c:Collection) ON EACH [c.name, c.description]
   ```

   Query via `db.index.fulltext.queryNodes('collection_text', $q)`,
   wired into the unified-search builder behind the `fulltext`
   operator. Covers 80% of "find collection by name/description"
   needs. Same approach for Container, DataObject, Reference,
   `SemanticAnnotation.propertyName` / `valueName`.

   Pros: zero new infra, one Cypher migration. Cons: scoring is
   Lucene-default; Neo4j fulltext index size is non-trivial.

2. **Postgres `tsvector` for timeseries / structured-data fields.**
   Where text payloads live in Postgres (StructuredData mirror,
   timeseries `string_value`), add a generated `tsvector` column
   + GIN index. `WHERE tsv @@ websearch_to_tsquery(?)`. This is
   exactly the surface #763 wants for "search inside payloads".

3. **External index (Elastic / OpenSearch / Meilisearch).** Only
   when (1) and (2) hit ranking-quality limits. Significant
   operational cost — another store to keep in sync. Defer.

The `fulltext` operator dispatches to (1) or (2) based on which
kind the term applies to. Clients see one operator; the backend
routes.

### 2.5 Facets — opportunistic but high-leverage

Search UIs benefit massively from facet counts (e.g. "32 results
in Container 'Run-2025-A', 14 with annotation `dcterms:creator`,
…"). The unified envelope leaves a `facets` object for this.
Phase the work:

1. **Phase 1: kind facet.** Free — `count(kind)` is part of the
   query plan when `select` includes multiple kinds.
2. **Phase 2: annotation facet.** Top N
   `(annotation.property, annotation.value)` pairs in the result
   set. One Neo4j aggregation, cached per query hash for ~30 s.
3. **Phase 3: time facet** (timeseries / structured) — bucketed
   by year/month, served from a pre-aggregated MV; aligns with
   the continuous-aggregate work in perf §5.2.

### 2.6 Pagination — cursor

The current offset-based pagination
(`pageNumber`, `pageSize` on Collections; ordinal `page`, `size`
on Containers) silently breaks under concurrent writes — rows
may be skipped or duplicated when results shift. Cursor-based
pagination keyed on `(orderBy, id)` is stable.

Backwards compat: `pageNumber` / `pageSize` continue to work on
the legacy endpoints; the unified endpoint is cursor-only.

### 2.7 Streaming results

For result sets > N rows, return a `streamUrl` that the client
can follow (NDJSON over `text/x-ndjson`). Same pattern as the
CSV streaming proposal in perf §11.A.2: a `StreamingOutput`
backed by `Query.getResultStream()` for Postgres-backed kinds
and a Neo4j `Result.stream()` for graph kinds. Constant heap.

---

## 3. Searching for semantic annotations — concrete

The current `hasAnnotation` predicate:

```json
{ "property": "hasAnnotation", "operator": "contains", "value": "dcterms:creator::JoeBloggs" }
```

The proposal:

```json
{
  "and": [
    { "property": "annotation.propertyIRI", "operator": "eq",
      "value": "http://purl.org/dc/terms/creator" },
    { "property": "annotation.valueIRI",    "operator": "eq",
      "value": "https://example.org/people/JoeBloggs" }
  ]
}
```

- Symmetric handling of property and value.
- IRIs as first-class strings, not packed into a `::`-delimited
  pair (which is also a parsing hazard — IRIs contain `:`).
- Composable with non-annotation predicates trivially.

Two related affordances on top:

- **`annotation.label` operator.** Matches the *label* (the
  human-readable string) instead of the IRI. Useful when the user
  is browsing; stale-label hazard documented in
  `../archive/semantics/14-semantic-improvements.md` §6.
- **Find-by-annotation across kinds.**
  `select: ["Collection", "DataObject", "Timeseries"]` +
  `annotation.property = "dcterms:creator"` returns every
  annotated entity in one envelope. Today this is impossible —
  `hasAnnotation` is implemented per-kind in the Cypher builder.

This presupposes the annotation model unification in
`../archive/semantics/14-semantic-improvements.md` §2 — annotations on
File / StructuredData / Spatial don't exist yet.

---

## 4. RDF / SPARQL — what it buys

The annotation model already speaks RDF in spirit (every annotation
has `propertyIRI` + `valueIRI`). Promoting the search layer to speak
SPARQL is a small step with two concrete benefits:

1. **Federated semantic queries.** A user can write
   `SELECT ?ts WHERE { ?ts dcterms:subject ?s . ?s rdfs:subClassOf
   <…/Calibration> }` against the union of (a) shepard's
   annotation graph and (b) a registered ontology endpoint. Today
   the system already calls out to SPARQL for label fetching
   (`SparqlConnector.java`); reusing the same connector for
   `SERVICE <…>` in a federated query is mostly plumbing.
2. **Reasoning.** With a triplestore (see §5 and
   `../archive/semantics/14-semantic-improvements.md` §6) supporting RDFS/OWL inference,
   queries become *transitively* aware of subclass / subproperty
   relationships defined in imported ontologies. "Find all
   timeseries annotated with `Sensor`" returns those annotated
   with `TemperatureSensor`, `PressureSensor`, etc., without
   each annotation needing the full chain — purely from
   ontology semantics.

The escape hatch is `raw.sparql` (§2.2). Routing is:

- SPARQL parser → resolve subject/predicate/object IRIs against
  the local annotation index → translate to a Cypher query when
  the pattern is local-only;
- For federated / inference patterns, dispatch to the triplestore
  (GraphDB, see semantic doc §6).

This is **not free**: a SPARQL parser + algebra is non-trivial
infrastructure. Use Apache Jena ARQ to avoid hand-rolling.
Treat it as a Phase-3 follow-up; the unified API (§2) is
useful without it.

---

## 5. Read-only SQL — one practical opening

For the timeseries / structured-data side, exposing a curated SQL
view through a `raw.sql` operator on the unified search endpoint
is the minimum-effort answer to several open issues:

- **#763 / !763** ("search annotatedTimeseries"): becomes a SQL
  query joining the timeseries hypertable with an
  `annotated_timeseries` view derived from the Neo4j
  `AnnotatableTimeseries` nodes (refreshed on annotation
  create/delete; cheap).
- **!80** (timeseries payload search concept): superseded —
  fulltext on `string_value` plus `raw.sql` cover it.
- **Power users**: `psql`-equivalent ergonomics without exposing
  the actual database.

Hard rails:

- Read-only role with `SET default_transaction_read_only = on`.
- `statement_timeout` set per request (e.g. 10 s).
- Row limit on the response (e.g. 100 k).
- Schema scoped to a curated set of views (`shepard_public.*`),
  not the application's tables. Views handle the row-level
  permissions check.

---

## 6. Cross-store search — the coordination problem

A query like "Container with annotation X **and** containing a
timeseries whose payload has `> 1000` points in the last day"
spans Neo4j (annotation) and TimescaleDB (point count). Today
this is a sequence of API calls + client-side intersection. In
the unified API:

- The `where` tree carries predicates of both flavours.
- The planner (a small piece of new code, not a big optimiser)
  decides which store leads:
  - If the most selective predicate is on annotations, query
    Neo4j first → list of `(containerId, timeseriesId)` →
    Postgres `IN (…)` filter.
  - If on time/value range, query Postgres first → list of
    `timeseriesId` → Neo4j filter by annotation.
- The id alignment from perf §11.B is the prerequisite — the
  planner needs the same identifier on both sides of the join.

This is the long-term payoff of unifying search; the steps in
§2 don't unlock it on day one but they don't preclude it either.

---

## 7. Migration path

| Step | Work | Risk |
|---|---|---|
| 1. Implement `POST /search/v2` with the predicate-JSON layer (no fulltext, no raw escape hatches) and the unified envelope. Frontend gains `useUnifiedSearch`. | M | low |
| 2. Add `fulltext` operator backed by Neo4j fulltext indexes (§2.4 option 1). Migrate frontend search pages. | S-M | low |
| 3. Mark legacy `POST /search/*` endpoints `@deprecated`; leave behaviour intact. Telemetry on caller mix. | XS | none |
| 4. Add cursor pagination + streaming to v2. Introduce `include` and basic facets (kind facet only). | M | low |
| 5. Add tsvector + GIN to Postgres-backed kinds (§2.4 option 2); fulltext operator routes accordingly. Resolves #763 fulltext part. | S-M | low |
| 6. Add `raw.sql` (read-only views, statement timeout, row cap). Resolves #763 SQL part, supersedes !80. | M | medium (correct sandboxing is the risk) |
| 7. Triplestore + `raw.sparql` (depends on `../archive/semantics/14-semantic-improvements.md` §6). Federated annotation queries. | L | medium |
| 8. Cross-store planner (§6). Removes client-side intersection patterns. | L | medium |

Steps 1–3 are the minimum-viable unification and individually
shippable. Steps 5–6 are the response to the timeseries-search
open issues. Steps 7–8 are the long arc.

---

## 8. Open questions

- **Permissions model on raw escape hatches.** A SPARQL or SQL
  query that reaches into rows the user can't otherwise see is
  the obvious risk. Today's permissions are entity-scoped (per
  Collection / Container / DataObject); SQL views must enforce
  the same — likely via row-level security tied to the
  authenticated user's role set.
- **Versioning interaction.** shepard has versioned entities
  (Cluster B). Should the unified search default to the latest
  version, all versions, or be parameterised? Today's behaviour
  varies by endpoint; v2 should pick one and stick.
- **Sort stability under cursor pagination.** Compound cursors
  on `(orderBy, id)` only work when `orderBy` is non-null and
  monotonic. Document the contract; reject otherwise.
- **OpenAPI vs. predicate-JSON friction.** OpenAPI doesn't
  describe predicate trees well; consider supplementing with a
  JSON Schema for the `where` body and linking from the OpenAPI
  doc.

---

## 9. References

- Current search code: `backend/src/main/java/de/dlr/shepard/common/search/`
  (`Neo4jQueryBuilder.java`, `MongoDBQueryBuilder.java`, services).
- REST: `SearchRest.java:44-204`,
  `DataObjectSemanticAnnotationRest.java`,
  `AnnotatableTimeseriesRest.java`.
- Frontend: `frontend/pages/search/index.vue`,
  `frontend/utils/buildSearchQuery.ts`,
  `frontend/composables/context/use{Collection,Container,DataObject}Search.ts`.
- Open issues: #683, #688, #722, #758, #763 (TS payload search);
  MR !763 (annotatedTimeseries search), MR !80 (legacy concept).
- Companion docs: `12-timescaledb-performance-analysis.md` §11.B
  (id alignment), `../archive/semantics/14-semantic-improvements.md` (annotation model).
