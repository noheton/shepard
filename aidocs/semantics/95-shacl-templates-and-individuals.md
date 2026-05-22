---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 95 — SHACL templates, named individuals, and ontology-driven UI

**Status.** Design — ready to implement.
**Snapshot date.** 2026-05-21.
**Audience.** Contributors implementing the slice (backend +
frontend); operators evaluating Shepard at scale; funders and
strategic reviewers who need the architectural claim grounded.

**Originating items.** Strategic discussion on 2026-05-21 about
ontology-driven UI ("the ontology IS the UI"), template
composition, and writable individuals. Couples to:

- `aidocs/14` — semantic improvements baseline
- `aidocs/48` — internal semantic repository via neosemantics
- `aidocs/65` — admin-configurable ontology preseed (ONT1c)
- `aidocs/semantics/N1b/ONT1a/ONT1b/ONT1c` — shipped ontology infra
- `aidocs/47` — plugin SPI (templates ship via plugins)
- `aidocs/integrations/67` — Helmholtz Unhide (export-shape consumer)
- `aidocs/87` — timeseries-appId migration (shapes will need to
  reference timeseries channels by appId once L2e ships)

---

## 0. Review status (honest version)

Each part of this doc carries different confidence levels. A
reader who wants to know what's defensible vs what's still
speculative should look here first.

