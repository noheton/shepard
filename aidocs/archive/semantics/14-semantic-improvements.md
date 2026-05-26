---
stage: decommissioned
last-stage-change: 2026-05-24
superseded-by: aidocs/semantics/100-consistent-semantic-annotation-design.md
---

# Semantic Annotations — Improvements & Knowledge-Graph Path (DECOMMISSIONED)

> **Superseded** by [`aidocs/semantics/100-consistent-semantic-annotation-design.md`](100-consistent-semantic-annotation-design.md)
> (2026-05-24). The "extending annotations to all payload kinds via bridge
> nodes" approach (§2 of this doc, with `AnnotatableFile` /
> `AnnotatableStructuredData` / `AnnotatableSpatial` siblings to the
> existing `AnnotatableTimeseries`) is replaced by the canonical-primitive
> approach: any entity with an `appId` is annotatable via a typed subject
> reference; the bridge-node sprawl is sunset (per aidocs/100 §11.3) when
> `:Timeseries.appId` and `:ShepardFile.appId` are populated universally
> (TS-IDc + the relevant file-storage migration). The GUI improvements
> here (§3 IRI-paste demotion + search-as-you-type) are absorbed into
> aidocs/100 §5; the triplestore-as-vocabulary path is absorbed into
> aidocs/100 §4 (vocabularies layer over the already-shipped n10s
> substrate). Retained as historical context; do not implement against it.

**Scope.** Forward-looking design note for shepard's semantic-annotation
layer. Covers (a) extending annotations from "timeseries-only inside a
TimeseriesContainer" to *all* payload types, (b) improving the GUI
workflow (label reliance, search-as-you-type on terms), and (c) the
opportunity space opened by integrating a triplestore (e.g. GraphDB) for
serving ontologies and knowledge graphs.

Companion to `13-search-improvements.md` (where annotations are queried)
and `12-timescaledb-performance-analysis.md` §11.B (where the id alignment
that this work depends on is described). The three documents are
designed to be read in sequence: perf → search → semantic.

**Status legend.** Same as the perf doc.

---

## 1. State of the model today

### 1.1 Domain model — asymmetric

Two ways to attach a `SemanticAnnotation` to something:

- **`BasicEntity` inheritance.** `BasicEntity`
  (`common/neo4j/entities/BasicEntity.java:24-25`) declares
  `@Relationship(type = HAS_ANNOTATION) List<SemanticAnnotation> annotations`.
  All graph entities derived from `BasicEntity` inherit this for free —
  Collection, DataObject, Reference subclasses, and the `*Container`
  classes that extend `BasicContainer extends BasicEntity`.
- **`AnnotatableTimeseries` bridge node.**
  (`context/semantic/entities/AnnotatableTimeseries.java`) — a
  separate `@NodeEntity` keyed by `(containerId, timeseriesId)` with
  its own `@Relationship(type = HAS_ANNOTATION)`. Lives because a
  timeseries is *inside* a `TimeseriesContainer` in Postgres, not a
  Neo4j node, so it can't inherit from `BasicEntity`.

Concretely supported today:

| Subject type | Annotation support | Address by |
|---|---|---|
| Collection | ✅ via `BasicEntity` | numeric id |
| DataObject | ✅ via `BasicEntity` | (collectionId, dataObjectId) |
| BasicReference (URI / FileReference / DataObjectReference / TimeseriesReference / StructuredDataReference / SpatialDataReference) | ✅ via `BasicEntity` | (collectionId, dataObjectId, referenceId) |
| **Individual timeseries** (a row in `timeseries`) | ✅ via `AnnotatableTimeseries` | (containerId, timeseriesId) |
| **Individual file** (a `ShepardFile` inside a `FileContainer`) | ❌ | — |
| **Individual structured-data document** | ❌ | — |
| **Individual spatial-data record** | ❌ | — |

The pattern is clear: payloads that live in non-Neo4j stores (Postgres
hypertable, file/oid store, MongoDB, PostGIS) only have annotation
support if a bridge node is built for them. Only timeseries got one.

### 1.2 Vocabulary layer — purely external

