# Internal Semantic Repository via Neosemantics — Design

**Scope.** Evaluate using **neosemantics (`n10s`)** — Neo4j's RDF
toolkit — to host **general-purpose ontologies** (units of measure,
PROV-O, schema.org, QUDT, Dublin Core, FOAF) inside shepard's
existing Neo4j instance, instead of requiring an external triple
store (Stardog / AllegroGraph / GraphDB / Apache Jena Fuseki).

**Status.** Concept design.
**Snapshot date.** 2026-05-08.
**Originating items.** User question: "Can neosemantics be used as
an internal semantic repository? For general-purpose ontologies —
units, PROV, ..." Plus: "internal neo semantic store should be
pre-seeded with common ontologies. Integrate this in the demo
dataset."

---

## 1. Why this matters

Today's semantic-repository surface
(`backend/src/main/java/de/dlr/shepard/context/semantic/`):

- `SemanticRepositoryType` enum — `SPARQL`, `JSKOS`, `SKOSMOS`.
- `ISemanticRepositoryConnector` — `getTerm(iri)` + `healthCheck()`.
- Implementations talk **outwards** to user-configured endpoints.

Two friction points:

1. **Deploying a triple store is a barrier.** A casual researcher
   wanting to annotate `vibration_max` with the QUDT IRI for `g rms`
   has to either find a public SPARQL endpoint that hosts QUDT (and
   tolerate its uptime / rate limits) or stand up a local triple
   store. Either path means the casual-user enablement story
   from `aidocs/42` / `aidocs/47 §1.0` breaks at the first step.
2. **General-purpose ontologies are repeated overhead.** Units of
   measure (UO / QUDT), PROV-O, Dublin Core, schema.org — every
   research deployment uses the same handful. Operators
   re-provision them per-instance instead of shipping with a
   sensible default.

Neosemantics offers the **answer with no additional service**:
shepard already runs Neo4j; n10s is a Neo4j plugin that turns the
same database into an RDF triple store with SPARQL and Cypher both
addressing the imported ontology.

---

## 2. Neosemantics in 60 seconds

