# UI & Graph Ergonomics — Design Cluster

**Status.** Concept design — cluster doc covering eight related asks.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors.

**Originating items.** A single user session naming eight related
asks around UI ergonomics, graph navigation, and semantic
enrichment. Verbatim: drag-and-drop on the tree, a navigable graph
view, RO (Relation Ontology) added to the pre-seeded set, less
cumbersome internal references, container/collection duality, a
DBpedia Databus rich-reference plugin, GraphRAG-shaped AI, and an
underspecified "odix / for infpro usecase **zeigen**" line.
Couples to: `aidocs/33-frontend-workflow-analysis.md` (frontend
baseline — Nuxt 3 / Vue 3 / Vuetify 3), `aidocs/42-vision.md`
(casual-user north star + primitives), `aidocs/43-ai-opportunities.md`
(AI / LLM features sibling), `aidocs/47-dev-experience-and-plugin-system.md`
(plugin SPI for the DBpedia reference), `aidocs/48-internal-semantic-repository-via-neosemantics.md`
(pre-seeded ontology bundle for the RO addition), `aidocs/54`
(templates — D1 properties node uses `:ShepardTemplate`),
`aidocs/55` (provenance — UI1 move-history surface),
`aidocs/49-in-app-user-docs.md` and `aidocs/51-instance-admin-role.md`
(style reference).

---

## 1. Umbrella and how to read this doc

This doc bundles eight related ergonomics and semantic-enrichment
asks into one design pass. Each ask gets its own section, each
section stands alone with its own phasing IDs and gate column,
each is ≤ 80 lines.

The asks cluster around two pressures the casual-user north star
(`aidocs/42 §"Who it's for"`) names directly:

- **"Reduce friction at the click level."** Drag-and-drop on the
  tree (§2), a graph view (§3), `@`-mention autocomplete for
  internal references (§4), and the Container/Collection duality
  cleanup (§5) — all four reshape today's modal-stepper friction
  per `aidocs/33 §3`.
- **"Enrich shepard's semantic + AI surface."** RO addition to
  the neosemantics bundle (§6), DBpedia Databus references (§7),
  and a GraphRAG layer over the existing graph (§8) — three asks
  that ride on top of `aidocs/48` and `aidocs/43`.

The §9 "odix / zeigen" ask is sparse; it's recorded here as a
placeholder with the two most likely interpretations and a
maintainer decision request.

§10 collapses every phase ID into one table; §11 lists open
questions for the maintainer; §12 cross-references the
neighbouring designs; §13 records what this doc is **not**.

The doc lists eight new backlog umbrellas — **UI1, UI2, UI3, D1,
ONT1, REF1, AI1, BIZ1**. The dispatcher (per `aidocs/16` and the
`aidocs/00-index.md` reading order) reconciles them into the live
ledger; this doc is the source design for that reconciliation.
**Do not edit `aidocs/16` from this PR** — the dispatcher does.

---

## 2. §UI1 — Tree drag-and-drop reordering

**Backlog umbrella.** UI1 (`UI1a`, `UI1b`, `UI1c`).

**The ask.** "People have been wishing for drag-and-drop functions
in the lefthand-side tree to reorder objects. Move should be
default; copy possible."

**Today.** The lefthand sidebar
(`frontend/components/context/sidebar/CollectionSidebar.vue:101-117`
per `aidocs/33 §W4`) renders the Collection → DataObject →
sub-DataObject tree via `useTreeviewItems.ts:12-25`. Reordering or
re-parenting a DataObject means deleting it and re-creating it
under the new parent — destructive and slow.

**Proposal.** The tree supports HTML5 drag-and-drop. **Move is
the default action.** Copy on `Ctrl` (Windows/Linux) or `Alt`
(macOS) modifier — same convention as Finder / Explorer / VS Code.

**Cycle prevention.** Move-into-self and move-to-descendant are
prevented client-side (the drop target greys out during drag) and
server-side (the move endpoint refuses with `409 Conflict` +
`graph.cycle_forbidden` per the H4 RFC 7807 catalogue).

**Backend endpoint.** `PATCH /v2/dataobjects/{appId}` accepts a
`parentAppId` field (`application/merge-patch+json` per P21x).
Permission-checked: the caller must have write on **both** the
source and the destination DataObject (and on their respective
Collections). Per `CLAUDE.md` API-version policy this is `/v2/`
only — the `/shepard/api/...` surface stays frozen.

