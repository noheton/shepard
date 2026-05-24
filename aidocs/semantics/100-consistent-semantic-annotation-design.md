---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor + maintainer
supersedes:
  - aidocs/semantics/14-semantic-improvements.md
---

# 100 — Consistent semantic annotation surface (UI-first + MCP CRUD)

**Status.** Design ready for v6 implementation wave.
**Audience.** Contributors + maintainers shipping the cross-cutting capability
that lets every researcher and every AI agent annotate quasi any entity in
shepard with vocabulary-controlled, queryable semantic statements.
**Depends on.** `:SemanticAnnotation` existing entity (V3/V10/V56), n10s
(V49 bootstrap), `:SemanticConfig` (N1c2, V27), PluginManifest SPI
(`aidocs/platform/47 §2`), MCP loopback (`aidocs/platform/30`), AI v6 SSOT
(`aidocs/integrations/97`), PG-multitenant ADR (`aidocs/strategy/105`),
PROV-O typed Activity (PROV1a + NEO-AUDIT-001 forward-fix).
**Predecessor / cross-reference.** Predecessor: `aidocs/semantics/14`
(folded into §3 + §11). Extends, does not supersede: `aidocs/semantics/48`
(internal n10s repo), `aidocs/semantics/65` (admin-configurable ontology
preseed — the existing `:SemanticConfig` singleton is the substrate this
doc extends), `aidocs/semantics/95` (SHACL templates — bigger; this is a
slice on annotation primitives), `aidocs/semantics/94` (m4i integration),
`aidocs/semantics/96` (upper-ontology alignment), `aidocs/data/90`
(SPATIAL-V6 — the per-Collection `MeasurementSchema` shape that informs
§4 vocabulary scoping).
**First consumer.** UI default-mode 3-click annotate affordance on
DataObject + Collection detail pages; MCP CRUD tools for any AI client
that the existing `list_annotations` (`ContentMcpTools.java:140`)
already partially serves.

---

## §0 — Reading guide

Read this on two axes simultaneously: **ease-of-use UI** (a researcher
opens an entity, clicks Annotate, picks a predicate from autocomplete,
picks a value from autocomplete, done — three clicks, zero IRIs typed)
and **AI/MCP CRUD** (Claude or any MCP client lists, searches, creates,
updates, deletes annotations as first-class operations, every write
captured as a typed f(ai)²r `:Activity`). The two surfaces read the
same canonical store. The candidate standing rule that crystallises:
**"Always: semantic annotations are first-class on every entity. UI
affordance + MCP tool + SPARQL surface + SHACL validation all read
from one canonical store."** §17 formalises.

---

## §1 — TL;DR + v6 positioning

shepard v6 ships consistent semantic annotation as a cross-cutting
capability over the `appId` substrate. Three sentences:

1. One canonical primitive — `:SemanticAnnotation` Neo4j node, typed
   subject/predicate/object plus f(ai)²r provenance — backs every UI
   affordance, every MCP tool, every SPARQL query, every SHACL
   validation. No per-payload-kind annotation shape; no free-text bag.
2. The UI default path is **three clicks**: open entity → click
   Annotate → pick predicate (autocomplete from current-scope
   vocabularies) → pick value (autocomplete from vocabulary +
   previously-used values) → Enter. No IRIs typed, no SPARQL learned.
3. The MCP surface is **eleven narrow tools** the agent navigates with
   one `list_vocabularies` to start; every write returns the
   `activity_appid` of the captured `:Activity` so an agent
   self-reports its provenance to the same audit trail a human writes.

The one canonical primitive:

| Column | Shape | Notes |
|---|---|---|
| subject | `(subjectKind, subjectAppId)` | any entity with an `appId` (DataObject, Collection, References, Containers, Activities, Users, ShepardFiles, even Annotations themselves) |
| predicate | `predicateIri` (column `propertyIRI` today) | IRI in a vocabulary registered in `:SemanticConfig` |
| object | one of `objectLiteral` (column `valueName`) OR `objectIri` (column `valueIRI`); plus optional `(numericValue, unitIRI)` QA-1 quantitative shape | typed by `expected_object_type` on the predicate |
| source | `(sourceActor, sourceMode ∈ {🧑,🤖,🤝}, sourceActivityAppId)` | f(ai)²r — every write back-links to its `:Activity` |
| validity | `(validFromMillis, validUntilMillis)` | optional; NULL `validUntil` = currently asserted |
| confidence | `0.0..1.0` | human writes default 1.0; AI writes carry model confidence |

Two MUSTs: **MUST-3-click** (basic-mode UI default; never hide a
control behind advanced mode per R-48); **MUST-MCP-first-class**
(every CRUD surface available as an MCP tool with self-describing
description + JSON-schema input + structured output).

**Differentiator.** Kadi4Mat models annotations as free-text tags;
SciCat exposes per-record property forms with fixed columns;
openBIS attaches typed properties to entity types; Coscine builds on
RDF but its UI requires picking a JSON-Schema-shaped metadata template
upfront. Shepard's commitment: **one shape that's vocabulary-controlled
by default but accepts open-world annotations from authorised users,
queryable from SPARQL AND REST AND MCP, with f(ai)²r-typed provenance
on every write, with a 3-click default UX that beats every European
competitor on the casual path.**

---

## §2 — Reuse-first survey

Per `feedback_reuse_before_reimplement.md`. What we considered, what
we adopt, what we reject, with the decisive reason per row.