[`neosemantics`](https://github.com/neo4j-labs/neosemantics)
("`n10s`") is a Neo4j Labs plugin that:

- Imports RDF (Turtle / RDF/XML / JSON-LD / N-Triples) into Neo4j
  as nodes + relationships using a **lossless graph mapping** (or
  configurable LPG-friendly modes).
- Exposes the imported graph as **SPARQL via a built-in endpoint**
  (Neo4j `db.semantics.sparql` procedures + `n10s.rdf.export`).
- Lets the **same nodes** be addressed via Cypher AND SPARQL — no
  duplicated storage.
- Ships as a JAR dropped into Neo4j's `plugins/` folder + one
  bootstrap call to initialise constraints. No separate process.

Practical implications for shepard:

- **Zero new infrastructure** for the operator. Neo4j is already
  required.
- **One database to back up**, not two.
- **Same auth context** — shepard's existing Neo4j credentials
  serve the SPARQL endpoint (post-config).
- **Limit: storage scales with Neo4j's storage budget.** A few
  hundred MB of ontology fits comfortably alongside shepard's
  domain graph; multi-GB ontology dumps would be a different
  conversation.

---

## 3. Architecture

### 3.1 New `SemanticRepositoryType` value: `INTERNAL`

```java
public enum SemanticRepositoryType {
  SPARQL,        // unchanged — user-configured external endpoint
  JSKOS,         // unchanged
  SKOSMOS,       // unchanged
  INTERNAL,      // NEW — n10s-backed, same Neo4j as shepard
}
```

The existing `ISemanticRepositoryConnector` interface stays
unchanged. A new `InternalSemanticConnector` implements it by
running Cypher / SPARQL queries against the local Neo4j via the
same driver shepard already injects.

### 3.2 Bootstrap

n10s requires an idempotent one-time configuration call:

```cypher
CALL n10s.graphconfig.init({
  handleVocabUris: 'IGNORE',
  keepLangTag: true,
  handleMultival: 'ARRAY',
  handleRDFTypes: 'LABELS_AND_NODES'
});
CREATE CONSTRAINT n10s_unique_uri IF NOT EXISTS
  FOR (r:Resource) REQUIRE r.uri IS UNIQUE;
```

shepard wraps this in a startup hook that runs **after** the
existing `MigrationsRunner` (post-A1e fail-fast) so the
`n10s_unique_uri` constraint creation isn't competing with V11's
appId constraints. Idempotent — safe to re-run.

### 3.3 Namespace separation: shepard vs ontology

shepard's domain nodes (Collection, DataObject, FileReference, ...)
**and** ontology resources (UnitOfMeasure, Activity, Person, ...)
share one graph. Two safety rails:

1. **n10s imports apply the `Resource` label** to every imported
   ontology node. shepard's domain entities never carry `Resource`.
   Cypher reads can filter by absence/presence of the label to
   distinguish.
2. **shepard's Cypher writes never touch nodes with the `Resource`
   label.** Enforced by a `WHERE NOT n:Resource` filter in any
   write query that traverses without an explicit label match.
   This is a one-line audit add in `Neo4jQueryBuilder` and
   `GenericDAO`.

Reverse direction (n10s writes touching shepard nodes) is
prevented by the user-controlled scope of the import — shepard's
admin imports specific RDF files into specific namespaces; n10s
doesn't auto-discover anything.

### 3.4 Reading ontology terms from shepard

`InternalSemanticConnector.getTerm(termIri)`:

```cypher
MATCH (r:Resource {uri: $termIri})
OPTIONAL MATCH (r)-[:rdfs__label]->(label:Resource)
WHERE label.uri STARTS WITH 'http://www.w3.org/2001/XMLSchema#string'
   OR label.uri CONTAINS '@'
RETURN coalesce(label.uri, r.rdfs__label) AS label,
       r.rdfs__label AS fallback
```

In practice n10s materialises labels per language as separate
properties (`rdfs__label_en`, `rdfs__label_de`, etc.) when
`keepLangTag = true`. The connector returns the language map
the existing interface expects.

### 3.5 SPARQL endpoint exposure (optional)

For users who want to run SPARQL queries against the loaded
ontologies (e.g. external Sparklis-style explorers):

- n10s exposes SPARQL via Neo4j's HTTP API at
  `http(s)://<neo4j>:7474/rdf/<dbname>/sparql`.
- shepard's frontend / admin UI **does not** proxy this directly.
- Operators who want public SPARQL access put their own auth
  proxy in front of Neo4j's HTTP API (or use Neo4j's built-in
  auth) — shepard doesn't take responsibility for that scope.

Alternative: shepard could ship a `/v2/semantic/{repoAppId}/sparql`
proxy endpoint that wraps the n10s HTTP call with shepard's auth.
**Deferred** — useful but not v1.

---

## 4. Pre-seeded ontologies

The whole point of "common ontologies" is that shepard ships them
**by default**. A fresh shepard starts with the following loaded
into the `INTERNAL` semantic repository:

| Ontology | IRI prefix | Role | Size (rough) |
|---|---|---|---|
| **PROV-O** (W3C) | `http://www.w3.org/ns/prov#` | Provenance vocabulary; pairs with `aidocs/30` lineage design | ~3 MB Turtle |
| **Dublin Core Terms** | `http://purl.org/dc/terms/` | Common metadata properties (creator, created, license, etc.) | < 100 KB |
| **schema.org core** | `https://schema.org/` | RO-Crate exports already use schema.org; pre-seeding makes those terms resolvable in shepard | ~2 MB Turtle |
| **FOAF** | `http://xmlns.com/foaf/0.1/` | Person / Agent / Organization for author-style annotations | < 100 KB |
| **QUDT v2.1 units** (subset) | `http://qudt.org/vocab/unit/` | Units of measure for scientific values; pairs with the LUMEN sensor channels | ~5 MB Turtle |
| **OM 2** (Ontology of Units of Measure) | `http://www.ontology-of-units-of-measure.org/resource/om-2/` | Alternative units ontology; QUDT is the default but OM2 ships for cross-compat | ~2 MB |
| **W3C Time** | `http://www.w3.org/2006/time#` | Time interval / duration vocabulary | < 100 KB |
| **GeoSPARQL** | `http://www.opengis.net/ont/geosparql#` | For spatial-data references (`aidocs/16` `spatial` profile) | < 1 MB |