**Cross-Collection moves.** Deferred to UI1c. The first cut
restricts moves to within-the-same-Collection; a cross-Collection
drag opens a confirmation modal ("This will detach the DataObject
from Collection X and attach it to Collection Y; permissions on the
DataObject will reset to Y's defaults. Continue?"). The confirmation
is mandatory because cross-Collection permission inheritance is
non-obvious (per `aidocs/24` permission review).

**Copy action.** `Ctrl`-drag fires `POST /v2/dataobjects/{appId}/copy`
with body `{parentAppId: <destination>}`. Copy is **shallow** for
v1 — only the DataObject + its attributes, not the References (the
References stay pointed at their original payloads). Deep copy is
deferred behind real demand.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **UI1a** | Frontend DnD with optimistic update + rollback on 4xx. `PATCH /v2/dataobjects/{appId}` with `parentAppId` field. Within-Collection only. | M | L2d (`appId`-native `/v2/` shelf) |
| **UI1b** | Server-side cycle prevention (`409 graph.cycle_forbidden`). | S | UI1a |
| **UI1c** | `Ctrl`-drag copy via `POST /v2/dataobjects/{appId}/copy`. Cross-Collection move confirmation modal. | M | UI1a + UI1b |

**Open question.** Do we want a server-side **move history** for
the provenance trail (per `aidocs/55`)? Probably yes once PROV1
lands — each `PATCH parentAppId` would emit a `prov:wasDerivedFrom`
edge naming the previous parent. Defer the wiring until `aidocs/55`
PROV1a; for now the move endpoint is "atomic re-parent without
history."

---

## 3. §UI2 — Dynamic navigable graph view

**Backlog umbrella.** UI2 (`UI2a`, `UI2b`, `UI2c`).

**The ask.** "A dynamic (navigable) 'graph' view of the Collection
would be nice as well."

**Today.** The Collection page (`pages/collections/[collectionId]/index.vue`
per `aidocs/33 §W3`) shows the Collection as a tree plus tabs for
description, attributes, lab journal. The graph topology
(References, Annotations, predecessor edges) is invisible unless
the user clicks into each entity.

**Proposal.** A new **"Graph view" tab** on the Collection page
renders the Collection's internal graph as an interactive
node-link visualisation. Nodes = DataObjects + References +
Annotations. Edges = `contains`, `references`, `annotated_with`,
`predecessorOf` (the `aidocs/42` "Lineage / predecessors" surface).

**Library pick — cytoscape.js.** Comparison:

| Library | Verdict |
|---|---|
| **cytoscape.js** | **Recommended.** Most mature, declarative JSON model, layouts (cose, dagre, breadthfirst, concentric) built-in, no D3-flavoured imperative wiring, Vue wrapper available (`vue-cytoscape`). |
| vis-network | Easy to wire, fewer layouts, harder to style at scale (>200 nodes). |
| sigma.js | WebGL-fast at large graph sizes, but the JSON model is less declarative; overkill for ~500-node Collections. |
| d3-force | Maximum flexibility, but every layout / interaction / styling decision is a hand-coded affair. Too much surface area for a Collection-view tab. |

cytoscape.js's `dagre` layout matches the Collection's tree-with-
back-edges shape; `cose-bilkent` handles the dense-cluster
fallback when a Collection has many cross-references.

**Interactions.**

- **Click a node** → opens the entity's detail panel inline (same
  pane shape as the existing tree-click).
- **Drag a node** → repositions in the view; no backend change
  (positions are display-only).
- **Double-click a Collection-internal "child" Collection** →
  navigates to that Collection's graph view (post-D1c when nested
  Collections are first-class).
- **Right-click** → context menu (open detail, copy `appId`,
  hide-from-view).

**Layout persistence.** Per-user per-Collection. The Vue component
saves the (nodeAppId → x,y) map to localStorage by default; if the
user is logged in and has `aidocs/36 §3.2`-style preferences,
the layout writes to `/v2/users/me/preferences/graph-layouts/{collectionAppId}`
so it follows them across devices.

**Performance.** Hard cap of 500 nodes by default. Above 500 the
view degrades to a **clustered** rendering: References are
collapsed into "N references on DataObject X" badges; Annotations
collapse similarly. A "show full graph" button overrides the cap
with a warning ("rendering may be slow"). Pagination by
predecessor-chain depth is the long-term answer; deferred.

**Data source.** `GET /v2/collections/{appId}?profile=relations`
returns the Collection's graph in a single payload (nodes + edges).
The `relations` profile is a new value of the existing
`?profile=` query param (per `aidocs/26 §"profile"`). It's
read-only and cached for 60s server-side.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **UI2a** | Basic graph render from `/v2/collections/{appId}?profile=relations`. Click → detail. Drag → reposition (display-only). | M | L2d (`/v2/` shelf) |
| **UI2b** | Per-user layout persistence — localStorage v1, `/v2/users/me/preferences/graph-layouts/{collectionAppId}` post-U1c. | S | UI2a + U1c |
| **UI2c** | Filtering by entity kind (DataObject only / + References / + Annotations) and by Annotation tag. Clustered rendering above 500 nodes. | M | UI2a |

