---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor + maintainer + strategic
supersedes: none
---

# 106 — Shepard's Role in the Industrial-AI Value Chain

*Strategic positioning paper. Audience: maintainers prioritising the next wave of AI-related
work, contributors choosing between plugin / core / extension shapes, and stakeholders
asking "where does Shepard fit in the Plattform-Industrie-4.0 Industrial-AI agenda?"
Companion to [`aidocs/integrations/97`](../integrations/97-shepard-plugin-ai-design.md) (the
`shepard-plugin-ai` SSOT) and [`aidocs/strategy/70`](70-competitor-landscape-and-feature-ideas.md)
(competitive landscape). Cross-feeds [`aidocs/16`](../16-dispatcher-backlog.md) §INDAI-V6-\*.*

---

## §1 — TL;DR + thesis

**Thesis.** The Plattform-Industrie-4.0 Industrial-AI framework (March 2026) is a
four-layer stack: hardened infrastructure, industry-grade datasets, industry-proven
models, industrial AI solutions [^1]. Of those four layers, **Shepard already occupies
Layer 2 as a credible substrate** (its v2 model, PROV-O lineage, f(ai)²r typed
`:Activity` capture, and SHACL-validated annotations are a near-exact match for what
the framework calls "industry-grade datasets" with "consistent data language across
enterprise boundaries"). It does **not** belong in Layer 1 (compute), **partially**
in Layer 3 (model governance, not model serving), and as a **deployment substrate**
in Layer 4 (the place industrial AI solutions land their outputs and audit trail).

The honest positioning that follows from this — and that the rest of the paper
defends — is a five-role decomposition: substrate (shipped), training-data curation
(designed, partially shipped), model-evaluation harness (gap), model-deployment
governance (partially shipped via `:AIConfig`), audit-and-reproducibility (shipped
via PROV-O + f(ai)²r). The biggest gap is the model-evaluation harness; the second-biggest
is a model-card / model-registry primitive that ties EASA's Learning-Assurance
discipline [^2] to Shepard's existing Collection/DataObject lineage.

**Three architectural commitments** this paper proposes the project make explicitly:

1. **Shepard is the substrate, not the model.** Models live in Hubs (Sovereign
   AI Model Hubs per [^1]; HuggingFace; SAIA; GWDG); Shepard owns the **provenance**,
   **datasets**, and **evaluation runs** that point at them. Plugins are the integration
   shape — never a model serving runtime in core.
2. **Every AI-touched artefact carries a typed Activity row.** This is already a MUST
   per [`aidocs/integrations/97 §0`](../integrations/97-shepard-plugin-ai-design.md);
   this paper restates it as a strategic commitment because EU AI Act Article 50 [^3]
   (effective 2 Aug 2026) and EASA Concept Paper Issue 02 DA-09/DM-04 [^2] both
   require the same shape.
3. **Capability + Skill as first-class ontological terms.** The three external
   concept papers ([^4][^5][^6]) converge on AAS + MaRCO/CaSk capability/skill
   ontologies as the integration substrate for I4.0 plug-and-produce. Shepard's
   existing `shepard-plugin-aas` (designed in
   [`aidocs/integrations/52-aas-backend-integration.md`](../integrations/52-aas-backend-integration.md))
   should ship a capability/skill primitive that lets DataObjects carry a typed
   `realizes` edge to a Capability instance — closing the loop between "what data
   did we capture?" and "what production capability was exercised when capturing it?"

The paper closes with **fourteen INDAI-V6-\*** backlog rows (§10) to be filed in
`aidocs/16`. They are sequenced to land alongside the AI-V6 wave (which gates on
`PG-COLLAPSE-001` per [`aidocs/strategy/105 §8`](105-postgres-multitenant-decision.md)).

---

## §2 — The Plattform-Industrie-4.0 Industrial-AI framework

The Plattform-Industrie-4.0 article "Industrial AI: Driving Future Value Creation"
(March 2026) [^1] defines Industrial AI as the technological foundation for a
**resilient, sovereign, and competitive** European industry, achievable only when
four system layers are systematically coordinated:

| Layer | What it is | Plattform-i40 framing |
|---|---|---|
| **L1 — Hardened AI-ready Infrastructure** | Robust, scalable shared compute; cloud-edge continuum centralised DC ↔ decentralised production | "designed per European values" |
| **L2 — Industry-grade Datasets** | High-quality data access; digital-twin-generated simulation data where real data absent; **consistent data language across enterprise boundaries**; secure sharing preserving organisational control | the data-sovereignty layer |
| **L3 — Industry-proven AI Models** | Models that understand physical relationships + sector-specific workflows; **Sovereign AI Model Hubs** provide pre-trained models without surrendering raw data | model-governance layer |
| **L4 — Industrial AI Solutions** | Ready-to-deploy solutions; standardised model frameworks; self-optimising environments learning continuously from operational data | the consumption layer |

Five framework primitives are named: **Manufacturing-X** (the industrial data
ecosystem, the umbrella the dataset layer plugs into [^7]), **Sovereign AI Model
Hubs**, **Cloud-Edge Continuum**, **Digital Twins** (for simulation data
generation), **Autonomous AI Agents** (strategic objective), and **Frontier-AI-Lab**
(the access model).

The framework is deliberately reminiscent of Jay Lee's earlier "Industrial AI"
formulation [^8] — the same data → model → deploy → maintain loop, but with three
European-policy overlays the 2019 paper does not carry: (1) **sovereignty** as a
design constraint (no surrender of raw data to extract pre-trained models);
(2) **physical-relationship awareness** (models must understand the domain, not
just the data); (3) **cross-enterprise data language** (the Manufacturing-X
dataspaces inheritance).

**Mapping Shepard's existing capability surface onto the four layers:**

| Layer | Shepard's role today | Status |
|---|---|---|
| L1 — Infrastructure | None (and shouldn't have one — that's the cluster operator's domain) | N/A |
| L2 — Datasets | Primary substrate: collections + dataobjects + provenance + SHACL + f(ai)²r `:Activity` rows; m4i + CHAMEO + Material-OWL semantic alignment [^9]; Helmholtz Unhide publish path [^10] | **shipped (L2-grade)** |
| L3 — Models | Partial: `:AIConfig` admin pattern + per-plugin model registry (per AI-V6-005); no first-class Model entity; no model-card primitive; no eval-run primitive | **partial — gap** |
| L4 — Solutions | Substrate for outputs: every AI-generated artefact lands as `:DataObject` with `:Activity` lineage; wiki-writer plugin (TPL9) + planned dataset-publisher are L4 examples | **substrate-shipped** |

The L2 alignment is what makes Shepard **structurally relevant** to the agenda:
not as an AI platform, but as the **dataset substrate that Sovereign Model Hubs and
Industrial AI Solutions both depend on** for evidence, provenance, and re-use.

---

## §3 — The three external concept papers

The three papers cited in the task surface a **single architectural pattern**: AAS
+ MaRCO/CaSk ontologies as the semantic-interoperability bridge between
plant-floor reality (AAS submodels) and AI-tractable reasoning (OWL/RDF
ontologies). They are all by the same Paris/Hamburg/Munich research lineage —
which is itself a signal that the pattern has converged.

### 3.1 Huang, Dhouib, Malenfant (IECON 2021) — "An AAS Modeling Tool for Capability-Based Engineering of Flexible Production Lines" [^4]

Introduces a **capability-based engineering** approach within the AAS framework
for flexible production lines, implemented as a Papyrus/UML modeling tool. The
core concept: production resources advertise **capabilities** (implementation-
independent function specs) and execute via **skills** (the actual implementations).
A flexible production line is one where a needed capability can be matched to any
resource that advertises it.

**What it adds for Shepard.** A typed primitive for **capabilities**. Today Shepard
DataObjects carry free-form attribute keys (`bench`, `propellant`, `process_step`);
mapping each one to a typed `mfg:Capability` lets a user query "find all DataObjects
captured while exercising Stiffener-Welding capability, regardless of which physical
welder was used." That's a missing query the LUMEN/MFFD seeds cannot answer today.

### 3.2 Vieira da Silva, Köcher, Gill, Weiss, Fay (ETFA 2023) — "Toward a Mapping of Capability and Skill Models using Asset Administration Shells and Ontologies" [^5]

Presents the **bidirectional mapping** between AAS submodels and a capability/skill
ontology (CaSk): forward via RML rules (AAS JSON → RDF), reverse via RDFex (RDF → AAS
JSON). This is the **interoperability bridge** between the two communities that
otherwise model the same concept in incompatible ways.

**What it adds for Shepard.** A concrete mapping recipe. When `shepard-plugin-aas`
(designed in [`aidocs/integrations/52`](../integrations/52-aas-backend-integration.md))
ships its first ingest path, the import side should consume RML rules to project
AAS submodels into the Shepard knowledge graph; the export side should round-trip
back to AAS JSON for downstream consumers. Shepard avoids re-inventing the rules
by adopting RML directly (W3C-on-track) — the same plugin-first / reuse-first
posture that [`aidocs/integrations/97 §2`](../integrations/97-shepard-plugin-ai-design.md)
applies to embedding providers.

### 3.3 Huang, Dhouib, Palacios Medinacelli, Malenfant (ICPS 2023) — "Semantic Interoperability of Digital Twins: Ontology-based Capability Checking in AAS Modeling Framework" [^6]

The 2021 paper's continuation: it transforms AAS plant models into **MaRCO
(Manufacturing Resource Capability Ontology)** instances and queries the expanded
ontology to find resources that match a needed capability — within Papyrus for
Manufacturing, an MDE toolchain that aggregates AAS + AI (knowledge representation
and reasoning).

**What it adds for Shepard.** A **runtime semantic-matching pattern**. The MaRCO
ontology is open and DLR-relevant (it intersects Material-OWL and CHAMEO, both of
which Shepard already cross-references per
[`aidocs/agent-findings/dlr-ontology-catalog.md`](../agent-findings/dlr-ontology-catalog.md)).
A `shepard-plugin-marco` thin adapter that bootstraps MaRCO into the internal
semantic repo (per the `V49__Bootstrap_internal_semantic_repository` pattern) and
exposes a `SPARQL`-side query for "find DataObjects whose realised capability is a
subclass of `marco:JoiningCapability`" closes the gap between MFFD-style process
documentation and FAIR-discoverable capability descriptions.

**Cumulative insight.** Three papers, one converged pattern: **AAS for structure,
MaRCO/CaSk for semantics, RML for the bridge.** Shepard's plug-in surface is the
right shape to absorb all three without core changes — except for one missing
primitive: a `:Capability` node type (or its representation as a Shepard
individual under the `aidocs/strategy/91-forinfpro` ontology layer). §6 lists this
as core-extension `CORE-EXT-001`.

---

## §4 — Shepard's five roles in the Industrial-AI value chain

### Role 1 — Substrate (shipped)

Shepard already **is** an L2-grade industry-grade dataset substrate:

- **Stable identifiers**: `appId` (UUID v7) per entity; survives renames, exports,
  upstream-v1 → v2 migration; per
  [`aidocs/strategy/87`](87-dlr-zlp-positioning.md) is one of the genuine
  differentiators against upstream Shepard.
- **Lineage**: Predecessor/Successor relationships on every DataObject; PROV-O
  serialisation via PROV1a; auditable for DIN EN 9100 §8.4 and EASA DM-04 [^2].
- **Multi-substrate storage**: Neo4j for graph + metadata, TimescaleDB for
  high-rate sensor data, Garage for binary blobs, pgvector (post-PG-COLLAPSE-001)
  for AI embeddings — each substrate chosen for fit per
  [`aidocs/strategy/105`](105-postgres-multitenant-decision.md).
- **Semantic alignment**: m4i + CHAMEO + Material-OWL preseeded into the internal
  semantic repository per `V49__Bootstrap_internal_semantic_repository.cypher`
  (and the ontology-preseed admin surface per
  [`aidocs/semantics/65`](../semantics/65-admin-configurable-ontology-preseed.md)).
- **Cross-enterprise data language**: Helmholtz Unhide publish-plugin
  ([`aidocs/integrations/67`](../integrations/67-unhide-publish-plugin.md)) +
  federation / dataspaces ([`aidocs/strategy/94`](94-federation-and-dataspaces.md))
  give Shepard collections an outbound interoperability path.

**Gap (substrate-side).** No first-class `license` field on DataObject/Collection
([`project_competitive_position.md`](https://nucli.de) cites this as the #1 actionable
gap — KIP1e); no embargo/access-rights enum; no ORCID stamp at entity creation.
These are not Industrial-AI-specific gaps but they block Industrial-AI use cases
(every external evaluator dataset needs a clear license; embargo windows are
standard for MFFD-class IP-sensitive data).

### Role 2 — Training-data curation (designed, partially shipped)

A "training-data curation" role is what Shepard already does for human researchers
(versioning, annotation, lineage) but it has to be re-expressed in **ML-pipeline
vocabulary** to be useful to AI consumers:

- **Splits** (train/val/test) — currently expressible as Collections with
  Predecessor/Successor edges, but no standard predicate. EASA Concept Paper
  Issue 02 §DA-08 [^2] requires **proof of independence between train/val/test**
  — a typed `mlsplit:trainPartitionOf` / `mlsplit:validationPartitionOf` /
  `mlsplit:testPartitionOf` predicate set, validated by SHACL, would close this.
- **Croissant export** — FAIR4-CROISSANT-EXPORT is already filed
  (`aidocs/16` line 1346) as a deferred Publisher-plugin feature. Shipping it
  promotes Shepard collections from "data" to "indexable ML datasets" on
  Hugging Face / OpenML / Kaggle.
- **Data Quality Reports (DQRs)** — EASA DA-04/DA-05 [^2]; a typed `:Activity`
  with `actionKind:'DATA_QUALITY_REPORT'` whose `targets[]` references the
  Collection and whose attached file is the DQR PDF/JSON. Largely shippable today
  via existing primitives; what's missing is the UI affordance to **see** the DQR
  for a Collection without traversing the graph by hand.

**Gap.** No ML-split predicate set. No first-class DQR-as-Activity affordance.
Both filed as INDAI-V6-005 / INDAI-V6-006.

### Role 3 — Model-evaluation harness (gap)

This is the **largest gap**. Shepard today has no primitive for "a model
evaluation run": which model (with which weights), which test dataset, which
metrics, which output predictions, against which ground truth. The ML community
has converged on this shape (MLflow's `Run`; Weights & Biases' `Run`; FAIR4ML's
`fair4ml:Evaluation`) — Shepard needs an analogous shape that respects its
graph-native design.

**Proposed primitive.** A `:Evaluation` Activity subclass with edges to:
- `:Activity{actionKind:'EVALUATION'}` (the run itself);
- `fair4ml:trainedOn` / `fair4ml:testedOn` → `:Collection` (the dataset);
- `fair4ml:evaluatedOn` → `:DataObject` (the test partition);
- `fair4ml:hasMetric` → `:StructuredDataContainer` (metric table);
- `fair4ml:references` → `:Model` (the model — see Role 4).

This composes with Shepard's existing PROV-O surface, requires no new substrate,
and aligns with the FAIR4ML/Croissant track already in the backlog. **Filed as
CORE-EXT-002.**

### Role 4 — Model-deployment governance (partial)

The **admin-runtime config pattern** Shepard standardised in
[`feedback_admin_runbooks_pattern`](https://nucli.de) + [A3b
`:FeatureToggleRegistry`] + AI-V6-005 `:AIConfig` is the right shape for **gating
which models are allowed**. But Shepard has no first-class **Model entity** —
models exist as fields on `:AIConfig` (`embeddingModelId`, `chatModelId`) or as
URLs on Hub configs (`endpointUrl`), not as graph nodes with their own provenance.

**Proposed primitive.** A `:Model` node carrying `model_id`, `provider`, `version`,
`license`, `model_card_url`, `weights_sha256`, `trained_on_collection_ids[]`,
`evaluation_ids[]`. Lives in core (every plugin compiles against it). Plugin
implementations register specific model bindings per
[`aidocs/integrations/97 §3`](../integrations/97-shepard-plugin-ai-design.md).

**Why core, not plugin.** Per CLAUDE.md "plugin-first": exceptions are
"identity primitives every plugin compiles against." A Model entity is precisely
that — `shepard-plugin-ai` needs it for embedding-model registry, a future
`shepard-plugin-eval-harness` needs it for evaluation-run targets,
`shepard-plugin-aas-capability` needs it to bind "this capability is realised by
this AI-controlled robot." **Filed as CORE-EXT-003.**

### Role 5 — Audit + reproducibility (shipped)

Two MUSTs from [`aidocs/integrations/97 §0`](../integrations/97-shepard-plugin-ai-design.md):

- **MUST-Tier-0** — local-by-default execution path. Reproducibility implication:
  every install can re-embed without network egress; experiments are reproducible
  on a laptop.
- **MUST-provenance** — every AI interaction lands as `:Activity`. EU AI Act
  Article 50 [^3] transparency obligation (effective 2 Aug 2026) demands exactly
  this: any user can ask "what AI touched this artefact?" and get a typed answer.

**Gap.** A user-facing **AI-provenance widget** that surfaces the `:Activity` chain
on a DataObject detail page (today the data is captured but the UI does not yet
render it). FAIR4-DASHBOARD-INTEGRATE (`aidocs/16` line 1343) is the queued piece.
Filed as a frontend-only piece of INDAI-V6-009.

---

## §5 — Feature ideas surfaced (12)

Each closes one of the gaps above. Each is shippable as an additive change with
no API-version impact.

1. **License field on DataObject + Collection (`INDAI-V6-FEAT-001`).** Promote
   `project_competitive_position.md` #1 actionable gap to an INDAI-tracked
   feature. SPDX-id enum; default `null` (treated as "unspecified"); flowed into
   Unhide + Croissant + DataCite exports. **Why Industrial-AI-relevant:** every
   training dataset needs a license; missing licenses block Sovereign Model Hub
   ingestion.

2. **Embargo-until + access-level enum (`INDAI-V6-FEAT-002`).** `embargo_until`
   datetime + `access_level: OPEN | RESTRICTED | EMBARGOED | CLOSED` on
   DataObject + Collection. Required for MFFD-class IP-sensitive data that
   participates in evaluation runs.

3. **ML-split predicate set (`INDAI-V6-FEAT-003`).** `mlsplit:trainPartitionOf /
   validationPartitionOf / testPartitionOf` predicates with SHACL shape enforcing
   disjointness (no DataObject in two partitions of the same split). Closes EASA
   DA-08 [^2].

4. **DQR-as-Activity affordance (`INDAI-V6-FEAT-004`).** Frontend chip on
   Collection detail surfacing the latest `:Activity{actionKind:'DATA_QUALITY_REPORT'}`
   with a click-through to the attached DQR file. Backend already supports
   capture; this is purely a render gap.

5. **AI-provenance badge + chain on DataObject detail (`INDAI-V6-FEAT-005`).**
   The UI piece of MUST-provenance. Read-only widget rendering the `:Activity`
   chain (embed → search → chat → write-back) with model id, cost, timestamp.
   Aligns with EU AI Act Art-50 [^3] disclosure obligation.

6. **Model-card stub on Model entity (`INDAI-V6-FEAT-006`).** Once `:Model` lands
   (CORE-EXT-003), a model-card markdown stub stored alongside it; rendered in
   the same `/help` infrastructure as plugin docs (per the
   `aidocs/ops/49-in-app-user-docs.md` pattern). Closes part of EASA LM-01
   ("documentation of model design choices").

7. **`mfg:realizesCapability` edge on DataObject (`INDAI-V6-FEAT-007`).** Typed
   edge from DataObject → Capability instance (from MaRCO ontology). Lets users
   query "every DataObject captured while exercising joining capabilities" —
   the missing query from §3.

8. **Evaluation-run create + read endpoints (`INDAI-V6-FEAT-008`).** REST shape:
   `POST /v2/evaluations` (create), `GET /v2/evaluations/{appId}` (read with
   inline metrics), `GET /v2/models/{appId}/evaluations` (cross-model). Implements
   Role 3 minimum viable surface.

9. **Sovereign Model Hub registry config (`INDAI-V6-FEAT-009`).** Admin-runtime
   `:HubConfig` singleton listing trusted Sovereign Model Hubs (HuggingFace
   private; SAIA; GWDG). When a plugin requests a model, the registry resolves
   which hub to pull from and whether it's allowed (`allowExternal` semantics
   inherited from AI-V6-005). Closes Plattform-i40 L3 sovereignty requirement [^1].

10. **Digital-twin synthetic-data tag (`INDAI-V6-FEAT-010`).** Plattform-i40 L2
    explicitly cites "digital twins generating realistic simulation data where
    real data is absent." A `provenance:isSynthetic: true` flag on DataObject
    + Collection (already conceptually present in LUMEN seed:
    `description: "NOT REAL DLR/LUMEN data"`) needs to become a structured field
    so synthetic and real data are programmatically distinguishable in
    evaluation runs.

11. **Federated-evaluation receipt (`INDAI-V6-FEAT-011`).** When a downstream
    consumer (Sovereign Hub; external lab) evaluates a model trained on a
    Shepard-hosted dataset, an inbound REST endpoint records the receipt as a
    `:Activity{actionKind:'EXTERNAL_EVALUATION_RECEIPT'}` against the Collection.
    Closes "evidence flows both ways" gap for cross-enterprise Industrial-AI.

12. **EU AI Act Art-50 disclosure endpoint (`INDAI-V6-FEAT-012`).**
    `GET /v2/disclosure/entity/{appId}` returns a structured machine-readable
    record of every AI interaction that touched the entity, in a format aligned
    with the [EU AI Act Service Desk guidance on transparency] [^3]. Pure read
    over existing `:Activity` data; the surface is the value-add.

---

## §6 — Plugin ideas surfaced (5)

Each follows CLAUDE.md "plugin-first" guidance: a plugin shape unless the
component is an identity primitive. Each carries the three-document docs trinity
(reference + quickstart + install per CLAUDE.md plugin-docs rule).

### 6.1 `shepard-plugin-model-registry`

**Capability surface.** First-class `:Model` entity admin (alongside the core
type from CORE-EXT-003); model-card markdown rendering; weights-SHA-256
verification; license + intended-use + ethical-considerations fields per the
[Model Cards for Model Reporting] approach (Mitchell et al. 2019).

**Why plugin.** The data primitive is core (every other AI plugin needs it); the
admin surface, the model-card renderer, and the weights-verification pipeline
have plugin-shape cadence (release tied to FAIR4ML evolution, not Shepard core).

**Dependencies.** Postgres (for model-card text + embeddings); CORE-EXT-003
(`:Model` type); pgvector schema (`shepard_models` extension on the collapsed-PG).

### 6.2 `shepard-plugin-eval-harness`

**Capability surface.** Evaluation-run orchestration: take a `:Model` + a Collection
with ML-split predicates → kick off an evaluation (locally via a TEI/Ollama
sidecar; remotely via SAIA/GWDG) → land predictions as a StructuredDataContainer →
compute metrics → write back as `:Activity{actionKind:'EVALUATION'}`. Reuses the
sidecar pattern from `shepard-plugin-ai` (so the docker image already exists for
embedding workloads).

**Why plugin.** Eval orchestration is task-specific (different metrics for
classification, regression, time-series forecasting, anomaly detection); it has
its own release cadence; it depends on external libraries (scikit-learn-style
metric libs) that Shepard core does not need.

**Dependencies.** `shepard-plugin-ai` (for the LLM-as-judge eval path); `shepard-
plugin-model-registry`; SHACL shapes for ML-split disjointness (INDAI-V6-FEAT-003).

### 6.3 `shepard-plugin-aas-capability`

**Capability surface.** Implements §3's converged pattern. Bootstraps **MaRCO**
into the internal semantic repo; ships RML rules for the AAS → RDF forward
mapping and RDFex rules for the reverse. Exposes
`POST /v2/aas/import` (AAS submodel → DataObject + Capability nodes) and
`GET /v2/aas/export/{collectionId}` (Shepard collection → AAS submodel). Owned by
the same plugin family as `shepard-plugin-aas` (designed in
[`aidocs/integrations/52`](../integrations/52-aas-backend-integration.md)) — the
"capability" piece is the AI-adjacent extension.

**Why plugin.** AAS integration is bounded; not every Shepard install needs it
(LUMEN doesn't; MFFD does; PLUTO doesn't). The Papyrus/UML toolchain dependency
is real and is best isolated.

**Dependencies.** Internal semantic repo (already shipped); MaRCO ontology
(public); RML processor (Carml or RMLMapper — Apache-2.0); CORE-EXT-001
`:Capability` node type.

### 6.4 `shepard-plugin-synthetic-data`

**Capability surface.** Digital-twin-driven synthetic-data generation per
Plattform-i40 L2's "where real data is absent" axis. Hooks: a SimulationProvider
SPI; one reference implementation that generates timeseries from a parametric
model (e.g. a fatigue-life simulator for MFFD CFRP), tagged
`provenance:isSynthetic:true` (INDAI-V6-FEAT-010). Output writes to TimescaleDB
just like real data — the `:Activity{actionKind:'SYNTHESISED'}` row is the
provenance discriminator.

**Why plugin.** Domain-specific simulators are domain-specific; the SPI is core
(`SimulationProvider` interface in `de.dlr.shepard.spi.simulation`), the
implementations are plugins. First user: LUMEN/MFFD synthetic-data demos
(already partially built by `seed.py` — formalising it as a plugin SPI
generalises the pattern).

**Dependencies.** `:Activity` capture (shipped); INDAI-V6-FEAT-010 (synthetic
tag).

### 6.5 `shepard-plugin-mlops-bridge`

**Capability surface.** Thin adapter exporting Shepard's evaluation-run +
model-card data to MLflow / Weights & Biases / Comet ML — so an ML engineer who
already lives in those tools sees their Shepard runs alongside their other runs.
Inbound side: webhook receiver for "evaluation completed" events from those tools
that lands the run back as a Shepard `:Activity`.

**Why plugin.** Three different MLOps tools, three different APIs, three different
release cadences. Core stays MLOps-tool-agnostic; the bridges live in plugins.

**Dependencies.** `shepard-plugin-eval-harness`; outbound webhook infrastructure
(reuse `shepard-plugin-matrix` pattern from
[`project_matrix_plugin.md`](https://nucli.de)).

---

## §7 — Core extension ideas surfaced (3)

These are the things CLAUDE.md plugin-first rule allows in-core because they are
identity primitives every plugin compiles against.

### 7.1 CORE-EXT-001 — `:Capability` node type

**What.** Neo4j label `:Capability` with `HasAppId` marker; fields `name`,
`namespace_iri`, `definition_text`, `ontology_term_iri`. Edges:
`:DataObject -[:REALIZES_CAPABILITY]-> :Capability`,
`:Capability -[:SUBCLASS_OF]-> :Capability` (hierarchy).

**Why core.** Three different plugins compile against it (aas-capability,
eval-harness for capability-conditioned evaluation, future analytics for
capability-conditioned queries); the ontology bootstrap path (V## migration)
needs core access; the v2 REST surface (`GET /v2/capabilities/{appId}`,
`GET /v2/collections/{appId}/capabilities`) is small enough to host in core.

**Migration shape.** `V##__Add_capability_type.cypher` (idempotent, additive,
no breaking change to existing DataObjects which simply have zero
`:REALIZES_CAPABILITY` edges initially).

### 7.2 CORE-EXT-002 — `:Activity{actionKind:'EVALUATION'}` first-class subtype

**What.** Not a new label; an enum-value addition to `:Activity.actionKind` with
a typed payload schema (validated by SHACL) requiring `:Model` reference,
`:Collection` test-partition reference, metric table reference. The `:Evaluation`
view is a query-side projection (`MATCH (a:Activity {actionKind:'EVALUATION'}) ...`)
not a new label — preserves the uniform `:Activity` shape that
[`aidocs/integrations/97 §7`](../integrations/97-shepard-plugin-ai-design.md)
established.

**Why core.** The provenance shape is a cross-cutting concern (every plugin
contributing to model evaluation needs to write the same shape); the SHACL
validation belongs in core's SHACL infrastructure (per
[`aidocs/agent-findings/data-ontologist-prov-o-v15.md`](../agent-findings/data-ontologist-prov-o-v15.md));
the REST read surface is small and uniform.

**Why not a separate `:Evaluation` label.** Evaluations are activities; a
distinct label would fork the `:Activity` graph and break the uniform
provenance-traversal queries Shepard relies on for the
`/v2/provenance/entity/{appId}` endpoint.

### 7.3 CORE-EXT-003 — `:Model` node type

**What.** Neo4j label `:Model` with `HasAppId`; fields `model_id` (e.g.
`jinaai/jina-embeddings-v2-base-de`), `provider`, `version`, `license`,
`model_card_url`, `weights_sha256` (nullable; hash of the weights file if locally
served), `intended_use_text`, `ethical_considerations_text`. Edges:
`:Model -[:TRAINED_ON]-> :Collection`,
`:Model -[:EVALUATED_BY]-> :Activity{actionKind:'EVALUATION'}`.

**Why core.** Every AI plugin (`shepard-plugin-ai`, `shepard-plugin-eval-harness`,
`shepard-plugin-wiki-writer`, future `shepard-plugin-anomaly-detector`) compiles
against this type. The `:AIConfig.embeddingModelId` field currently held as a
free-form string is upgraded to `:AIConfig -[:USES_MODEL]-> :Model` (additive
migration with backfill from existing string values).

**Why not a plugin.** Multiple plugins depend on it; making it a plugin creates a
dependency-direction problem (the plugin-loading order would have to special-case
this one plugin). Core is the natural home.

**Open question.** Should `:Model` carry a default `:Capability` edge (i.e.
"this embedding model realises an `EmbeddingCapability`")? The §3 capability
pattern suggests yes; the v0 implementation should ship without and the question
re-opens once `shepard-plugin-aas-capability` is in flight. Filed as INDAI-V6-OPEN-001.

---

## §8 — Honest competitive position

**Per [`project_competitive_position.md`](https://nucli.de) and the just-pushed
reverse-requirements doc:** Shepard is structurally well-positioned for the
Industrial-AI agenda **precisely because** it skips the parts most of its competitors
have over-invested in.

| Competitor | What they do well in Industrial AI | What Shepard does that they don't |
|---|---|---|
| **Kadi4Mat** [^11] | ELN completeness; SHACL schema export; NFDI4ING community | Timeseries-native at MFFD scale; W3C PROV-O provenance with f(ai)²r typed AI Activity; Helmholtz HKG publishing; sovereignty-first AI design (local Tier 0 default) |
| **openBIS** [^12] | Mature ELN; long industrial usage | Plugin SPI; AI-V6 capability surface; admin-runtime config pattern; multi-substrate by design |
| **NOMAD** [^13] | Computational physics + materials; rich metadata extraction | Industrial process data; experimental + sensor focus; non-physics domains |
| **SciCat** [^14] | Photon-science scale; metadata harvest pipelines | General research data; non-photon use cases; AI provenance native |
| **MLflow** [^15] | Model registry; experiment tracking; UI | Dataset substrate; provenance as graph not log; aerospace + manufacturing domain primitives; SHACL validation |
| **Weights & Biases** [^16] | Polished UX for ML experiment tracking | Data-centric view (datasets as primary, runs as secondary); on-prem sovereignty; FAIR + EASA alignment |
| **Hugging Face Hub** [^17] | Model + dataset hosting at scale; community | Provenance graph; SHACL; multi-substrate for sensor data; sovereignty (local-first) |

**The honest position.** Shepard is **not** an MLOps tool; it is the
**data-provenance substrate** that an MLOps tool plugs into. The Industrial-AI
agenda places this substrate at L2 — exactly the layer where Shepard's existing
strengths (provenance, identifiers, semantic alignment, multi-substrate
storage) are decisive and where the competing platforms are weakest.

**The honest gap.** Shepard does not today have a **first-class model and
evaluation primitive**. Adding it (CORE-EXT-002 + CORE-EXT-003 + the
eval-harness plugin) is the smallest, most strategically dense move available
to close the gap to L3 governance without overreaching into L1 or model-serving.

---

## §9 — Regulatory alignment

The Industrial-AI agenda exists inside a regulatory envelope that already has
hard deadlines. Shepard's positioning has to map cleanly to it.

### 9.1 EU AI Act Article 50 (effective 2 Aug 2026) [^3]

**Obligation.** Providers of AI systems generating synthetic content (audio,
image, video, text) must mark outputs in machine-readable format and disclose to
the user that content is artificially generated, at first exposure.

**Shepard's alignment.** MUST-provenance per
[`aidocs/integrations/97 §0`](../integrations/97-shepard-plugin-ai-design.md)
already commits Shepard to typed `:Activity` capture for every AI interaction.
INDAI-V6-FEAT-012 (the disclosure endpoint) is the explicit Art-50 hook.
INDAI-V6-FEAT-005 (AI-provenance badge) is the user-facing disclosure surface.

**Penalty for non-compliance.** EUR 15M or 3% of worldwide annual turnover [^3].
For DLR institutional deployments this is a hard line: shipping AI-generated
artefacts without provenance disclosure is a regulatory liability, not just bad
form.

### 9.2 EASA Concept Paper Issue 02 (March 2024) [^2]

**Status.** Anticipated MOC; expected to become adopted MOC via RMT.0742 by
2028. Already used in real certification dialogues (MLEAP, Wayfinder).

**Data Management family alignment (DA-01…DA-09).** Shepard ships DA-01
(data-set definition), DA-02 (data-collection trace), DA-03 (data-source
identification), DA-08 (independence between train/val/test — gated on
INDAI-V6-FEAT-003), DA-09 (data lineage — shipped via Predecessor/Successor +
PROV-O). DA-04/DA-05 (DQR) gated on INDAI-V6-FEAT-004.

**Data Management gates (DM-01…DM-08).** Shepard ships DM-01 (data-set version
control via appId stability), DM-02 (data-set integrity via Garage CAS),
DM-04 (data-set lineage). DM-08 (data-set retention) is operator-policy.

**What Shepard does NOT ship.** Generalisation bounds (LM-04), runtime ODD
monitoring (EXP-05/06/07), HAT capability (HF-01…HF-11). These are model-level
and operational concerns outside the data-substrate mandate. The
[`aidocs/agent-findings/easa-ai-regulatory-positioning.md`](../agent-findings/easa-ai-regulatory-positioning.md)
position holds: *Shepard is a credible evidence platform for DM/DA + EXP-08/09; it
is not an assurance engine.*

### 9.3 DIN EN 9100 [^18]

**Relevance.** §8.4 (control of externally provided processes, products, and
services) maps onto Shepard's lineage + Predecessor/Successor + capability
edges (INDAI-V6-FEAT-007). §7.5 (documented information) maps onto SHACL
validation + ontology preseed. The auditor-readability gap is closed by
INDAI-V6-FEAT-005 (AI-provenance widget) + INDAI-V6-FEAT-009 (Sovereign Model
Hub registry — the "which models did we permit?" disclosure).

### 9.4 DLR institutional posture

Per [`aidocs/strategy/93-management-context-and-compliance.md`](93-management-context-and-compliance.md)
and [`project_dlr_institutional_strategy.md`](https://nucli.de): DLR Line 4
(Innovation) is the Industrial-AI vehicle; **D4 = MCP/Claude**; DataHub =
Manufacturing-X + MOSS. Two Shepard instances are in play (nuclide-hosted dev,
DLR-hosted production). The Industrial-AI agenda hits both: dev instance is
where the eval-harness plugin matures; production instance is where
EU-AI-Act-disclosure-by-default ships first.

---

## §10 — Backlog rows (to file)

To be added to [`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md) as
a new `## INDAI-V6-* — Industrial-AI value-chain role` section. Sized per the
existing T-shirt convention (XS / S / M / L / XL).

| ID | Title | Size | Status | Notes |
|---|---|---|---|---|
| INDAI-V6-FEAT-001 | License field (SPDX-id) on DataObject + Collection; flowed to Unhide + Croissant + DataCite | S | queued | Promotes KIP1e to INDAI-tracked. **Industrial-AI relevance:** every Hub-ingested dataset needs a license. |
| INDAI-V6-FEAT-002 | `embargo_until` + `access_level: OPEN/RESTRICTED/EMBARGOED/CLOSED` enum on DataObject + Collection | S | queued | Required for MFFD-class IP-sensitive evaluation runs. |
| INDAI-V6-FEAT-003 | `mlsplit:trainPartitionOf / validationPartitionOf / testPartitionOf` predicates + SHACL disjointness shape | M | queued | Closes EASA DA-08 [^2]. Pairs with INDAI-V6-FEAT-008. |
| INDAI-V6-FEAT-004 | DQR-as-Activity affordance: frontend chip on Collection detail | S | queued | Backend already supports capture; render gap only. |
| INDAI-V6-FEAT-005 | AI-provenance badge + read-only `:Activity` chain on DataObject detail | M | queued | User-facing piece of MUST-provenance. EU AI Act Art-50 [^3] disclosure surface. Pairs with FAIR4-DASHBOARD-INTEGRATE (`aidocs/16` line 1343). |
| INDAI-V6-FEAT-006 | Model-card markdown stub on `:Model` entity; rendered through `/help` infrastructure | S | queued | Closes part of EASA LM-01 [^2]. Gated on CORE-EXT-003. |
| INDAI-V6-FEAT-007 | `mfg:realizesCapability` edge: DataObject → `:Capability` | M | queued | The missing query from §3. Gated on CORE-EXT-001. |
| INDAI-V6-FEAT-008 | Evaluation-run create + read endpoints: `POST /v2/evaluations`, `GET /v2/evaluations/{appId}`, `GET /v2/models/{appId}/evaluations` | M | queued | Implements Role 3 minimum viable surface. Gated on CORE-EXT-002 + CORE-EXT-003. |
| INDAI-V6-FEAT-009 | Sovereign Model Hub registry: admin-runtime `:HubConfig` singleton + REST + CLI parity | M | queued | Plattform-i40 L3 sovereignty [^1]. Extends `:AIConfig` pattern (AI-V6-005). |
| INDAI-V6-FEAT-010 | `provenance:isSynthetic: true` structured flag on DataObject + Collection | XS | queued | Plattform-i40 L2 digital-twin tag [^1]. Today implicit in LUMEN seed's `description`; needs structured field. |
| INDAI-V6-FEAT-011 | Inbound REST: `POST /v2/evaluations/external-receipt` → `:Activity{actionKind:'EXTERNAL_EVALUATION_RECEIPT'}` | S | queued | Cross-enterprise evidence flow. |
| INDAI-V6-FEAT-012 | `GET /v2/disclosure/entity/{appId}`: machine-readable AI-interaction record per EU AI Act Art-50 [^3] | S | queued | Hard regulatory deadline 2 Aug 2026 [^3]. Read-only over existing `:Activity`. |
| CORE-EXT-001 | `:Capability` node type + REST surface + V## migration | M | queued | Three plugins depend on it. |
| CORE-EXT-002 | `:Activity{actionKind:'EVALUATION'}` enum addition + SHACL payload shape | S | queued | No new label; query-side projection. |
| CORE-EXT-003 | `:Model` node type + REST surface + `:AIConfig` migration to typed edge | M | queued | Identity primitive every AI plugin compiles against. |
| INDAI-V6-PLUGIN-001 | `shepard-plugin-model-registry` scaffold + model-card renderer + weights-SHA-256 verification | L | designed | Gated on CORE-EXT-003. |
| INDAI-V6-PLUGIN-002 | `shepard-plugin-eval-harness` scaffold + sidecar reuse from `shepard-plugin-ai` + scikit-style metrics | XL | designed | Gated on INDAI-V6-FEAT-008 + CORE-EXT-002. |
| INDAI-V6-PLUGIN-003 | `shepard-plugin-aas-capability`: MaRCO bootstrap + RML forward + RDFex reverse | L | designed | Sibling of `shepard-plugin-aas` ([`aidocs/integrations/52`](../integrations/52-aas-backend-integration.md)). Cite [^4][^5][^6]. |
| INDAI-V6-PLUGIN-004 | `shepard-plugin-synthetic-data`: SimulationProvider SPI + reference LUMEN/MFFD impl | L | designed | Plattform-i40 L2 digital-twin axis [^1]. |
| INDAI-V6-PLUGIN-005 | `shepard-plugin-mlops-bridge`: MLflow + W&B + Comet adapters | L | designed | Outbound webhook reuse from `shepard-plugin-matrix`. |
| INDAI-V6-OPEN-001 | Open question: should `:Model` carry a default `:Capability` edge? | XS | open | Re-open once `shepard-plugin-aas-capability` is in flight. |

**Sequencing note.** Roll-out order: CORE-EXT-003 → CORE-EXT-001 → CORE-EXT-002
→ INDAI-V6-FEAT-001/002/010 (additive fields) → INDAI-V6-FEAT-003 (SHACL shape) →
INDAI-V6-PLUGIN-001 (model registry) → INDAI-V6-FEAT-008 (eval endpoints) →
INDAI-V6-PLUGIN-002 (eval harness) → INDAI-V6-FEAT-005/012 (disclosure UI + API,
**hard deadline 2 Aug 2026 for Art-50**) → remaining plugins. Per CLAUDE.md
"plugins ship their own documentation" each plugin ships docs trinity in the
same PR.

---

## References

[^1]: Plattform Industrie 4.0. (2026, March 23). *Industrial AI: Driving Future
    Value Creation.* Bundesministerium für Wirtschaft und Klimaschutz.
    <https://www.plattform-i40.de/IP/Redaktion/DE/Standardartikel/industrial-AI-driving-value-creation.html>
    (Companion infographic: PDF, 2 MB, 23 March 2026.)

[^2]: European Union Aviation Safety Agency. (2024, March). *Artificial
    Intelligence Concept Paper, Issue 02: Guidance for Level 1 & 2 machine
    learning applications.* EASA.
    <https://www.easa.europa.eu/en/downloads/139504/en>

[^3]: European Parliament and Council. (2024, June 13). *Regulation (EU)
    2024/1689 (Artificial Intelligence Act), Article 50: Transparency
    obligations for providers and deployers of certain AI systems.* Article 50
    transparency obligations effective 2 August 2026. EU AI Act Service Desk:
    <https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-50> Penalty
    schedule: EUR 15M or 3% of worldwide annual turnover, per article 99.

[^4]: Huang, Y., Dhouib, S., & Malenfant, J. (2021, October). An AAS Modeling
    Tool for Capability-Based Engineering of Flexible Production Lines. *IECON
    2021 — 47th Annual Conference of the IEEE Industrial Electronics Society.*
    IEEE document 9589329.
    <https://ieeexplore.ieee.org/document/9589329>
    HAL: <https://hal.science/hal-03476685>

[^5]: Vieira da Silva, L. M., Köcher, A., Gill, M. S., Weiss, M., & Fay, A.
    (2023, September). Toward a Mapping of Capability and Skill Models using
    Asset Administration Shells and Ontologies. *28th IEEE International
    Conference on Emerging Technologies and Factory Automation (ETFA 2023).*
    DOI: <https://doi.org/10.1109/ETFA54631.2023.10275459>
    arXiv: <https://arxiv.org/abs/2307.00827>

[^6]: Huang, Y., Dhouib, S., Palacios Medinacelli, L., & Malenfant, J. (2023,
    May). Semantic Interoperability of Digital Twins: Ontology-based Capability
    Checking in AAS Modeling Framework. *2023 IEEE 6th International Conference
    on Industrial Cyber-Physical Systems (ICPS 2023), Wuhan, China.*
    DOI: <https://doi.org/10.1109/ICPS58381.2023.10128003>
    HAL: <https://hal.science/cea-04169941>

[^7]: Plattform Industrie 4.0. *Manufacturing-X: Industrielle Datenökosysteme.*
    <https://www.plattform-i40.de/IP/Navigation/EN/Manufacturing-X/Manufacturing-X.html>

[^8]: Lee, J., Singh, J., & Azamfar, M. (2019). *Industrial Artificial
    Intelligence.* arXiv: <https://arxiv.org/abs/1908.02150>

[^9]: HMC, Materials Open Lab, CHAMEO project. Material-OWL +
    Characterisation Ontology cross-referenced in Shepard's internal semantic
    repository per `backend/src/main/resources/neo4j/migrations/
    V49__Bootstrap_internal_semantic_repository.cypher`. See
    [`aidocs/agent-findings/dlr-ontology-catalog.md`](../agent-findings/dlr-ontology-catalog.md).

[^10]: Helmholtz Metadata Collaboration. *Unhide harvest feed.* See
    [`aidocs/integrations/67-unhide-publish-plugin.md`](../integrations/67-unhide-publish-plugin.md).

[^11]: Brandt, N., Selzer, L., Nestler, B., Riem, V., et al. *Kadi4Mat: A
    research data infrastructure for materials science.* KIT IAM-CMS. See
    [`aidocs/strategy/70-competitor-landscape-and-feature-ideas.md`](70-competitor-landscape-and-feature-ideas.md).

[^12]: Bauch, A., Adamczyk, I., Buczek, P., et al. (2011). *openBIS: a flexible
    framework for managing and analyzing complex data in biology research.*
    ETH Zurich. BMC Bioinformatics 12:468.

[^13]: Draxl, C., & Scheffler, M. (2019). *The NOMAD laboratory: from data
    sharing to artificial intelligence.* Journal of Physics: Materials 2(3):
    036001.

[^14]: SciCat collaboration. *SciCat: scientific catalog system for facility
    data.* European Spallation Source + Paul Scherrer Institut + RAL.
    <https://scicatproject.github.io/>

[^15]: Zaharia, M., et al. (2018). *Accelerating the Machine Learning Lifecycle
    with MLflow.* IEEE Data Eng. Bull. 41(4): 39–45.

[^16]: Biewald, L. (2020). *Experiment tracking with Weights & Biases.*
    <https://www.wandb.com/>

[^17]: Hugging Face. *Hub: models, datasets, spaces.*
    <https://huggingface.co/docs/hub/>

[^18]: Deutsches Institut für Normung. *DIN EN 9100:2018-08, Quality management
    systems — Requirements for aviation, space and defence organizations.* Beuth
    Verlag.

---

### Companion documents

- [`aidocs/integrations/97-shepard-plugin-ai-design.md`](../integrations/97-shepard-plugin-ai-design.md) — `shepard-plugin-ai` v6 SSOT
- [`aidocs/strategy/70-competitor-landscape-and-feature-ideas.md`](70-competitor-landscape-and-feature-ideas.md) — competitive landscape
- [`aidocs/strategy/105-postgres-multitenant-decision.md`](105-postgres-multitenant-decision.md) — substrate ADR that gates AI-V6 + INDAI-V6 work
- [`aidocs/agent-findings/easa-ai-regulatory-positioning.md`](../agent-findings/easa-ai-regulatory-positioning.md) — EASA Concept Paper alignment
- [`aidocs/integrations/52-aas-backend-integration.md`](../integrations/52-aas-backend-integration.md) — `shepard-plugin-aas` (sibling of INDAI-V6-PLUGIN-003)
- [`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md) — backlog SSOT where the rows in §10 will land