**Total: ~13 MB of ontology data.** Negligible against Neo4j's
storage budget. Loaded via n10s `n10s.rdf.import.fetch(...)` from
locally-bundled Turtle files in `backend/src/main/resources/ontologies/`
to avoid runtime fetch dependency on the source URLs.

**Pinned versions.** Each ontology bundle records the source URL +
SHA-256 + version + retrieval date. shepard never auto-updates;
admin runs `shepard-admin semantic refresh-ontologies` (per
`aidocs/22 §4.x` — new command) to pull a newer Turtle file from
the canonical URL, validate the SHA, and re-import.

**Loading is a startup task, not a migration.** Cypher migrations
(V11+) modify shepard's domain schema; ontology loading is
content-level and idempotent (n10s skips already-imported triples).
The startup hook checks each ontology's "last imported" version
and re-imports only when the bundled Turtle's SHA-256 differs.

---

## 5. LUMEN demo integration

The LUMEN-inspired showcase seed (`examples/seed-showcase/`)
currently uses **placeholder IRIs** under
`https://shepard.dlr.de/showcase/lumen-inspired#` for phase-of-burn
annotations and the `dlr:vibration-anomaly` marker. This works for
the demo but doesn't showcase the internal-semantic-repo feature.

**After this design lands**, the seed switches to **real ontology
IRIs** drawn from the pre-seeded set:

| Original placeholder | Replacement | Source |
|---|---|---|
| `https://shepard.dlr.de/showcase/lumen-inspired#precool` | `https://shepard.dlr.de/showcase/lumen-inspired#precool` *(stays — domain-specific phase, no general ontology covers this)* | LUMEN-local |
| `https://shepard.dlr.de/showcase/lumen-inspired#vibration` (channel attribute) | `http://qudt.org/vocab/unit/G_Earth` (g-force unit) **plus** `http://www.w3.org/2006/time#hasDuration` for the spike duration | QUDT + W3C Time |
| `dlr:created_by` (lab journal authorship) | `http://www.w3.org/ns/prov#wasAttributedTo` + `http://xmlns.com/foaf/0.1/Person` | PROV-O + FOAF |
| `dlr:test_engineer` (DataObject attribute) | `http://www.w3.org/ns/prov#Agent` | PROV-O |
| `dlr:campaign` | `http://www.w3.org/ns/prov#Activity` | PROV-O |
| Channel-quality `dlr:severity=HIGH` | `http://www.w3.org/2006/time#Instant` for the anomaly timestamp + custom SKOS scheme for severity (ships as a small `lumen-severity.ttl` in the seed) | W3C Time + bespoke SKOS |

The seed gains a new **`examples/seed-showcase/data/ontologies/`**
directory containing:

- `lumen-phases.ttl` — the seven phase-of-burn IRIs declared as a
  SKOS concept scheme.
- `lumen-severity.ttl` — `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` as a
  SKOS hierarchy.
- A `import_ontologies.py` snippet at the top of the seed scripts
  that loads these into the local n10s instance (`SemanticRepositoryType.INTERNAL`)
  before annotations are created.

**The story this tells.** A fresh `python seed.py --host ...
--apikey ...` ends up with:

- The pre-seeded common ontologies (PROV-O, QUDT, schema.org, ...).
- The LUMEN-specific phase + severity vocabularies.
- Annotations on the data referencing the **real** ontology IRIs.

The notebook (`anomaly-analysis.ipynb`) gains a new cell that
demonstrates a SPARQL query against the internal store:

```python
# Find all DataObjects in this Collection that have annotations
# from QUDT under the unit "G_Earth" and a severity above LOW.
sparql = """
PREFIX qudt: <http://qudt.org/vocab/unit/>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX lumen: <https://shepard.dlr.de/showcase/lumen-inspired#>

SELECT ?dataObject ?severity WHERE {
  ?dataObject prov:wasAttributedTo ?unit .
  ?unit a qudt:G_Earth .
  ?dataObject lumen:severity ?severity .
  FILTER(?severity != lumen:LOW)
}
"""
results = SemanticRepositoryApi(client).run_sparql(repo_id, sparql)
```

That's a real demonstration of internal-semantic-repository value.

---

## 6. Trade-offs vs an external triple store

| Aspect | Internal (n10s) | External (Stardog / Jena Fuseki / GraphDB) |
|---|---|---|
| **Operator setup** | Zero — Neo4j already runs | New service + replication + auth |
| **Backup** | One backup target | Two |
| **Storage limit** | Bounded by Neo4j machine's disk | Independent; can be much larger |
| **SPARQL feature completeness** | n10s is **OK, not best-in-class**. Some SPARQL 1.1 features (federation, aggregation edges) lag pure triple stores. | Full SPARQL 1.1 |
| **Reasoning / inference** | None today (n10s doesn't ship a reasoner) | Stardog has industry-strength reasoning |
| **Performance for large ontologies** | OK to a few hundred MB; multi-GB is slow | Built for it |
| **Casual-user enablement** | **Wins decisively** — works out of the box | Requires admin work |
| **Power-user reasoning** | Falls short — the user goes external if they need it | Wins |

**Verdict: ship `INTERNAL` as the default, keep `SPARQL` /
`JSKOS` / `SKOSMOS` for users who outgrow it.** The migration
between modes is a config flip — nothing in shepard's data needs
to move (annotations reference IRIs, not which repository served
them).

For users who want both (pre-seeded ontologies internal **plus**
custom domain ontologies external), shepard already supports
**multiple `SemanticRepository` entities** — the new `INTERNAL`
type just becomes one more repository in the list.

---

## 7. Phasing — N1 series ("Native semantic repository")

| ID | Slice | Size | Gate |
|---|---|---|---|
| **N1a** | n10s plugin in the Neo4j compose service (`infrastructure/docker-compose.yml` mounts the JAR + sets `dbms.security.procedures.unrestricted=n10s.*`); `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector`; n10s bootstrap startup hook (post-A1e). | M | A1e (shipped) |
| **N1b** | Pre-seeded common ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL). Bundled Turtle files at `backend/src/main/resources/ontologies/`. SHA-256 pinning. | M | N1a |
| **N1c** | **shipped** — `shepard-admin semantic refresh-ontologies [--bundles=…] [--force]` CLI subcommand + `POST /v2/admin/semantic/refresh-ontologies` backend endpoint (instance-admin gated). `OntologyRefreshService` walks the manifest, fetches each bundle's pinned `canonicalUrl`, recomputes SHA-256, and re-imports via `n10s.rdf.import.inline` when the hash differs (or `force=true`). Best-effort per bundle — partial failures land in `errors[]` with 200 OK. | S | N1a + `aidocs/22` |
| **N1d** | LUMEN seed integration — `lumen-phases.ttl` + `lumen-severity.ttl` + `import_ontologies.py`; replace placeholder IRIs in `seed.py` and `import_upstream.py` with real ontology IRIs; new SPARQL cell in `anomaly-analysis.ipynb`. | S | N1b |
| **N1e** | Frontend annotation picker shows pre-seeded ontology terms by default (search "G_Earth" → QUDT IRI auto-completes). Couples to `aidocs/14` and `aidocs/13` search-as-you-type. | M | N1a + `aidocs/14` |
| **N1f** | Optional `/v2/semantic/{repoAppId}/sparql` proxy endpoint that wraps n10s SPARQL with shepard auth. | M | N1a |
| **N1g** | (deferred) Reasoner integration — pure Cypher inference rules for SKOS broader-narrower; no full RDFS / OWL reasoner. | L | parked |