**Open question.** Cytoscape.js is the recommendation, but
vis-network is a real alternative for teams who prefer its
defaults. Flagged in §11.

---

## 4. §UI3 — Less-cumbersome internal references

**Backlog umbrella.** UI3 (`UI3a`, `UI3b`, `UI3c`).

**The ask.** "How can we make the internal Collection and
DataObject References a little less cumbersome?"

**Today.** Citing an internal entity from a description, lab-journal
entry, or annotation comment means typing a `/shepard/api/...` URL
by hand. The user opens the entity in another tab, copies the URL
out of the address bar, pastes it into the markdown. Casual users
abandon — they paste a name instead, and three months later nobody
can find what was meant.

**Proposal.** **`@`-mention autocomplete.** In any rich-text field
(description, lab-journal, annotation comment), typing `@` opens a
search-as-you-type dropdown. Prefixes narrow the search:

- `@coll` → search Collections
- `@do` → search DataObjects
- `@ref` → search References (file, structured, timeseries,
  spatial, HDF post-A5, git post-G1)
- `@` (no prefix) → search across all entity kinds

Picking an item inlines an **opaque chip token** in the text. On
render, the chip resolves to a clickable link with the current
entity's name (so renaming the target reflects everywhere
referencing it).

**Underlying syntax.** `[entity:<appId>]` — post-L2d when `appId`
is the canonical identifier per the L2 chain (`aidocs/25`). The
chip stores the `appId` only; the display name is resolved at
render time, so renames propagate. Three syntax candidates were
considered:

| Candidate | Verdict |
|---|---|
| `[entity:<appId>]` | **Recommended.** Stable across renames; explicit kind unnecessary because `appId` is globally unique post-L2d. |
| `@<appId>` | Shorter but mixes with the trigger character; renders ugly in raw markdown. |
| `[[entity-name]]` (Obsidian) | Friendly but breaks on rename. Punt. |

**Implementation.** TipTap is the rich-text editor used today for
the lab journal (`aidocs/37`); add a **custom Mention extension**
keyed off the search endpoint `GET /v2/search?q=...&kinds=Collection,DataObject,FileReference,StructuredDataReference,TimeseriesReference,SpatialDataReference`
(uses the unified `/search/v2` per `aidocs/13 §2`). The endpoint
returns `[{appId, kind, displayName, breadcrumb}]` sorted by
relevance + recency.

**Render-side chip → link resolution.** A small frontend resolver
takes a Markdown string, parses `[entity:<appId>]` tokens, fires
one batched `GET /v2/entities?appIds=...` call, replaces tokens
with anchor tags. Cached in-component for the lifetime of the
view.

**Orphan handling.** A chip pointing to a deleted entity renders
as `[deleted]` (greyed out) instead of breaking the link. The
chip's `appId` is preserved in the source markdown so a future
restore-from-snapshot (per `aidocs/41` V2) re-resolves it.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **UI3a** | `GET /v2/search?q=...&kinds=...` search endpoint (or reuse `aidocs/13` P7 if shipped). TipTap Mention extension wired into the lab-journal editor. | M | L2d + P7 (`aidocs/13`) |
| **UI3b** | Render-side `[entity:<appId>]` → anchor-tag resolution, batched lookup. Extend to description + annotation comment fields. | S | UI3a |
| **UI3c** | Orphan-handling — `[deleted]` chip render, snapshot-aware restore. | S | UI3b + V2 (`aidocs/41`) |

**Open question.** Syntax choice flagged in §11.

---

## 5. §D1 — Collection / Container duality + properties node

**Backlog umbrella.** D1 (`D1a`, `D1b`, `D1c`).

**The ask.** "How can we make the container / collection duality
of systems easier? Can we extend the `default_filecontainer`
strategy? Or move that to a properties node that also holds
template info and other things?"

**Today.** A Collection carries — directly on the Collection
node — a `defaultFileContainerOid` property + the
`fileContainerCreationStrategy` enum. The Container vs Collection
distinction is a long-standing source of confusion: a Container
is a storage-level bucket (FileContainer, TimeseriesContainer,
StructuredDataContainer, SpatialContainer) while a Collection is
the campaign-level grouping. Casual users think of them as the
same thing and the API forces them apart.

**Proposal.** Introduce a **`:CollectionProperties` Neo4j node**
that hangs off each Collection via a `:HAS_PROPERTIES` edge and
holds:

- The default-FileContainer-strategy config currently on the
  Collection node.
- **Template-info** — links to `:ShepardTemplate` nodes per
  `aidocs/54` (which templates are allowed in this Collection's
  per-Collection allow-list, per `aidocs/39 §"Decisions"` and the
  user's "Collection owners decide which templates are allowed"
  constraint).