| Part | Confidence | Review gate before implementation |
|---|---|---|
| 1 — Shapes as templates | high (pattern is well-understood) | TPL1c needs the 15-widget catalogue verified against the MFFD + EU-Project worked examples |
| 2 — Shapes as views | high (renderer infra exists) | TPL2c MUST be feature-flagged per-Collection (see TPL2c row in §15) |
| 3 — Shapes as agent contracts | high (MCP infra exists, task #80 already extended) | none — additive |
| 4 — Named individuals | high (PROV1a already captures Activities) | none — additive |
| 5 — Composition (`sh:node`) | high (SHACL native) | render-time cycle guard + lazy expansion are the bug-prone parts |
| 6 — Writable layer | medium (UX-bound, untested) | TPL1e ships TTL-paste-box only; visual editor is v2 |
| 7 — Upper-ontology alignment | **review-gated** | **TPL3 needs at least one external ontologist sign-off on aidocs/96 §3.1**; otherwise scope back to PROV-O + IAO only |
| 8 — Graph relationship | high | none |
| 9 — Scaling | **estimates only** | **TPL2d load test must run before scaling numbers are quoted externally** |
| 10 — Backward compatibility | medium | TPL4 lossy long-tail acknowledged honestly; institute-side review needed |
| 11 — Plugin model | high (pattern shipped in MCP) | none |
| 12 — Git ingestion | high (pattern well-understood) | TPL5b MUST include rollback procedure (see TPL5b row) |
| 13 — DLR ontology landscape | superseded | by `aidocs/agent-findings/dlr-ontology-catalog.md` + `industrial-robotics-ontology-audit.md` — re-fold during synthesis pass |
| 14 — Network-shaped data | medium (claim demoted) | full knowledge-graph render is v2; v1 ships multi-parent + lateral nav |
| 14ee — ODIX worked example | high (runnable demonstrator at `examples/mffd-showcase/`) | none |
| 15 — F(AI)²R AI provenance | medium (proposal-grade) | F(AI)²R is a community proposal, not EASA-endorsed; ship per TPL9 with regulatory deadline **2026-08-02** |
| 16 — Distributed-ledger anchor | medium (Bloxberg dependency) | TPL17 pairs with cross-anchor (OpenTimestamps/Bitcoin) for ledger-shutdown resilience |

**Most-urgent path:** TPL9 (F(AI)²R) for the 2026-08-02 EU AI
Act Article 50 deadline. **Highest-risk path:** TPL3 (upper
ontology) gated on ontologist review.

---

## 1. What this design is

A coherent, end-to-end story for how Shepard turns its ontology
layer from a passive metadata store into the active source of truth
that drives the UI, the API contract, and the agent surface.

The single-sentence claim:

> **Shepard is the RDM platform where the ontology drives the UI —
> without giving up scale, ops simplicity, or upstream
> compatibility.**

Concretely, this design ships seven mutually-reinforcing
capabilities under one architecture:

1. SHACL shapes as **templates** for Collection / DataObject
   creation (forms generated on the fly)
2. SHACL shapes as **views** — list columns, detail-page tabs,
   filter facets all derive from the same shape
3. SHACL shapes as **agent contracts** — MCP tool input schemas
   generated from shapes; new payload kind = zero MCP code
4. **Named individuals** with stable `shepard:` IRIs so every
   DataObject, Container, and Activity is queryable / referenceable
   as a first-class ontology individual
5. **Composition** — `sh:node` lets templates reference other
   templates; a mini-shape library (NDTGate, CalibrationCert, etc.)
   plugs into any parent template
6. **Writable layer** — instance-admins author shapes at runtime;
   trusted power-users coin new vocab terms (gated by usage count)
7. **Upper-level alignment** to BFO / IAO / EMMO / PROV-O so
   domain models built in Shepard get free OBO Foundry interop

This is the structural piece that turns Shepard from "yet another
RDM platform" into infrastructure other platforms can compose with.

---

## 2. Layered architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Ontology layer (RDF + SHACL in :SemanticRepository)            │
│  ────────────────────────────────────────────────────────       │
│  • Vocabularies: BFO, IAO, EMMO, PROV-O, QUDT, CHAMEO,          │
│                  Dublin Core, shepard-upper:                    │
│  • Classes:      shepard:Collection, shepard:DataObject,        │
│                  mffd:MFFDCampaign, mffd:BridgeWelding, …       │
│  • Shapes:       mffd:MFFDCampaignShape, …                      │
│  • Individuals:  CAL-2026-0421 (reused calibration cert), …    │
└────────────────────────────────┬────────────────────────────────┘
                                 │  INSTANCE_OF edges
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  Storage layer (Neo4j property graph)                           │
│  ──────────────────────────────────────────                     │
│  • Labels: :Collection, :DataObject, :Container, :Activity      │
│  • Edges:  HAS_CONTAINER, HAS_REFERENCE, HAS_ANNOTATION(:type), │
│            HAS_PREDECESSOR(:type), HAS_PERMISSION, …            │
│  • Indexes on appId, name, status, propertyIRI+valueIRI         │
│  • External handles: containerId → Influx / Mongo / S3 / HDF5   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  Service layer (Java, plugins, MCP)                             │
│  • Auth, permissions, lifecycle, audit                          │
│  • Read shape → render UI / generate forms / type MCP tools     │
│  • Read graph → answer queries fast                             │
└─────────────────────────────────────────────────────────────────┘
```

The ontology layer is the source of truth for *vocabulary and
shape*. The graph layer is the source of truth for *instances,
performance, and security*. The service layer connects them.

---

## 3. Uniquely positioned at the intersection of five constraints

The architecture above is **uniquely positioned at the
intersection** of five constraints — that is, it is the only
shape known to the authors that satisfies all five at once. This
is a defensible "uniquely positioned" claim, not a formal
Pareto-optimality proof; a future counter-example (a niche EU
project nobody knows of) would not invalidate the architecture,
only the claim. The five constraints:

1. W3C-standard vocabularies (RDF, OWL, SHACL, PROV-O) — for FAIR
   alignment, ecosystem reuse, vendor independence
2. Graph-scale performance — sub-100 ms typical operations at
   10⁵+ DataObjects
3. Plugin-extensible — adding a payload kind doesn't require a fork
4. Upstream-compatible — `/shepard/api/...` paths frozen against
   `gitlab.com/dlr-shepard/shepard 5.2.0`
5. Operator-runnable without a specialist DBA — `docker compose up`,
   no Stardog/GraphDB cluster

Pure triplestores lose (2). Pure property graphs lose (1).
Microservice meshes lose (5). Hardcoded UIs lose (1, 3, 5). The
three-layer architecture is the only known shape that satisfies
all five at once.

Drop any constraint and a different shape opens up. The 2027
horizon (when L2e ships and the V1 upstream-compat surface can
relax) may move constraint 4; revisit then.

### Pareto-optimality footnote (deferred)

Earlier drafts of this doc claimed "Pareto-optimal." That is a
formal claim requiring a proof that no other architecture
satisfies all five constraints simultaneously — and we cannot
prove a negative. "Uniquely positioned" is the defensible
version. The constraint-set framing IS the actual insight; the
formal-sounding adjective was the rhetorical overreach.

---

## 4. Part 1 — Shapes as templates (forms)

A SHACL `sh:NodeShape` targeting a `shepard:Collection` or
`shepard:DataObject` class declares the form fields, validation
rules, and child slots for an instance of that class. The frontend
renders a Vuetify form from the shape JSON; the backend validates
the submitted payload against the same shape before instantiation.

### Worked example: EU Project as a Collection template

```turtle
:EUProject  a owl:Class ;
  rdfs:subClassOf shepard:Collection ;
  rdfs:label "EU Project" ;
  rdfs:comment "Horizon Europe / Clean Aviation JU funded project" .

:EUProjectShape a sh:NodeShape ;
  sh:targetClass :EUProject ;
  sh:property [
    sh:path     dcterms:title ;
    sh:datatype xsd:string ;
    sh:minCount 1 ;            # required
    sh:name     "Project title" ;
    sh:order    1 ;
  ] ;
  sh:property [
    sh:path     :gaNumber ;
    sh:pattern  "^[0-9]{6}$" ;
    sh:name     "Grant Agreement number" ;
    sh:order    2 ;
  ] ;
  sh:property [
    sh:path     :hasPhase ;
    sh:class    :ProjectPhase ;
    sh:minCount 3 ; sh:maxCount 3 ;
    sh:name     "Phases" ;
    sh:order    10 ;
  ] .

:Phase0  a :ProjectPhase ; rdfs:label "Phase 0 — Scoping"      ; :phaseOrder 0 .
:Phase1  a :ProjectPhase ; rdfs:label "Phase 1 — Demonstrator" ; :phaseOrder 1 .
:Phase3  a :ProjectPhase ; rdfs:label "Phase 3 — Validation"   ; :phaseOrder 2 .
```

The user clicks `New → EU Project`, sees a form with three fields +
three pre-populated phase slots, fills in, submits, and gets a
Collection with three pre-wired DataObjects all carrying the right
INSTANCE_OF edges.

### Widget mapping — ~15 widgets, with explicit scope

The most-developed open-source SHACL-form library (`shacl-form` by
HfT Stuttgart) covers ~70% of common SHACL after years of work.
Earlier drafts of this doc claimed "10 widgets cover 90%" — that
was optimistic. Honest target for v1:

**v1 (TPL1c — shippable in 5 days):**

```
sh:datatype xsd:string             → v-text-field
sh:datatype xsd:string + maxLength → v-textarea
sh:datatype xsd:integer/decimal    → v-text-field type=number
sh:datatype xsd:dateTime           → v-date-picker
sh:datatype xsd:boolean            → v-switch
sh:in (small enumeration)          → v-select (closed list)
sh:class <Class>                   → v-autocomplete over existing individuals
sh:class + sh:nodeKind sh:IRI      → IRI picker (+ "create new")
sh:class shepard:FileBundle        → file-upload widget
sh:class shepard:TimeseriesContainer → "channels expected"
+ qudt:unit on the property        → numeric input with unit pill suffix
sh:node <Shape>                    → recursive inline form (lazy expanded)
sh:minInclusive + sh:maxInclusive  → slider with constraint band
```

13 v1 widgets — covers the MFFD demonstrator and EU-Project worked
example end-to-end.

**v2 (post-M1, before broad rollout):**

```
sh:or / sh:xone (disjunctions)     → tabbed form per branch
sh:qualifiedValueShape +
  sh:qualifiedMinCount             → "add at least N of kind X" widget
sh:in (large enumeration, >50)     → v-autocomplete-with-search instead of v-select
```

**Out-of-scope for v2 (research-grade SHACL features):**

- Sequence paths in `sh:path` (PROV's `[prov:wasGeneratedBy, prov:wasAssociatedWith]`)
- Inverse paths (`^prov:wasDerivedFrom`)
- Deep nesting ≥ 4 levels (lazy expansion handles the rendering;
  the UX of 4-deep forms is a separate design problem)
- SPARQL-based constraints (`sh:sparql`) — Shepard's SHACL engine
  may accept them; the *form generator* doesn't render them

Document the v2-and-out-of-scope status on each shape so reviewers
know what's supported.

---

## 5. Part 2 — Shapes as views (list / detail / facets)

The same shape drives more than the form. Three additional
renderers reading the same source:

- **List columns.** A shape property tagged with
  `shepard-ui:primaryColumn true` appears as a column on the
  Collection's DataObject list. `sh:order` decides column order.
  Different Collection classes get different columns automatically
  — `:MFFDCampaign` shows *technique, frame, NDT result, operator*;
  `:LUMENCampaign` shows *propellant, thrust, test engineer*. Same
  Vue component.

- **Detail-page tabs.** A shape property tagged with `sh:group`
  groups into a tabbed section. `:TapeLayup` has *Plies* tab,
  `:BridgeWelding` has *Weld Params* tab — generated from the
  shape, not hardcoded.

- **Filter facets.** Every shape property becomes a potential
  facet on the Collection's filter panel. Combined with composite
  indexes on `(propertyIRI, valueIRI)`, this gives multi-facet
  filtering at 10⁵-scale Collections.

---

## 6. Part 3 — Shapes as agent contracts (MCP)

Each shape automatically generates an MCP tool input schema. A
new payload kind shipped as a plugin adds shapes; new MCP tools
appear without backend code.

Two new core MCP tools:
- `list_templates` — returns the catalogue of shape IRIs the agent
  can instantiate, with descriptions from `rdfs:comment`
- `instantiate_template(shapeAppId, payload)` — atomic Collection+
  children creation; backend validates `payload` against the
  shape's SHACL constraints; returns the new Collection appId

Agentic ingest becomes structural: an LLM reading a directory of
files says "this looks like an MFFD campaign", calls
`list_templates`, picks `:MFFDCampaignShape`, and emits a payload
that instantiates the seven-step scaffold. Today's "wrong-container"
class of failures (the one that bit Qwen in the LUMEN
session) disappears because the shape declares which container
kinds each payload slot expects.

---

## 7. Part 4 — Named individuals as first-class

Today Shepard's annotations *point at* IRIs (consumer mode). The
DataObject itself has no IRI. This design promotes every
DataObject, Container, Activity, and Annotation to a first-class
named individual:

```
http://semantics.dlr.de/shepard-instance/<instance-id>/dataobject/<appId>
http://semantics.dlr.de/shepard-instance/<instance-id>/container/<appId>
http://semantics.dlr.de/shepard-instance/<instance-id>/activity/<appId>
```

The IRI is derived from the appId (UUID v7, already stable, sortable,
opaque-correct) + the instance's base URL.

Effect:
- Annotations can name a DataObject as their value:
  "TR-006 `prov:wasInformedBy` TR-005"
- The PROV1a audit log (`:Activity` nodes) becomes a queryable
  provenance graph — every API mutation is a typed
  `prov:Activity` individual
- Cross-Collection lineage works without ad-hoc URLs

---

## 8. Part 5 — Composition (sh:node, mini-shape library)

`sh:node` makes templates reference templates. This is the move
that turns the template system from a scaffold into a Lego set.

```turtle
:MFFDCampaignShape sh:property [
  sh:path :hasProcessStep ;
  sh:node :ProcessStepShape ;      # nested form, lazily expanded
  sh:minCount 7 ; sh:maxCount 7 ;
] .

:ProcessStepShape sh:property [
  sh:path mffd:hasCalibrationCert ;
  sh:class :CalibrationCertificate ;  # autocomplete + "+ create new"
] .
```

### Mini-shape library to ship in V##

- `:NDTGateShape` — inspection result, inspector, report file
- `:CalibrationCertificateShape` — cert ID, valid-until, document
- `:NCRShape` — finding, severity, corrective action
- `:SignOffShape` — agent, timestamp, approval document
- `:WeldParametersShape` — current, voltage, time, pressure (with
  QUDT unit annotations)
- `:ProcessStepShape` (abstract) — technique, operator, gate, cert

Each is independently developed, tested, reused across MFFD, LUMEN,
PLUTO, and future domains.

### Two reference modes, both needed

- `sh:node` — **inline composition**: create new child inline
- `sh:class` — **reference existing**: autocomplete over existing
  individuals; "+ create new" if the writable-individual
  permission holds

The user picks. Same widget, two behaviours. Same calibration cert
gets reused across all 7 process steps because the autocomplete
surfaces it on top.

### Render-time concerns

- **Lazy expansion.** Nested sub-shapes collapse on first render;
  user clicks to expand. Prevents infinite-loop on recursive
  templates (folder → subfolder).
- **Cycle guard.** Render-time only; data-model accepts cycles.
- **Versioning.** Each shape carries `dcterms:hasVersion`; an
  instance pins its shape version on creation. UI surfaces
  "template updated → migrate" as a per-instance action.

---

## 9. Part 6 — Writable individuals & shapes

Three authority tiers govern who can edit what:

| Tier | Authority | Examples |
|---|---|---|
| **Vendor** | Immutable, ships in V## migration | BFO, IAO, EMMO, PROV-O, `shepard-upper:`, core mini-shapes |
| **Instance-admin** | Mutable via `/v2/admin/semantic/...` | `mffd:` domain ontology, MFFD-specific shapes, instance-local terms |
| **Trusted power-user** | Mutable, gated by usage count | `shepard:user-vocab` — researcher-coined terms; promoted to instance-vocab after ≥3 distinct uses |

UI: from any annotation dialog or shape editor, "Create new term"
opens a sub-form (label, parent class, definition, contributor).
The new term lands in the appropriate tier based on the editor's
role. Three rules to keep it sane:

1. New terms must subclass an existing term (no orphans)
2. Definitions are required (forces articulation)
3. Promotion to the next tier requires either admin approval or
   usage threshold (≥3 distinct DataObjects using the term)

The community curates itself by use. This is the structural fix
for top-down controlled-vocab approaches that die the moment
domain shifts — Shepard harvests vocab from the work.

---

## 10. Part 7 — Upper-level alignment (BFO 2020 + IOF Core + IAO)

For Shepard to support researchers building **their own models**
in a way that interoperates with the OBO Foundry ecosystem
(~1,500 ontologies in biomedical + industrial domains)
and the IOF (Industrial Ontologies Foundry) family, domain
ontologies must align to a shared upper level.

> **Important caveat — alignment is non-trivial.** Getting BFO
> alignment right takes ontologist review. OBO Foundry has
> years of documented fix-ups for wrong alignments. Shepard's
> TPL3 implementation is **gated on at least one external
> ontologist reviewer signing off** on the alignment axioms in
> `aidocs/semantics/96` §3.1. If no such reviewer is available
> within the timeline, TPL3 ships with **PROV-O + IAO only**
> (well-understood, low-risk) and defers BFO / IOF Core / EMMO
> to a later release. The companion doc `aidocs/96` documents
> the alignment in detail.

### The alignment stack

```
bfo:           Basic Formal Ontology 2.0
                 (W3C-standard, OBO Foundry, philosophical realism)
   ↑
iao:           Information Artifact Ontology
                 (datasets, plans, documents — what RDM really stores)
   ↑       ↑
emmo:      shepard-upper:
   ↑               ↑                      (∼50 alignment axioms)
mffd:    pluto:    user-vocab:            (domain ontologies)
```

- **BFO 2.0** as foundation. `Continuant` (things persisting:
  `Material`, `Instrument`, `Collection`) vs `Occurrent`
  (processes: `TapeLayup`, `BridgeWelding`, `Investigation`).
- **IAO** for information artifacts. `iao:DataItem`,
  `iao:DocumentPart`, `iao:Plan`, `iao:Settings` — these are what
  Collections, DataObjects, and Annotations actually *are*.
- **EMMO** for materials/manufacturing. DLR has been involved in
  EMMO since 2018; `emmo:Material` and `emmo:Manufacturing` cover
  everything in the MFFD process chain natively.
- **PROV-O** for activities and lineage. Already in use; explicitly
  aligned to `bfo:Occurrent`.
- **`shepard-upper:`** is a thin (∼50 axiom) Shepard-specific layer
  declaring how Shepard's core concepts map to BFO / IAO. No new
  content — just alignment statements.

### The crucial design decision

**Upper-level alignment is OPT-IN to think about, MANDATORY to align to.**

A researcher building a domain ontology in Shepard sees:

```
Class: BridgeWelding
   subClassOf: ProcessStep
   subClassOf: ManufacturingActivity
```

They never type `bfo:Process`. But behind the scenes the chain
extends:

```
mffd:BridgeWelding ⊑ mffd:ProcessStep ⊑ mffd:ManufacturingActivity
                  ⊑ shepard-upper:Process ⊑ bfo:Process
```

so when their data joins a cross-domain query, the BFO joins it.
The upper-level is rails under the road — load-bearing but
invisible to the driver.

### "Domain-ontology starter kit"

A Template-of-Templates: researcher picks "I'm modelling: a process /
a material / an instrument / a measurement / an information artifact"
and the kit creates a subclass tree pre-aligned to the right BFO/IAO/
EMMO root. Lowers the floor for non-ontologists to contribute models.

### Companion design doc

Full alignment table + worked examples ships in
`aidocs/semantics/96-upper-ontology-alignment.md` (separate doc to
keep this one focused on the architecture).

---

## 11. Part 8 — What folds, what stays (graph relationship)

A common reading of "ontology drives the UI" is "the existing graph
is now redundant." That is wrong. The graph and the ontology are
**layered**, not parallel. Three things the graph does, evaluated
each separately:

| Job | Status | Reason |
|---|---|---|
| **Storage substrate** (Neo4j labels, indexes, physical layout) | Stays | Indexed Cypher is 100–1000× faster than SPARQL for typical RDM queries. At MFFD scale (10⁵ DOs), giving this up kills the platform. |
| **Semantic model** (what is a Collection, DataObject, Container) | Folds into ontology | This is what RDF/OWL natively expresses. Java classes stay (OGM needs them) but become projections of the ontology. |
| **Access, lifecycle, audit** (permissions, status machine, `:Activity` log) | Stays | OWL/SHACL has no native answer; bespoke service-layer concerns; CLAUDE.md security perimeter rule. |

### Three places where the graph genuinely becomes redundant — and the migration

1. **Free-text `attributes` dict on DataObject.** Today TR-004 stores
   `propellant: "LOX/LCH4"` as a key-value attribute *and* could as
   a typed SemanticAnnotation. These duplicate. Migration: a V##
   Cypher script walks every DataObject, lifts known keys to
   typed `:propertyIRI` annotations using a mapping table (default
   set for the most-used keys; unknown keys fall to
   `shepard:legacy/<key>` synthetic predicates).
   Backward-compat handled in §13.

2. **Untyped `:Predecessor` / `:Successor` edges.** Today an edge
   says "TR-004 is a predecessor of TR-005" but doesn't say *why*.
   Migration: add an optional `:type` property carrying a PROV-O
   IRI (`prov:wasInformedBy`, `prov:wasDerivedFrom`,
   `prov:wasRevisionOf`). Existing edges stay readable as untyped;
   new writes type by default.

3. **Hardcoded `Collection` vs `DataObject` Java class split.** Both
   already extend `AbstractDataObject`. The distinction is real in
   the V1 API (frozen) but artificial in concept. Under
   ontology-driven, both are `shepard:Resource` with different
   shape targets. V2 API collapses the distinction; V1 keeps
   serving the split.

### What is **not** redundant (despite appearance)

- **Container abstraction** (Timeseries/File/Structured/Video/HDF5)
  — each is both conceptual (ontology) AND physical (storage
  handle to Influx/Mongo/S3/HDF5). Don't fold.
- **User/Group/Permission graph** — auth perimeter, immutable rule.
- **`:Activity` audit log** — stays as graph nodes but gains PROV-O
  typing, becomes queryable.

---

## 12. Part 9 — Scaling characteristics

**The question that matters more than any architecture diagram:**
does the layered approach hold up at MFFD scale and beyond? This
section answers by operation type × scale tier.

> **Caveat — these are model-based estimates, pending TPL2d load
> test.** Numbers below come from Neo4j community-published
> benchmarks (16 GB heap, indexed reads, single primary) scaled
> to Shepard's query patterns. Real measurements may vary 5–10×
> based on heap size, page-cache hit rate, query plan, and GC
> pause behaviour. **Do not put these numbers in funding pitches
> or external positioning until TPL2d produces measured results
> on Shepard's actual schema.** They are useful for engineering
> direction-setting only.

### Scaling table

| Operation | 10³ (LUMEN) | 10⁴ (small inst.) | 10⁵ (MFFD) | 10⁶ (industrial) | 10⁷ (consortium) |
|---|---|---|---|---|---|
| List DataObjects (paginated 50) | <5 ms | <10 ms | ~15 ms | ~30 ms | ~80 ms ⚠ |
| Get DataObject detail (depth 2) | <10 ms | <15 ms | ~20 ms | ~25 ms | ~30 ms |
| Channel data (LTTB 2000 pts) | 80 ms* | 80 ms* | 80 ms* | 80 ms* | 80 ms* |
| Lineage walk (≤ 20 hops) | <5 ms | <10 ms | ~15 ms | ~25 ms | ~50 ms |
| All annotations for a DO | <2 ms | <3 ms | <5 ms | ~8 ms | ~15 ms |
| Read SHACL shape (uncached) | <5 ms | <5 ms | <5 ms | <5 ms | <5 ms |
| Read SHACL shape (cached) | <0.1 ms | <0.1 ms | <0.1 ms | <0.1 ms | <0.1 ms |
| Instantiate template (1 col + 7 DOs) | ~150 ms | ~150 ms | ~200 ms | ~250 ms | ~350 ms |
| Facet query (1 prop, indexed) | <10 ms | ~20 ms | ~80 ms | ~300 ms ⚠ | ~1.5 s ⚠⚠ |
| Facet query (3-AND, all indexed) | ~30 ms | ~60 ms | ~150 ms | ~600 ms ⚠ | ~3 s ⚠⚠ |
| SPARQL over shapes only | <50 ms | <50 ms | <50 ms | <50 ms | <50 ms |
| SPARQL over instances (Neo4j) | ~100 ms | ~300 ms | ~1 s ⚠ | ~5 s ⚠⚠ | infeasible |

\* Channel data bottleneck is InfluxDB + LTTB, not graph. Flat with
graph scale.

⚠ = needs attention. ⚠⚠ = will break without intervention.

### What the table says

1. **Bulk operations are flat across scale tiers.** Paginated list,
   detail get, annotations, lineage walks — all bounded by page
   size, local fan-out, or depth, not graph size. This is the
   property graph paying off.
2. **Facet queries are the real scaling pressure point.** With
   composite indexes on `(propertyIRI, valueIRI)`, fine at 10⁵;
   with materialized counts, fine at 10⁶; beyond that needs
   sharding.
3. **Template instantiation grows mildly.** Shape lookup is
   cached; the rest is bounded by the template's child count.
4. **Arbitrary SPARQL-over-instances is the genuine ceiling.**
   Scope SPARQL queries to a Collection or DataObject subtree —
   never the global graph. UX constraint that follows from the
   architecture; the SPARQL UI (N1f) enforces it.

### Bottlenecks ranked, with mitigations

**Tier 1 — must address before MFFD ships** (10⁵ scale):

1. **Composite index on `(propertyIRI, valueIRI)` for
   SemanticAnnotation.** One Cypher migration, one-time build,
   fixed forever. ROI: facets work at MFFD scale.
2. **Bound depth on lineage queries.** Cap unbounded
   `[:PREDECESSOR_OF*]` at `[*1..30]` (longest realistic
   manufacturing chain) or a runtime parameter.

**Tier 2 — for 10⁶ scale:**

3. **Per-Collection materialized facet counts.** Pre-aggregate into
   a `:FacetCount {collectionId, propertyIRI, valueIRI, count}`
   node, updated on DataObject create/delete via Cypher trigger.
4. **Read replicas.** Single primary + 2 replicas triples read
   throughput. Standard Neo4j Causal Cluster.
5. **Server-side facet computation.** Frontend asks for
   pre-aggregated counts, not raw rows. (Pairs with task #25.)

**Tier 3 — for 10⁷ and beyond:**

6. **Cold/archive tier.** Move ARCHIVED Collections to a cold
   partition. Pairs with task #27.
7. **Sharding by Collection.** Standard sharded-graph pattern.
8. **Federated SPARQL service.** Separate Stardog/GraphDB for
   cross-Collection analytical queries; hot path stays on Neo4j.

### Cost of the ontology layer itself

Concretely: how much does ontology-driven UI add vs. a hardcoded
UI? Component-by-component:

- Shapes are small and rarely change (a typical shape is ~50
  triples; full Shepard shape catalog at maturity ~5000 shapes).
  Entire shape store fits in memory.
- Shape lookups are per-request, cached (existing
  `EntityIdResolver` pattern). First lookup ~5 ms; thereafter
  <0.1 ms within the same request.
- Form generation is client-side — backend ships shape JSON,
  frontend renders. Zero server cost beyond shape lookup.
- SHACL validation at submit — Apache Jena's SHACL engine handles
  typical shapes in <20 ms even on large instances.
- INSTANCE_OF edges — one extra edge per DataObject. At 10⁶ DOs,
  10⁶ extra edges. Neo4j is fine with this.

**Net: ontology-driven UI adds <50 ms per write and <5 ms per
read at any scale.** The performance ceiling is the *existing*
graph operations (facets, lineage, SPARQL-over-instances). The
ontology layer is essentially free.

### When to actually start worrying

| Threshold | Trigger | Mitigation effort |
|---|---|---|
| 10⁵ DOs in one Collection | MFFD goes live | One index migration. Days. |
| 10⁶ DOs total in instance | Probably 18–24 months out at current adoption | Read replicas. ~2 weeks ops. |
| 10⁷ DOs OR cross-Collection SPARQL hot | Far horizon; hypothetical until multiple institutes onboard | Federated SPARQL service. ~6 weeks. |

The architecture is sound to 10⁶ DOs with one index migration and
one trigger. Beyond that needs standard Neo4j patterns
(replicas, sharding), not exotic engineering. The risk is not the
architecture; it's whether anyone validates the numbers with a
real load test before MFFD goes live.

---

## 13. Part 10 — Backward compatibility

The fork has three layers of compat to maintain:

### Layer A — V1 `/shepard/api/...` paths

Frozen. CLAUDE.md mandates byte-compatibility with
`gitlab.com/dlr-shepard/shepard 5.2.0`. Nothing in this design
touches V1 endpoints. V1 keeps reading the `attributes` dict and
untyped Predecessor edges exactly as before.

### Layer B — Existing data on disk

Dual-write window during migration. The flow:

1. V## ships a Cypher migration that **lifts existing
   `attributes` keys to typed annotations** using a default
   vocabulary mapping table. Known keys (e.g. `propellant`,
   `bench`, `test_engineer`) map to standard IRIs from the
   ontology. Unknown keys map to `shepard:legacy/<key>` synthetic
   predicates so the data isn't lost.
2. Writes in the V2 API produce **both** attributes (legacy) AND
   annotations (new) — atomic dual-write — until deprecation
   completes.
3. Migration is **idempotent** and **fail-fast** per the CLAUDE.md
   migration rules. A `V##_R__` rollback file is shipped.

### Layer C — Custom MCP/REST integrations

The V2 API keeps the `attributes` field present in responses for
≥6 months alongside the new `annotations` field. Deprecation
warning logged on every read. Migration guide in
`docs/reference/v1-to-v2-migration.md` explains how to read
annotations as the new source of truth.

### Layer D — Existing shapes / templates / individuals

Per §8 (composition versioning):

- Each shape carries `dcterms:hasVersion`
- An instance pins its shape version at creation time
- UI surfaces "template updated → migrate" as a per-instance
  action; never automatic
- Existing instances continue to validate against their pinned
  version

### Migration order

1. Ship V## with `shepard-upper:` ontology + composite index on
   `(propertyIRI, valueIRI)` + index on `INSTANCE_OF` edges
2. Run attribute-lifting migration (idempotent)
3. Enable dual-write in V2 services
4. Frontend rollout — shape-driven UI behind a feature flag
5. After 6 months: deprecation warnings → removal of attributes
   field from V2 responses

Each step is independently revertible.

### Aidocs/34 entry

A new row in `aidocs/34-upstream-upgrade-path.md` documents:

| Change | Type | Operator action | Migration files |
|---|---|---|---|
| Ontology-driven UI (TPL1–TPL2) | ADDITIVE | None required; V2-only; V1 unchanged | `V##__bootstrap_shacl_templates.cypher`, `V##_R__rollback.cypher` |

---

## 14. Part 11 — Plugin contribution model

Plugins ship their own shapes alongside their other artefacts:

```
plugins/<plugin-id>/
   ├── shapes/
   │   ├── <plugin-id>-domain.ttl       # the ontology
   │   ├── <plugin-id>-shapes.ttl       # SHACL shapes
   │   └── <plugin-id>-upper.ttl        # alignment to shepard-upper
   ├── docs/
   ├── src/main/java/…
   └── …
```

At startup, the plugin SPI auto-discovers `plugins/*/shapes/*.ttl`
and loads them into `:SemanticRepository`. The shapes immediately
become available to:

- The Template gallery (`POST /v2/templates/.../instantiate`)
- The form generator (frontend renders without code change)
- The MCP tool surface (auto-generated tool inputs)
- The list / detail / facet renderers

A new payload kind shipped as a plugin is **operationally a TTL
file** plus any plugin-specific physical-storage code (the
container service implementation, which is unavoidable). The
*conceptual* part lives entirely in the ontology.

---

## 14b. Part 12 — Git-based ontology ingestion

The "drop-data-and-go" git ingestion pattern (task #26) extends
naturally to ontologies. Researchers maintain their ontology in
a git repo (versioned, branchable, mergeable, reviewable via PRs);
Shepard pulls from the git URL on a schedule or webhook.

This is how OBO Foundry actually distributes ontologies — every OBO
ontology has a git repository with a release pipeline (typically
using ROBOT, the OBO release toolkit). Aligning to that workflow
gives Shepard immediate compatibility with the OBO ecosystem.

### Workflow

```
researcher's git repo (GitLab / GitHub / DLR Gitea)
    ├── ontology/<id>.ttl             # the ontology
    ├── shapes/<id>-shapes.ttl        # SHACL shapes
    ├── upper/<id>-upper.ttl          # alignment to shepard-upper
    ├── catalog.xml                   # OBO-style catalog
    ├── release.yml                   # ROBOT release pipeline
    └── README.md
                              │
                              ▼
                   git push / merge to main
                              │
                              ▼
              webhook → /v2/admin/semantic/git/sync
                              │
                              ▼
        Shepard ingests, validates, dry-run preview
                              │
                              ▼
          instance-admin approves → deployed
```

### Three ingestion modes (pick per repo)

| Mode | Trigger | Use case |
|---|---|---|
| **Webhook** | git push event → Shepard pull | Hot-path: researcher's own ontology, iterating fast |
| **Scheduled** | cron (default: hourly) | Tracking external ontologies (BFO, IAO updates) |
| **On-demand** | admin clicks "Sync now" | Manual control for critical ontologies |

### Versioning & branch support

- Shepard pins each loaded ontology to a **git commit SHA**, not
  just `dcterms:hasVersion`. Provenance is exact.
- Each Shepard environment (dev / staging / prod) can track a
  different branch (`develop` / `staging` / `main`). Branch-based
  deployment for ontologies, same as for code.
- Rollback is `git revert` + `Sync now` — same mental model as
  application rollback.

### Admin UI

Under `/me#semantic-repositories`, instance-admin sees:

```
Connected ontology repos
─────────────────────────────────────────────────────────
✓ git.dlr.de/zlp/mffd-ontology    main @ commit a4b2c1f
  Last sync 2026-05-21 14:23  ·  47 classes  ·  12 shapes
  [Sync now]  [View diff]  [Disconnect]

✓ github.com/EMMO/EMMO            v1.0.0 (tag)
  Last sync 2026-05-15  ·  ~3000 classes  ·  read-only

+ Connect new repo (URL, optional auth token, branch)
```

### Dry-run + diff preview

Before each accepted sync, Shepard generates a **diff report**:

- New classes added
- Existing classes whose definition changed
- Classes referenced by existing instances that would be removed
  (these *block* the sync — admin must resolve first)
- New shapes; modified shapes; shapes referenced by existing
  instances (existing instances stay pinned to their previous
  shape version per §8)
- SHACL validation results against existing instances

The diff is shown in the admin UI; sync proceeds only on approval.

### Plays well with

- **shepard-plugin-importer** (per memory: importer-library design)
  — plugins can declare which ontology repos they depend on, pulled
  automatically on plugin install
- **task #26** — git-as-data-source becomes git-as-everything-source
- **plugin SPI** — plugin's `plugins/<id>/shapes/*.ttl` works for
  in-tree ontologies; the git-ingestion path is for
  out-of-tree, instance-specific ones

### Implementation cost

Roughly 1 sprint:

- Backend git client (already have one for shepard-plugin-git
  integration concept; reuse)
- ROBOT-style validation pipeline (Apache Jena does the bulk)
- Webhook receiver + signature verification (GitHub/GitLab/Gitea
  formats)
- Dry-run differ
- Admin UI

Slot in as **TPL5** in §15.

---

## 14c. Part 13 — DLR ontology landscape: what to integrate

A successful upper-aligned design needs to land with the DLR-relevant
ontologies pre-integrated, so researchers find their domain language
already spoken when they open Shepard for the first time.

### Already shipped (per ONT1a/b/c, aidocs/65)

`prov-o`, `dublin-core`, `schema-org`, `foaf`, `qudt`, `om-2`,
`time`, `geosparql`, `obo-relations`, `metadata4ing` (m4i)

### High-confidence additions for the TPL3 bootstrap

| Ontology | Source / sponsor | Why for Shepard |
|---|---|---|
| **BFO 2.0** | OBO Foundry / IFOMIS | Upper-level foundation (§10) |
| **IAO** | OBO Foundry | Information artifacts; what Datasets / Documents / Plans actually are |
| **EMMO** | European Materials Modelling Council; DLR co-developer | Materials & manufacturing; MFFD domain natively expressible |
| **CHAMEO** | EU CHADA / MaterialsDigital | Characterisation methodology — instruments, methods, samples |
| **RO** (Relations Ontology) | OBO Foundry | Curated relation predicates beyond `rdfs:` |
| **Pizza/Wine** (skip) | — | (Example ontologies, no production value) |

### Candidates to investigate (DLR/German research context)

These need empirical validation — repos identified, content audited,
licence checked — before committing to integration. **Worth a
focused audit pass** (see below):

| Candidate | Likely sponsor | Relevance |
|---|---|---|
| **NFDI4Ing terminology** | NFDI4Ing consortium (RWTH, DLR, others) | Engineering data — sister project of m4i, broader scope |
| **NFDI4Earth ontologies** | NFDI4Earth (GFZ, DLR, AWI) | Earth observation, PLUTO mission data context |
| **NFDI-MatWerk** | NFDI materials science | Materials data; orthogonal to EMMO, complementary |
| **HMC metadata profiles** | Helmholtz Metadata Collaboration hubs | Per-hub domain extensions (Aeronautics/Space/Transport hub directly relevant) |
| **MaterialsDigital ontologies** | BMBF Plattform MaterialDigital | DLR institutes are active partners |
| **DLR ZLP MFFD-specific terminology** | DLR Augsburg (ZLP) | If documented; otherwise becomes our first user-vocab contribution |
| **DLR Earth Observation thesaurus** | DLR EOC Oberpfaffenhofen | If RDF-published |
| **EUROCONTROL aviation ontology** | EUROCONTROL | Aviation systems; LUMEN/PLUTO context |
| **ECSS Space Engineering ontology** | ESA / ECSS | Standardised space engineering; PLUTO mission alignment |
| **CCSDS-aligned ontologies** | CCSDS / ESA | Mission ops, telemetry; PLUTO native |

### Audit task

**OOA1 — DLR ontology audit** (deferred specialised-agent work):
spawn the **ecosystem-advocate** agent role with the prompt
"Identify all DLR-maintained, DLR-coauthored, or
DLR-institutionally-used ontologies (RDF/OWL/SHACL) suitable for
Shepard integration. For each: source URL, sponsor, licence,
maintenance status, scope description, and recommended Shepard
priority (must-have / nice-to-have / probably-skip)."

Output to `aidocs/agent-findings/dlr-ontology-catalog.md`. Should
be one focused day of agent work; informs the TPL3 bootstrap
shortlist.

### Integration mechanics

Per Part 12 (git ingestion):
- BFO, IAO, RO ship via git URL pinned to released tag (OBO
  Foundry ontologies all have stable git repos)
- EMMO, CHAMEO, m4i extensions ship via git URL pinned to tag
- DLR-specific ontologies (once audited and TTL-published)
  ship via the same git path
- Vendor-tier (BFO/IAO/EMMO) becomes the V## bootstrap; DLR
  ontologies become recommended-but-optional bundles per the
  ONT1c admin-configurable preseed pattern

### Strategic outcome

The starter Shepard for a DLR institute opens with:
- BFO/IAO/EMMO/PROV-O upper layer (always-on)
- DLR-flavour vocabularies (institute-relevant, pre-selected)
- "Connect your ontology repo" wizard for institute-specific
  extensions

So adoption-day-zero, the researcher sees their domain language
already in the autocomplete. No "bring your own ontology" friction.

---

## 14d. Part 14 — Beyond hierarchy: network-shaped data organisation

A common misperception of Shepard today is that data is organised
strictly hierarchically: Collection → DataObject → child DataObject
→ Container → Reference. That's the *default view*, not the data
model. Underneath, Neo4j is a property graph — every entity has an
appId, edges are typed, and any node can edge to any other node.
The ontology-driven design makes network organisation explicit and
queryable as a first-class capability.

### What's already possible (today, no new code)

- **Typed annotations as cross-references.** Every annotation with
  `propertyIRI` pointing at another DataObject's IRI is a typed
  cross-edge. CAL-2026-0421 (one calibration cert individual) can
  be referenced from every process step that used it.
- **Many-to-many via PROV-O.** `prov:wasInformedBy`,
  `prov:used`, `prov:wasDerivedFrom` already let a DataObject
  reference any number of others, in any direction. Lineage is
  already a DAG.
- **Cross-Collection annotation IRIs.** An annotation's value IRI
  can point at a DataObject in a completely different Collection.
  The data model imposes no Collection boundary on semantic edges.

### What this design unlocks at the conceptual layer

1. **Multi-parent membership.** Under the ontology model, a
   DataObject is an individual; the Collection is just a
   `:MEMBER_OF` relation. A single DataObject can be a member of
   multiple Collections (e.g. a calibration record is in the
   campaign-specific Collection AND in the lab's calibration
   archive Collection). Today the V1 schema enforces single
   ownership for upstream compat; V2 can drop that constraint.

2. **Network-shaped Collections.** A Collection no longer has to
   be a hierarchical container. It can be a *namespace* that
   collects an arbitrary network of typed individuals with
   typed edges between them. The Collection's "Shape" declares
   what kinds of edges are expected (e.g. `:MFFDCampaign hasEdges
   [:precedes, :uses_material, :inspected_by, :produces]`).

3. **Saved network views / SPARQL queries as first-class
   widgets.** A power user writes a SPARQL query
   ("all DataObjects within 3 hops of TR-004 where
   `severity = HIGH`") and saves it as a named view. The view
   becomes a UI widget showing the network result, refreshed
   live. Same architecture as the existing list/detail views —
   just with a graph renderer instead of a table.

4. **Cross-institute federation.** Once individuals have stable
   IRIs, an annotation can point at an IRI from *another DLR
   institute's Shepard instance*. Cross-instance graph queries
   become possible via federated SPARQL (long-horizon: tier 3 of
   the scaling plan, §12).

### Network-shaped views — what TPL6 actually ships

**Honest scoping note:** earlier drafts of this part promised four
network-shaped renderers including a free-form "knowledge graph"
view rendered as a force-directed layout. At MFFD scale the
framewelding subtree alone has 3,371 individuals — force-directed
layouts hairball above ~150 nodes, and even hierarchical layouts
struggle. The **real win** in TPL6 is **multi-parent membership**
(a DataObject can belong to multiple Collections) plus
**cross-reference indicators on detail pages** that let users
laterally navigate the graph node-by-node. Full knowledge-graph
rendering is demoted to a v2 concern once the substrate handles
streaming sub-graph queries.

TPL6 v1 ships:

| View | What it shows | Renderer | Scale ceiling |
|---|---|---|---|
| **Process network** | Process steps as nodes, material/data flow as edges, quality gates as decoration | Reuse `CollectionLineageGraph` w/ dagre | ~100 nodes (one process chain) |
| **Material genealogy** (subset) | All DataObjects touching a given material, layered by process step | Hierarchical layout, depth-bounded | ~50 nodes per material |
| **Cross-reference indicators on the detail page** | Per-DataObject, list of incoming + outgoing typed edges with "navigate" buttons | List + icon — not a graph render | unbounded (paginated) |
| **Multi-parent membership** | A DataObject visible from multiple Collections | (storage / API only — no new viz) | unbounded |

TPL6 v2 (post-M3, gated on substrate maturity):

- Inspector graph (bipartite layout — works at ~100 nodes per
  inspector)
- Free-form SPARQL → graph render via Cytoscape.js with
  server-side sub-graph extraction (only renders the result of a
  bounded query, not the global graph)

The framing matters because "Shepard renders the whole graph"
sets an expectation that doesn't hold at MFFD scale. "Shepard
shows the right *slice* for the question you asked" is the
honest claim.

### What "network organisation" means for the user

Today's mental model: "I'm in Collection X → drill into DataObject
Y → see its containers."

Network-augmented mental model: "I'm looking at a knowledge graph
where every node is an individual, every edge is typed and
semantically meaningful, and I can pivot at any node to traverse a
different edge type." The hierarchical view is one projection of
the graph; the process network is another; the inspector graph is
a third. The same data, different lenses.

The key UX move: every individual has both a "card" representation
(today's detail page — used in tree-shaped contexts) and a "node"
representation (the same individual displayed as a graph node —
used in network-shaped contexts). Switching between views is a
display toggle, never a re-query.

### What this does NOT mean

- **Collections don't go away.** They remain the unit of
  permission, the unit of FAIR publication, and the operator's
  unit of management. The Collection is a *namespace*, not a
  *constraint*.
- **The hierarchical view doesn't go away.** It's the right
  default for casual users and for navigation by familiarity.
- **Performance ceiling stays the same.** Graph queries scoped
  to a Collection are fast; arbitrary global graph queries hit
  the same scaling limits described in §12.

### Implementation cost

Most of this falls out of the architecture for free once the
ontology layer is shipping. Concrete new work:

- **Multi-parent membership in V2** — schema change, V2 endpoints,
  migration: ~1 sprint
- **Saved-query / saved-view storage and rendering** — new
  service + Vue component: ~1 sprint
- **Two additional network views (process network, knowledge
  graph)** — leverages existing graph-render code: ~1 sprint
- **Cross-Collection navigation surface polish** — breadcrumbs
  that show Collection-namespace context when navigating across:
  ~3 days

Slot in as **TPL6** in §15. Network-shaped views are an additive
capability on top of the core ontology-driven design, not a
prerequisite — TPL6 can land independently after M1/M2.

---

## 14ee. Part 14f — Worked example: ODIX (semantic process analysis at ZLP)

The architecture in Parts 1–14 is not theoretical — it has a working
prototype operating today at DLR Augsburg ZLP. The **ODIX
(infusion-analysis) project** does exactly what the design proposes
as the platform default, but hand-coded per-use-case. Documenting
it here serves three purposes: (a) it validates that the loop
works in practice, (b) it shows the gap between
"hand-coded once" and "platform-native", (c) it gives MFFD a
direct precedent to lift from.

### What ODIX is

`infusion-analysis` (Jupyter-driven, `shepard-client v5.x`,
`sparqlwrapper`, `networkx`, `pandas`) operates against a Shepard
instance at `frontend.bt-au-cube3.intra.dlr.de` and a custom CFRP
infusion ontology (`odix/semantic_analysis/prototype_ontology/process.ttl`).

The ontology declares:

```turtle
:InfusionProcess          owl:Class                # VAP / VARI processes
:ProcessParameter         owl:Class  (abstract)
  ├── :ResinTemperature       (constraint 20–40 °C, unit om:degreeCelsius)
  ├── :VacuumLevel            (constraint 0.95–1.0 bar)
  ├── :InfusionPressure       (constraint 0.1–0.5 bar)
  ├── :ResinFlowRate          (constraint 3–30 L/h)
  └── :MouldTemperature       (constraint 30–60 °C)
:Constraint               owl:Class                # min/max-bound shape
:Defect                   owl:Class
  ├── :DrySpot
  ├── :Porosity
  └── :IncompleteImpregnation

# wired by:
:hasConstraint   ObjectProperty (ProcessParameter → Constraint)
:causesDefect    ObjectProperty (ProcessParameter → Defect)
:hasUnit         ObjectProperty (ProcessParameter → om:Unit)
```

OM (Ontology of units of Measure 2.0) carries the units. Note the
shape: ODIX's pre-design ontology already uses one of the V49 seeded
vocabularies (OM-2) without anyone planning it — convergent evolution.

### The analytics loop (verbatim from `semantic_analysis/README.md`)

```
For each timeseries reference in an infusion DataObject:
  check annotations for observableProperty IRIs
  For each annotated IRI:
    get process constraints (min-max) from ontology
    get timeseries; compute metrics
    check constraints against metrics
    if metrics out of constraint → add IRI to anomaly candidates
  For each anomaly candidate:
    get process consequences (e.g. :causesDefect → :DrySpot)
    optionally weight by frequency / co-occurrence
```

This **is** Shepard's planned platform behaviour: an ontology
declares what each parameter means + acceptable range + downstream
defect; an analysis loop pulls the metrics from the timeseries and
flags out-of-band readings. ODIX implements it once for resin
infusion; TPL1+TPL3 makes it the default for any process whose
SHACL shape carries equivalent `:hasConstraint` / `:causesDefect`
declarations.

### Why this matters for MFFD

MFFD's process steps (Tape Layup, Bridge Welding, etc.) have
direct analogues in ODIX's vocabulary:

| ODIX concept | MFFD analogue |
|---|---|
| `:InfusionProcess` | `mffd:TapeLayup`, `mffd:BridgeWelding`, … |
| `:ProcessParameter` | `mffd:WeldCurrent`, `mffd:WeldPressure`, `mffd:TCPTemperature`, `mffd:ConsolidationForce`, … |
| `:Constraint` | Per-parameter min/max from process spec |
| `:Defect` | `mffd:DelaminationDefect`, `mffd:VoidDefect`, `mffd:UnderweldDefect`, … |
| `:causesDefect` | The QA rules every operator already knows by heart |

When TPL1 + TPL3 ship the MFFD process ontology, ODIX's
`:hasConstraint`/`:causesDefect` pattern transplants directly. The
import script for MFFD becomes the demonstrator that operates
ODIX's loop natively (not hand-coded in a notebook).

### What ODIX shows about Shepard's deployment topology

ODIX targets `frontend.bt-au-cube3.intra.dlr.de` — a **distinct
Shepard instance** from:

- `frontend.bt-au-cube1.intra.dlr.de` (Dataship default, ZLP general)
- `bt-au-rebar.intra.dlr.de` (REBAR's Airflow server)

This is multi-instance reality at one institute: **per-domain
deployments, each with its own ontology, federated via Databus
(Dataship) for cross-discovery**. The pattern matters for the
design because:

1. The Pareto-claim's "ops simplicity" constraint must hold across
   multiple instances per institute, not just one.
2. The git-ingestion of ontologies (Part 12) needs to work *per
   instance* — cube3 pulls a different ontology repo than cube1.
3. The network-shaped data organisation (Part 14) extends across
   instances via shared IRIs once individuals have stable
   shepard-instance-prefixed identifiers (Part 4).
4. The publish-plugin family (Part 11 + Dataship adapter) is the
   inter-instance federation bus, not just an external-catalog
   bridge.

### What the platform-native version adds beyond ODIX

| Aspect | ODIX today | Shepard-native after TPL1+3+9 |
|---|---|---|
| Ontology authoring | Hand-written TTL in a notebook prototype dir | Git-ingested + admin-editable shapes (Parts 6 + 12) |
| Annotation step | Manual via Shepard UI or API | Form-generated from shape (Part 1) |
| Constraint check | Python notebook + SPARQL | Native to Shepard's analytics path; SHACL `sh:minInclusive` / `sh:maxInclusive` enforced platform-side |
| Defect reporting | Notebook output | Annotation on DataObject; visible in detail view (Part 2); queryable as facet |
| Provenance of the AI involvement (if Claude / GPT was consulted) | Untracked | Captured as `fair2r:AuditPass` per Part 15 |
| Cross-instance discovery | None (or manual) | Databus / Unhide publish (Part 11 + Dataship adapter) |
| Multi-domain reuse | Re-author per domain | Mini-shape library (Part 5) — `:hasConstraint` / `:causesDefect` patterns are reusable across infusion, welding, layup, machining |

ODIX's existence is the strongest single piece of evidence that
the design is feasible: someone at DLR already does this loop
manually every time they analyse an infusion dataset. TPL1+3+9
generalises the pattern; ODIX's notebooks become a thin layer
on top of the platform default rather than the platform itself.

---

## 14e. Part 15 — AI provenance capture (f(ai)²r alignment)

The PROV-O integration we already use (Part 4: named individuals;
PROV1a Activity capture filter) becomes load-bearing the moment AI
agents start writing to Shepard. EU AI Act Article 50 (transparency
obligations for AI-generated content), funding-body disclosure
requirements, and the broader FAIR-for-AI movement all require:
*for every artefact, can we say who or what produced it, with what
inputs, when, and whether it has been verified?*

The reference is the **F(AI)²R** community proposal — a candidate
FAIR-for-AI-Assisted-Research extension. It was first articulated
in §8 of Krebs (2026), *Obscurity Is Dead: AI-Assisted Hacking, Key
to Interoperability or Security Nightmare?*
(<https://github.com/noheton/Obscurity-Is-Dead>, CC-BY-4.0,
independent / hobbyist research, **explicitly not a DLR work** per
that paper's §9.5). The name was proposed 2026-05-04 as *FAIR4AI*,
renamed 2026-05-05 to *F(AI)²R* — the "AI-assisted" dimension
transforms *every* FAIR axis, so the (AI) factor is multiplied into
the acronym rather than appended as an external "4AI" suffix.

The working reference implementation is
<https://github.com/noheton/f-ai-r> (Florian Krebs, ORCID
0000-0001-6033-801X; MIT code/RDF, CC-BY-4.0 manuscript). It ships
the PROV-O extension, the verification ladder, the agent prompts,
and a worked example. F(AI)²R complements but does not duplicate
FAIR4RS [Chue Hong et al., 2022] (research software) and FAIR4ML
[RDA, 2024] (ML models) — neither yet covers AI-mediated research
*processes* (exportable transcripts, versioned prompts,
verification-status ladders, structured redaction).

Shepard implements F(AI)²R's vocabulary as a vendor-tier ontology
and wires it into every AI-driven write path. The relationship:
F(AI)²R is the *emerging community standard* (proposal + reference
TTL); Shepard is *the operational platform* that consumes it. This
is conceptually identical to Shepard adopting PROV-O or DataCite —
not "DLR adopts an in-house project," but "Shepard implements a
W3C-style standard from the field."

### The f(ai)²r vocabulary, mapped to Shepard

Direct adoption of `https://noheton.org/f-ai-r/ns#` (prefix
`fair2r:`):

```turtle
# Agents
fair2r:AIAgent          ⊑ prov:SoftwareAgent    # an LLM/agent system
fair2r:HumanResearcher  ⊑ prov:Person           # the human collaborator

# Entities
fair2r:Claim            ⊑ prov:Entity   # a verifiable assertion in stored output
fair2r:Source           ⊑ prov:Entity   # a cited source
fair2r:Prompt           ⊑ prov:Plan     # the prompt that drove an activity
fair2r:Transcript       ⊑ prov:Entity   # full session record

# Activities
fair2r:AuthoringPass    ⊑ prov:Activity # an AI-driven *write* action
fair2r:AuditPass        ⊑ prov:Activity # an AI-driven *verify* action

# Verification ladder (rungs as named individuals)
verif:unverified, verif:needs-research, verif:lit-retrieved,
verif:ai-confirmed, verif:human-confirmed, verif:source-vendored,
verif:lit-read

# Properties
fair2r:verificationState  (Claim → VerificationState)
fair2r:contradicts        (Claim → Claim)
fair2r:repairs            (Activity → Claim)  # provenance-metadata fix
```

### The mapping to Shepard's data model

| f(ai)²r concept | Shepard manifestation |
|---|---|
| `fair2r:AuthoringPass` | Any MCP-driven `POST/PATCH/PUT` activity captured by PROV1a |
| `fair2r:AuditPass` | Any MCP-driven `GET` (typed when the caller explicitly identifies as AI) |
| `fair2r:AIAgent` | The MCP agent — one named individual per model (`agent:claude-opus-4-7`, `agent:gpt-5o`, `agent:qwen3-coder-30b`, `agent:saia-gwdg-llama`, …) |
| `fair2r:HumanResearcher` | The OIDC user the AI acted on behalf of (`prov:actedOnBehalfOf`) |
| `fair2r:Prompt` | A `:StructuredData` document carrying the prompt text + hash + system prompt + RAG context — optional, plugin-contributed |
| `fair2r:Claim` | A typed annotation on a DataObject — every AI-generated `SemanticAnnotation` IS a claim |
| `fair2r:verificationState` | An annotation property on the claim — defaults to `verif:unverified` |

### The no-parentless-claim invariant

f(ai)²r's strongest invariant: *every `fair2r:Claim` must carry a
`prov:wasGeneratedBy` edge to a named Activity*. Shepard enforces
this via SHACL — a shape constraint on `fair2r:Claim` that says
`sh:property [sh:path prov:wasGeneratedBy ; sh:minCount 1]`.
Submission of an unannotated claim fails validation at the gateway.

This is the structural fix for "I don't remember which AI wrote
this annotation" — the invariant makes that question always
answerable.

### Header convention for AI-tagged requests

Every MCP/agent-driven request to Shepard carries:

| Header | Semantics |
|---|---|
| `X-AI-Agent: <agent-iri-or-id>` | Which model/agent. Maps to `fair2r:AIAgent` individual. |
| `X-AI-Prompt-Hash: <sha256>` | Content-addressed prompt fingerprint |
| `X-AI-Prompt-ID: <ent-iri>` | Optional: reference to a `fair2r:Prompt` entity stored separately (full text in `:StructuredData`) |
| `X-AI-Verification: unverified \| ai-confirmed \| human-confirmed` | Defaults to `unverified` |
| `X-AI-Session-ID: <transcript-iri>` | Optional: links to the conversation transcript |

MCP tool implementations (Claude Code, Claude Desktop, custom
agents) populate these headers automatically. The
`McpAuthFilter` we just refactored becomes the natural place to
read them and annotate the resulting `:Activity` node.

### Activity capture extensions to PROV1a

Today PROV1a captures the bare `:Activity` (method, path,
timestamp, user). Extension on the AI path:

```turtle
act:upload-2026-05-21-abc
    a fair2r:AuthoringPass ;
    prov:startedAtTime "2026-05-21T16:01:33Z"^^xsd:dateTime ;
    prov:wasAssociatedWith agent:claude-opus-4-7 ;
    prov:wasAssociatedWith agent:human-fkrebs ;  # on whose behalf
    prov:used ent:prompt-sha256-abc123 ;          # the prompt
    prov:generated ent:annotation-7831 .           # the resulting claim

ent:annotation-7831
    a fair2r:Claim ;
    prov:wasGeneratedBy act:upload-2026-05-21-abc ;  # invariant satisfied
    fair2r:verificationState verif:unverified .
```

Every AI-generated DataObject is now queryable as:

```sparql
SELECT ?do ?model ?state WHERE {
  ?do prov:wasGeneratedBy ?act .
  ?act a fair2r:AuthoringPass ;
       prov:wasAssociatedWith ?model .
  ?model a fair2r:AIAgent .
  ?do fair2r:verificationState ?state .
  FILTER (?state = verif:unverified)
}
```

…answers "which AI-generated data is still unverified in this
Collection."

### Frontend surface

| UI affordance | Behaviour |
|---|---|
| **AI provenance badge** | Every DataObject card / detail page gets a small badge showing "Generated by Claude Opus 4.7 (unverified)" if the wasGeneratedBy chain includes an `fair2r:AIAgent`. Click → opens the Activity detail (model + prompt + timestamp + verification state). |
| **Verification state pill** | Colour-coded: red (unverified), yellow (ai-confirmed), green (human-confirmed). Verification is a one-click action for the data owner. |
| **"AI activity" facet** | Collection list view gets a facet "Show only AI-generated / human-only / mixed" — built from the existing facet renderer (Part 2). |
| **Prompt provenance** | Detail page has a "Show prompt" toggle for any AI-generated artefact — opens the stored `fair2r:Prompt` content. Optional per-instance config: prompts publicly visible, prompts admin-only, prompts hashed-only. |
| **Verification ladder UI** | A small ladder widget showing where on `verif:*` the claim sits. Promoting up the ladder is the action; the path is logged as a new `:Activity`. |

### Plugin SPI contribution

Plugins that use AI (e.g. `shepard-plugin-ai` for auto-annotation;
`shepard-plugin-wiki-writer` for Confluence sync) ship a small
contract:

```java
public interface AiActivityCapture {
    void recordActivity(
        Class<? extends Activity> activityType,  // AuthoringPass / AuditPass
        AiAgent agent,                            // model identifier
        String promptHash,
        Optional<String> promptContent,
        VerificationState initialState
    );
}
```

The plugin SPI provides this; plugins call it before/after each AI
invocation. The default implementation writes to the `:Activity`
graph via the existing PROV1a path. No new endpoints; no new
storage.

### Implementation slices

| Slice | Scope | Days |
|---|---|---|
| **TPL9a** | Bootstrap f(ai)²r namespace as vendor-tier ontology in V## (vendor the TTL from <https://github.com/noheton/f-ai-r/blob/main/doc/provenance.ttl>; ~50 axioms; MIT-licensed) | 1 |
| **TPL9b** | Extend PROV1a capture: detect `X-AI-Agent` header; type Activities as `fair2r:AuthoringPass`/`AuditPass`; link to agent individual; mint Activity IRI | 2 |
| **TPL9c** | SHACL shape enforcing the no-parentless-claim invariant on `fair2r:Claim`-typed annotations | 1 |
| **TPL9d** | Frontend AI provenance badge + verification state pill + facet | 4 |
| **TPL9e** | Prompt-content storage + UI viewer (configurable visibility) | 3 |
| **TPL9f** | "Verification ladder" UI widget for one-click promotion through the rungs; each promotion creates a `fair2r:repairs` Activity | 3 |
| **TPL9g** | MCP tools auto-populate the X-AI-* headers in their outbound calls (today's MCP tools already know they're MCP-driven; just need to write the headers) | 1 |
| **TPL9h** | SPARQL canned queries — "all unverified AI-generated claims", "all activities by agent X this week", "all human-confirmed annotations" — exposed in N1f SPARQL UI | 2 |

**Total: ~17 days.** Slots in as a milestone-M2-companion or
parallel M3 work. Pairs naturally with TPL5 (git ingestion of the
f(ai)²r namespace from upstream) and TPL3 (upper-ontology
bootstrap migration carries the f(ai)²r vendoring).

### Strategic positioning

This closes three otherwise-open gaps at once:

1. **EU AI Act Article 50 compliance** — Shepard ships transparent
   AI-output labelling natively, not as an afterthought.
2. **FAIR-for-AI** — the f(ai)²r reference paper is the
   methodology; Shepard becomes the operational platform that
   implements it. Researchers writing AI-aided papers (Nature,
   Science increasingly requiring disclosure) have an audit trail
   to point at.
3. **Author-side coherence** — The same researcher (Krebs)
   articulated the F(AI)²R proposal in published work and is
   building the Shepard implementation. The intellectual lineage
   is auditable: Obscurity-Is-Dead §8 → F(AI)²R reference repo →
   Shepard runtime. Citation hygiene preserved (the proposal is
   independent / non-DLR work; Shepard is the DLR-fork
   implementation that consumes it).

The competitive claim becomes:

> "Shepard is the RDM platform where every AI interaction that
> produces stored output is captured as a typed PROV-O activity,
> visible to operators, queryable via SPARQL, and verifiable
> through the f(ai)²r verification ladder."

No other platform in the reference set ships this. It's a moat.

### Open questions

- **Prompt visibility default.** Some institutes will want prompts
  public; others restricted to data owners (proprietary methods).
  Per-instance config; default to admin-only.
- **What counts as a "claim".** Strict: every AI-generated
  annotation. Loose: every AI-generated artefact (DataObject,
  Container, Reference). Recommend strict for the SHACL invariant,
  loose for the badge UI.
- **Verification of compound claims** (a paragraph summarising 10
  sources). f(ai)²r handles this via `fair2r:Source` + per-claim
  source list; Shepard inherits the pattern.

### Companion doc

Worth a small companion `aidocs/integrations/97-fair2r-integration.md`
that documents the exact header convention, the SPARQL canned
queries, and the migration path for existing AI-touched data
(retro-type any historical Activity whose user-agent string
indicates an AI source).

---

## 14f. Part 16 — Distributed-ledger anchoring for tamper-evident audit

The three regulatory frameworks Shepard now intersects (EASA AI
Concept Paper Issue 02, EU AI Act 2024/1689 Article 50, EU
Machinery Regulation 2023/1230) all require **immutable audit
trails**. EU 2023/1230 mandates 10-year retention for technical
files + 5-year retention for safety-software change logs;
EASA's W-shape generates artefacts a notified body audits years
later; Article 50 mandates machine-readable AI-output marking
that must remain verifiable.

The integrity problem: today, an auditor must trust that
Shepard's PROV-O graph + Regulatory Evidence Pack (TPL14) +
F(AI)²R activities (TPL9) have not been retroactively edited
between the time the work happened and the time the auditor
arrives. The traditional answer is "operational controls,
write-once storage, signed backups" — fine, but defeasible.

A **distributed-ledger anchor** gives **tamper-evident proof of
existence at time T** without putting the data itself on chain:

```
At each REP export / PROV-O snapshot / F(AI)²R Activity:
    1. Compute SHA-256 root hash of the artefact (graph + bundle)
    2. Submit (hash, timestamp, optional metadata IRI) to the chosen ledger
    3. Receive transaction ID; store it on the Activity / REP record
    4. Auditor later: recompute hash → verify against ledger
       → confirm timestamp → trust the audit trail
```

The data never goes on chain — only the hash. GDPR safe. Cheap
to operate. Verifiable indefinitely.

### Choice of ledger — three viable options

| Ledger | Cost | Privacy | DLR-feasibility | Notes |
|---|---|---|---|---|
| **Bitcoin (via OpenTimestamps)** | ~free batched | Public hash only | Highest trust; truly decentralised; well-understood | Settlement latency ~1 hour; mature service via opentimestamps.org |
| **Ethereum (mainnet)** | ~$1–10 per anchor at current gas | Public hash only | Trust comparable to Bitcoin; faster settlement; smart-contract option | Cost volatile; tooling: chainpoint.org, Bloxberg |
| **Bloxberg** | Free for academics | Public hash only | **Academic-purpose-built**, governed by 50+ academic institutions including DLR | <https://bloxberg.org>; proof-of-existence API; ideal default |
| **Polygon / sidechain** | <$0.01 per anchor | Public hash only | Cheap; less institutional trust | Useful for batch-anchoring intermediate Activities |
| **DLR-internal Hyperledger Fabric** | Operational only | Permissioned (no public hash leak) | Maximum sovereignty | Loses the "external trust anchor" property — auditor must trust DLR's ledger |

**Recommendation:** **Bloxberg** as primary anchor (academic
trust, free, purpose-built); **OpenTimestamps/Bitcoin** as
cross-anchor for the highest-stakes artefacts (a single REP for
a Part 21 submission would land on both). DLR-internal Hyperledger
only if data-sovereignty constraints absolutely forbid public
hash visibility — which is rare for SHA-256 of an evidence pack.

### Shepard architecture

A new plugin: **`shepard-plugin-ledger-anchor`**.

```
shepard-plugin-ledger-anchor
   ├── adapter/bloxberg.py      (primary)
   ├── adapter/opentimestamps.py
   ├── adapter/ethereum.py
   ├── adapter/polygon.py
   └── adapter/hyperledger.py   (DLR-internal option)
```

Wiring:

1. Every TPL9 `fair2r:AuditPass` Activity, every TPL14 REP
   export, every PROV1a `:Activity` of `type ∈
   {AnnotationActivity, ApprovalActivity, ReleaseActivity}`
   becomes a candidate for anchoring
2. SHACL shape declares `shepard:anchorRequired true` on the
   activity type that needs it (per-instance config: a research
   instance might anchor only on REP export; a regulated-prod
   instance might anchor every Activity)
3. Plugin computes hash, submits to ledger, stores
   `(ledgerName, transactionId, anchoredAt)` triple on the Activity
4. UI badge: "🔒 Anchored on Bloxberg, 2026-05-21T16:32Z, txid abc…"
5. Verifier action: one-click "verify anchor" recomputes hash +
   queries ledger → green/red pill

### The graph axioms

```turtle
@prefix shepard: <http://semantics.dlr.de/shepard#> .
@prefix prov:    <http://www.w3.org/ns/prov#> .

shepard:LedgerAnchor      rdfs:subClassOf  prov:Entity ;
    rdfs:label "Distributed-ledger anchor"@en .

shepard:hashSha256        a owl:DatatypeProperty ;
    rdfs:domain shepard:LedgerAnchor ; rdfs:range xsd:hexBinary .

shepard:ledgerName        a owl:DatatypeProperty ;
    rdfs:domain shepard:LedgerAnchor ; rdfs:range xsd:string .

shepard:ledgerTxId        a owl:DatatypeProperty ;
    rdfs:domain shepard:LedgerAnchor ; rdfs:range xsd:string .

shepard:anchoredAt        a owl:DatatypeProperty ;
    rdfs:domain shepard:LedgerAnchor ; rdfs:range xsd:dateTime .

shepard:anchors           a owl:ObjectProperty ;
    rdfs:domain shepard:LedgerAnchor ; rdfs:range prov:Entity .
```

A typical anchor:

```turtle
shepard:anchor-rep-mffd-2026-001
    a shepard:LedgerAnchor ;
    shepard:hashSha256 "a4b2c1f3…"^^xsd:hexBinary ;
    shepard:ledgerName "bloxberg" ;
    shepard:ledgerTxId "0xdeadbeef…" ;
    shepard:anchoredAt "2026-05-21T16:32:00Z"^^xsd:dateTime ;
    shepard:anchors shepard:rep-mffd-2026-001 .
```

### What this composes with

- **TPL9 F(AI)²R** — every `fair2r:AuditPass` can be optionally
  anchored. The verification ladder gets a new rung:
  `verif:ledger-anchored` (above `verif:human-confirmed`).
- **TPL14 REP** — every REP export gets anchored by default. The
  BagIt manifest carries the anchor proof; receivers can verify
  without trusting Shepard.
- **TPL11 Independence proof** — the Cypher query result + the
  anchor proves "the independence claim was true at time T and
  has not been edited since".
- **EU 2023/1230 Article 17(2)** retention — the technical file's
  10-year retention obligation can be satisfied by a 10-year-old
  anchor that still verifies, even after the maintaining institute
  is reorganised.

### Cost analysis

For DLR-scale operation:
- Bloxberg: **free** (academic governance includes DLR)
- OpenTimestamps batched: free, settles via Bitcoin every ~1 hour
- Anchoring frequency: ~10 REP exports/month per active campaign,
  ~1000 individual `fair2r:AuditPass` activities/day per institute
- Storage cost: ~50 bytes per anchor in Neo4j (the triple cluster
  above)
- Total cost: zero operational expense; one-time plugin development

### Honest caveats

- **Bloxberg dependency:** if Bloxberg shuts down (academic
  governance can change), the anchor's verifiability is lost. The
  cross-anchor pattern (Bloxberg + OpenTimestamps simultaneously)
  mitigates: even if Bloxberg disappears, the OpenTimestamps proof
  via Bitcoin continues to verify for as long as Bitcoin exists.
- **Hash collision attacks on SHA-256** — theoretical only at
  current state of art; if SHA-256 falls, the verification model
  needs algorithm migration (compute new hash with new algorithm,
  anchor the migration activity itself).
- **Quantum risk:** SHA-256 is reasonably quantum-resistant for
  collision finding (Grover speedup is only quadratic); not
  vulnerable on the same timeline as ECDSA signing. The Bitcoin
  / Ethereum signing layer is the part that may need post-quantum
  migration, not the SHA-256 anchor itself.
- **Bloxberg's smart-contract layer** is permissioned; if DLR's
  membership lapses, write access is lost (read / verify is
  always public).

### Implementation slice — TPL17

| Slice | Scope | Days |
|---|---|---|
| **TPL17a** | `shepard:LedgerAnchor` ontology + SHACL shape; `shepard:anchorRequired` shape property | 1 |
| **TPL17b** | `shepard-plugin-ledger-anchor` core + Bloxberg adapter | 4 |
| **TPL17c** | OpenTimestamps adapter (cross-anchor for high-stakes artefacts) | 2 |
| **TPL17d** | Frontend "🔒 Anchored" badge + one-click verify action | 3 |
| **TPL17e** | Auto-anchor wiring on TPL9 AuditPass + TPL14 REP export | 2 |
| **TPL17f** | Re-anchor migration path (if a hash algorithm needs replacing in future) | 1 |

**Total: ~13 days.** Slot after TPL9 + TPL14 ship, before any
external claim that Shepard provides "tamper-evident audit
trails."

### Strategic positioning addition

This is the **single sentence that closes the regulatory pitch:**

> "Every audit-relevant artefact in Shepard — provenance trail,
> AI-activity record, regulatory evidence pack — is anchored to a
> distributed ledger at creation time. Verification is one click;
> 10-year retention is structurally guaranteed."

Bloxberg + DLR's academic membership makes this both
philosophically and operationally defensible, where competitor
RDM platforms have **none** of this infrastructure.

---

## 15. Implementation slices

| Slice | Scope | Days |
|---|---|---|
| **TPL1a** | Neo4j entities `:Shape`, `:PropertyShape`; TTL loader in SemanticRepository bootstrap | 3 |
| **TPL1b** | REST `GET /v2/templates`, `GET /v2/templates/{appId}/shape`, `POST /v2/templates/{appId}/instantiate` | 3 |
| **TPL1c** | Frontend form generator from shape JSON; widget map (10 mappings); recursive `sh:node` rendering with lazy expand + cycle guard | 5 |
| **TPL1d** | Worked examples shipped in V## migration: `:EUProjectShape`, `:MFFDCampaignShape` + mini-shape library | 2 |
| **TPL1e** | Writable-shape admin UI v1 — **TTL paste-box** (validate + save), NOT a visual editor. A real visual SHACL editor is a 6-month project (cf. Protégé) and out of scope for this milestone. v2 adds field-level edit on top of v1. | 3 |
| **TPL1f** | MCP tools: `list_templates`, `instantiate_template`; shape→tool-input-schema generator | 1 |
| **TPL1g** | Plugin SPI: `plugins/<id>/shapes/*.ttl` auto-discovery | 2 |
| **TPL1h** | Composition + mini-shape library docs | 2 |
| **TPL2a** | Composite index on `(propertyIRI, valueIRI)` + lineage-depth cap | 1 |
| **TPL2b** | Materialized facet counts trigger | 3 |
| **TPL2c** | List-column / detail-tab / facet renderers driven by shape. **MUST be feature-flagged per-Collection (default off).** Changing list columns / detail tabs is a UX disruption for existing users; opt-in by Collection-admin only. | 5 |
| **TPL2d** | **Performance baseline at MFFD scale** — generate 10⁵ DOs synthetic; load test pre/post indexes | 2 |
| **TPL3** | Upper-ontology alignment + companion doc (`aidocs/semantics/96`) + starter-kit | 4 |
| **TPL4** | Backward-compat migration: attributes → annotations dual-write; deprecation warnings. **Honest long-tail framing:** the default vocab-mapping table covers the top-20 attribute keys (propellant, bench, test_engineer, …); the long tail goes to `shepard:legacy/<key>` synthetic predicates with no semantic meaning. This is a **lossy migration**; each institute's long-tail keys may need manual upgrade post-cutover. | 4 |
| **TPL5a** | Git ontology ingestion: backend git client, ROBOT-style validation, webhook receiver | 4 |
| **TPL5b** | Git ontology ingestion: admin UI, dry-run diff preview, branch-per-environment. **MUST include rollback procedure:** admin approves a bad ontology load → instances start failing SHACL → `git revert` upstream → "Sync now" admin click → preview shows diff in reverse → instances depending on removed terms get flagged for manual review. The shape-pinning model (instances pin to a shape version) gives most of the safety; the explicit rollback path closes the gap for the un-pinned middle. | 3 |
| **TPL5c** | DLR ontology audit (`aidocs/agent-findings/dlr-ontology-catalog.md`) + integration shortlist | 1 |
| **TPL6a** | Multi-parent membership in V2 schema + endpoints + migration | 5 |
| **TPL6b** | Saved-query / saved-view storage + Vue widget | 5 |
| **TPL6c** | Network views (process network + knowledge graph) — reuses existing graph renderer | 5 |
| **TPL6d** | Cross-Collection navigation polish — namespace-aware breadcrumbs | 3 |

Total: ~70 days. Realistic delivery in **10 weeks one-engineer** or
**6 weeks parallelised**. Three milestones:
- **M1** (TPL1*+TPL2a/b/c): MFFD demonstrator ships — "one click
  creates the entire seven-step campaign"
- **M2** (TPL2d+TPL3+TPL4+TPL5): production-ready — load-tested,
  upper-aligned, backward-compatible, git-ingested ontologies
- **M3** (TPL6): network-shaped organisation surfaced —
  multi-parent membership, saved network views, knowledge-graph
  navigation

---

## 16. Strategic positioning

This design is the differentiator for grant pitches and ecosystem
positioning. Concretely:

- **Clean Aviation JU / Horizon Europe pitch:** "Shepard is the
  RDM platform where the ontology drives the UI. Adding a new use
  case is a TTL file, not a sprint."
- **DLR institute adoption pitch:** "Your domain experts write the
  shape; you don't need a frontend developer. BT, RY, future
  institutes onboard in days."
- **OBO Foundry / FAIR-IM pitch:** BFO + IAO + EMMO alignment,
  PROV-O native, full SHACL validation, all open standards.
- **MFFD demonstrator pitch:** "Look — MFFD and LUMEN are the same
  Shepard with two different ontologies. Same Vue components, two
  completely different domain UIs." (Screenshot moment.)

The competitive position against:
- **Kadi4Mat:** templates exist but flat config, no ontology root,
  no SPARQL queryable
- **openBIS:** typed entities but proprietary XML schema
- **Coscine:** RDF profiles only drive form input, not the rest of
  the UI
- **SciCat / FAIRDOM / NOMAD:** generic fields on fixed UI
- **Solid / data-shapes:** SHACL→forms for personal pods, not
  multi-user research workflows

Nobody else has the full chain: SHACL shape → list view + detail
view + filters + form + MCP tool input + export mapping. Doing
this in Shepard is **the moat**.

---

## 17. Open questions / non-goals

- **OBO Foundry submission of `shepard-upper:`?** Tempting but
  bureaucratic. Defer until ≥2 institutes use it.
- **Real-time collaborative shape editing?** Out of scope for v1.
  Pessimistic locking is fine for instance-admin work.
- **Reasoning at instance level (OWL DL)?** Out of scope.
  Reasoning happens at shape-validation time only.
- **Migration from existing ad-hoc attributes vocabulary to typed
  annotations** — covered in §13 (Part 10) but the *content* of
  the default mapping table needs domain review per institute.

---

## 18. Companion docs

- `aidocs/semantics/96-upper-ontology-alignment.md` — BFO/IAO/EMMO
  alignment table + worked examples (separate doc, ships
  alongside the bootstrap migration in TPL3)
- `aidocs/integrations/92-mffd-real-data-import-strategy.md` — the
  MFFD-specific worked example using this design
- `aidocs/34-upstream-upgrade-path.md` — operator-facing tracker
  entry per §13

---

**Authorship.** Drafted 2026-05-21 from architectural discussion.
Captures: SHACL templates (parts 1–3), individuals (part 4),
composition (part 5), writable layer (part 6), upper-level
alignment (part 7), graph relationship (part 8), scaling (part 9),
backward compat (part 10), plugin model (part 11),
implementation slices, strategic positioning, open questions.