Recommended order: **N1a → N1b → N1d → N1c → N1e**. N1a + N1b
together ship the headline feature; N1d gets the LUMEN demo
showcasing it; N1c gives operators a refresh path; N1e closes
the casual-user UX loop.

---

## 8. Migrations

- No Neo4j Cypher migration needed in the V## series — n10s
  manages its own constraint via the `n10s_unique_uri` constraint
  added in the bootstrap hook (idempotent, post-A1e).
- **Tracker rows in `aidocs/34`:** N1a is **AWARE** (new
  `INTERNAL` repo type, new Neo4j plugin requirement; existing
  external repos unaffected). N1b is ZERO (additive bundled
  data). N1d is ZERO (showcase change only).

## 9. Risks

- **n10s plugin compatibility with Neo4j upgrades.** n10s tracks
  Neo4j major versions; a Neo4j 5 → 6 migration may need an n10s
  upgrade. Document in `docs/admin.md` Neo4j-upgrade section.
- **Namespace pollution.** A buggy ontology import could conflict
  with shepard's domain labels. Mitigated by §3.3's safety rails
  (`Resource` label discipline; `WHERE NOT n:Resource` on
  shepard's writes).
- **Storage growth.** Multi-GB ontology imports inflate Neo4j's
  page cache. Document the 50 MB / 500 MB / 5 GB ontology
  thresholds and recommend external triple store beyond.
- **SPARQL completeness.** Users hitting n10s's SPARQL gaps
  (federation, certain aggregations) need to fall back to the
  external `SPARQL` connector. Document the gap matrix in
  `docs/admin.md`.
- **Pre-seeded ontology drift.** The pinned versions become
  outdated; admins forget to refresh. Mitigation: `shepard-admin
  semantic refresh-ontologies --check-only` reports staleness;
  CI on the upstream tracker (this fork's repo) opens a PR when
  upstream URLs publish a new version.
- **Bundled ontology licensing.** All listed ontologies
  (PROV-O / DC / schema.org / FOAF / QUDT / OM2 / W3C Time /
  GeoSPARQL) are openly licensed (W3C Document License, CC0,
  CC-BY 4.0). Confirm + record per-ontology in
  `backend/src/main/resources/ontologies/LICENSING.md`.

## 10. What this is NOT

- **Not** a replacement for triple stores in serious knowledge-graph
  use cases. n10s is a Neo4j-flavoured RDF surface; for real
  reasoning or federation users still go external.
- **Not** an extension of shepard to host arbitrary RDF data.
  The `INTERNAL` repo is for **referenced ontologies** (the
  vocabulary annotations cite); shepard's domain data stays in
  the property-graph shape.
- **Not** a way to import the entire LOD cloud into shepard.
  Pre-seeded ontologies are an explicit short list; admins can add
  bundled-or-fetched extras one at a time.

## 11. Cross-references

- **aidocs:** `aidocs/semantics/14-semantic-improvements.md` (the broader
  semantic-annotation surface this design extends),
  `aidocs/16` (N1 series queueing entry will follow this design),
  `aidocs/22 §4.x` (`shepard-admin semantic refresh-ontologies`),
  `aidocs/30` (PROV-O alignment for lineage),
  `aidocs/34` (per-slice ZERO/AWARE rows),
  `aidocs/42` vision (mid-horizon bullet for the internal semantic
  repo + pre-seeded ontologies),
  `aidocs/44` (matrix row showing this fork ships pre-seeded
  ontologies whereas upstream requires external repo wiring).
- **Code seams:** `SemanticRepositoryType` (new enum value),
  `ISemanticRepositoryConnector` (interface unchanged; new impl
  `InternalSemanticConnector`), `Neo4jQueryBuilder` + `GenericDAO`
  (new `WHERE NOT n:Resource` filter).
- **Backlog:** new **N1** umbrella + N1a-N1g sub-IDs in
  `aidocs/16`.
- **Showcase:** `examples/seed-showcase/data/ontologies/`,
  `seed.py`, `import_upstream.py`, `anomaly-analysis.ipynb`.