- **Default ontology** — which pre-seeded ontology (`aidocs/48`)
  is the first hit in the annotation picker for this Collection.
- **Default permission** — what permission shape new DataObjects
  in this Collection inherit (per `aidocs/24` F2 group permissions
  when shipped).
- **UI defaults** — preferred view mode (tree / graph per §3),
  default sort order, default profile for nested DataObjects.

**Why a separate node.** Keeps the Collection entity itself
**stable across feature additions**. Today every new Collection-
level config field forces a Collection schema migration; with a
`:CollectionProperties` node, config additions are properties on
the side-node and the user-facing Collection shape stays clean.
Same shape as how `aidocs/36` U1 puts user preferences on a
side-node off `User`.

**Migration.** `V##__Add_collection_properties.cypher` —
idempotent. Walks every `:Collection` node; if it has no
`:HAS_PROPERTIES` edge, creates a `:CollectionProperties` node
copying over the existing `defaultFileContainerOid`,
`fileContainerCreationStrategy`, and any other current
Collection-level config. Existing reads via the old fields keep
working via a **read-through fallback** for one minor version
(`CollectionService.getDefaultFileContainerOid` checks the
properties node first, then falls back to the Collection node).
Writes go to the properties node only — the old field stays
NULL-on-new-writes for the deprecation window, then drops in a
future migration. Per `CLAUDE.md` migrations land idempotent +
fail-fast.

**Why this isn't just renaming a column.** It's a structural
change that unblocks future config additions without further
migrations. Each new config field is a property on
`:CollectionProperties` — no schema change.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **D1a** | New `:CollectionProperties` node + `:HAS_PROPERTIES` edge + `V##__Add_collection_properties.cypher` Cypher migration. Read-through fallback in `CollectionService`. | M | L2d |
| **D1b** | Writer-side updates — every new write of `defaultFileContainerOid` / `fileContainerCreationStrategy` / templates / etc. goes to the properties node, never the Collection node. Old fields drop in `V##__Remove_collection_properties_legacy_fields.cypher`. | S | D1a |
| **D1c** | UI surfaces a **"Collection settings"** pane backed by the properties node — `pages/collections/[collectionId]/settings.vue`. Same pane shape as the per-DataObject attributes pane. | M | D1a + D1b |

**Open question.** Should DataObject have a similar
`:DataObjectProperties` node? **Defer.** Today DataObjects don't
carry cross-cutting config; the attribute system covers per-entity
custom fields. Collection is the level that accumulates config
because it's the campaign-scope. Revisit if a DataObject-level
config emerges.

---

## 6. §ONT1 — Add RO (Relation Ontology) to the pre-seeded bundle

**Backlog umbrella.** ONT1 (`ONT1a`, `ONT1b`, `ONT1c`).

**The ask.** "RO Relation Ontology to ontologies."

**Today.** `aidocs/48 §4` lists the pre-seeded ontology bundle:
PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time /
GeoSPARQL — ~13 MB total. RO is not yet on the list.

**Proposal.** Add **RO (Relation Ontology)** — OBO-Foundry's
widely-used relation vocabulary defining `part_of`,
`participates_in`, `derives_from`, `precedes`, `has_input`,
`has_output`, and ~150 other relations. Heavily used in life-
sciences research-data work and increasingly outside.

| Property | Value |
|---|---|
| Source URL | `http://purl.obolibrary.org/obo/ro.owl` |
| Bundled filename | `backend/src/main/resources/ontologies/obo-relations.owl` |
| Size | ~5 MB |
| Format | OWL / RDF-XML; n10s imports both |
| Licence | CC0 (per the OBO Foundry licensing requirements) |
| SHA-256 | Pinned in `pom.xml` + bundled-data manifest |

**Why RO.** The LUMEN-inspired showcase already wants relations like
"`tr-006` was the bearing-replaced re-test **derived from**
`tr-004`" — today encoded as a custom `dlr:re_test_of` placeholder.
RO ships `derives_from` and `precedes` natively; the showcase seed
post-ONT1b uses the RO IRIs.

**Bundle delta.** `aidocs/48 §4`'s 13 MB bundle becomes ~18 MB
total — still negligible against Neo4j's storage budget.

**Recommended path.** **Add to the `aidocs/48 §4` bundle table.**
The dispatcher reconciles `aidocs/48` directly per
`CLAUDE.md` standing rule; this design only states the
requirement.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **ONT1a** | Download + SHA-256 pin + bundled `obo-relations.owl` + import-on-startup hook (extends `aidocs/48` N1b). | S | N1a (`aidocs/48`) |
| **ONT1b** | Showcase seed (`examples/seed-showcase/`) uses RO terms — `prov:wasGeneratedBy` + `ro:derives_from` for the bearing-replaced re-test edge in the LUMEN process-graph. | S | ONT1a |
| **ONT1c** | Frontend ontology-picker dropdown surfaces RO terms by default in the annotation flow (couples to N1e). | S | ONT1a + N1e |

