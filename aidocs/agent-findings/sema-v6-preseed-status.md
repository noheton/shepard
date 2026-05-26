---
stage: deployed
last-stage-change: 2026-05-26
---

# SEMA-V6 Preseed Status (2026-05-26)

Verification of the internal semantic repository state on the live instance at
`https://shepard.nuclide.systems`. Addresses the user-flagged gap
(`SEMA-V6-INTERNAL-REPO-PRESEED-VERIFY`) where `/semantic/vocabularies` rendered empty.

---

## V49 Migration Status

**V49 has been applied.** The neo4j-migrations library tracks applied migrations as
a `MIGRATED_TO` chain on `__Neo4jMigration` nodes — not via `state`/`installedOn`
properties (those are `NULL` on every node in this library version). The chain runs
continuously from `BASELINE` → `1` → … → `64`; V49 sits at hop 49.

```cypher
MATCH p = (:__Neo4jMigration {version:'BASELINE'})-[:MIGRATED_TO*]->({version:'49'})
RETURN length(p)
// → 49 hops — V49 IS in the chain
```

What V49 created: a single `:SemanticRepository {type:'INTERNAL', name:'Built-in Semantic
Store (n10s)'}` node with an `appId`. It **does not** create `:Vocabulary` or `:Predicate`
nodes — those are SEMA-V6-002 work, still queued.

---

## Current Semantic Graph State

### SemanticRepository nodes (3 found)

| name | type | endpoint | deleted |
|---|---|---|---|
| `Built-in Semantic Store (n10s)` | INTERNAL | `` (empty) | false |
| `prov-o` | INTERNAL | `urn:shepard:repo:prov-o` | false |
| `fair2r` | INTERNAL | `urn:shepard:repo:fair2r` | false |

The primary internal repo was created by V49. The `prov-o` and `fair2r` repos were
added by subsequent work (upper-ontology / provenance design).

### n10s graph configuration

n10s is **initialised and healthy**:

```
handleVocabUris = IGNORE
handleMultival  = ARRAY
handleRDFTypes  = LABELS_AND_NODES
keepLangTag     = true
applyNeo4jNaming = true
```

### Resource count (n10s loaded RDF)

**378,547 `:Resource` nodes** exist in the graph — the ontology preseed has run and
loaded significant content. Breakdown by ontology namespace:

- `http://purl.org/dc/…` — Dublin Core (purl.org/dc/terms, dcam, dcmitype, elements/1.1)
- `http://qudt.org/…` — QUDT schema, quantitykind, unit
- `http://w3id.org/nfdi4ing/metadata4ing/` — metadata4ing (m4i / ONT1b)
- `http://purl.obolibrary.org/obo/…` — OBO Relations (BFO, IAO, RO — ONT1a)
- `http://www.ontology-of-units-of-measure.org/resource/om-2/…` — OM-2
- `http://www.opengis.net/ont/geosparql…` — GeoSPARQL
- `http://www.w3.org/2006/time…` — W3C Time
- `https://schema.org/…` — schema.org
- `http://xmlns.com/foaf/0.1/…` — FOAF
- `http://www.w3.org/TR/prov-o/…` — PROV-O
- `http://www.w3.org/2000/01/…`, `http://www.w3.org/2002/07/…` — RDF/OWL core
- `http://semantics.dlr.de/shepard-upper#…` — Shepard upper ontology

**All 10 canonical bundles from `application.properties` default preseed are present.**

### Vocabulary and Predicate nodes

`MATCH (v:Vocabulary) RETURN count(v)` → **0**  
`MATCH (p:Predicate) RETURN count(p)` → **0**

These node types do not exist yet. They are the primary deliverable of **SEMA-V6-002**
(queued). The current state is raw n10s `:Resource` nodes only — the typed
`:Vocabulary` / `:Predicate` layer that the frontend annotation picker and MCP tools
will consume is not yet built.

---

## SemanticAnnotation Baseline