There is no local term store. `SemanticRepository`
(`context/semantic/entities/SemanticRepository.java`) is a registry of
external SPARQL endpoints. `SparqlConnector` calls them only at
annotation creation time, only to fetch a label for a user-pasted IRI.

Consequence: the only way a user *picks* a term today is to paste an
IRI string. There is no autocomplete, no term browser, no validation
that the IRI exists, no preview of "what this term actually means" —
just a label string, fetched once, snapshotted into
`propertyName` / `valueName`, and never refreshed.

### 1.3 GUI workflow — IRI-paste

`frontend/components/context/semantic/annotation/add-dialog/
AddAnnotationDialog.vue` shows the cost of (1.2):

- Four text fields (`propertyIRI`, `valueIRI`, two repository
  pickers) and no autocomplete on the IRI fields themselves.
- Submit triggers backend label fetch from the chosen repository's
  SPARQL endpoint (`SparqlConnector.getTerm`). On failure: silent
  empty label.
- Display side
  (`SemanticAnnotationChip.vue`): renders `propertyName` /
  `valueName` (the snapshot strings) as link text pointing at the
  IRIs. If the SPARQL endpoint changed the rdfs:label, shepard
  doesn't know.

**Label reliance.** Search queries via `hasAnnotation` use
`propertyName::valueName` strings (see `13-search-improvements.md` §3),
which means the label *is* part of the query key. A label drift on
the upstream ontology silently breaks search results from past data.

### 1.4 Open issues

From `aidocs/archive/03-issues-status.md:70-85`:

- **#43** — semantic annotation feature, PARTIAL, partly blocked on
  the Neo4j refactor (#274).
- **#553, #656** — BLOCKED on #274.
- **#660** — overlaps the Neo4j refactor itself.

#274 has been the load-bearing blocker for ~year+ on this cluster.
Whether it lands or is split, the design below should still hold,
because the asymmetry in 1.1 is independent of the Neo4j-driver
question in #274.

---

## 2. Extending annotations to all payload types

### 2.1 The general shape — one bridge per payload kind

Following the `AnnotatableTimeseries` template:

| Payload kind | Bridge entity | Identifier tuple |
|---|---|---|
| Timeseries | `AnnotatableTimeseries` (existing) | (timeseriesContainerId, timeseriesId) |
| File | `AnnotatableFile` (new) | (fileContainerId, fileOid) |
| StructuredData document | `AnnotatableStructuredData` (new) | (structuredDataContainerId, documentId) |
| Spatial record | `AnnotatableSpatial` (new) | (spatialDataContainerId, recordId) |

Each is a Neo4j `@NodeEntity implements Annotatable` with a
`@Relationship(type = HAS_ANNOTATION) List<SemanticAnnotation>`,
created lazily on first annotation, deleted when its last annotation
is removed (or eagerly — same trade-off as today's timeseries
bridge).

### 2.2 Generalising the bridge

Three of the four bridges differ only in the identifier tuple type
and the parent container type. The model can be generalised
without losing type-safety in Java by parameterising:

```java
public abstract class AnnotatablePayload<ID> implements Annotatable {
  protected long containerId;
  protected ID payloadId;
  @Relationship(type = HAS_ANNOTATION)
  protected List<SemanticAnnotation> annotations;
}
```

…with concrete subclasses `AnnotatableTimeseries extends
AnnotatablePayload<Integer>`, `AnnotatableFile extends
AnnotatablePayload<UUID>`, etc. Same Cypher patterns; one set of
service-layer helpers.

The unique constraint moves to a generic
`(payloadKind, containerId, payloadId)` triple — a single index
covering all four kinds, supports the cross-kind search proposed
in `13-search-improvements.md` §3 ("find every annotated entity by
property X").

### 2.3 REST surface

Mirrors today's `AnnotatableTimeseriesRest`:

```
GET    /file-containers/{cid}/files/{fileOid}/semantic-annotations
POST   /file-containers/{cid}/files/{fileOid}/semantic-annotations
DELETE /file-containers/{cid}/files/{fileOid}/semantic-annotations/{id}
```

…and the same shape for structured-data and spatial. The numeric
id (or UUID for files) is the only address — *not* a 5-tuple
analogue. Keeps the API surface uniform and aligns with the
performance-doc §11.B push toward id-as-canonical.

### 2.4 Permissions

Annotations inherit the read/write permission of their *parent
container* via the existing permission model on `BasicContainer`.
No new permission types; the bridge nodes are not directly
permissioned.

This matches today's behaviour for timeseries annotations; a user
who can read the container can read its annotations, and the
write permission on the container governs annotate/un-annotate.

### 2.5 Migration

For existing data, no migration is needed: the bridge nodes are
created on first annotation. The migration is purely adding new
endpoint surface + Neo4j entity classes.

If a user wants to bulk-annotate (e.g. label every file in a
container with a provenance term), the unified-search proposal
in `13-search-improvements.md` §2 carries enough vocabulary to
support a bulk POST — but the model in this doc doesn't require
it.

### 2.6 Generalised payload-type model — opportunity

Once the four bridges exist, "any payload type, any annotation"
is one small step from "any payload type, any *typed property*".
The bridge nodes can carry not just annotations but:

- **System-managed metadata.** Indexed-once-then-attached values
  like a content hash, a fulltext index pointer (see search doc
  §2.4), a `last_validated_at`.
- **Cross-kind references.** A `RELATED_TO` edge between two
  bridge nodes — "this file documents this timeseries"
  (already loosely supported via `BasicReference` subclasses, but
  the bridges could carry typed semantic edges instead of
  string-typed references).

Treat as future work; the immediate goal is feature parity.

---

## 3. The label-attribute problem — and how to fix it

### 3.1 Diagnosis

Current annotations carry `propertyName` and `valueName` — labels
fetched once at creation. They are used:

- **Display (UI):** `SemanticAnnotationChip.vue` shows the label
  as link text. Stale label = misleading display.
- **Search (Cypher):** `hasAnnotation` predicate matches on
  `propertyName::valueName`. Stale label = silently divergent
  query results.
- **JSON wire format:** clients see both the label and the IRI;
  some clients use the label as a stable key. They shouldn't, but
  the model invites it.

This is the user's "GUI relies on label" observation made
concrete. The label is *snapshotted display data* but is being
treated as identity.

### 3.2 Target — IRI is identity, label is presentation

Two changes:

1. **Identity is `(propertyIRI, valueIRI)`.** All searches /
   uniqueness checks key on the IRI pair. The unified-search
   `annotation.label` predicate (search doc §3) exists for human
   browsing, but the canonical predicates are `annotation.propertyIRI`
   and `annotation.valueIRI`.
2. **Labels are derived data, refreshable.** Move
   `propertyName` / `valueName` out of the annotation row into a
   side table (or just treat them as caches with a TTL).
   Refresh on a schedule; on-demand when the upstream ontology
   has been re-imported into the local triplestore (§6).
   Multi-language: store labels per-locale, render in the user's
   locale.

This is a small DB change but a big UX change — search results
keep working as ontologies evolve, and labels can localize.

### 3.3 Search-as-you-type for terms

The minimum viable autocomplete:

- Backend endpoint `GET /terms/search?q=…&repository=…&kind=property|value`.
- Backed by a local index over the loaded ontologies (fulltext on
  `rdfs:label`, `skos:prefLabel`, `skos:altLabel`).
- Without a triplestore: a simple Neo4j fulltext index on
  `Term` nodes synced from the registered SPARQL endpoints. With
  a triplestore (§6): the same query goes against the triplestore.
- Frontend: `<v-autocomplete :items="useTermSearch(q)">` in
  `AddAnnotationDialog.vue`, replacing the free-text IRI fields.

The user types "creator" → autocomplete shows
`dcterms:creator (Dublin Core)`,
`prov:wasAttributedTo (PROV-O)`,
`schema:author (schema.org)`. They pick one; the IRI is bound
behind the scenes.

This is the single highest-impact GUI change and is independent
of the broader model work.

### 3.4 Annotation-workflow improvements

Beyond autocomplete:

- **Quick-pick recents.** A user annotating a batch usually reuses
  the same property. Track the last N
  `(propertyIRI, valueIRI prefix)` pairs per user; surface them.
- **Bulk apply.** When the user has selected multiple entities
  (multi-select in a list view), the add-dialog applies the
  annotation to all of them. UI today is one-by-one.
- **Inline editing.** `SemanticAnnotationChip.vue` is read-only;
  hover for delete/edit. No reason it can't gain inline replace
  (replace IRI / value while keeping the link).
- **Validation feedback.** When the IRI doesn't dereference (404 /
  no `rdfs:label`), show inline. Today silently snapshots an
  empty label.
- **Visual ontology hierarchy.** Once a triplestore is in place,
  the term picker can show the local hierarchy
  (`rdfs:subClassOf` / `skos:broader` chains) — "you picked
  TemperatureSensor; its parent class is Sensor; siblings:
  PressureSensor, FlowSensor".

Sequence: search-as-you-type first (independent of triplestore),
then bulk apply, then ontology-hierarchy view (after §6).

---

## 4. Connecting timeseries id discipline (perf §11.B)

The 5-tuple-vs-id mismatch from the performance doc shows up here:

- Annotations on timeseries already use the numeric id
  (`AnnotatableTimeseries.timeseriesId`).
- Data endpoints address the timeseries via the 5-tuple.
- Therefore a search for "annotated timeseries" returns numeric
  ids, but a CSV export call needs a 5-tuple. The frontend
  bridges this with extra round-trips.

The id-alignment plan in performance §11.B is the prerequisite
for cross-kind search results to be directly callable on data
endpoints. This document doesn't repeat it; just notes the
dependency.

For the *new* annotatable kinds (file / structured / spatial),
do not invent a 5-tuple analogue — start from the numeric id
(or UUID) day one. Don't grow a second instance of the same
problem.

---

## 5. Triplestore integration — opportunity

### 5.1 Why a triplestore at all

shepard's annotations are RDF triples wearing a Java costume:
`(subjectEntity, propertyIRI, valueIRI)` is exactly an RDF triple.
Today they live in Neo4j as nodes/edges, queried via the Cypher
hand-roll. The cost of *not* having a triplestore:

- **No reasoning.** RDFS/OWL inference (subClassOf, subPropertyOf,
  inverseOf, transitive properties, …) means you can answer
  questions about ontologies that the data doesn't literally
  state. Today we have none of that.
- **No federated SPARQL.** Already noted in search doc §4.
- **No ontology storage.** Ontologies aren't stored anywhere —
  shepard talks to remote SPARQL endpoints to fetch labels,
  nothing else. There is no offline copy, no provenance, no
  versioning of "which ontology was current when this annotation
  was made".
- **Search-as-you-type on terms is slow** without a local index.
  Round-tripping to a remote SPARQL endpoint for every keystroke
  isn't acceptable.

### 5.2 Candidate: GraphDB (or alternatives)

GraphDB (Ontotext) is one candidate:

- **Pros:** First-class SPARQL 1.1 + OWL2 RL/QL inference; SHACL
  validation; free edition for small workloads; mature; native
  geo + fulltext indexes; SPARQL Update.
- **Cons:** Free edition has node/license limits; Java/JVM (extra
  JVM in the stack); learning curve.

Alternatives:

- **Apache Jena Fuseki** — open source, lighter, no commercial
  edition concerns; weaker reasoning; would need TDB2 backend.
- **Stardog** — strong inference, paid.
- **Eclipse RDF4J** in-process (server- or library-mode) — light,
  Java-native; works well for ≤ low millions of triples; weaker
  reasoning than GraphDB.
- **Neo4j neosemantics (n10s) plugin** — keep Neo4j, add SPARQL +
  inference on top. Lower operational cost (no new container);
  weaker than a dedicated triplestore for inference but
  potentially "good enough".

**Recommendation:** Start with **n10s on the existing Neo4j**
to validate the *use cases* before adopting a separate store.
If reasoning / SPARQL throughput become bottlenecks, graduate
to a dedicated triplestore (RDF4J first, GraphDB if commercial
support is wanted).

### 5.3 What lives in the triplestore

Two zones, kept separate:

- **Ontology zone.** Imported ontologies (Dublin Core, PROV-O,
  schema.org, SKOS vocabularies, the project's own ontology if
  it has one). Updated by an admin job; versioned by date.
- **Annotation zone.** A *projection* of every
  `SemanticAnnotation` in shepard, kept in sync. Each annotation
  is one triple
  `<shepard://entity/Collection/42> <propertyIRI> <valueIRI>`,
  with shepard URIs minted per kind:

  ```
  shepard://entity/Collection/{id}
  shepard://entity/DataObject/{collectionId}/{dataObjectId}
  shepard://entity/Timeseries/{containerId}/{timeseriesId}
  shepard://entity/File/{fileContainerId}/{fileOid}
  …
  ```

The two are in the same store but in separate named graphs, so
SPARQL queries can be scoped (`FROM <…/ontology>` vs
`FROM <…/annotations>`) or use the union by default.

### 5.4 Sync strategy

Two options:

1. **Dual-write.** Service-layer annotation create/delete writes
   both to Neo4j (for the existing read path) and to the
   triplestore. Cheap, but two stores can diverge if one fails.
2. **Outbox / change-data-capture.** Annotation writes go to
   Neo4j only; an async worker reads a Neo4j change feed
   (`apoc.trigger`, or a dedicated audit table) and replays into
   the triplestore. Eventually consistent; survives triplestore
   downtime.

Option 1 is fine to start; switch to (2) when scale demands.

### 5.5 What the application gets

- **Reasoning.** `?ts a :Sensor` matches everything annotated with
  `:TemperatureSensor` if the ontology says
  `:TemperatureSensor rdfs:subClassOf :Sensor`. Today: not possible.
- **Federated SPARQL** (search doc §4).
- **SPARQL `CONSTRUCT` for export.** Knowledge-graph export of an
  experiment ("here is the RDF for collection X") — see §6.
- **Local term lookup at autocomplete latency.** §3.3 becomes a
  millisecond query against a local store.
- **Validation via SHACL.** A project-defined shape
  `:CalibrationRun sh:property [ sh:path :hasCalibrationCurve ;
  sh:minCount 1 ]` becomes machine-checkable on annotation
  create/update. Foundation for "Was this experiment fully
  annotated?" dashboards.

---

## 6. Knowledge-graph use cases

The user pointed to <https://zenodo.org/records/16736336> as an
example of where this can go. Without specifically endorsing or
reproducing that paper's design, the genre is "research data +
domain ontology + provenance graph = a queryable knowledge graph
of an experimental program". shepard sits exactly on the
ingestion side of that.

What unlocks once §5 lands:

### 6.1 Provenance-as-RDF

Every annotation already has an implicit provenance tail
(creator, creation time). Promoting that to PROV-O triples
(`prov:wasAttributedTo`, `prov:generatedAt`,
`prov:wasDerivedFrom` between linked entities) gives a queryable
provenance graph "for free" — the data is already there, just
not exposed as triples.

### 6.2 Cross-experiment graphs

The collection / dataobject / reference structure of shepard is
already a graph. Projecting it as RDF (one triple per Neo4j
edge, preserving entity URIs from §5.3) lets a researcher run
queries like:

```sparql
SELECT ?ts WHERE {
  ?coll a :Experiment ;
        :hasDataObject ?do .
  ?do   :hasReference  ?ref .
  ?ref  :pointsTo      ?ts .
  ?ts   :annotatedWith :TemperatureSensor .
}
```

…across collections / experiments. Today this is N+1 REST calls.

### 6.3 Domain-ontology alignment

When a research community has a shared ontology (e.g. SOSA/SSN
for sensors, OBO Foundry vocabularies for life sciences,
discipline-specific schemas in DLR's portfolio), shepard
annotations align with it once, and downstream tooling (analysis
notebooks, papers, FAIR catalogues) can rely on the alignment
for free.

### 6.4 Knowledge-graph export

A `GET /collections/{id}/export?format=turtle` endpoint that
emits the RDF projection of a collection — its DataObjects,
References, annotations, and (optionally) the provenance and
ontology subgraphs needed to interpret it standalone.
This is what makes a shepard collection "shareable as a
knowledge graph", in the spirit of the linked Zenodo example.

### 6.5 SHACL-driven completeness dashboards

If the project has a shape defining "what an annotated
experiment must contain", a dashboard can show "37 of 42
collections satisfy the shape". Surfaces the soft contract that
today lives only in domain experts' heads.

### 6.6 Fitness for FAIR

Findable / Accessible / Interoperable / Reusable — all four
benefit:

- **Findable**: stable IRIs as identifiers, dereferenceable.
- **Accessible**: SPARQL endpoint as a public read interface
  (scoped by permission) on top of the existing REST API.
- **Interoperable**: ontologies as the lingua franca with
  external tools.
- **Reusable**: provenance + SHACL = self-describing datasets.

---

## 7. Roadmap

| Phase | Work | Dependency |
|---|---|---|
| A | Generalised `AnnotatablePayload<ID>` and three new bridges (file, structured, spatial) + REST endpoints (§2) | none |
| B | Search-as-you-type term picker; backend `/terms/search` over Neo4j fulltext index of locally cached terms (§3.3) | none |
| C | Refactor labels to a side cache; identity is IRI pair (§3.2) | A or B in flight (small enough to do alone) |
| D | n10s plugin on Neo4j; expose SPARQL endpoint scoped to local annotation graph; first reasoning use case (§5.2 with Neo4j route) | C |
| E | Outbox-based sync from Neo4j writes to triplestore (§5.4 option 2); replace n10s with dedicated store if needed | D |
| F | Federated SPARQL via `raw.sparql` operator in unified search (search doc §4) | D |
| G | Knowledge-graph export endpoint (§6.4) | D |
| H | SHACL validation + completeness dashboards (§6.5) | D |
| I | Cross-store search planner (search doc §6) — uses annotation IRI joins to bridge Neo4j and Postgres results | F |

Phase A unblocks the long-standing semantic cluster (#43, #553,
#656, #660). Phases B–C are GUI-visible quick wins. Phase D is
the structural pivot that opens E–H.

---

## 8. Open questions

- **Which ontologies to load by default?** Dublin Core and PROV-O
  are universal. Project / discipline ontologies need an admin
  decision per deployment.
- **Multi-tenant ontology scope.** If two projects on one
  shepard instance use conflicting ontologies, the local cache
  needs scoping. Probably per-`SemanticRepository`.
- **Reasoning cost.** OWL DL inference is not free; choose the
  reasoning profile (RL? QL? RDFS+?) per ontology and document
  the trade-off.
- **Backwards compat for `propertyName` / `valueName` in API
  responses.** The label refactor (§3.2) shouldn't break
  existing JSON consumers; keep the fields, fill them from the
  cache. Mark them "presentation only" in OpenAPI.
- **Triplestore in the all-in-one image** (ADR 003). If
  GraphDB / Fuseki is added, the all-in-one image grows
  meaningfully; n10s avoids that. Worth weighing in the
  decision.
- **Permissions on SPARQL.** Already raised in search doc §8.
  Federated reasoning across permission boundaries is the
  hardest case; default-deny is the safe answer.

---

## 9. References

- Backend semantic code:
  `backend/src/main/java/de/dlr/shepard/context/semantic/`
  (`SemanticAnnotation.java`, `AnnotatableTimeseries.java`,
  `SparqlConnector.java`, `SemanticAnnotationService.java`,
  endpoints `*SemanticAnnotationRest.java`).
- Annotatable interface: `common/neo4j/entities/Annotatable.java`,
  `BasicEntity.java:24-25`.
- Frontend: `frontend/components/context/semantic/annotation/`
  (`AddAnnotationDialog.vue`,
  `SemanticAnnotationChip.vue`),
  `frontend/composables/annotated.ts`.
- Containers without annotation support today:
  `data/file/entities/FileContainer.java`,
  `data/structureddata/entities/StructuredDataContainer.java`,
  `data/spatialdata/daos/SpatialDataContainerDAO.java`.
- Open issues: #43, #274, #553, #656, #660; see
  `aidocs/archive/03-issues-status.md` cluster "Semantic annotations" and
  "Neo4j refactor".
- Companion docs: `12-timescaledb-performance-analysis.md` §11.B,
  `13-search-improvements.md`.
- ADR: `architecture/src/09_architecture_decisions/
  018-semantic-annotations-on-data.adoc` (current decision).