**Note.** This could ship as a single slice in a `aidocs/48 §6`
add-on PR rather than its own three-phase rollout — the size is
small. Phasing IDs are recorded above for tracker consistency.

---

## 7. §REF1 — DBpedia Databus rich-reference plugin

**Backlog umbrella.** REF1 (`REF1a`, `REF1b`, `REF1c`).

**The ask.** "Consider a new reference type as a plugin. A
reference to a DBpedia Databus instance (internal) entities
showing a rich reference (preview, description, title, ...)."

**Today.** shepard's payload-kind references are file / structured /
timeseries / spatial / (post-A5) HDF / (post-G1) git. Internal-
to-shepard References (cross-Collection) are the same as the §4
ask. **External-to-shepard semantic references** — citing a
DBpedia Databus dataset, a Wikidata entity, a Zenodo deposition —
are today done as a plain URL in a description field. No preview,
no title resolution, no graceful degradation when the cited entity
moves.

**Proposal.** Ship `DBpediaDatabusReference` as a **new
payload-kind plugin** built on the SPI from `aidocs/47 §2`. The
plugin lives in its own JAR — `shepard-plugin-ref-dbpedia-databus`
— and drops in via the SPI's `PayloadKind` interface.

**Data model.**

```java
public class DBpediaDatabusReference extends BasicReference {
  private URI databusInstanceUri;  // e.g. https://databus.dbpedia.org/
  private URI entityIri;           // the cited dataset's Databus IRI
  // ... plus inherited BasicReference fields ...
}
```

The reference points at a Databus dataset by IRI. The Databus
instance is named separately so a single shepard can cite multiple
Databus instances (the canonical `databus.dbpedia.org` plus any
private DBpedia Databus deployment).

**Rich preview.** `GET /v2/references/{appId}?profile=all` returns
the standard reference payload **plus** a `preview` object fetched
from the Databus instance's content-negotiated metadata:

```json
{
  "appId": "...",
  "kind": "dbpedia-databus",
  "databusInstanceUri": "https://databus.dbpedia.org/",
  "entityIri": "https://databus.dbpedia.org/dbpedia/wikidata/instance-types/2023-04-01",
  "preview": {
    "title": "DBpedia Wikidata Instance Types",
    "description": "RDF dump of Wikidata-derived instance-of triples...",
    "publisher": "DBpedia Association",
    "thumbnailUri": "...",
    "issued": "2023-04-01"
  }
}
```

The Databus dataset metadata is fetched via content-negotiated
`Accept: text/turtle` GET against the entity IRI; the response is
parsed for `dct:title`, `dct:description`, `dct:publisher`,
`dct:issued`, `schema:thumbnailUrl`.

**Caching.** Preview responses are cached server-side for **24h**
to avoid hammering the Databus on every read. The cache key is
`(databusInstanceUri, entityIri)`; invalidation is time-based plus
an explicit `POST /v2/references/{appId}/preview/refresh` for
operators who know an upstream change happened.

**Frontend render.** A card-shaped preview in the Reference detail
view: title, description, publisher, thumbnail (if present),
"Open on DBpedia Databus" link.

**Best-effort policy.** The Databus content-negotiation contract
is **not** controlled by shepard. If DBpedia changes the metadata
shape, the plugin renders a degraded card ("title unresolved")
and logs the parse failure. The plugin is **off-by-default**
until v1; operators opt in via `shepard.payload.dbpedia-databus.enabled=true`.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **REF1a** | Plugin scaffold (Quarkus extension JAR per `aidocs/47 §2`) + `DBpediaDatabusReference` entity + Cypher migration (`V##__Add_appId_constraint_DBpediaDatabusReference.cypher`). | M | PL1a (`aidocs/47`) + L2d |
| **REF1b** | Preview-fetch via Apache Jena RDF parsing + 24h server-side cache. `POST /v2/references/{appId}/preview/refresh` invalidation endpoint. | M | REF1a |
| **REF1c** | Frontend rich-card render — card component, "Open on DBpedia Databus" link, degraded-state UX. | S | REF1b |

**Open question.** Who maintains the plugin if DBpedia's
content-negotiation contract changes? Document as "best-effort,
off-by-default until v1"; revisit governance once external
contributors emerge.

---

## 8. §AI1 — GraphRAG over shepard