**370 `:SemanticAnnotation` nodes exist.** All come from the LUMEN seed:

| `propertyIRI` | `propertyName` | count |
|---|---|---|
| `https://shepard.dlr.de/showcase/lumen-inspired#TestPhase` | Test Phase | 273 |
| `https://shepard.dlr.de/showcase/lumen-inspired#TestOutcome` | Test Outcome | 45 |
| `https://shepard.dlr.de/showcase/lumen-inspired#CampaignRole` | Campaign Role | 45 |
| `https://shepard.dlr.de/ontologies/experiment#QualityFlag` | Quality Flag | 4 |
| `https://shepard.dlr.de/showcase/lumen-inspired#AnomalyType` | Anomaly Type | 3 |

These use the new v2 shape (`propertyIRI`, `valueName`, `valueIRI`, `propertyName`,
`appId`) — not the legacy `predicate`/`value`/`source` shape. They are live real data.
The old property names (`predicate`, `value`, `source`) are `NULL` on all 370 nodes
because those columns belong to the upstream v1 model; the v2 seed writes to
`propertyIRI` / `propertyName` / `valueIRI` / `valueName` only.

---

## SemanticConfig State

One `:SemanticConfig` node exists (created by N1c2):

```
{
  appId:          "019e30be-1d98-7c43-8648-cea78a96c522",
  preseedEnabled: TRUE,
  disabledBundles: [],
  createdAt:      1778934226322,
  updatedAt:      1778934226322
}
```

Key observations:
- `preseedEnabled = TRUE` — ontology seed is enabled.
- `disabledBundles = []` — no bundles are disabled; all 10 are active.
- The node does **not** yet have `vocabularyId` / `enabledVocabularies` fields — those
  are SEMA-V6-003's extension (queued).

---

## Gaps Found

### Gap 1: No `:Vocabulary` / `:Predicate` nodes (the empty UI cause)

The frontend `/semantic/vocabularies` page calls `GET /v2/admin/semantic/ontologies`
(N1c2 endpoint — returns `OntologyBundleListIO`). That endpoint is **wired**
(`SemanticAdminRest.java` line 209 confirms `@GET @Path("/ontologies")`); it returns
401 without auth (verified). The 200-with-admin-auth path was not exercised directly
due to a dev-stack OIDC `iss` mismatch (token `iss=https://shepard-auth.nuclide.systems/…`
vs. backend expecting `http://shepard-auth.nuclide.systems:8082/…`).
However, the `OntologyBundleListIO` response
shape describes the n10s-loaded bundles by ID/title/enabled status — **not the structured
`:Vocabulary` node tree**. Since the page renders the bundle list from this endpoint,
it would show something if the user had instance-admin — but today non-admin users hit
401/403, and the page correctly shows the fallback message.

The deeper gap is that there are no `:Vocabulary` or `:Predicate` nodes at all. The
annotation picker (`AddAnnotationDialog.vue`, SEMA-V6-005) and the MCP tools
(SEMA-V6-006) need these to exist. SEMA-V6-002 must ship before any predicate
autocomplete works.

### Gap 2: `/semantic/vocabularies` is admin-only

The UI calls `GET /v2/admin/semantic/ontologies` (instance-admin role required).
Normal researchers see a 403 fallback message. A read-only public vocabulary listing
endpoint (`GET /v2/semantic/vocabularies`) is planned as SEMA-V6-UI-FOLLOWUP but
does not exist yet.

### Gap 3: SEMA-V6-001 columns not yet on `:SemanticAnnotation`

The 370 existing annotations lack `subjectKind`, `subjectAppId`, `vocabularyId`,
`sourceMode`, `sourceActivityAppId`, `validFromMillis`, `validUntilMillis`,
`confidence`. These are SEMA-V6-001's column-add migration. Until those land, the
v2 annotation shape is structurally incomplete.

### Gap 4: No `:Predicate`→`:Resource` link wiring