| Component | What it gives | What's missing | Decision | Decisive reason |
|---|---|---|---|---|
| W3C Web Annotations Data Model ([w3.org/TR/annotation-model](https://www.w3.org/TR/annotation-model/)) | open standard for portable web annotations; JSON-LD wire shape; `body`/`target`/`motivation` shape | designed for textual/spatial selectors on documents; weak fit for our typed predicate-value model | **adopt at wire-export layer** | round-trip-friendly JSON-LD export per [Annotations Vocabulary §3](https://www.w3.org/TR/annotation-vocab/); consumers don't need to parse Turtle |
| W3C SKOS ([w3.org/TR/skos-reference](https://www.w3.org/TR/skos-reference/)) | controlled-vocabulary modelling; `Concept`, `ConceptScheme`, `broader/narrower/related`, `prefLabel/altLabel` | none for our scope | **adopt for vocabulary-of-predicates + vocabulary-of-values** | the existing n10s preseed already loads SKOS-shaped ontologies (m4i, QUDT, schema.org) |
| n10s neosemantics | RDF view + SPARQL surface over the existing Neo4j; loaded ontologies as `:Resource` nodes | NEO-AUDIT-018 SKOS bloat (174k bare `:Resource` nodes) | **adopt as RDF/SPARQL view** | already in stack per V49 bootstrap; the §14 NEO-018 mitigation is "annotations are typed `:SemanticAnnotation`, not bare `:Resource`" |
| JSON-LD framing ([w3.org/TR/json-ld11-framing](https://www.w3.org/TR/json-ld11-framing/)) | JSON-shaped wire export of RDF with explicit context | none | **adopt** for `GET /v2/annotations/...` responses | consumers (Jupyter, ChatGPT, custom scripts) get plain JSON without parsing Turtle |
| Hypothesis.is API ([h.readthedocs.io](https://h.readthedocs.io/en/latest/api-reference/)) | production-grade web-annotation API; tag-suggestion + permission patterns; user/group scopes | per-document selector model is wrong for our entity-typed subjects | **study, don't adopt whole shape** | their tag-suggestion + group-scope patterns inform §4 vocabulary-precedence + §5 personal-vocabulary modes |
| Wikidata statement model ([wikidata.org/wiki/Wikidata:Statements](https://www.wikidata.org/wiki/Wikidata:Statements)) | best-in-class autocomplete-with-controlled-vocabulary UX; references-and-qualifiers on every statement | qualifiers / references are out of v0 scope (folded into §11 v2) | **study for UX**; **adopt cardinality-per-property pattern** | the keyboard ergonomics + property-picker shape are the production reference for the §5 picker |
| Annotorious ([annotorious.com](https://annotorious.com/)) | spatial annotations on images / 3D viewports | spatial only | **defer** | lands as a SPATIAL-V6 follow-on per `aidocs/data/90`; this design covers text/structured annotations only |
| Zotero tag system | casual annotation UX shape researchers know | flat tag space; no predicates | **study, reject the shape** | the discoverability angle informs §5 "what can I annotate this with?" affordance |
| Notion property types ([developers.notion.com](https://developers.notion.com/reference/property-object)) | database-row tag affordance; every researcher has seen it | per-database schema; no cross-database vocabulary | **study, reject the shape** | the typed-value affordance (date/number/select/multi-select) informs §3 `expected_object_type` |
| PROV-O ([w3.org/TR/prov-o](https://www.w3.org/TR/prov-o/)) | activity capture; `wasAssociatedWith`/`used`/`generated` predicates | partial in shepard today per NEO-AUDIT-001 | **adopt + reinforce** | every annotation write lands as a typed `:Activity` per §10 |
| pgvector ([github.com/pgvector/pgvector](https://github.com/pgvector/pgvector)) | embedding storage + HNSW kNN | not the primary store | **adopt for embedding-similarity** | autocomplete + suggestion ranking on top of vocabulary-loaded predicates per §7 |
| MCP spec ([modelcontextprotocol.io](https://modelcontextprotocol.io/specification)) | tool-description + JSON-schema input + structured output | none for our scope | **adopt** | the §6 tool surface follows the verb-first capability-not-implementation pattern |
| Existing `:SemanticAnnotation` entity | `propertyName/propertyIRI/valueName/valueIRI/numericValue/unitIRI/propertyRepository/valueRepository`; QA-1 quantitative shape | no subject-kind column; no f(ai)²r mode; no temporal validity; no vocabulary scope | **evolve in-place** per §3 + §11 | 56 existing rows on production today; replacing-not-evolving would break legacy paths |
| Coscine MAML / Kadi4Mat extras | per-record metadata-template UX | template upfront, not entity-by-entity casual | **reject as default UX**; **revisit at SHACL `MeasurementSchema` layer per `aidocs/data/90 §3`** | the SHACL strict-mode shape is the long-tail rigour path; basic UX stays casual |
| openBIS object-type properties | typed-properties-per-object-type | requires schema-first, not casual | **reject as default UX** | same rationale as Coscine; SHACL is our equivalent for strict-mode |
| SciCat metadata facets | free-form metadata block with facet aggregation on search | weak vocabulary control; no IRIs | **reject** | doesn't meet FAIR-I; precedent for casual but no answer for the RDM-side rigour |

**What we build.** Five thin extensions over the existing entity:
six new columns (`subjectKind`, `subjectAppId`, `vocabularyId`,
`sourceMode`, `sourceActivityAppId`, `validFromMillis`,
`validUntilMillis`, `confidence`), one `/v2/annotations/*` polymorphic
REST surface, three Vuetify components (Chip + Dialog + SuggestionPanel),
ten MCP tools (siblings to the existing `list_annotations`), one
`SemanticVocabularyProvider` SPI for plugin-contributed vocabularies,
one `:VocabularyConfig` extension to the existing `:SemanticConfig`.
**What we adopt.** Everything else.

---

## §3 — The canonical annotation primitive

One shape every annotation conforms to. We **evolve the existing
`:SemanticAnnotation` entity in place** rather than introduce a sibling
primitive — the 56 production rows + the existing REST surface + the
already-shipped MCP `list_annotations` all keep working.

### 3.1 Neo4j storage shape

```cypher
// the existing label, extended with new columns (V## migration; idempotent)
CREATE (a:SemanticAnnotation {
  appId:                <uuidv7>,                        // existing
  // === existing columns (UNCHANGED in v0; field-name renames deferred per feedback_appid_to_shepardid.md) ===
  propertyName:         'material',                      // human-readable predicate label snapshot
  propertyIRI:          'http://shepard.dlr.de/v/material',  // controlled-vocabulary IRI (the predicate)
  valueName:            'CF/LMPAEK',                     // object label snapshot OR literal text
  valueIRI:             null,                            // OR controlled-vocabulary IRI for entity-valued objects (one of literal/IRI is non-NULL)
  numericValue:         null,                            // QA-1 quantitative path
  unitIRI:              null,                            // QA-1 quantitative unit
  // === NEW v0 columns (this design) ===
  subjectKind:          'DataObject',                    // entity kind being annotated
  subjectAppId:         <uuidv7>,                        // FK-by-convention to the subject's appId
  vocabularyId:         'shepard-domain-v1',             // which vocabulary the predicate came from
  sourceMode:           'human' | 'ai' | 'collaborative', // f(ai)²r mode 🧑/🤖/🤝
  sourceActivityAppId:  <uuidv7>,                        // back-pointer to the :Activity capturing the create/update
  confidence:           1.0,                             // 0..1; human writes default 1.0; AI writes carry model confidence
  validFromMillis:      <ms>,                            // temporal validity
  validUntilMillis:     null                             // NULL = currently asserted
})
CREATE (a)-[:ANNOTATES]->(subject)              // typed edge to the subject (any HasAppId entity)
CREATE (a)-[:USES_PREDICATE]->(p:Resource {uri: $predicateIri})  // existing n10s-loaded :Resource node — see §14 NEO-018 mitigation
// existing :PROPERTY_REPOSITORY / :VALUE_REPOSITORY relationships stay; deprecated when vocabularyId column is fully populated
```

Invariants (substrate-level CHECK + service-level pre-condition):

  - **(`objectLiteral` is non-NULL) XOR (`objectIri` is non-NULL)** —
    every annotation says either "the value is this text" or "the
    value is this IRI-identified thing". `numericValue + unitIRI`
    are additive and orthogonal (QA-1 quantitative path).
  - **`sourceMode` ∈ {`human`, `ai`, `collaborative`}** — f(ai)²r
    capture per `feedback_ai_human_collab_provenance.md`.
  - **`sourceActivityAppId` non-NULL on every write** — every
    annotation knows the `:Activity` that captured it (the EU AI Act
    Art-50 transparency hook).
  - **`subjectAppId` resolves to a `HasAppId` node** — service-level
    check; not a Neo4j FK (n10s plays loosely with FKs); orphan
    annotations get swept by the SM1 stale-reference scheduler.
  - **Temporal overlap policy** — two annotations with the same
    `(subjectAppId, predicateIri)` and overlapping
    `[validFrom, validUntil)` windows is **the explicit update
    pattern**: the latest write closes the prior window
    (`validUntilMillis ← now`) and opens a new one. Out-of-band
    correction is `update_annotation` (§6); silent override is
    refused.

### 3.2 JSON wire shape (JSON-LD framed)

```json
{
  "@context": "https://shepard.dlr.de/v2/annotations/context.jsonld",
  "appId": "019e83a4-...",
  "subject": { "kind": "DataObject", "appId": "019e30b0-..." },
  "predicate": {
    "iri": "http://shepard.dlr.de/v/material",
    "label": "material",
    "vocabulary": "shepard-domain-v1"
  },
  "object": { "literal": "CF/LMPAEK" },
  "quantitative": null,
  "source": {
    "actor": "shepard.actor.fkrebs",
    "mode": "human",
    "activityAppId": "019e83a5-..."
  },
  "validity": { "from": "2026-05-24T11:32:00Z", "until": null },
  "confidence": 1.0
}
```

For quantitative annotations the `"object"` block carries the literal
form (`"12.5"`) and `"quantitative"` carries
`{"value": 12.5, "unitIri": "http://qudt.org/vocab/unit/G_RMS"}`.

The wire shape is JSON-LD-framed per [json-ld11-framing](https://www.w3.org/TR/json-ld11-framing/)
so the same payload round-trips into RDF for consumers that want it
and stays parse-as-plain-JSON for the rest.

### 3.3 Turtle / RDF export

```turtle
<shepard:DataObject/019e30b0-...> <http://shepard.dlr.de/v/material> "CF/LMPAEK" .
<shepard:Annotation/019e83a4-...> a oa:Annotation ;
    oa:hasTarget <shepard:DataObject/019e30b0-...> ;
    oa:hasBody [ rdf:value "CF/LMPAEK" ; sh:path <http://shepard.dlr.de/v/material> ] ;
    prov:wasGeneratedBy <shepard:Activity/019e83a5-...> ;
    prov:wasAttributedTo <shepard:User/fkrebs> .
```

The "flat triple" form (line 1) is what consumers want for SPARQL
joins; the OA-shaped form (lines 2-5) is for full Web Annotations
Data Model export per
[w3.org/TR/annotation-model §3](https://www.w3.org/TR/annotation-model/#bodies-and-targets).
Both are produced by the same renderer at `/v2/annotations/{appId}.ttl`
content-negotiated.

### 3.4 The legacy `attributes` map is one of these

The `attributes: Map<String,String>` field on `:AbstractDataObject` +
`:Collection` is **structurally an under-typed instance of this
primitive**. Every `attributes||<key>` property today is a (subject,
predicate, value) triple — the same shape as `:SemanticAnnotation`,
just missing the typing (`expected_object_type`), the vocabulary
control (`vocabularyId`), the provenance back-pointer
(`sourceActivityAppId`), the validity window, and the queryability via
SPARQL. The 90+ keys NEO-AUDIT-005 catalogued (`material`, `bench`,
`propellant`, `v16_pass1`, `source_*`, the brief LIC1-pre-shipment
`license` collision) are all annotations in everything but name.

This is the structural reason for the §11 migration. It also has a
basic-mode UX implication called out explicitly in §5.0 below: there
is **one** annotation primitive, **one** UI button, **one** mental
model — the legacy "Add attribute" affordance + the legacy
"Add annotation" affordance collapse into the same dialog from v0.

### 3.5 "Everything is annotatable"

The subject of an annotation is any entity with an `appId`. The list
today: `DataObject`, `Collection`, `BasicReference` (all subclasses),
`*Container` (every kind), `ShepardFile`, `StructuredData`, `Timeseries`
(once TS-IDc lands its `appId`), `LabJournalEntry`, `Activity`,
`User`, `SemanticAnnotation` itself (annotations-on-annotations is a
real use case for AI verification: "this annotation flagged by
reviewer-AI as low-confidence"), `Snapshot`. The §15 decisions log
defends "no per-kind annotation entities" — the
`AnnotatableTimeseries` bridge node introduced for legacy reasons
(per `aidocs/semantics/14 §1.1`) gets folded by §11 Phase 3 when
`:Timeseries.appId` is non-NULL universally; the same applies to
`AnnotatableFile` / `AnnotatableStructuredData` etc. that the prior
design proposed but never built.

---

## §4 — Vocabularies + predicate model

Per `feedback_ontology_first.md`: a predicate exists in a vocabulary;
a vocabulary exists in (or imports from) an ontology; new vocabularies
are admin-managed.

### 4.1 Vocabulary entity (Neo4j)

```cypher
CREATE (v:Vocabulary {
  appId:           <uuidv7>,
  iri:             'https://shepard.dlr.de/v/mffd-domain',
  label:           'MFFD domain vocabulary',
  description:     'AFP/welding process terms specific to the MFFD upper-fuselage demonstrator.',
  owner_kind:      'instance' | 'collection' | 'user',
  owner_appid:     <uuidv7>,                  // NULL for instance-scope
  governance:      'curated' | 'open',        // curated = admin reviews additions; open = power-user can mint
  precedence:      10,                         // higher = preferred in autocomplete
  enabled:         true
})
```

### 4.2 Predicate entity (Neo4j)

```cypher
CREATE (p:Predicate {
  appId:                <uuidv7>,
  iri:                  'http://shepard.dlr.de/v/material',
  label:                'material',
  description:          'Engineering material identifier for a sample / part / assembly.',
  expected_object_type: 'literal' | 'iri' | 'quantitative',
  expected_object_class_iri: 'http://www.matportal.org/onto/Material',  // for 'iri'-typed objects, the SKOS scheme to draw from
  default_unit_iri:     'http://qudt.org/vocab/unit/G_RMS',             // for 'quantitative'-typed objects (optional)
  cardinality:          'single' | 'multi'    // SHACL sh:maxCount = 1 or unbounded
})
CREATE (p)-[:IN_VOCABULARY]->(v)
```

The expected_object_class_iri lets the §5 value picker filter
autocomplete to "concepts in this SKOS scheme" instead of "all values
ever seen for this predicate".

### 4.3 Vocabulary precedence

Three governance scopes, precedence highest-wins:

  - **Instance vocabularies** (admin-curated): bootstrap-shipped
    (m4i, CHAMEO, EMMO sub-modules, schema.org/Dublin Core/PROV-O/QUDT,
    GeoSPARQL, OBO RO, and the `shepard:` core vocab) — already
    loaded via N1b/ONT1a/ONT1b per the `:SemanticConfig` substrate.
    Admin can disable any non-required bundle (per N1c2).
  - **Collection vocabularies**: a Collection can register its own
    `MeasurementSchema` SHACL shape (per `aidocs/data/90 §3`) which
    declares the predicates valid inside that Collection. Per-Collection
    annotation autocomplete prioritises this scope.
  - **Personal vocabularies**: a researcher can mint local predicates
    (`my_observation_quality`) for their own use; surfaces in their
    own UI; doesn't pollute the instance picker for other users.
    Sharing happens via export, not auto-publish.

### 4.4 Bootstrap vocabulary set

Ships out-of-the-box, instance-scope, admin-curated (the same list
the `:SemanticConfig` preseed already handles per N1b/ONT1a/ONT1b):

| Vocabulary | IRI | Use |
|---|---|---|
| `shepard-core` | `https://shepard.dlr.de/v/core/` | identity, lifecycle, lineage predicates the platform itself emits |
| schema.org | `https://schema.org/` | broad-web vocabulary; `creator`/`license`/`keywords`/`temporalCoverage` |
| Dublin Core Terms | `http://purl.org/dc/terms/` | FAIR metadata baseline |
| PROV-O | `http://www.w3.org/ns/prov#` | activity/agent/entity predicates |
| QUDT | `http://qudt.org/vocab/unit/` | units of measurement |
| W3C Time | `http://www.w3.org/2006/time#` | temporal predicates |
| GeoSPARQL | `http://www.opengis.net/ont/geosparql#` | spatial predicates |
| FOAF | `http://xmlns.com/foaf/0.1/` | persons/organisations |
| m4i v1.4.0 | `http://w3id.org/nfdi4ing/metadata4ing/` | engineering-research process modelling per `aidocs/semantics/94` |
| OBO RO | `http://purl.obolibrary.org/obo/ro.owl` | OBO relations |

`MFFD-domain`, `CHAMEO`, `EMMO`, `IOF-Core`, `BFO`, `IAO`, `AAS submodels`,
`CCSDS mission predicates` ship as opt-in extensions (per
N1c2's per-bundle enable/disable). The §15 decisions log defends
"open-world default with strict-mode opt-in".

### 4.5 Vocabulary governance — `:SemanticConfig` extension

Per `feedback_admin_runbooks_pattern.md` and CLAUDE.md "surface
operator knobs": the existing `:SemanticConfig` singleton (V27, N1c2)
gains four runtime-mutable fields:

  - `personalVocabulariesEnabled: bool` (default `false`)
  - `collectionVocabulariesEnabled: bool` (default `true`)
  - `strictModeDefault: 'open' | 'strict'` (default `open`)
  - `vocabularyPrecedenceOrder: ['instance', 'collection', 'user']`
    (default; admin can swap)

`POST /v2/admin/semantic/ontologies` (vocabulary upload per N1c2)
already shipped; this design extends with:

  - `POST /v2/admin/semantic/vocabularies` — register a Vocabulary
    (one-to-many with already-loaded ontologies; lets an admin scope
    "the FOAF predicates we actually want surfaced" without
    re-uploading FOAF)
  - `PATCH /v2/admin/semantic/vocabularies/{appId}` — flip
    `enabled`/`precedence`/`governance`
  - `DELETE /v2/admin/semantic/vocabularies/{appId}` — refuse if
    `required: true` (per N1c2)

CLI parity per `feedback_admin_runbooks_pattern.md`:

```
shepard-admin semantic vocabularies {list,add,enable,disable,set-precedence,remove}
shepard-admin semantic vocabularies upload <ttl-file> --label=... --scope=instance|collection|user
```

A vocabulary upload mutating `:SemanticConfig` lands in `:Activity`
via the existing `ProvenanceCaptureFilter` (PROV1a — admin endpoints
captured automatically); no new wiring.

---

## §5 — The UI affordance (ease-of-use focus)

### 5.0 One concept, one button — collapse "Add attribute" into "Annotate"

The legacy UI has two buttons for what §3.4 named as the same
underlying concept: **"Add attribute"** (the free-text key-value
affordance writing to `:DataObject.attributes||*`) and
**"Add annotation"** (the existing IRI-typed `AddAnnotationDialog.vue`
writing to `:Annotation`). Researchers experience this as **two
near-identical dialogs that pick different storage paths for the
same act of describing a thing**. The collapse is non-optional:
**from v0 there is one button — "Annotate" — that opens the §5.1
three-click dialog**. The `:SemanticAnnotation` substrate from §3
absorbs both legacy paths.

The shape:

- **Basic mode**: one "Annotate" button per entity. No second button,
  no escape hatch. The 3-click dialog covers 100% of the legacy
  use cases.
- **Advanced mode**: the same "Annotate" button is the default. A
  secondary "Add legacy raw attribute" menu item exists under an
  advanced disclosure, labelled with a deprecation tag pointing at
  §11 Phase 2/3. Writes via this path land in the legacy
  `:DataObject.attributes||*` map AND in `:SemanticAnnotation` via
  the §11 Phase 2 dual-write — equivalent on the read side, but the
  raw-attribute affordance disappears entirely at Phase 3.
- **Backward chips**: existing `:DataObject.attributes||*` entries
  render as chips alongside `:SemanticAnnotation` chips in v0
  (visually-indistinguishable display + identical edit/delete flow);
  the storage difference is invisible to the user.

This is the structural fix for what the user flagged as "two
buttons, one concept" — and it ships in v0 (SEMA-V6-017), not
deferred to the §11 substrate migration. The substrate migration
follows underneath; the UX collapse leads.

The existing `AddAnnotationDialog.vue` (320 lines, IRI-typed
autocomplete with `SemanticRepository` picker per side) is the
predecessor; this design simplifies the default and demotes the
picker to advanced mode.

### 5.1 The three-click default flow (basic mode)

The flow a casual researcher walks through, with no IRI typed:

1. **Click "Annotate"** on any entity detail page (button is always
   visible; advanced mode adds the secondary `Bulk-annotate` and
   `Suggest from AI` buttons next to it per R-48 superset rule).
2. **Type in the predicate field** — autocomplete fires after 2
   chars (300 ms debounce). The picker shows results from
   current-scope vocabularies in precedence order, each row carrying
   `{label, vocabulary-chip, description-tooltip, expected_object_type-icon}`.
   The user picks with `↓` + `Enter`.
3. **Type in the value field** — autocomplete fires:
   - If `expected_object_type = 'iri'` → search SKOS concepts in
     `expected_object_class_iri` scope (via n10s SPARQL).
   - If `expected_object_type = 'literal'` → search previously-used
     values for this predicate across this Collection (Postgres
     `pg_trgm` index per §7).
   - If `expected_object_type = 'quantitative'` → render a numeric
     input + unit-picker that defaults to `default_unit_iri`.
   User picks (or types-and-Enter for a new literal).
4. **Press Enter** → annotation lands; modal closes; chip appears
   below the entity name with optimistic-update.

Total: 3 clicks (Annotate, predicate, value) + 1 Enter. Mobile-friendly.
Keyboard-only navigable.

### 5.2 Inline chip editing

Annotations render as `<v-chip>` rows under the entity name. Click
a chip → opens edit dialog (same shape as create). Click X → confirm
dialog → soft-delete (the annotation row stays for audit; `validUntil`
gets set to `now`; the chip disappears from the UI per `validUntil
IS NOT NULL` filter).

Per `feedback_basic_advanced_superset.md` the chip is visible in
basic AND advanced mode; advanced mode adds a "Provenance" footer
on hover showing the `(actor, mode, activity, when)` tuple.

### 5.3 Bulk annotation

`<v-data-table>` row-select + "Annotate selected" → opens the same
dialog. On submit, one annotation per subject is created (each gets
its own `:Activity` row; one batch-Activity per bulk operation
captures the group). The §9 performance number: 1000 entities × 1
annotation each in ≤ 5 s end-to-end on Tier 0 hardware.

### 5.4 Per-Collection annotation gallery

Collection landing carries a "Common annotations in this collection"
panel: top-10 most-used predicates (count + sparkline) with
click-to-filter ("show me DataObjects where `material = CF/LMPAEK`"
→ list view filtered). Per Hypothesis.is API pattern — the
discoverability is the affordance.

### 5.5 Vocabulary picker (advanced mode)

When typing a predicate in advanced mode, the picker shows
vocabulary-chip filters above the result list ("from m4i / from
shepard-core / from your personal vocab / from this collection's
schema"). The default shows all-vocabularies in precedence order.

### 5.6 AI suggestion panel (advanced mode default; basic mode
opt-in per `:SemanticConfig.suggestionsInBasicMode`)

A "Suggest annotations" button on the entity detail surfaces a panel:

> *Looking at this DataObject (TR-006), the similar DataObjects
> TR-002, TR-003, TR-005 are annotated with:*
> - `propellant = LOX/LH2` (used on 3 of 3 similar) — **Apply?**
> - `test_bench = P8.4` (used on 2 of 3) — **Apply?**
> - `propellant_batch = LOX-2026-05-14` (used on 1 of 3) — **Apply?**

Click "Apply" → annotation lands in `sourceMode='collaborative'` 🤝
with `sourceActivityAppId` pointing at BOTH the suggesting AI
`:Activity` and the human-accept `:Activity`. The suggestion ranking
uses pgvector kNN per `aidocs/integrations/97 §8` flow.

### 5.7 Keyboard ergonomics

  - `Ctrl-A` (anywhere on entity page) → opens annotation dialog
  - `↑`/`↓` + `Enter` → navigate autocomplete; pick
  - `Tab` → predicate → value
  - `Esc` → dismiss
  - `Ctrl-Enter` → save (so an inline edit doesn't require mouse)

### 5.8 Discoverability

A "What can I annotate this with?" `<v-icon>` next to the Annotate
button opens a sheet showing:
  - The vocabularies in scope for this entity (chips with
    descriptions);
  - The 10 most-common predicates used on similar entities
    (Collection-scope + system-wide);
  - A 60-second guided tour for first-time users (one-shot, stored
    in localStorage).

### 5.9 Vuetify component shapes

Sketch only (no code):

  - `<AnnotationChip :annotation />` — visible chip; click → edit;
    X → confirm-delete; hover → provenance tooltip in advanced mode
  - `<AnnotationDialog :subject :mode="'create'|'edit'" />` — the
    3-click form; predicate-picker + value-picker + optional advanced
    panel (validity dates + confidence + force-vocabulary-scope)
  - `<AnnotationSuggestionPanel :subject />` — the AI-driven
    suggestion list (per 5.6); each row a "Apply" button

---

## §6 — The MCP tool surface (AI usage focus)

Load-bearing for axis #2. The existing `list_annotations` tool
(`ContentMcpTools.java:140`) is unchanged in signature; the ten new
tools are siblings. Eleven tools total. Yes that's a lot — the §14
honest concerns line acknowledges, defends with Wikidata-style
ergonomics (the agent reads the description and picks the right tool;
broader tools force JSON-schema dispatch the LLM is worse at).

Each tool registered via `@Tool(name=..., description=...)` per
`io.quarkiverse.mcp.server` (the same pattern as the existing
`ContentMcpTools`). Auth via `McpAuthFilter` per
`aidocs/platform/30 §6` (Bearer JWT or shepard API key) — no new
credentials; permission post-filter on every tool.

| # | Tool | Purpose | Returns |
|---|---|---|---|
| 1 | `list_vocabularies` | enumerate active vocabularies (scope + precedence + governance) | `[{appId, iri, label, scope, precedence, predicate_count, governance}]` |
| 2 | `search_predicates(query, vocabulary?, expected_object_type?)` | autocomplete-equivalent for the agent | `[{iri, label, vocabulary, expected_object_type, description}]` |
| 3 | `search_values(predicate_iri, query)` | predicate-aware value autocomplete (SKOS-scope or trigram) | `[{value, label, iri?, used_count}]` |
| 4 | `list_annotations(subject_kind, subject_appid, predicate_iri?)` | **existing tool** — unchanged signature; returns now include `vocabularyId`, `sourceMode`, `confidence`, `validity` | `[<annotation-wire-shape>]` |
| 5 | `get_annotation(annotation_appid)` | full detail of one annotation | `<annotation-wire-shape>` |
| 6 | `create_annotation(subject_kind, subject_appid, predicate_iri, object_literal?, object_iri?, numeric_value?, unit_iri?, confidence?, valid_from?, valid_until?)` | the create path | `{annotation_appid, activity_appid}` |
| 7 | `update_annotation(annotation_appid, object_literal?, object_iri?, valid_until?, confidence?)` | the update path (closes previous validity window, opens new one if value changes) | `{annotation_appid, activity_appid}` |
| 8 | `delete_annotation(annotation_appid, reason?)` | soft-delete; `validUntil ← now`; `reason` captured in `:Activity.summary` | `{activity_appid}` |
| 9 | `suggest_annotations(subject_kind, subject_appid, max?)` | the AI-suggestion side; uses pgvector + simple heuristics; agent decides whether to apply | `[{predicate_iri, suggested_value, confidence, reasoning}]` |
| 10 | `find_annotated(predicate_iri, value_pattern?)` | reverse lookup — what's annotated with X? | `[{subject_kind, subject_appid, value, similarity?}]` |
| 11 | `find_similar_annotated(subject_kind, subject_appid, k?)` | semantic similarity over annotation sets | `[{subject_appid, similarity, shared_predicates: [...]}]` |

Tool descriptions follow the `ContentMcpTools` pattern — verb-first,
capability-not-implementation, declared limits + chaining hints
("combine with `get_data_object` for full content"). EU AI Act
Art-50 transparency hook: every write returns `activity_appid` so
the calling agent (and any downstream consumer) can resolve the
provenance via `GET /v2/provenance/entity/<activity_appid>`.

The MCP tool authorisation reads the request principal off
`McpContextBridge` per `McpAuthFilter` and applies the same
permission check the REST surface does:
  - **read** tools (1, 2, 3, 4, 5, 10, 11) — require Reader on the
    subject's Collection (annotations inherit per R-06);
  - **write** tools (6, 7) — require Writer;
  - **delete** (8) — require Manager OR original author (per
    `confidence`-loose annotation hygiene);
  - **suggest** (9) — Reader-equivalent; reading similar entities to
    propose annotations doesn't reveal new content.

---

## §7 — Substrate decision (cross-link to PG-multitenant ADR)

Three substrates touch the annotation surface; per
`feedback_db_review_all_stores.md` this design covers all three
explicitly.

### 7.1 Neo4j — primary store

The `:SemanticAnnotation` + `:Vocabulary` + `:Predicate` + `:ANNOTATES`
+ `:USES_PREDICATE` + `:IN_VOCABULARY` edges live in the existing
Neo4j. All writes land here first (single source of truth per
`feedback_shacl_single_source_of_truth.md`). n10s renders the RDF
view + SPARQL surface "for free" — no new SPARQL endpoint, no
duplicated triple store. The `:USES_PREDICATE` edge points at an
existing n10s-loaded `:Resource` node (the predicate IRI was loaded
when the vocabulary was loaded per V49) — **no new `:Resource`
nodes are created per annotation write**; this is the NEO-AUDIT-018
mitigation called out in §14.

### 7.2 `shepard_ai` schema — embedding + trigram indexes

Per `aidocs/strategy/105 §4` the four reserved schemas are
`shepard_ts`/`shepard_spatial`/`shepard_ai`/`shepard_tables`. **This
design opens no fifth schema.** The indexes the annotation surface
needs are folded into `shepard_ai`:

```sql
-- additive to AI v6 migrations (`plugins/ai/src/main/resources/db/ai/migration/`)
-- V1.0.3__predicate_embeddings.sql
CREATE TABLE shepard_ai.predicate_embeddings (
  predicate_iri   TEXT PRIMARY KEY,
  embedding       VECTOR(768) NOT NULL,
  embedded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  embedded_text   TEXT NOT NULL                       -- "label: description" (debug)
);
CREATE INDEX predicate_embeddings_hnsw ON shepard_ai.predicate_embeddings
  USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=64);

-- V1.0.4__annotation_value_index.sql
CREATE TABLE shepard_ai.annotation_value_index (
  predicate_iri   TEXT NOT NULL,
  value_literal   TEXT NOT NULL,
  value_iri       TEXT,
  vocabulary_id   TEXT,
  used_count      INT NOT NULL DEFAULT 1,
  first_seen      TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (predicate_iri, value_literal, COALESCE(value_iri,''))
);
CREATE INDEX annotation_value_trgm ON shepard_ai.annotation_value_index
  USING gin (value_literal gin_trgm_ops);             -- pg_trgm prefix/fuzzy autocomplete
CREATE INDEX annotation_value_predicate ON shepard_ai.annotation_value_index (predicate_iri, used_count DESC);
```

The autocomplete query path: `pg_trgm` prefix-match for "values
typed so far" + pgvector kNN-on-embedding for "semantically similar
predicates the user might want". Hybrid retrieval is per
[Crunchy Data pgvector-FTS write-up](https://www.crunchydata.com/blog/hybrid-search-with-postgres-and-pgvector).

The schema name (`shepard_ai`) reads as "AI-only" today; per
`aidocs/integrations/97 §6` it absorbs the semantic-annotation
indexes too. The §15 decisions log records "rename to
`shepard_semantic_ai` if/when chat lands and the schema name
genuinely misleads".

### 7.3 No new substrate

Per synthesis §3 AP-X9 (sibling-substrate split where a schema
split would suffice) — adding a fifth schema OR a sidecar
vector/index store would violate the substrate-discipline
commitment. The two new tables ride the collapsed PG; the embedding
schema column-types match the AI v6 `embeddings_768` shape per
`aidocs/integrations/97 §6.4`.

---

## §8 — Vocabulary governance + admin surface

Already covered in §4.5 + N1c2. The shape:

  - The existing `:SemanticConfig` singleton (V27, N1c2) gains four
    fields per §4.5;
  - New REST under `/v2/admin/semantic/vocabularies/*` per §4.5;
  - CLI parity under `shepard-admin semantic vocabularies` per
    §4.5;
  - Per-vocabulary mutation lands in `:Activity` via
    `ProvenanceCaptureFilter` (no new wiring);
  - The existing per-ontology upload (`POST /v2/admin/semantic/ontologies`)
    stays unchanged — vocabularies cite already-loaded ontologies
    rather than re-uploading.

The §15 decisions log defends the split: an **ontology** is the
loaded RDF graph; a **vocabulary** is the curated subset of
predicates surfaced in the UI/MCP picker. An admin can have m4i
loaded (ontology) but only surface a 30-predicate "MFFD process
core" subset (vocabulary).

---

## §9 — Performance + scale

Every claim cites a primary source.

### 9.1 Annotation row count at MFFD scale

`(17K DOs × ~5 annotations) + (12K files × 1 if file-level
annotations enabled) + (200 Collections × ~10) = ~99K annotations`.
Neo4j: trivial at this scale (the existing 174K bare `:Resource`
node count per NEO-AUDIT-018 dominates). n10s SPARQL surface:
unchanged page-cache footprint (annotations are typed
`:SemanticAnnotation`, NOT bare `:Resource`).

### 9.2 Predicate vocabulary size

Realistic upper bound 5K predicates across bootstrap vocabularies
(m4i ~1.2K + CHAMEO ~600 + EMMO sub-modules ~800 + ISO AP242 ~400 +
schema.org ~900 + shepard-core ~50 + per-Collection custom ~100 + …).
pgvector at 768d: 5K × 3.3KB ≈ **17 MB raw + ~38 MB HNSW** per
[pgvector index sizing notes](https://github.com/pgvector/pgvector#index-options).

### 9.3 Autocomplete latency targets

  - Predicate picker (P95 < 100 ms): hybrid `pg_trgm` prefix + pgvector
    kNN, per [Crunchy Data hybrid search benchmark](https://www.crunchydata.com/blog/hybrid-search-with-postgres-and-pgvector)
  - Value picker for `expected_object_type='literal'` (P95 < 80 ms):
    `pg_trgm` over `annotation_value_index` per [pg_trgm docs](https://www.postgresql.org/docs/16/pgtrgm.html)
  - Value picker for `expected_object_type='iri'` (P95 < 200 ms):
    n10s SPARQL `SELECT ?c ?l WHERE { ?c skos:inScheme <X> ; skos:prefLabel ?l . FILTER(CONTAINS(?l, $query)) }`
    bounded by the SKOS scheme size (worst case NASA thesaurus ~17K
    concepts per NEO-AUDIT-018)

### 9.4 SPARQL surface

Annotations queryable as flat triples per §3.3. Latency unchanged
from the existing n10s baseline (the audit's NEO-018 caveat is
about SKOS-imported subjects, not about our typed
`:SemanticAnnotation` nodes).

### 9.5 Bulk-annotate throughput

Target: 1000 entities × 1 annotation in ≤ 5 s wall-clock on Tier 0
hardware. Achieved via batch `UNWIND` Cypher (`UNWIND $rows AS r
MATCH (s {appId: r.subjectAppId}) CREATE (a:SemanticAnnotation {...})
CREATE (a)-[:ANNOTATES]->(s)`); one batch `:Activity` row
(`actionKind: 'ANNOTATE_BATCH'`) per operation per
`aidocs/integrations/97 §7.2` batch shape.

### 9.6 MCP tool latency

  - read tools (1, 2, 3, 4, 5, 10, 11): < 200 ms P95 typical
  - `create/update/delete` (6, 7, 8): < 300 ms P95 (one Cypher
    write + one `:Activity` write + one PG index update)
  - `suggest_annotations` (9): up to 2 s P95 (pgvector kNN +
    candidate-rank + result composition); the tool description
    explicitly tells the agent this is the slow one

### 9.7 Storage growth

Linear in `(entities × avg annotations × avg value-string-length)`.
At MFFD shape: ~99K rows × ~500B per row ≈ **50 MB** in Neo4j +
indexes; ~5K predicate embeddings ≈ **55 MB** in pgvector; ~20K
distinct values × 200B in `annotation_value_index` ≈ **4 MB**.
Total annotation-surface footprint at MFFD scale: **~110 MB**.

### 9.8 AI-suggestion cost

Per `aidocs/integrations/97 §10.7` Tier 0 default: one embed per
suggested predicate-value pair; ~10 tokens each; ~$0 with local
TEI; ~$0.001 with Tier 3 OpenAI per [text-embedding-3-small pricing](https://openai.com/api/pricing/).

---

## §10 — Provenance + AI mode capture (f(ai)²r reinforcement)

Per `project_fair2r_integration.md` + `feedback_ai_human_collab_provenance.md`
+ `aidocs/integrations/97 §7`: every annotation write lands as a
typed `:Activity`.

### 10.1 Per-write Activity shape

```cypher
CREATE (a:Activity {
  appId:            $uuidv7,
  actionKind:       'ANNOTATE_CREATE' | 'ANNOTATE_UPDATE' | 'ANNOTATE_DELETE' | 'ANNOTATE_SUGGEST',
  agentUsername:    'shepard.actor.fkrebs' | 'shepard.actor.local-tei' | 'shepard.actor.collaborative',
  agentMode:        'HUMAN' | 'AI' | 'COLLABORATIVE',
  targetKind:       'SemanticAnnotation',
  targetAppId:      $annotationAppId,
  startedAtMillis:  $startMs,
  endedAtMillis:    $endMs,
  summary:          '🧑 created annotation: material = CF/LMPAEK on TR-006'
})
SET a.attrs = {
  subject_kind:    'DataObject',
  subject_appid:   $subjectAppId,
  predicate_iri:   'http://shepard.dlr.de/v/material',
  vocabulary_id:   'shepard-domain-v1',
  object_kind:     'literal' | 'iri' | 'quantitative',
  object_value:    'CF/LMPAEK',
  confidence:      1.0,
  delete_reason:   $reasonOnDelete,
  ai_model_id:     $modelIdOnAi,                       // only when agentMode='AI'
  suggested_by_activity: $suggestActivityAppIdOnAccept // for 🤝 collaborative
}
```

Per NEO-AUDIT-001 the typed edges (`(User)-[:wasAssociatedWith]->(Activity)`,
`(Activity)-[:generated]->(SemanticAnnotation)`,
`(Activity)-[:used]->(predicate-Resource)`) are wired in the same
forward-fix that closes NEO-AUDIT-001 across the platform.

### 10.2 Collaborative mode (🤝)

When a user clicks "Apply" on an AI suggestion (per §5.6):
  1. The suggest call wrote a `:Activity{actionKind:'ANNOTATE_SUGGEST', agentMode:'AI'}`;
  2. The accept call writes a `:Activity{actionKind:'ANNOTATE_CREATE', agentMode:'COLLABORATIVE'}` carrying `suggested_by_activity: $aiActivityAppId`;
  3. The created annotation's `sourceMode='collaborative'`, `sourceActivityAppId=$collaborativeActivityAppId`.

A query "show me all collaborative annotations on TR-004" returns
the trail; `GET /v2/provenance/entity/<annotationAppId>` resolves
both Activities, both Agents, both timestamps, both modes — the EU
AI Act Art-50 transparency obligation satisfied as a substrate
property.

### 10.3 Read path

`GET /v2/provenance/entity/<annotationAppId>` (existing surface;
NEO-AUDIT-001 forward-fix lands the typed edges) returns the
annotation's full provenance trail. The MCP `get_annotation` tool
includes `activity_appid` in its response so the agent can chain
the provenance call.

---

## §11 — Migration from existing free-text annotations

There's no separate `:Annotation` Neo4j label. What the prompt's
predecessor design called "legacy free-text annotation" is actually
**`AbstractDataObject.attributes||*`** — the OGM `@Properties` flatten
that creates one Neo4j property per map key. NEO-AUDIT-005 catalogued
the cost: 90+ distinct `attributes||*` keys on `:DataObject` (one of
which is `attributes||license` coexisting with the typed `license`
field per LIC1; another is `attributes||v16_pass1` appearing 8507
times — a transient ingest marker that should have been
`:Activity{actionKind: 'IMPORT', targetAppId: ...}`).

### 11.1 Three phases

**The UI collapse leads the substrate migration.** Per §5.0, the
basic-mode UI ships the unified "Annotate" button in v0 (SEMA-V6-017),
before any of the substrate phases below land. Researchers get the
unified mental model immediately ("one button, one concept") while the
storage layer catches up underneath through the three phases.

  - **Phase 1** (this design, v0): introduce the evolved
    `:SemanticAnnotation` with new columns + the polymorphic
    `/v2/annotations/*` REST + the MCP tools + the 3-click UI. Existing
    `:SemanticAnnotation` rows (56 on production) get backfilled with
    `subjectKind`/`subjectAppId` from their `:HAS_ANNOTATION` edge
    source label; `sourceMode='human'` for all; `vocabularyId` inferred
    from `propertyRepository`. Existing `AbstractDataObject.attributes`
    bag UNTOUCHED on the substrate side; the legacy "Add attribute"
    button is removed from the basic-mode UI in v0 even though the
    storage is still bifurcated (advanced-mode escape hatch carries
    the legacy raw-attribute write path with a deprecation tag).
  - **Phase 2** (v1): write-time dual-write. When a user adds a
    free-text attribute via the legacy UI/API, a sibling
    `:SemanticAnnotation` with `predicateIri='shepard:legacyAttribute',
    objectLiteral=<value>, valueIRI=NULL, sourceMode='human'` is
    also created. This couples to the **NEO-AUDIT-005 promote-to-Activity
    fix** for import-provenance keys (`source_*`, `v16_pass1`, etc.)
    — those become `:Activity.attrs` rather than annotations.
  - **Phase 3** (v2): legacy `AbstractDataObject.attributes` becomes
    read-only. Existing keys backfilled into either `:Activity` (for
    provenance keys) or `:SemanticAnnotation{predicate=shepard:legacyAttribute}`
    (for domain keys). The `Map<String,String> attributes` field on
    `AbstractDataObject` stays as a getter (so v1 wire shape is
    preserved); writes route to the new substrate.

### 11.2 Backfill helper

Admin runbook `docs/admin/runbooks/migrate-legacy-attributes.md`
(referenced as `ADMIN-RUNBOOKS-LIBRARY` per
`feedback_admin_runbooks_pattern.md`) walks an operator through
mapping legacy keys to controlled-vocabulary predicates via a guided
dialog: per-key, "which vocabulary predicate is this?" → bulk-apply.
Operator-curated; not automatic (LLM-suggestion lands per §5.6 once
v1 ships, but the operator approves).

Honest scope: this is a multi-quarter migration. Phase 1 is this
design; Phases 2 + 3 are signalled in the backlog but not designed
in detail here (separate SSOTs when they land).

### 11.3 Legacy bridge-node sunset

`AnnotatableTimeseries` (the bridge node for per-channel annotations
per `aidocs/semantics/14 §1.1`) plus the never-built
`AnnotatableFile`/`AnnotatableStructuredData`/`AnnotatableSpatial`:
sunset when `:Timeseries.appId` and `:ShepardFile.appId` are
populated universally (TS-IDc + the relevant file-storage migration).
Then any payload is a subject by its `appId` directly — bridge node
not needed.

---

## §12 — Plugin shape

The semantic annotation surface is **core, not plugin** — every
plugin and every entity benefits from one shape; per-plugin
annotation SPI would be punitive (every plugin would re-implement
the same Vue dialog, MCP tools, n10s integration, …). The exception
to the CLAUDE.md "plugin-first" rule is documented per the rule's
own clause: "identity primitives ... the shapes every plugin compiles
against" — annotations on `appId` are a sibling primitive.

### 12.1 What plugins extend — the vocabulary SPI

```java
package de.dlr.shepard.spi.semantic;

public interface SemanticVocabularyProvider {
  /** Returns vocabulary fragments this plugin ships; loaded at startup via ServiceLoader. */
  List<VocabularyFragment> vocabularies();
}

public record VocabularyFragment(
    String vocabularyIri,
    String label,
    String description,
    String turtleClasspath,    // e.g. "/META-INF/vocabularies/spatial-v1.ttl"
    boolean enabledByDefault,  // admin can flip
    Set<String> required       // predicate IRIs the plugin functionally needs
) {}
```

Bootstrap via `ServiceLoader` on the same lifecycle as PayloadKind
(per `aidocs/platform/47 §2.6`). Each plugin's vocabulary surfaces
under its own `vocabulary_id`; precedence inherits from
plugin-disabled lifecycle (disabling the plugin disables its
vocabulary).

### 12.2 Concrete plugin vocabularies

  - `shepard-plugin-spatial`: geo + time + sweep predicates from
    GeoSPARQL + OWL-Time + `shepard:spatial/*` (per `aidocs/data/90`)
  - `shepard-plugin-cad`: ISO AP242 / STEP geometry predicates (per
    `aidocs/data/78`)
  - `shepard-plugin-aas`: AAS submodel predicates (when AAS plugin
    lands)
  - `shepard-plugin-ai`: NO vocabulary — the plugin _uses_ the
    annotation surface but doesn't define new predicates
  - `shepard-plugin-importer`: NO vocabulary — same rationale

### 12.3 Why not per-payload-kind annotation entities

The `AnnotatableTimeseries` precedent (per `aidocs/semantics/14`)
exists because `:Timeseries.appId` was NULL; once
TS-IDc lands the bridge node is redundant. The §15 decisions log
defends "no per-kind annotation entities; the subject is the appId,
the plugin contributes only vocabulary".

---

## §13 — v0 / v1 / v2 milestone breakdown

### v0 (MVP — "3-click + MCP CRUD ships")

**Scope.** Evolved `:SemanticAnnotation` entity + 4 column-add
migrations + polymorphic `/v2/annotations/*` REST surface + 3-click
UI dialog + chip rendering + 11 MCP tools + `:Activity` capture for
all writes + `:Vocabulary`/`:Predicate` Neo4j entities + bootstrap
vocabulary seed (10 instance-scope vocabularies per §4.4) +
`:SemanticConfig` four-field extension + admin REST + CLI parity +
`SemanticVocabularyProvider` SPI scaffold + docs trinity (Phase 1
backfill helper deferred to v1).

**Test obligations** per `feedback_always_write_tests.md`:
  - `SemanticAnnotationEntityIT` — column-add migration round-trip;
    invariant CHECKs honoured
  - `AnnotationV2RestTest` — polymorphic CRUD + permission gates +
    error envelope (RFC 7807)
  - `AnnotationDialog.test.ts` + `AnnotationChip.test.ts` (Vitest) —
    3-click flow + autocomplete debounce + permission gating
  - `AnnotationMcpToolsTest` — 11 tools registered + input validation +
    permission post-filter + `:Activity` capture
  - `VocabularyConfigServiceTest` — `:SemanticConfig` extension +
    precedence resolution
  - `AnnotationPlaywrightE2E` (per `feedback_validate_via_ui.md` +
    `feedback_validate_user_viewport.md`) — annotate-a-DataObject
    at 4K viewport + assert chip + assert provenance footer

**Acceptance.** `make dev` → seed LUMEN → open TR-004 in the UI →
click Annotate → type "propellant" → pick `propellant` from m4i →
type "LOX" → pick `LOX/LH2` → Enter → chip appears within 200 ms.
Switch to Claude desktop with the MCP connector → `search_predicates
"material"` returns `material@shepard-core` + `hasMaterial@m4i` →
`create_annotation subject_kind=DataObject subject_appid=<tr-004>
predicate_iri=... object_literal=CF/LMPAEK` returns
`{annotation_appid, activity_appid}` → `get_annotation
<annotation_appid>` reflects the write → `GET
/v2/provenance/entity/<activity_appid>` returns the f(ai)²r-typed
Activity row. **All on Tier-0 stack with zero external dependencies.**

**Docs trinity** per `feedback_three_audience_docs.md`:
  - `docs/reference/semantic-annotations.md` — full primitive +
    REST + MCP + SPARQL reference
  - `docs/help/annotating-data.md` — task page: "How do I annotate
    a DataObject?"
  - `docs/admin/runbooks/manage-vocabularies.md` — operator: add
    vocabulary + enable/disable + per-Collection scoping
  - in-app `/help` route serves all three per
    `aidocs/ops/49-in-app-user-docs.md`

### v1 ("ergonomic AI suggestions + bulk + per-collection vocab")

AI suggestion panel UI (per §5.6) + `suggest_annotations` MCP tool
fully wired + bulk-annotate UI + per-Collection annotation gallery
+ personal vocabulary minting UI + `:SemanticConfig.personalVocabulariesEnabled`
toggle ON by default + collaborative-mode (🤝) capture polish + the
legacy-attribute Phase 2 dual-write + admin runbook for the
backfill helper.

### v2 ("SHACL strict mode + spatial + multi-subject + temporal UI")

SHACL strict-mode validation (gated on synthesis §3 T1 SHACL
substrate landing per `aidocs/semantics/95`) + spatial-annotation
extension (Annotorious integration tied to SPATIAL-V6 brush views)
+ multi-subject annotations (annotate "TR-001 and TR-003 share this
defect" as one row referring to N subjects via a `:DefectGroup`
intermediary) + temporal-validity UI panel + qualifiers/references
on annotations (per the Wikidata statement model in §2) + Phase 3
of the legacy migration.

Per CLAUDE.md "plugin-first": every milestone respects the
core/plugin split — annotation primitive stays core; vocabulary
fragments flow through the plugin SPI. Tracker obligations land in
the same PR per `feedback_continuous_doc_maintenance.md`:
`aidocs/34` upstream-upgrade ledger row (new substrate columns +
new REST + new MCP tools surface), `aidocs/42` vision currency
(§18), `aidocs/44` matrix flip (📐 designed → 🚧 in-flight → ✓
shipped per milestone), `docs/_data/references.bib` for cited
external standards.

---

## §14 — Honest concerns + open questions

| # | Concern | Mitigation | Backlog |
|---|---|---|---|
| 1 | **Vocabulary curation labour.** Who maintains bootstrap vocabularies + per-Collection vocabs + personal vocabs? The admin burden may be real. | Bootstrap ships maintained by the shepard team (existing N1c2 cadence); per-Collection is the Collection owner's job; personal is the user's. Personal vocabulary minting gated behind `:SemanticConfig.personalVocabulariesEnabled=false` default. | SEMA-V6-014 |
| 2 | **Predicate URI stability.** Once an annotation references `shepard:material`, that IRI must never change semantics; vocabulary versioning. | Vocabulary IRI carries a version segment (e.g. `https://shepard.dlr.de/v/core/v1/material`); a v2 minting is a new IRI; existing annotations keep working; the §15 decisions log defends "never reuse an IRI for a different concept". | folded into §4 |
| 3 | **Permission semantics — who deletes?** Original author + Manager + Collection owner. AI-mode 🤖 annotations: any Writer can delete (the AI isn't a permission-holder; the human who configured the AI is). | Default policy in `:SemanticConfig.annotationDeletePolicy` per A3b; operator can flip to author-only-strict. | SEMA-V6-013 |
| 4 | **Cardinality enforcement.** `shepard:material` is `cardinality:single`. User adds a second `material` value — what happens? | Default v0 policy: **update-in-place** (the new write closes the prior validity window). Operator can flip to **refuse-with-error**. Multi-cardinality (`cardinality:multi`) always appends. Per the §15 decisions row "update-in-place is the casual-user expectation; refuse is the rigorous-user expectation; we default casual". | SEMA-V6-007 |
| 5 | **The n10s bare-Resource gotcha (NEO-AUDIT-018).** Adding annotations must NOT create new bare `:Resource` nodes (174K already from SKOS imports). | The `:USES_PREDICATE` edge points at the **already-loaded** vocabulary `:Resource` (loaded by `OntologySeedService` at startup per V49 + N1b/c). Annotations are typed `:SemanticAnnotation` nodes. **Zero new bare `:Resource` per annotation write.** Validated by an architecture test (`SemanticAnnotationDoesNotCreateBareResourceTest`). | SEMA-V6-010 |
| 6 | **The EAV gotcha (NEO-AUDIT-005).** Adding annotations must NOT recreate the `attributes||*` blow-up. | `:SemanticAnnotation` is a typed node with typed properties (no `Map<String,String>` field anywhere). NEVER add a key to `:DataObject.attributes||*` for annotation purposes — that path is read-only in v1, deprecated in v2. **All new metadata flows through `:SemanticAnnotation`.** Validated by an architecture test (`AttributesMapHasNoNewWritersTest`). | SEMA-V6-011 |
| 7 | **Confidence semantics for human writes.** Default 1.0 is honest? | For a human write the value is "the human asserted this, no calibration". `1.0` is conventional; we could encode "asserted vs calibrated" as a separate field but the §15 decisions row defers — every consumer either trusts the human or doesn't; the field is a debug aid, not a decision driver. The AI path uses model-reported confidence (Tier 0 jina embeddings don't emit per-prediction confidence; the AI's "confidence" for suggestion-acceptance is the kNN similarity score). | acknowledged |
| 8 | **Multi-subject annotations.** "TR-001 and TR-003 share this defect" — first-class shape? | Out of v0 scope; v2 introduces a `:DefectGroup` (or similar) intermediary node referenced by N subjects. v0 + v1 model as N single-subject annotations sharing a `summary` field. Per §15 — "simplicity wins; first-class multi-subject is a niche". | SEMA-V6-FUTURE-MULTI-SUBJECT |
| 9 | **The legacy attribute migration cost.** Phases 2 + 3 are real work. | Signalled in §11; Phase 2 lands in v1; Phase 3 in v2; backfill helper (admin runbook) lands in v1. | SEMA-V6-012 |
| 10 | **MCP tool count (11) is large.** | Wikidata-style ergonomics — the agent reads each tool's description and picks; the descriptions explicitly chain ("combine with `get_data_object` for full content"). The alternative — 3 broad tools — forces the LLM to JSON-schema-dispatch which is empirically worse on tool-selection benchmarks. | acknowledged in §6; documented as a deliberate cut |
| 11 | **Reverse-requirements break / tighten.** This design **tightens R-07** ("Annotations are free-text key-value, with a semantic-promotion path"): semantic annotations are now the **default** path, not the opt-in promotion. The free-text bag becomes the legacy escape hatch. We do not break R-07 — we walk it through the §11 phases. Tightens R-08 (every mutating call generates a `:Activity` row) by ensuring annotation writes carry the full f(ai)²r capture. Compatible with R-30 (no v1 byte-shape changes; legacy `/shepard/api/.../semanticAnnotations` endpoints stay; new shape lands at `/v2/annotations`). | per §11 phasing | folded |
| 12 | **`McpAuthFilter` shape confirmation.** Verified: the filter accepts OIDC JWT OR shepard API key per `aidocs/platform/30 §6`. Annotation MCP tools inherit; no new credentials. | no new work | confirmed |
| 13 | **REST shape — polymorphic vs per-kind?** Picked polymorphic `/v2/annotations/*` with `subject_kind+subject_appid` body params. Defended in §15. | n/a | n/a |
| 14 | **AppId → shepardId rename.** Per `feedback_appid_to_shepardid.md` the rename is deferred to a single coordinated pass; this design preserves the `appId` naming throughout for now. Same applies to `propertyIRI → predicateIri` etc. | no change in v0 | folded into the deferred rename pass |

---

## §15 — Decisions log

| # | Decision | Alternatives | Decisive constraint | Cut |
|---|---|---|---|---|
| D1 | **Substrate**: Neo4j primary + `shepard_ai` for embedding/trigram indexes | pgvector-only; sidecar vector DB | `feedback_shacl_single_source_of_truth.md` + `aidocs/strategy/105 §4` reserves 4 schemas — no 5th | Neo4j + fold into `shepard_ai` |
| D2 | **One canonical primitive** (extended `:SemanticAnnotation`) vs per-domain entities | sibling `:Annotation` label; per-PayloadKind annotation entities | EAV + bridge-node cost; one substrate sweep cheaper than N | evolve in place |
| D3 | **Modal dialog** vs inline-edit-everywhere | inline `<v-text-field>` per chip | mobile + bulk + keyboard ergonomics; modal works everywhere | modal |
| D4 | **Reject free-text when vocabulary expects IRI** vs silent-accept | always-accept | `feedback_referenced_data_infinite_retention.md` rigour posture | reject-with-helpful-error |
| D5 | **Multi-valued cardinality**: per-predicate `cardinality:single|multi` constraint; default single; UI update-in-place on cardinality conflict | always-append; refuse-with-error | casual-user expectation per §14 #4 | single + update-in-place; operator can flip per-predicate |
| D6 | **Personal vocabulary visibility**: private-by-default; export-to-share | auto-public | privacy default per `feedback_referenced_data_infinite_retention.md` philosophy applied to user IP | private |
| D7 | **Vocabulary precedence**: admin > collection > user (highest wins; UI surfaces all three tagged) | admin-only; user-first | admin > collection > user matches stewardship cadence; per `feedback_admin_runbooks_pattern.md` | admin > collection > user |
| D8 | **MCP tool granularity**: 11 narrow tools (Wikidata-style) vs 3 broad tools | 3 broad | empirical: LLMs are better at description-driven dispatch than JSON-schema dispatch per [MCP best-practice](https://modelcontextprotocol.io/specification) + Wikidata UX precedent | 11 narrow |
| D9 | **Evolve existing `:SemanticAnnotation`** vs replace with sibling | sibling `:Annotation` label | 56 production rows + existing REST + existing `list_annotations` MCP tool; replacing breaks all three | evolve in place; rename deferred to the shepardId pass |
| D10 | **Polymorphic REST `/v2/annotations/*`** vs per-subject-kind paths | `/v2/entities/{kind}/{appId}/annotations` per-kind | one endpoint set matches MCP shape; per-kind multiplies surface; per `feedback_ui_api_parity.md` MCP IS a first-class consumer | polymorphic |
| D11 | **Annotation is core, vocabulary is plugin** | annotation as plugin too | every entity benefits — annotation belongs with the identity primitives; per CLAUDE.md "in-tree by necessity" clause | core annotation + plugin vocabulary SPI |
| D12 | **Vocabulary IRI versioning**: include version segment; never reuse IRI for new concept | bare IRI + interpretation evolves | predicate IRI stability is a 10-year promise per FAIR | version segment mandatory |
| D13 | **Schema name `shepard_ai`** vs new `shepard_semantic` | open 5th schema | `aidocs/strategy/105 §4` AP-X9 — no new schema; rename later if it becomes misleading | `shepard_ai`; revisit when chat lands |

---

## §16 — Backlog rows to file

For the user to file in `aidocs/16` under a new section
**`## SEMA-V6-* — consistent semantic annotation surface`**.

```markdown
## SEMA-V6-* — consistent semantic annotation surface

SSOT: [`aidocs/semantics/100-consistent-semantic-annotation-design.md`](semantics/100-consistent-semantic-annotation-design.md).
Cross-cutting capability — UI 3-click + 11 MCP tools + SPARQL surface
+ SHACL strict-mode (v2) all over one canonical primitive.

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| SEMA-V6-001 | Column-add migration on `:SemanticAnnotation` (`subjectKind`, `subjectAppId`, `vocabularyId`, `sourceMode`, `sourceActivityAppId`, `validFromMillis`, `validUntilMillis`, `confidence`) + backfill for the 56 existing rows | S | queued | §3.1; idempotent V##__SemanticAnnotation_v6_columns.cypher |
| SEMA-V6-002 | `:Vocabulary` + `:Predicate` Neo4j entities + OGM + 10-vocab bootstrap seed (per §4.4) | M | queued | §4; cites already-loaded ontologies; no new ontology imports |
| SEMA-V6-003 | `:SemanticConfig` extension (4 new fields) + admin REST extension + CLI parity (`shepard-admin semantic vocabularies …`) | S | queued | §4.5; extends N1c2 |
| SEMA-V6-004 | Polymorphic `/v2/annotations/*` REST surface (list/get/create/update/delete/find) with permission gates, JSON-LD-framed responses, OA-shaped Turtle export | M | queued | §3.2 + §3.3; absorbs the existing per-container annotation paths into one polymorphic shape |
| SEMA-V6-005 | 3-click UI dialog (`<AnnotationDialog>`) + chip rendering (`<AnnotationChip>`) + predicate + value autocomplete (SKOS or pg_trgm depending on `expected_object_type`) + Vitest + Playwright e2e at 4K viewport | M | queued | §5; supersedes the legacy `AddAnnotationDialog.vue` advanced-only flow |
| SEMA-V6-006 | 10 new MCP tools (`list_vocabularies`/`search_predicates`/`search_values`/`get_annotation`/`create_annotation`/`update_annotation`/`delete_annotation`/`suggest_annotations`/`find_annotated`/`find_similar_annotated`); existing `list_annotations` extended to return new fields | M | queued | §6; auth via `McpAuthFilter` |
| SEMA-V6-007 | `:Activity` capture wrapper for every annotation write (CREATE/UPDATE/DELETE/SUGGEST); `agentMode` (🧑/🤖/🤝); `sourceActivityAppId` back-pointer on the annotation; gated on NEO-AUDIT-001 forward-fix landing | S | queued | §10; pairs with PROV-RESOLVER work; cardinality update-in-place handling lives here too |
| SEMA-V6-008 | `shepard_ai.predicate_embeddings` + `shepard_ai.annotation_value_index` tables (Flyway in `plugins/ai/...`) + autocomplete service consuming both | S | queued | §7.2; gated on `aidocs/strategy/105` (PG-COLLAPSE-001) + `AI-V6-002` |
| SEMA-V6-009 | `SemanticVocabularyProvider` SPI + ServiceLoader bootstrap + scaffold under `de.dlr.shepard.spi.semantic` + reference implementation in `shepard-plugin-spatial` (geo + time predicate fragment) | M | queued | §12; first plugin-shipped vocabulary |
| SEMA-V6-010 | Architecture test `SemanticAnnotationDoesNotCreateBareResourceTest` (NEO-AUDIT-018 mitigation: zero new `:Resource` per annotation write) + `AttributesMapHasNoNewWritersTest` (NEO-AUDIT-005 mitigation: no new code adds to `:DataObject.attributes||*`) | S | queued | §14 #5 + #6; CI gate |
| SEMA-V6-011 | Phase 2 legacy-attribute dual-write: `AbstractDataObject` setter creates sibling `:SemanticAnnotation{predicate=shepard:legacyAttribute}`; couples to NEO-005 import-prov-to-Activity promotion | M | queued (v1) | §11.1; multi-substrate; pairs with NEO-005 fix |
| SEMA-V6-012 | Phase 3 legacy-attribute write-disable + bulk backfill + admin runbook `docs/admin/runbooks/migrate-legacy-attributes.md` | L | queued (v2) | §11.2; per `ADMIN-RUNBOOKS-LIBRARY` pattern |
| SEMA-V6-013 | Annotation delete-policy operator knob (`:SemanticConfig.annotationDeletePolicy` ∈ `'author-or-manager'` (default) `\|` `'author-only'` `\|` `'manager-only'`) + admin REST + CLI | XS | queued (v1) | §14 #3; A3b pattern |
| SEMA-V6-014 | Personal vocabulary minting UI + permission gating + `:SemanticConfig.personalVocabulariesEnabled` default `false` → admin opt-in | S | queued (v1) | §14 #1 |
| SEMA-V6-015 | Docs trinity: `docs/reference/semantic-annotations.md` + `docs/help/annotating-data.md` + `docs/admin/runbooks/manage-vocabularies.md` (in-app `/help` discoverable per `aidocs/ops/49`) | M | queued | §13 v0 |
| SEMA-V6-016 | CLAUDE.md standing-rule formalisation ("Always: semantic annotations are first-class on every entity") | XS | queued | §17 |
| SEMA-V6-FUTURE-MULTI-SUBJECT | First-class multi-subject annotations via `:DefectGroup`-style intermediary | L | future (v2+) | §14 #8 |
| SEMA-V6-FUTURE-SHACL-STRICT | SHACL strict-mode validation + qualifiers/references + temporal-validity UI | L | future (v2; gated on SHACL substrate per synthesis §3 T1) | §13 v2; couples to `aidocs/semantics/95` |
| SEMA-V6-FUTURE-SPATIAL-ANNOT | Annotorious integration (spatial annotations on images/3D viewports) tied to SPATIAL-V6 brush views | L | future (v2; pairs with `aidocs/data/90`) | §2 row "Annotorious — defer"; couples to SPATIAL-V6 |
```

**Predecessor rows absorbed / refactored** (cross-reference in the
filing PR rather than mutating in place):
  - `N1c2` admin REST extended (not replaced) — SEMA-V6-003;
  - `ONT1d` ontology picker (whatever the per-aidocs/16 ID is) —
    folded into §5 UI affordance;
  - parts of `TPL4` legacy-attribute dual-write — SEMA-V6-011/012.

---

## §17 — Standing rule (candidate for CLAUDE.md)

> **Always: semantic annotations are first-class on every entity.**
> The UI's 3-click annotate affordance, the MCP CRUD tools, the
> SPARQL surface, and the SHACL validation all read from one
> canonical store (`:SemanticAnnotation`). New entity kinds (in-tree
> or plugin) MUST be annotatable from day one; the SPI seam is the
> `appId` itself. Plugins extend the picker via the
> `SemanticVocabularyProvider` SPI — never via per-kind annotation
> entities. **Why:** cross-cutting queryability is the FAIR-R1
> promise + the AI-substrate enabler; per-kind annotation shapes
> accrete EAV bloat (NEO-AUDIT-005) + bridge-node sprawl
> (`AnnotatableTimeseries` precedent). **How to apply:** any new
> payload kind ships with no annotation-shipping code (it's free via
> the `appId`); any new vocabulary ships via the SPI declaration or
> the admin upload path; the free-text `:AbstractDataObject.attributes`
> bag is a graveyard for legacy keys and a write-disabled escape
> hatch — never the destination for new metadata.

---

## §18 — Vision currency

Per CLAUDE.md "Always: keep `aidocs/42-vision.md` current". The v0
SEMA shipment requires these edits in the same PR:

1. **Move "Semantic annotation surface" from "Where it's going" to
   "What's in the box (today)"** with the line:
   > *"Three-click semantic annotation on any entity — predicate +
   > value autocomplete from vocabulary-controlled ontologies (m4i,
   > schema.org, Dublin Core, PROV-O, QUDT, GeoSPARQL, OBO RO,
   > shepard-core, FOAF, W3C Time, plus per-Collection and
   > admin-uploadable vocabularies). Same shape over REST, MCP, and
   > SPARQL. Every write captured as a typed f(ai)²r `:Activity`
   > with human/AI/collaborative mode."*
2. **Add a bullet under §"f(ai)²r in practice"**:
   > *"Every annotation create/update/delete lands as a typed
   > `:Activity` row with mode (🧑 human / 🤖 AI / 🤝 collaborative),
   > vocabulary, predicate, before+after value, model identifier
   > (when AI), and confidence. Closes EU AI Act Art-50 transparency
   > for AI-touched metadata."*
3. **Update the §"Plugin ecosystem" bullet** to add:
   > *"`SemanticVocabularyProvider` SPI — plugins ship vocabulary
   > fragments that surface in the picker; spatial ships geo+time
   > predicates, cad ships ISO AP242, aas ships submodel predicates."*
4. **Do NOT add a payload-kind row** — annotations are cross-cutting,
   not a payload kind.
5. **Do NOT modify §"Honest gaps"** for annotations — they were
   already partial (R-07); the gap closes when v0 ships.

The `aidocs/44-fork-vs-upstream-feature-matrix.md`: flip the
"Semantic annotations" row from `🚧 in-flight (partial)` to
`📐 designed (v6 plan)` on this design's commit; flip to
`🚧 in-flight (v0)` when SEMA-V6-001 + -002 + -004 are merged; flip
to `✓ shipped (v0)` when the full v0 milestone (SEMA-V6-001
through -010 + -015 + -016) is merged.

The `aidocs/34-upstream-upgrade-path.md`: new row noting "v6 adds
the polymorphic `/v2/annotations/*` REST surface, the 10 new MCP
tools, and the column-add migration on `:SemanticAnnotation`. Admin
upgrading from upstream gets the new surface additively; legacy
`/shepard/api/.../semanticAnnotations` endpoints stay byte-stable
per R-30. New deploy-time properties: none. New admin REST surface:
4 endpoints under `/v2/admin/semantic/vocabularies/*`. The
`shepard_ai` schema gains two tables (`predicate_embeddings`,
`annotation_value_index`); harmless on a fresh install, additive on
an upgrading install (Flyway-tracked)."

---

## §19 — See also

**Internal**:
  - `aidocs/43-reverse-engineered-requirements.md` §2 (R-07 free-text +
    semantic-promotion path — tightened, not broken)
  - `aidocs/strategy/105-postgres-multitenant-decision.md` §4
    (4-schema reservation; `shepard_ai` absorbs annotation indexes)
  - `aidocs/integrations/97-shepard-plugin-ai-design.md` §6 + §8
    (pgvector substrate; "find similar" RAG flow that
    `suggest_annotations` rides on)
  - `aidocs/platform/30-mcp-plugin-design.md` (tool-registration
    pattern; `McpAuthFilter`; the existing `list_annotations` tool
    extended here)
  - `aidocs/semantics/14-semantic-improvements.md` (predecessor;
    SUPERSEDED by this doc)
  - `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md`
    (n10s + RDF/SPARQL view — still active)
  - `aidocs/semantics/65-admin-configurable-ontology-preseed.md`
    (`:SemanticConfig` substrate; N1c2 — extended, not superseded)
  - `aidocs/semantics/94-metadata4ing-integration-design.md`
    (m4i bootstrap; surfaces in the picker)
  - `aidocs/semantics/95-shacl-templates-and-individuals.md`
    (SHACL strict-mode lands here in v2)
  - `aidocs/semantics/96-upper-ontology-alignment.md` (BFO/IAO/EMMO/PROV-O
    upper layer; predicates resolve into this stack)
  - `aidocs/data/90-spatial-as-temporal-sweep.md` §3 (per-Collection
    `MeasurementSchema` SHACL shape; informs §4.3 collection-scope
    vocabularies)
  - `aidocs/platform/47-dev-experience-and-plugin-system.md` §2
    (PluginManifest SPI; the `SemanticVocabularyProvider` rides this)
  - `aidocs/agent-findings/neo4j-n10s-design-audit-2026-05-24.md`
    (NEO-AUDIT-005 EAV + NEO-AUDIT-018 SKOS bloat — both honoured
    by §14)
  - `aidocs/agent-findings/synthesis-architecture-report-2026-05-24.md`
    §3 T1 (SHACL-as-SoT — the strict-mode endpoint of this surface)

**External**:
  - [W3C Web Annotations Data Model](https://www.w3.org/TR/annotation-model/)
  - [W3C Web Annotations Vocabulary](https://www.w3.org/TR/annotation-vocab/)
  - [W3C SKOS Reference](https://www.w3.org/TR/skos-reference/)
  - [W3C PROV-O](https://www.w3.org/TR/prov-o/)
  - [W3C JSON-LD 1.1 Framing](https://www.w3.org/TR/json-ld11-framing/)
  - [Hypothesis.is API reference](https://h.readthedocs.io/en/latest/api-reference/)
  - [Wikidata Statements](https://www.wikidata.org/wiki/Wikidata:Statements)
  - [Annotorious](https://annotorious.com/)
  - [Notion property types](https://developers.notion.com/reference/property-object)
  - [Model Context Protocol specification](https://modelcontextprotocol.io/specification)
  - [pgvector](https://github.com/pgvector/pgvector)
  - [pg_trgm](https://www.postgresql.org/docs/16/pgtrgm.html)
  - [Crunchy Data hybrid search with pgvector](https://www.crunchydata.com/blog/hybrid-search-with-postgres-and-pgvector)
  - [neosemantics (n10s)](https://neo4j.com/labs/neosemantics/)
  - [Kadi4Mat documentation](https://kadi4mat.readthedocs.io/) (comparative annotation model)
  - [Coscine MAML overview](https://docs.coscine.de/en/metadata/) (comparative)
  - [openBIS object types](https://openbis.readthedocs.io/en/latest/uncategorized/openbis-collection.html) (comparative)
  - [SciCat datasets API](https://scicatproject.github.io/) (comparative)

---

*End of §19.*