**Backlog umbrella.** AI1 (`AI1a`, `AI1b`, `AI1c`). **Distinct
from the `aidocs/43` AI1 series** — that's the LLM-features
umbrella; this is the GraphRAG retrieval layer that the
`aidocs/43` chat consumes. To avoid ID collision, the dispatcher
reconciles this as **GR1** (graph-RAG) or as `aidocs/43`'s
AI1 sub-IDs (`AI1q+`); the source label here is **GR1** going
forward in the doc.

**The ask.** "Regarding AI possibilities — is there anything we
can do with vectorstores and embeddings, also all these graphs?
Looking for similarities. I am thinking of '(Graph)RAG' my
shepard."

**Today.** `aidocs/43` designs the LLM chat layer (snap dashboards,
natural-language search, lab-journal authoring assist) but the
retrieval is **text-shaped** — searches today are `aidocs/13`
Lucene-style predicates against names, descriptions, attributes.
shepard's graph topology — who derives from whom, who annotates
what — is invisible to the LLM.

**Proposal.** A **GraphRAG layer** over shepard's existing Neo4j +
the casual-user metadata fields (descriptions, lab-journal entries,
annotations). Four components:

**1. Embeddings on every entity.** For every Collection /
DataObject / lab-journal entry / Reference, compute an embedding
from its text content (`description + name + concatenated
children's names + annotation labels`). Embeddings live in a
**Neo4j 5.13+ native vector index** — no sidecar service needed.

```cypher
CREATE VECTOR INDEX shepard_entity_embedding IF NOT EXISTS
FOR (n:DataObject)
ON n.embedding
OPTIONS {indexConfig: {`vector.dimensions`: 1536, `vector.similarity_function`: 'cosine'}}
```

(One index per entity-kind, since dimensions are model-dependent.)

**Library pick — native Neo4j vector index.** Comparison:

| Option | Verdict |
|---|---|
| **Native Neo4j 5.13+ vector index** | **Recommended.** Zero new infrastructure; shepard already runs Neo4j (per the `aidocs/48` argument). Same backup target. SPARQL+Cypher+vector all on one DB. |
| Qdrant sidecar | More vector-features (HNSW tuning, filtering), but new service to deploy + back up + auth. |
| Chroma / Weaviate | Adds another moving part. Same trade-off as Qdrant. |
| pgvector on the existing Postgres | Possible — shepard already runs Postgres for TimescaleDB. But couples vector queries to a DB that's optimised for timeseries. Punt. |

Native Neo4j wins on the casual-deployment story per the
`aidocs/48` precedent.

**2. Similarity search endpoint.**
`GET /v2/search/similar?to=<entityAppId>&topN=10` returns the
top-N most-text-similar entities. Useful for "find runs like this
one." Cypher:

```cypher
MATCH (target {appId: $appId})
CALL db.index.vector.queryNodes('shepard_entity_embedding', $topN, target.embedding)
YIELD node, score
RETURN node, score
ORDER BY score DESC
```

Permission-filtered post-query via `aidocs/24 P2`
`filterAllowedForUser`.

**3. GraphRAG retrieval pattern.** When the `aidocs/43` LLM
chat is asked a shepard question, retrieval pulls **both**:

- **Text-similar entities** via the vector index (top-K by
  cosine).
- **Graph-neighbours** via Cypher (entities within N hops of any
  text-hit — parents, children, predecessors, annotated-by-same-
  term, etc.).

The two retrieval sets are **fused** (RRF — reciprocal rank
fusion, the standard GraphRAG primitive) and the top-K fused
results form the LLM context. The "Graph" in GraphRAG is the
graph topology — it informs which retrievals are relevant beyond
raw text similarity. Two entities with identical descriptions but
no graph connection are less relevant than two with similar
descriptions plus a `predecessorOf` edge.

**4. Model choice.** `aidocs/43 §"Model choice"` already lays out
BYOK OpenAI-compatible + `ai.baseUrl` + `ai.model` selection. Same
applies for embeddings. Defaults:

- BYOK path: `text-embedding-3-small` (OpenAI) at 1536 dims, cheap.
- Free path: `nomic-embed-text` via Ollama (local, free, 768
  dims), or `bge-small-en` similar.

The embedding-model dimension is read from the configured backend
and the Neo4j vector index is created with matching dimensions on
first run. Re-embedding on model change is a one-off background
job; same `aidocs/32` long-running-job pattern.

**Pre-compute strategy.** Embeddings compute **async** on entity
write (description / name / attributes change). A Quarkus
`@Scheduled` job picks up dirty entities every 30s, batches them
(100 per call), embeds, writes back. Acceptable freshness lag
~1 minute. **Not** synchronous on the write path — that would
add LLM-API latency to every shepard write.

**Phasing.**