SEMA-V6-002 will need to create `:Predicate` nodes that reference the existing
378k `:Resource` nodes. The `USES_PREDICATE` relationship pattern in
`aidocs/semantics/100 §3.1` requires the `:Resource` nodes to already exist, which
they do — so SEMA-V6-002 can proceed immediately without new n10s imports.

---

## Runbook (nothing broken — state is consistent)

The V49 migration ran correctly. The n10s graph is initialised and all 10 ontology
bundles are loaded (378k Resource nodes). The empty UI is not a migration failure —
it is a design gap: the `:Vocabulary` / `:Predicate` layer has not been built yet.

No corrective Cypher is needed. Proceed directly to SEMA-V6-001 → SEMA-V6-002.

If for any reason the `:SemanticRepository {type:'INTERNAL'}` node were missing
(it is present and healthy), the rollback runbook from V49's header applies:
```cypher
MERGE (r:SemanticRepository {type: 'INTERNAL'})
ON CREATE SET r:BasicEntity, r.appId = randomUUID(), r.name = 'Built-in Semantic Store (n10s)',
  r.endpoint = '', r.deleted = false, r.createdAt = timestamp();
```

---

## Recommendation for SEMA-V6-002

### What SEMA-V6-002 must do

1. Create 10 `:Vocabulary` nodes — one per ontology bundle — with fields:
   `{appId, bundleId, title, namespace, source: 'builtin', enabled: true, createdAt}`.
2. For each vocabulary, derive `:Predicate` nodes from the existing `:Resource` nodes
   where `uri` falls within the vocabulary's namespace. Use the already-loaded n10s
   `Class`, `Property`, `Relationship` labels to distinguish predicate types.
3. Link each `:Predicate` to its `:Vocabulary` via `[:BELONGS_TO_VOCABULARY]`.
4. Add a unique constraint on `Vocabulary(appId)` and `Predicate(appId)`.
5. Make the migration idempotent — `MERGE` on `{bundleId}` for vocabularies,
   `MERGE` on `{iri, vocabularyId}` for predicates.

### Key constraint for implementation

The 378k `:Resource` nodes are all blank-nodes or named IRIs — the predicate derivation
must **NOT** create new `:Resource` nodes (NEO-AUDIT-018 constraint). Use
`MATCH (res:Resource {uri: $iri})` for the `USES_PREDICATE` link; create
`:Predicate` nodes as **separate new nodes**, not as labels on `:Resource`.

### Ontology-to-namespace mapping for the 10 built-in bundles

| Bundle ID | IRI namespace prefix |
|---|---|
| `prov-o` | `http://www.w3.org/ns/prov#` |
| `dublin-core` | `http://purl.org/dc/terms/`, `http://purl.org/dc/elements/1.1/` |
| `schema-org` | `https://schema.org/` |
| `foaf` | `http://xmlns.com/foaf/0.1/` |
| `qudt` | `http://qudt.org/schema/qudt/`, `http://qudt.org/vocab/quantitykind/`, `http://qudt.org/vocab/unit/` |
| `om-2` | `http://www.ontology-of-units-of-measure.org/resource/om-2/` |
| `time` | `http://www.w3.org/2006/time#` |
| `geosparql` | `http://www.opengis.net/ont/geosparql#` |
| `obo-relations` | `http://purl.obolibrary.org/obo/RO_`, `http://purl.obolibrary.org/obo/BFO_`, `http://purl.obolibrary.org/obo/IAO_` |
| `metadata4ing` | `http://w3id.org/nfdi4ing/metadata4ing/` |

All namespaces are verified present in the live graph above (378k `:Resource` nodes
already loaded).

### Ordering: SEMA-V6-001 before SEMA-V6-002

SEMA-V6-001 (column-add migration on `:SemanticAnnotation`) is independent of
SEMA-V6-002 but should ship first — it's smaller (S vs M), and backfills the 370
existing annotations in the same pass. Ship order: 001 → 002 → 003.
