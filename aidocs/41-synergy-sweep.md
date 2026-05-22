# 41 — Synergy sweep: collapse-where-generalisation-helps

**Status.** Strategic synthesis — post-design-explosion.
**Snapshot date.** 2026-05-21.
**Audience.** Maintainers asking "do we really need N plugins / N concepts / N flows?"
**Originating prompt.** User 2026-05-21: *"do a synergy sweep over all captured ideas in docs and here. collapse features where generalisation would help — same for plugins (publish is great example)"*

This doc is a forcing function: take everything in `aidocs/40` ecosystem inventory + `aidocs/95` SHACL design + `aidocs/96` upper ontology + `aidocs/97` modern REBAR + the captured prompts, and ask: **where are we modelling the same idea twice?**

Each section names a generalisation and lists what it absorbs.

---

## 1. The **Capability** abstraction (the user's eureka)

> *"shepard capability ontology (extended by plugins). CLASS `_store` → FileBundle matches to either S3 or Gridfs. Basically an abstraction to design what you want rather than what you model."*

This is the single biggest collapse in the whole design. Today Shepard has:

- `FileContainer` with `StorageProvider` SPI choosing between S3 / GridFS / local
- `TimeseriesContainer` with one backend (InfluxDB, soon TimescaleDB)
- `StructuredDataContainer` with MongoDB
- Six container kinds in `PayloadKind` SPI, each separately modelled

**Capability collapses this:** users declare what they want (`shepard:storeBytes`, `shepard:storeSamples`, `shepard:storeDocuments`, `shepard:storeRelations`); the platform binds them to whatever provider is configured.

```turtle
shepard:Capability   a owl:Class .
shepard:storeBytes      a shepard:Capability ; rdfs:comment "store opaque byte blobs" .
shepard:storeSamples    a shepard:Capability ; rdfs:comment "store ts-indexed samples" .
shepard:storeDocuments  a shepard:Capability ; rdfs:comment "store JSON-shaped records" .
shepard:storeRelations  a shepard:Capability ; rdfs:comment "store typed edges (graph)" .
shepard:authenticate    a shepard:Capability .
shepard:notify          a shepard:Capability .
shepard:publish         a shepard:Capability .
shepard:anchor          a shepard:Capability .
shepard:reason          a shepard:Capability .
```

Plugins **register capabilities** instead of "being a container kind". Composition is `sh:and`-style:

```
shepard:FileBundle  ⊑ Capability[storeBytes]
shepard:Timeseries  ⊑ Capability[storeSamples]
mffd:CalibrationCert ⊑ Capability[storeBytes, storeDocuments]  ← already composite!
```

**What this absorbs:**
- The 6 PayloadKind SPI flavours collapse to compositional capability sets
- StorageProvider SPI (S3/GridFS/local) becomes one of many providers FOR `storeBytes`
- TimescaleDB vs InfluxDB becomes a provider choice FOR `storeSamples` — operators pick, users don't care
- Person = User, API-key acts-on-behalf-of-User → both `Capability[authenticate]`, one inherits the other's act
- The PROV-O angle is native: `prov:Agent` is anything that has `Capability[authenticate]`

**TPL-slice impact:** TPL3 should bootstrap the capability ontology **first**; everything else attaches to it. Saves ~3 TPLs worth of one-off modelling.

---

## 2. The **Publish family** collapses to one plugin

The publish plugin family already started collapsing during design. Final shape:

```
shepard-plugin-publish  (one plugin)
   ├── adapter/databus.py       ← Dataship-derived (DBpedia)
   ├── adapter/unhide.py        ← Helmholtz Unhide (UH1 series)
   ├── adapter/openaire.py      (future)
   ├── adapter/re3data.py       (future)
   ├── adapter/datacite.py      (DOI minting; mandatory for FAIR-publish)
   ├── adapter/zenodo.py        (academic citation, pairs with DataCite)
   ├── adapter/fdo-publisher.py ← FDO (FAIR Digital Object) — user's open question
   └── adapter/aas-publisher.py ← AAS submodels (Industrie 4.0)
```