| ID | Slice | Size | Gate |
|---|---|---|---|
| **GR1a** | Embedding job (async, Quarkus `@Scheduled`) + Neo4j vector index + `GET /v2/search/similar?to=...` endpoint. | L | L2d + `aidocs/43` AI1a (LlmClient base) + Neo4j ≥ 5.13 |
| **GR1b** | Frontend "find similar" button on the Collection / DataObject / Reference detail page. Surfaces top-N similar entities as a sidebar list with similarity scores. | M | GR1a |
| **GR1c** | GraphRAG fusion pattern as a service consumed by `aidocs/43`'s LLM chat — `GraphRagService.retrieve(query, contextEntity, topK)`. RRF over vector hits + N-hop neighbour walk. | L | GR1a + `aidocs/43` AI1e |

**Open question.** Acceptable freshness lag for embedding
re-compute? Recommendation 1 minute; flagged for maintainer
confirmation in §11.

---

## 9. §BIZ1 — odix / "zeigen" for InfPro use-case (placeholder)

**Backlog umbrella.** BIZ1 (pending).

**The ask, verbatim.** "odix / for infpro usecase **zeigen** ..."

**State.** Sparse. The brief uses the German word **"zeigen"
("show / demonstrate")** as the operative verb, paired with
"odix" and "InfPro" as use-case labels. Without further context
the design can't commit to a shape.

**Two candidate interpretations.**

| # | Interpretation | What it would look like |
|---|---|---|
| (i) | **Public-display mode for a Collection** — a "presentation" toggle so a researcher can show a partner what a campaign produced **without exposing internal details**. Like a "story view" of a Collection. | New `Collection.displayMode` enum (`INTERNAL` / `PRESENTATION`); presentation mode hides certain DataObject attributes flagged `presentation: false`; render is a polished read-only Collection page; URL is `/p/{collectionAppId}` (token-secured if Collection isn't PUBLIC). |
| (ii) | **Published-snapshot URL** — same idea as `aidocs/41` V2 snapshots, exposed publicly with a token-secured short URL. | Snapshot creates the immutable view; a `POST /v2/snapshots/{appId}/publish` mints a short URL `/s/{token}` that returns the snapshot's contents to anyone with the token. Already largely covered by V2 + a thin URL-shortening layer. |

**The two interpretations are not exclusive.** Interpretation (i)
is the **viewer** (how the data is rendered); interpretation (ii)
is the **delivery mechanism** (how the URL is shared). A real
"zeigen" feature probably needs both — render a polished read-only
view AND ship it via a short URL.

**Decision request for the maintainer.** Two questions:

1. Is "zeigen" closer to (i), (ii), both, or something else
   entirely (e.g. an existing DLR-internal product called "odix"
   that shepard should integrate with)?
2. Who is the audience? Internal-DLR partner, external public,
   journal reviewer? Each implies a different auth / branding /
   feature shape.

**Until clarified.** BIZ1 stays in `aidocs/16` as **needs-decision**.
Do not dispatch.

---

## 10. Phasing summary table

All sub-slices in one place. Per-row gates are recorded in the
detail tables above.

| Series | Slices | Series gate |
|---|---|---|
| **UI1** (tree DnD) | UI1a → UI1b → UI1c | L2d |
| **UI2** (graph view) | UI2a → UI2b → UI2c | L2d (+ U1c for UI2b) |
| **UI3** (`@`-mention) | UI3a → UI3b → UI3c | L2d + P7 (`aidocs/13`) |
| **D1** (CollectionProperties) | D1a → D1b → D1c | L2d |
| **ONT1** (RO ontology) | ONT1a → ONT1b → ONT1c | N1a (`aidocs/48`) |
| **REF1** (DBpedia Databus) | REF1a → REF1b → REF1c | PL1a (`aidocs/47`) + L2d |
| **GR1** (GraphRAG) | GR1a → GR1b → GR1c | L2d + `aidocs/43` AI1a + Neo4j ≥ 5.13 |
| **BIZ1** (zeigen) | needs-decision | maintainer clarification |

**Sequencing note.** UI1 / UI2 / UI3 / D1 all depend on L2d (the
`/v2/` shelf with `appId` as native identifier per `aidocs/25`).
Order within each series is sequential; **across series, the
casual-user-impact ordering is UI1 → UI3 → UI2 → D1 → ONT1 →
GR1 → REF1**. UI1 and UI3 are highest immediate friction-reduction;
UI2 is delight-feature; D1 is structural cleanup; ONT1 is semantic
enrichment; GR1 unlocks the AI sibling; REF1 is most-niche.

---

## 11. Open questions for the maintainer

Consolidated from the per-section flags. Each needs a decision
before the dispatched slice is finalised.

1. **BIZ1's actual scope.** The "odix / zeigen" ask is
   underspecified. Maintainer to pick between §9's two
   interpretations or supply a third. **Highest priority** —
   blocks dispatch.
2. **UI2 library pick.** Cytoscape.js is the design's
   recommendation; vis-network is the real alternative for teams
   who prefer its defaults. Maintainer can override with one line.
3. **GR1 vector-store backend.** Native Neo4j 5.13+ vector index
   is the design's recommendation; Qdrant sidecar is the real
   alternative for teams who want HNSW-tuning + filter expressions.
   Maintainer can override with one line.
4. **D1 scope — properties node for DataObject too?** The design
   says no (Collection-only); maintainer can flip to yes if a
   DataObject-level config field is on the horizon.
5. **UI3 `@`-mention syntax.** Three candidates listed in §4:
   `[entity:<appId>]` (recommended), `@<appId>`, `[[entity-name]]`
   Obsidian-style. Recommendation is `[entity:<appId>]` for
   rename-stability; maintainer can flip.
6. **GR1 embedding-freshness lag.** Recommendation 1 minute via
   async background worker. Maintainer can tighten (synchronous
   embed on write, adds API latency) or loosen (10 minutes, less
   compute pressure).

---

## 12. Cross-references

- `aidocs/13-search-improvements.md` — UI3 mention search reuses
  the P7 unified search endpoint.
- `aidocs/16-dispatcher-backlog.md` — UI1 / UI2 / UI3 / D1 / ONT1 /
  REF1 / GR1 / BIZ1 series queueing entries (added by dispatcher,
  not from this PR).
- `aidocs/24-permission-system-review.md` — UI1 cross-Collection
  move permission semantics; GR1 similarity-search
  permission-filtering via P2 `filterAllowedForUser`.
- `aidocs/25-neo4j-id-migration-design.md` — L2d gate on UI1 /
  UI2 / UI3 / D1 / REF1 / GR1 (`appId`-native `/v2/` shelf).
- `aidocs/33-frontend-workflow-analysis.md` — UI1 sidebar
  citation (`CollectionSidebar.vue`); UI3 lab-journal editor
  citation (TipTap usage).
- `aidocs/34-upstream-upgrade-path.md` — every series above adds
  one or more rows once it ships (admin-facing ledger; dispatcher
  reconciles).
- `aidocs/36-user-profile-and-settings-design.md` — UI2b graph-
  layout persistence lands on the `/v2/users/me/preferences/`
  surface.
- `aidocs/39-templates-design.md` and `aidocs/54` templates — D1
  properties node references `:ShepardTemplate`.
- `aidocs/41-snapshots-design.md` V2 — UI3c orphan-handling
  snapshot-aware restore; BIZ1 interpretation (ii) is a thin
  layer on top of snapshots.
- `aidocs/42-vision.md` — every user-visible series above flips a
  bullet from "near horizon" to "what's in the box (today)" on
  ship (per `CLAUDE.md` standing rule; dispatcher reconciles).
- `aidocs/43-ai-opportunities.md` — GR1 is the retrieval layer
  that `aidocs/43`'s LLM chat consumes; GR1c is the explicit
  hand-off.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — every series
  above gains a matrix row (dispatcher reconciles).
- `aidocs/47-dev-experience-and-plugin-system.md` — REF1 builds
  on PL1a's `PayloadKind` SPI.
- `aidocs/48-internal-semantic-repository-via-neosemantics.md` —
  ONT1 extends N1b's pre-seeded bundle (dispatcher reconciles
  `aidocs/48 §4` table).