All adapters consume the same shape — `shepard:PublicationShape` — and emit registry-specific JSON-LD / RDF. Differences are render-time, not model-time.

**What this absorbs:**
- UH1a–UH1d (Helmholtz Unhide plugin design)
- Dataship (DBpedia Databus)
- Future OpenAIRE / DataCite / Zenodo / FDO / AAS bridges
- The "long-running task" workflow (deposition creation, sign-off by Data Governance Officer) is the **plugin-publish workflow extension** — one workflow shape, one approval-Activity pattern, N adapters

**FDO open question (the user's):** publishing an FDO entails (a) minting a Handle / DOI via the `pidinst` plugin (existing project memory), (b) materializing the FDO record as a JSON-LD document conforming to RDA's FDO Spec, (c) lodging the record at a FDO-aware registry (Cordra is the reference). The adapter is ~150 lines of Python; FDO is *not* a separate plugin family, it's one more `shepard-plugin-publish` adapter.

---

## 3. The **Ingest family** is the publish family's mirror

Today the design has:
- `shepard-plugin-importer` (library of importers, git-referenced — project memory)
- `shepard-plugin-industrial` (OPC UA / Modbus — DRG generalisation)
- `shepard-plugin-mlops` (OpenLineage / MLflow — REBAR generalisation)
- TPL5 git ontology ingestion
- The user's new ask: a **"backend dropbox"** — push data via simple POST

All five are **the same plugin** with adapters:

```
shepard-plugin-ingest  (one plugin)
   ├── adapter/dropbox.py       ← NEW — backend POST receiver (the user's ask)
   ├── adapter/opc-ua.py        ← DRG generalisation, edge collection
   ├── adapter/modbus.py
   ├── adapter/mlflow.py        ← MLflow Tracking REST
   ├── adapter/openlineage.py   ← Airflow / Spark / dbt webhook receiver
   ├── adapter/git.py           ← TPL5 ontology / dataset ingestion
   ├── adapter/aas-ingest.py    ← AAS submodel POST (Industrie 4.0)
   └── adapter/ai-classifier.py ← AI-driven "figure out what this is" ← user's killer feature
```

The **dropbox adapter** the user just asked for is the simplest member of the family: `POST /v2/ingest/dropbox/<bundle-name>` accepts any payload, lands it as a FileBundle, optionally typed by a shape if the caller declares one.

**What this absorbs:**
- The user's *"dropbox where I can push data directly"* request
- DRG / industrial-cell ingestion (per project memory `project_drg_tooling.md`)
- REBAR-derived OpenLineage receiver
- shepard-plugin-importer library-of-importers
- TPL5 git ontology ingestion
- The **"unified ingestion UI" killer feature** — the AI classifier IS just another adapter; user uploads, AI classifier decides which adapter to route to

This is the structural answer to *"shepard figures out what to do with the data, how to represent it, annotate it"* — the AI classifier picks the right adapter, suggests a shape, and routes.

---

## 4. **Edge collection** is the right scope for `shepard-plugin-industrial`

The user: *"shepard-plugin-industrial I think this should be more on the edge. Integrated in timeseries collector. (collection happens on the edge)"*

Correct. The DRG pattern is **edge-side**, not Shepard-side:

```
   ┌─── Industrial cell (PLC / robot) ─────┐
   │   OPC UA server                       │
   │   ↓                                   │
   │   shepard-edge-collector              │ ← runs at the cell, not in Shepard
   │   (Python; small container)           │
   │   ├── subscribes to PLC variables     │
   │   ├── declared via SHACL shape        │
   │   ├── batches samples                 │
   │   └── POSTs to /v2/ingest/dropbox     │ ← uses the dropbox adapter
   └───────────────────────────────────────┘
                      ↓
                   Shepard
```

Reframing: `shepard-plugin-industrial` is split:
- **server side:** just a dropbox adapter variant that validates `Industrial` shapes
- **edge side:** a deployable Python collector (`shepard-edge-collector`) that lives on the cell PC and ships samples up

Design doc for the edge collector: `aidocs/integrations/98-edge-collector.md` (new — to draft separately).

---

## 5. **Saved views / Smartbundles / Sub-collections** are one concept

The user mentioned:
- *"smartbundles — show me all jpgs larger than 6kb in FileContainer"*
- Earlier: TPL6 "saved network views" (saved SPARQL queries with live UI)

These are the same thing: **a saved query that materializes as a virtual Collection / list**. One feature absorbs both:

```
shepard:SavedView  ⊑ shepard:Collection ;  # behaves like a Collection in the UI
                    rdfs:subClassOf prov:Bundle ;
   :hasQuery   shepard:CypherQuery | shepard:SparqlQuery ;
   :refreshes  shepard:Manual | shepard:OnAccess | shepard:Periodic .
```

`"all jpgs > 6kb"` is one canned query template. `"all unverified AI-claims"` (per TPL11) is another. Same widget, same shape, same persistence.

**What this absorbs:**
- Smartbundles
- Saved network views (TPL6b)
- Faceted-filter snapshots (TPL2c)
- The "Regulatory Evidence Pack target query" (TPL14)
- ODIX-style "show me all anomalies for this campaign" — that's a SavedView too

---

## 6. **Template strict-vs-loose** is one boolean on the shape

User: *"template enforcement: strict mode on shapes (exactly this shape) — and loose elements can be added"*

SHACL has the native primitive:
```turtle
:MFFDCampaignShape  sh:closed true   # strict mode
                  ; sh:ignoredProperties (rdf:type rdfs:label) .

:LooseCollectionShape sh:closed false  # loose — extra annotations OK
```

`sh:closed true` is "exactly these properties only; rejection on extra". `sh:closed false` (default) is loose. One toggle, no new design.

**TPL slice impact:** TPL1c's form renderer should respect `sh:closed` — in strict mode, hide the "+ Add custom annotation" button; in loose mode, show it.

---

## 7. **AAS endpoints** are dropbox-adapter + publish-adapter

User notes AAS endpoints are still in OpenAPI. The current shape is right:
- **AAS submodels** flowing IN to Shepard = ingest/aas-ingest adapter
- **AAS submodels** flowing OUT of Shepard = publish/aas-publisher adapter
- **AAS resource** as a first-class concept inside Shepard = NO — too coupled to Industrie 4.0 specifics; use `Capability[storeDocuments]` + an AAS-typed shape instead

So: keep the OpenAPI definitions (they're useful as a contract); remove the bespoke AAS storage logic; route through ingest/publish adapters.

---

## 8. **Notification / approval / sign-off** is one workflow plugin

Several places need approval workflows:
- Dataship publish to Databus needs "sign-off by Data Governance Officer" (the user's flag)
- TPL14 REP export needs sign-off Activity
- TPL10 DQR record carries `sign-off Activity` slot
- TPL5 git ontology ingestion needs admin approval
- AAS publish to a public registry needs same
- F(AI)²R verification-ladder climb needs human confirmation

All of these are **the same workflow primitive**: an Activity that names an Agent, requires a specific role, transitions a target Entity through a state machine, fires a notification on completion.

```
shepard-plugin-workflow
   ├── core: WorkflowDefinition shape + WorkflowInstance + Step
   ├── canned: ApprovalWorkflow (sign-off pattern)
   ├── canned: VerificationLadder (F(AI)²R promotion)
   └── canned: PublicationGate (DGO sign-off before publish)
```

This is a NEW plugin we hadn't yet named; it absorbs functionality scattered across TPL9, TPL10, TPL14, publish-family, ingest-family.

---

## 9. **Pipeline execution** absorbs MLOps + Notebooks + Workflow

The user wrote:
> *"synergy with jupyter hub? modules jupyter notebooks + orchestration dag via elyra like interface... jobs could be semantically parametrized to a shacl shape"*

Yes. `shepard-plugin-pipelines` (TPL18) absorbs:
- REBAR-style MLOps (Airflow / MLflow / MinIO / Marquez collapse)
- Notebook orchestration (Elyra-style drag-and-drop)
- JupyterHub integration (J2 series — designed but un-shipped per task #60)
- AI-driven re-organisation jobs (the user's `"AI uses new network model to generate a project knowledge graph iteration by iteration"`)
- The user's `"resize every jpg that arrives in context"` — that's a pipeline triggered by ingest events

The Elyra-style UI is **how a non-coder authors a pipeline.yaml**. Drag-drop emits the YAML manifest from §4 of aidocs/97. Same plugin, two authoring surfaces (YAML for power users, drag-drop for everyone else).

---

## 10. **The MBSE angle**: shapes ARE the model

The user asked for an MBSE perspective doc. The honest answer fits in one paragraph here, no new doc needed:

> Model-Based Systems Engineering's central claim is that the system is defined by interconnected models (requirements, behaviour, structure, parametric), not by documents. Shepard's ontology-driven design is operationally MBSE-shaped: SHACL shapes are the system structure model, PROV-O Activities are the behaviour model, annotation constraints are the parametric model, and the REP-export bundle IS the documentation projection. MFFD's process ontology, instance individuals, and the SHACL shapes around them are a working MBSE digital thread — the gap to SysML / Capella is that Shepard doesn't speak SysMLv2's wire format yet. A `shepard-plugin-mbse` would add SysMLv2 / OSLC / OSLC4MBSE input + output adapters — adding to the publish + ingest families. Not a new architecture; one more adapter pair.

That's the entire MBSE doc.

---

## The total collapse — before vs after

**Before this sweep (count from aidocs/40 + aidocs/95 + memories):**
- 6 PayloadKind variants
- 4+ separate publish plugins (Dataship, UH1, OpenAIRE-planned, AAS-planned)
- 4+ separate ingest plugins (DRG, importer, industrial, MLOps)
- TPL10 DQR + TPL14 REP + TPL9 F(AI)²R sign-off (3 sign-off mechanisms)
- TPL6b saved views + Smartbundles + faceted snapshots (3 query-persistence mechanisms)
- AAS, OPC UA, MLflow, OpenLineage as separate code surfaces

**After this sweep:**
- 1 capability ontology + N composable shapes
- 1 publish plugin + N adapters
- 1 ingest plugin + N adapters (including dropbox, ai-classifier, opc-ua-edge)
- 1 workflow plugin + N canned workflows
- 1 SavedView concept absorbing smartbundles + saved-views + facet snapshots
- 1 pipelines plugin absorbing MLOps + notebooks + ai-reorganisation

Same demonstrable capability surface, ~⅓ the named plugins, fewer cognitive types for the operator.

---

## Recommendation for the next sprint

The single highest-leverage move is **§1 — bootstrap the Capability ontology**. Every other collapse depends on Capability being the native type-discriminator. Concretely:

1. Write `aidocs/semantics/98-capability-ontology.md` (focused, 100-line companion to aidocs/96)
2. Add capability classes to the shape catalogue (`backend/src/main/resources/shapes/capability-shapes.ttl`)
3. Refactor the V## bootstrap migration to declare the capabilities first, then layer the legacy PayloadKind subclasses on top
4. **Defer** the actual plugin-folding work — let plugins continue as-is; just make sure their next-version SHACL declares which capabilities they cover. The folding happens organically as plugins iterate.

Slot in as **TPL19** (Capability bootstrap) — gated only on internal review, ~2 days of work.

---

## Open eureka — capture as memory

The user's exact phrasing was *"the ontology-first idea is the key"*. Capturing as a separate memory file (`feedback_ontology_first.md`) so future sessions lead with this framing instead of re-deriving it.