- `aidocs/55` provenance — UI1 move-history wiring once PROV1a
  lands.

---

## 13. What this isn't

- **Not a frontend rewrite.** Every section above is additive on
  top of the existing Nuxt 3 / Vue 3 / Vuetify 3 stack
  (`aidocs/33`). UI1 / UI2 / UI3 ship as new components +
  extensions; D1c adds one settings pane.
- **Not a graph-database swap.** GR1 uses Neo4j's native vector
  index — no migration off Neo4j, no second graph store.
- **Not vendor-locked to one LLM provider.** GR1 inherits
  `aidocs/43`'s BYOK + admin-fallback resolution. Local Ollama
  paths work end-to-end; OpenAI is opt-in.
- **Not committing to BIZ1 without maintainer clarification.**
  §9 is a placeholder with two candidate interpretations; the
  dispatcher does **not** queue BIZ1 against an interpretation
  this doc invented. Maintainer decides first.
- **Not a breaking change to the upstream API.** Everything new
  lands under `/v2/` per the `CLAUDE.md` API-version policy. The
  `/shepard/api/...` surface stays byte-frozen.
- **Not free-for-all plugin sprawl.** REF1 rides the existing
  `aidocs/47 §2` PayloadKind SPI; the SPI shape is fixed, the
  reference-kind list grows.
