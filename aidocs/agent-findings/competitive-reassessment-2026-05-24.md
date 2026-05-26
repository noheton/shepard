---
stage: audited-by-personas
last-stage-change: 2026-05-24
---

# Competitive reassessment — post-V6-trinity verdict shift (2026-05-24)

**Role.** Ecosystem advocate + strategic advisor (combined re-walk).
**Scope.** Shepard's competitive position vs. five canonical European RDM
platforms + the AAS-side landscape, **with the verdict refreshed for the
five v6 SSOTs and one ADR that landed today**:

- [`aidocs/data/90-spatial-as-temporal-sweep.md`](../data/90-spatial-as-temporal-sweep.md) — spatial-as-temporal-sweep (15-use-case substrate)
- [`aidocs/integrations/97-shepard-plugin-ai-design.md`](../integrations/97-shepard-plugin-ai-design.md) — local-first AI plugin SSOT
- [`aidocs/strategy/105-postgres-multitenant-decision.md`](../strategy/105-postgres-multitenant-decision.md) — one-PG-N-schemas ADR (ACCEPTED)
- [`aidocs/semantics/100-consistent-semantic-annotation-design.md`](../semantics/100-consistent-semantic-annotation-design.md) — annotation primitive + MCP CRUD
- [`aidocs/43-reverse-engineered-requirements.md`](../43-reverse-engineered-requirements.md) — descriptive shadow of the implementation

**Predecessors (the baseline this delta is against).**
[`strategy-advisor.md`](strategy-advisor.md) (2026-05-21) +
[`ecosystem-advocate.md`](ecosystem-advocate.md) (2026-05-21) +
[`persona-digital-native-2026-05-24.md`](persona-digital-native-2026-05-24.md) +
the two memory crystallisations (`project_competitive_position.md` /
`project_pareto_position.md`). Read those first — this doc is the diff,
not a re-derivation.

---

## §1 — TL;DR: the verdict shift

The 2026-05-21 baseline placed shepard as **a complementary, not competitive,
peer to Kadi4Mat** on a "timeseries + provenance + plugins" axis, with the
top blocker being a missing `license` field and the leading risk being
adoption-vs-Coscine. That framing is still true on the surface — but the
five V6 landings re-shape the differentiator stack underneath it.

**The three verdict shifts that matter:**

1. **Shepard is no longer "complementary"; it is *substrate-different*.**
   The brush-sweep recognition in `aidocs/data/90` (15 manufacturing +
   NDT use cases collapsed to one `(time, anchor, profile, measurements)`
   row shape) and the **one-PG-N-schemas ADR** in `aidocs/strategy/105`
   (TimescaleDB + PostGIS + pgvector + future `shepard_tables`
   co-resident, joinable via plain SQL) means shepard now has a substrate
   **none of the surveyed five platforms can match without a multi-year
   re-architecture**. Kadi4Mat's PostgreSQL, openBIS's PostgreSQL, SciCat's
   MongoDB+Elasticsearch, Coscine's PostgreSQL, NOMAD's Elasticsearch all
   sit on stores where a "B-scan slice + AFP TCP trail + pgvector kNN
   over journal text" join is either impossible or requires three round
   trips through different APIs. Shepard's "one Postgres, four
   extensions, plain SQL across them" claim is now an architectural
   moat, not just a vision sentence. See §3 + §4.

2. **Shepard is the only RDM platform with a *MUST-Tier-0 local AI*
   commitment.** Per `aidocs/integrations/97 §0`, every AI surface MUST
   work out-of-the-box with zero external dependencies — a TEI sidecar
   carries `jina-embeddings-v2-base-de` (768d) the moment `make dev`
   finishes. Kadi4Mat's KadiAI is a BYO-algorithm dashboard
   (researcher-trains-model, no inference shipped). NOMAD's
   `nomad-aitoolkit` is a Jupyter-notebook scaffold. SciCat / openBIS /
   Coscine ship no AI. The YC-default "AI-as-OpenAI-binding" is the
   path shepard explicitly rejects via the four-tier provider model.
   See §4.

3. **Shepard's semantic-annotation primitive is *MCP-first-class with
   3-click default UX*** — the one cut no competitor offers. Per
   `aidocs/semantics/100 §1`, eleven narrow MCP tools (`list_vocabularies`,
   `search_annotations`, `assert_annotation`, …) read the same canonical
   `:SemanticAnnotation` store the UI's 3-click affordance writes to,
   with **every write captured as a typed f(ai)²r `:Activity`**. Kadi4Mat
   has RDF export + SHACL validation but no agent-CRUD shape; Coscine's
   RDF story is template-driven write-side; SciCat has no semantic
   layer. None expose annotation CRUD as an MCP tool surface for AI
   agents. See §4 + §10 thesis check.

The **direction of travel** is also worth naming: the pre-V6 framing
was "shepard is upstream-compatible with one frozen wire and a thin
fork ahead." The post-V6 framing is "shepard is a *substrate-different*
RDM platform whose differentiator is the shape of its data store —
timeseries + spatial + AI + tables under one PG, semantic + provenance
+ MCP on top, with byte-compatible v1 as a transition surface." That
**substrate framing is the new pitch** for any 2026-2027 funding /
conference / institute conversation.

What hasn't changed: adoption count is still the leading risk
(Coscine: 1,500+ users; Kadi4Mat: 100+ public records; shepard:
small DLR-internal pilot footprint). All three verdict shifts make
shepard *more* differentiated, not *more* adopted — they buy a sharper
pitch, not a bigger user base.

---

## §2 — Per-competitor 2026 update

What each platform has shipped or directionally signalled since the
2026-05-21 baseline. Where 2025-2026 release evidence exists, it's
cited.

### Kadi4Mat (KIT / NFDI4ING) — still the closest peer

**What's new since baseline:** the 2025-03 Community Meeting at HIFIS
[^kadi-cm] confirmed Kadi4Mat's positioning as "generic ELN +
research data infrastructure" with active roundtables shaping the
roadmap. The platform's headline page now foregrounds **KadiAI** as the
AI integration surface [^kadi-front] and reaffirms **RO-Crate
import/export + SHACL + RDF export**. Selzer/Nestler/Riem (the KIT
maintainers) are co-authors of the Schlenz et al. RDM handbook
[Schlenz2026][@schlenzRdmHandbook2026], making Kadi4Mat the
*reference-implementation peer* the engineering-RDM community
collectively treats as the standard.

**Direction signal:** KadiAI is BYO-algorithm — the researcher brings
the model + training script; Kadi4Mat provides the metadata-capture
scaffold so the run + hyperparameters + outputs are RDM-tracked. This
is **directionally complementary, not overlapping**, with shepard's
v6 AI cut. Shepard ships an inference-default (Tier 0 TEI sidecar);
Kadi4Mat ships a training-record scaffold. Both are correct answers
to different questions.

**Where Kadi4Mat is ahead.** Community visibility, NFDI4ING adoption,
the handbook reference status, public-record corpus, SHACL adoption
inside the workflow. Where shepard catches up via §4.

### Coscine (RWTH / NFDI4ING) — the federated platform

**What's new since baseline:** Coscine's roadmap page [^coscine-news]
shows 2026 work on **community-specific metadata storage** (June 2026
release), **API restructuring** (March 2026), and **AIMS federated
search inside TS4NFDI** for "more precise and detailed ontology
results." No AI-inference work surfaced. No timeseries-substrate work
surfaced.

**Direction signal:** Coscine is doubling down on the **federated
discovery + metadata-archival** cut (it's now used by 1,500+ users
across 138 institutions [^coscine-adoption]). The AIMS work suggests
Coscine wants to be the *ontology-search surface* for engineering
RDM, not the *workflow tool*. This complements shepard well: Coscine
is the discovery + cold-storage layer; shepard is the data-context
layer for active campaigns.

**Where Coscine is ahead.** Adoption (1,500+ users), federation
(RWTH + Helmholtz HDF), and NFDI4ING community velocity. Where
shepard catches up: stop chasing Coscine on adoption count; aim for
the integration story (shepard-above-Coscine per the handbook's §9.3).

### openBIS (ETH) — the lab-record platform with ELN heritage

**What's new since 2025:** openBIS 20.10.12 (production) shipped
significant ELN changes [^openbis-12]: lifted restrictions on object
creation under Spaces/Projects, redefined standard ELN types, and
made **Zenodo + ETH Research Collection exports compatible with
general export**. 20.10.12.3/12.4 (Feb 2026) carry stabilisation
fixes [^openbis-12-3]. No semantic-annotation, no AI, no timeseries
work shipped in this release line.

**Direction signal:** openBIS continues to lead on the **typed-property
+ deep-history per sample** axis (entity types carry strongly-typed
property fields with full audit). Its export-to-Zenodo work is
adjacent to shepard's KIP1d DataCite + UH1 Helmholtz Unhide cuts
— different downstream catalogues, same FAIR-publishing shape.

**Where openBIS is ahead.** Typed-property maturity, ELN UX
completeness, ZeNoodo + ETH Research Collection export pipeline.
Where shepard catches up: shepard's `:SemanticAnnotation` primitive
(`aidocs/semantics/100 §3`) is the **typed equivalent** at the
annotation layer, not the entity-property layer. Different cuts on
the same problem.

### SciCat (PSI / ESS / DESY / ILL / MAX IV / ALS / MLZ / Soleil) — facility-scale catalogue

**What's new since 2025:** SciCat 3.1.0 shipped 2025-03-06 [^scicat]
with continued focus on metadata aggregation across photon/neutron
facilities. Ten major facilities + DECTRIS CLOUD are on the deployed
list. SciCatCon 2026 announced for 30-Jun–2-Jul at MLZ Munich. No
AI, no RO-Crate, no semantic annotation features visible on the
public site.

**Direction signal:** SciCat is positioning as the *facility-side
data catalogue* that links proposals + techniques + dataset records.
Its absence of provenance / semantic / AI layers is **deliberate** —
the facility-context model assumes the proposal record + instrument
metadata already carry the structure SciCat needs.

**Where SciCat is ahead.** Facility-side adoption (10+ neutron /
photon sources), proven scale at ESS / PSI volumes, hardware-bound
metadata pipeline integrations. Where shepard catches up: shepard is
*not* a SciCat competitor for facility-bound photon/neutron data —
that's a different operational shape. The integration angle is "use
SciCat for facility-bound campaign metadata + shepard for the
follow-on data-shaping work."

### NOMAD (FAIRmat-NFDI / MPI) — the materials-science catalogue

**What's new since baseline:** NOMAD 1.4.0 shipped an **alpha new
GUI** [^nomad-news]. Plugin ecosystem is the active growth axis:
**`nomad-catalysis`** (CatalystSample + CatalyticReaction entry types),
**`nomad-aitoolkit`** (Jupyter-notebook AI scaffold) [^nomad-aitoolkit],
**`nomad-simulations`** (workflow base sections), and two new
exchange-format plugins demoed at MADICES 2025. Workflow utilities
support "AI-ready dataset curation."

**Direction signal:** NOMAD is the materials-science *catalogue+
exchange-format* leader, with strong workflow primitives and growing
plugin diversity. AI integration is **toolkit-scaffold-shaped** (not
inference-default like shepard v6). The DPG 2025 AI-driven Materials
Design symposium [^fairmat-news] frames AI as a *consumer* of
NOMAD's curated datasets, not as a built-in service.

**Where NOMAD is ahead.** Plugin maturity (a dozen+ shipped plugins
covering distinct entry types), materials-science domain depth,
workflow-utility primitives, AI-ready-dataset curation discipline.
Where shepard catches up: shepard's PluginManifest SPI (PM1a/PM1f)
matches NOMAD's structural choice; the differentiator is **inference
substrate vs. exchange-format substrate**.

### FAIRDOM-SEEK + the long-tail (Dataverse / Zenodo / B2SHARE / re3data)

**What's new:** FAIRDOM-SEEK's release cadence is steady [^fairdom]
but the headline AI/provenance moves are absent from public news;
the platform's ISA-JSON + RO-Crate roundtrip is a baseline
expectation. Dataverse and Zenodo are *deposit-archive* platforms,
not active-campaign tools — they're shepard *targets*, not
competitors. re3data is a *registry of registries* — also a target.

**Direction signal:** the long-tail is fragmenting into deposit
archives (Zenodo / B2SHARE / Dataverse) + facility catalogues
(SciCat / NOMAD) + active-campaign tools (Kadi4Mat / openBIS /
shepard). Shepard's market is the third bucket; integration with
the first two is FAIR-table-stakes (UH1 Helmholtz Unhide + KIP1d
DataCite shipped; Zenodo plugin queued per the priority memory list).

---

## §3 — The differentiator matrix (post-V6)

A single grid: feature × platform → `✓ shipped` / `📐 designed` /
`✗ none`. New V6-shifted rows marked **(V6)**.

| Feature axis | **Shepard (v6)** | Kadi4Mat | Coscine | openBIS | SciCat | NOMAD |
|---|---|---|---|---|---|---|
| **Timeseries as first-class payload** | ✓ TimescaleDB + ECharts inline | ✗ | ✗ | ✗ | ✗ | ✗ |
| **Spatial-as-temporal-sweep substrate** **(V6)** | 📐 `shepard-plugin-spatial` (15 use cases) | ✗ | ✗ | ✗ | ✗ | ✗ |
| **NDT (B/C/D-scan, PAUT, TOFD, ET, AE, DIC) on one substrate** **(V6)** | 📐 (rows 9-15 of aidocs/90) | ✗ | ✗ | ✗ | ✗ | ✗ |
| **Cross-substrate SQL JOIN (TS × Spatial × pgvector × Tables)** **(V6)** | 📐 ADR-ACCEPTED `aidocs/105` | ✗ | ✗ | ✗ | ✗ | ✗ |
| **MUST-Tier-0 local AI (zero-egress embedding by default)** **(V6)** | 📐 `aidocs/97 §4` TEI sidecar | ✗ (BYO model) | ✗ | ✗ | ✗ | ✗ (toolkit scaffold) |
| **Pre-seeded ontologies (PROV-O + QUDT + m4i + RO + GeoSPARQL + …)** | ✓ 10 bundles (`aidocs/42`) | ~ partial | ~ Dublin Core | ~ entity-type-scoped | ✗ | ~ domain-scoped |
| **W3C PROV-O provenance + m4i dual-typing** | ✓ (PROV1a–PROV1h) | ✗ | ~ DMP-linked | ~ entity-audit | ~ proposal lineage | ✓ workflows |
| **Snapshot-pinned reproducible RO-Crate export** | ✓ (V2d / aidocs/31) | ✓ RO-Crate roundtrip | ✗ | ✓ Zenodo export | ✗ | ✗ |
| **One-click DataCite DOI minting** | ✓ KIP1d plugin | ✗ (user-initiated) | ✗ (BYO DOI) | ✗ | ✓ via PaNOSC | ✓ |
| **Helmholtz Unhide / HKG feed** | ✓ UH1a plugin | ✗ | ~ partial | ✗ | ✗ | ✗ |
| **Plugin SPI for new payload kinds (drop-in JAR)** | ✓ PM1a + PM1f sidecars | ~ via REST | ~ via API | ✗ | ✗ | ✓ Python plugins |
| **License + accessRights enum on every entity** | ✓ LIC1 / FAIR-1 shipped | ✓ | ✓ | ✓ | ✓ | ✓ |
| **ORCID stamp at creation + display name** | ✓ U1a+U1b shipped | ✓ | ✓ | ✓ | ✓ | ✓ |
| **`:SemanticAnnotation` typed (subject/pred/object/source) + open-world** **(V6)** | 📐 `aidocs/100 §1` | ~ schema-typed | ~ template-driven | ~ entity-type-typed | ✗ | ✗ |
| **MCP CRUD tools (list / search / assert / retract / search-by-embed)** **(V6)** | 📐 11-tool surface (`aidocs/100 §10`) | ✗ | ✗ | ✗ | ✗ | ✗ |
| **f(ai)²r typed Activity per AI mutation** | ✓ AI1a shipped (`:AiActivity`) | ✗ | ✗ | ✗ | ✗ | ✗ |
| **Backend ELN / lab journal CRUD** | ✓ J1a/b/c/d shipped | ✓ ELN-first | ~ via DMP fields | ✓ ELN-mature | ✗ | ✗ |
| **HDF5 / HSDS via sidecar** | ✓ A5a shipped (UI pending) | ~ via storage backend | ~ via NFDI-Repository | ✓ data store integration | ~ via filesystem | ✓ first-class |
| **Templates DSL (admin-editable, JSON-bodied)** | ✓ T1a-T1f + UI2a shipped | ~ record types | ~ metadata profiles | ✓ entity types | ✓ proposal templates | ~ schema plugins |
| **Snapshots + diff + RO-Crate pinning** | ✓ V2b/c/e + UI1a shipped | ✗ | ✗ | ~ audit history | ✗ | ✓ workflow versions |
| **Adopters (public)** | small DLR ZLP pilot | 100+ public records | 1,500+ users / 138 institutions | 50+ academic sites | 10+ facilities (PSI/ESS/MAX IV/…) | 100k+ entries indexed |
| **Community visibility in handbook ([Schlenz2026][@schlenzRdmHandbook2026])** | ✗ (gap to close) | ✓ co-authored | ✓ §9.3 storage layer | ✗ | ✗ | ~ via NFDI domain pages |

The V6-marked rows are the ones where shepard moves from
"complementary" to "substrate-different" — they're collectively the
post-V6 verdict shift.

---

## §4 — Where shepard wins now

Five sharpened differentiators created or strengthened by V6.

### 4.1 Brush-sweep substrate (the SPATIAL-V6 cut)

The structural recognition in `aidocs/data/90 §1` — that **AFP head
sweeps, robot welding paths, ultrasonic line/B/C/D-scans, PAUT
sector scans, TOFD pair scans, ET impedance sweeps, AE
hit-localisation, DIC strain fields, structured-light scanner
passes, CT slices, and laser profilometer traces all collapse to one
substrate** — is *the v6 flagship*. The 15-row catalogue in §1 of
that doc maps each to the same `(time, profile_kind, profile_geometry,
anchor, orientation?, measurements, anchor_frame)` row. Manufacturing-
process recording + NDT inspection are the same problem under
different render modes.

**Why no competitor matches this.** Kadi4Mat, openBIS, SciCat, NOMAD,
Coscine all treat geometry as a static file (mesh upload, no time
axis) or treat time-series as scalars (no per-sample geometry). The
MFFD/PLUTO use-case wants both at once. Shepard's PostGIS-on-
TimescaleDB choice is the *one substrate* that serves both natively;
no plugin to any of the surveyed competitors gets there without a
multi-quarter substrate addition.

**Where this lands.** The killer demo is the AFP TCP thermal-trail
([`project_mffd_seed_demo.md`](../../mffd-showcase/seed.py) +
`aidocs/data/90` §11 acceptance test) — a brush stroke through 3D
space with each vertex colour-coded by bound measurement, time-
scrubbable, with the as-designed CAD model underneath via the
`shepard-plugin-cad` frame-handshake. *No other surveyed RDM
platform can render that view at all today*, let alone with
provenance + snapshots + annotations on the same canvas.

### 4.2 MUST-Tier-0 local AI

The rule crystallised in `aidocs/integrations/97 §0`:

> **Always: ship a working local default for every AI capability.**
> Every AI surface MUST work out-of-the-box with zero external
> dependencies. External providers are admin-gated opt-ins.

The Tier 0 default — a TEI CPU sidecar with `jina-embeddings-v2-base-de`
preloaded — means **embed-and-search works the moment `make dev`
finishes against the LUMEN demo without any operator action**. The
four-tier provider model (Tier 0 local CPU, Tier 1 local GPU, Tier 2
SAIA/GWDG networked, Tier 3 external cloud) makes the escalation path
explicit; an MFFD-IP-sensitive deployment never has to opt in.

**Why no competitor matches this.** Kadi4Mat's KadiAI is BYO-model
training scaffold (not inference). NOMAD's `nomad-aitoolkit` is a
Jupyter scaffold (notebook-scoped, not service-grade). SciCat,
openBIS, Coscine have no AI integration. The YC-default (every
AI-aware tool hard-bound to OpenAI) is the path shepard explicitly
rejects via the four-tier model. The result: shepard is the *only*
European RDM platform where the first-run experience includes
working semantic search with zero accounts, zero egress, zero GPU.

**Where this lands.** First-consumer is `shepard-plugin-wiki-writer`
(TEXT) + the MCP `search_by_embedding` tool (EMBEDDING) per the §1
"first consumer" line. The downstream wave is auto-annotation
suggestion, snap-dashboards (AI1e), and the natural-language search
that would otherwise require the digital-native persona to write
their own pgvector layer.

### 4.3 ADR-as-substrate-rigour (the `aidocs/105` shape)

The ADR-shipping discipline crystallised in `aidocs/strategy/105`
itself is a process differentiator. The doc:

- Opens with the verdict ("ACCEPTED, 2026-05-24"), not exploration.
- Closes seven audit rows (§9) + unblocks two flagship features
  (§10) in one ADR.
- Names what it does *not* design (§11) so the scope stays sharp.
- Carries an alternatives table (§12) with one-sentence rejection
  reasoning each.
- Lists same-PR tracker obligations (§13 row 1–7) for the trinity
  of standing-rule updates.

**Why this is a competitive moat.** The two-week round trip from
"plugin design has substrate ambiguity" → "audit reveals tension" →
"ADR resolves + unblocks shipping" demonstrates a *process*
European competitors don't visibly exhibit (the public release notes
of Kadi4Mat / Coscine / openBIS / SciCat don't surface ADR-style
substrate decisions at this cadence). Engineering rigour of this
shape is **itself the institutional pitch**: a funder reading
`aidocs/105` sees how the project makes irreversible decisions — and
the answer is "with citations, alternatives, audit-row closure, and
runtime knobs by default."

### 4.4 Typed semantic annotation + MCP CRUD

`aidocs/semantics/100 §1` lands the single-shape commitment:

> One canonical primitive — `:SemanticAnnotation` Neo4j node, typed
> subject/predicate/object plus f(ai)²r provenance — backs every UI
> affordance, every MCP tool, every SPARQL query, every SHACL
> validation. No per-payload-kind annotation shape; no free-text bag.

Plus the 3-click default + 11-tool MCP surface (`list_vocabularies`,
`search_annotations`, `assert_annotation`, `retract_annotation`,
`search_by_embedding`, etc.) with **every write returning
`activity_appid` so the agent self-reports provenance to the same
audit trail a human writes**.

**Why no competitor matches this.** Kadi4Mat has RDF export + SHACL
validation but no agent-CRUD shape; the MCP surface doesn't exist
because no MCP-first plugin design has landed. Coscine's RDF is
template-driven write-side (you pick a schema template upfront, not
a predicate at annotation time). SciCat has no semantic layer.
openBIS has typed entity properties but no IRI-based vocabulary
layer. **The MCP-first surface is genuinely new** — and it pairs
with V6's local-AI commitment, because the agent annotating with
shepard MCP tools can be powered by the in-process TEI sidecar
without any external dependency.

### 4.5 Pareto position **refreshed** — substrate, not just storage

Per §6 below — the substrate-different framing makes the five-
constraint claim sharper.

---

## §5 — Where shepard still loses (honest)

The verdict shifts do not close every gap. Be specific:

### 5.1 Adoption (the leading risk, still)

| Platform | Public adopters | Shepard's gap |
|---|---|---|
| Coscine | 1,500+ users, 138 institutions | ~750× |
| Kadi4Mat | 100+ public records, NFDI4ING-used | ~50× |
| openBIS | 50+ academic sites | ~25× |
| SciCat | 10+ major facilities | ~5× (different shape) |
| NOMAD | 100k+ indexed entries (different shape) | N/A |
| Shepard | small DLR ZLP pilot | — |

**Strongest pressure on this axis:** Coscine. Their AIMS federated
search + community-metadata work directly competes for the
"ontology-aware engineering RDM" mindshare — and they have the
NFDI4ING audience already paying attention. The verdict-shift in §1
does NOT change this; it sharpens shepard's pitch but does not
deploy a single new instance.

**Mitigation that fits the V6 reshape:** lean into the
*substrate-different* framing — Coscine is the federated discovery
layer; shepard is the active-campaign data-context layer; the
integration story ("Coscine → shepard → snap-dashboard") is the
narrative that doesn't fight Coscine's strength.

### 5.2 Community visibility — the handbook gap

Per the existing `project_competitive_position.md` memory: the
Schlenz et al. 2026 RDM handbook [^schlenz] is the community-
standard reference for engineering-research RDM in Germany, and it
**does not name shepard**. Kadi4Mat (Selzer/Nestler/Riem) and
Coscine (§9.3) are featured authors / case studies.

**Strongest pressure on this axis:** the handbook is durable
(CC BY-SA 4.0, V1.1, 100 pp, NFDI4ING-CADEN-funded). Closing this
gap means a CoRDI 2026/2027 paper that occupies the slot the
handbook does not have a candidate for: **industrial-scale process
data + multi-substrate + large-timeseries**. V6's spatial substrate
is the wedge.

### 5.3 ELN UX completeness

openBIS leads on typed-property ELN mature UX. shepard's J1 series
is backend-rich (J1a–J1d shipped) but UI-thin — the lab-journal-
history panel, the diff viewer, and the rich notebook surface all
exist as endpoints but not browser-renderable interactions per the
strategy-advisor's "backend-rich + UI-thin" finding.

**Strongest pressure on this axis:** openBIS for the ELN-mature
audience. **Mitigation:** ship the J1 UI in the same cluster as
the V6 wave; the SHACL-templates + ontology-picker work for V6
annotations gives the rich-form-renderer the J1 UI also needs.

### 5.4 Plugin maturity headcount

NOMAD ships a dozen+ shipped plugins (`nomad-simulations`,
`nomad-catalysis`, `nomad-aitoolkit`, `nomad-app-plugins-simulation`,
the two MADICES 2025 exchange-format plugins, …). shepard's
plugin headcount is smaller (11 modules per
`backend/plugins/` per `ecosystem-advocate.md`).

**Strongest pressure on this axis:** NOMAD's plugin ecosystem
proves the SPI shape pays off — and that pays off mostly in
domain-specific entry-types. shepard's plugin SPI is structurally
equivalent (PM1a + PM1f); the gap is plugin *quantity*, not plugin
*shape*. Mitigation: the V6 wave ships 3 plugins (spatial, ai,
upgraded annotation), narrowing the gap.

### 5.5 Facility-scale photon/neutron operations

SciCat's deployment at ten major facilities (PSI, ESS, MAX IV,
DESY, MLZ, etc.) is genuine domain depth. shepard is **not** trying
to compete here — but a funder asking "why not just use SciCat?"
needs the honest answer: facility-bound proposal-driven workflows
are SciCat's strength; shepard's strength is the *post-facility*
campaign work where the data leaves the facility and joins other
data in an integrated analysis.

---

## §6 — The Pareto restatement (refreshed for V6)

The `project_pareto_position.md` memory's five-constraint claim
stays at five constraints — V6 does not add or remove one, but it
**sharpens the language** of three and **strengthens the
defensibility** of all five.

| # | Constraint (pre-V6 language) | Post-V6 sharpening |
|---|---|---|
| 1 | W3C-standard vocabularies (RDF / OWL / SHACL / PROV-O) | **Now also: typed `:SemanticAnnotation` primitive + MCP CRUD reads same store** (aidocs/100). The vocabularies are no longer just an export shape — they're the *write-time substrate* the UI + agents both interact with. |
| 2 | Graph-scale performance — sub-100 ms typical ops at 10⁵+ DataObjects | **Now also: cross-substrate SQL joins across TS+spatial+pgvector+Tables on one PG** (aidocs/105). The graph layer stays Neo4j; the *measurement substrate* gains joinable depth that no triplestore-only or single-substrate competitor matches. |
| 3 | Plugin-extensible — adding a payload kind doesn't require a fork | **Now also: brush-sweep substrate + local-first AI ship as drop-in plugins, not core forks** (aidocs/90 / aidocs/97). The plugin SPI now carries SidecarSpec (PM1f) so plugins can declare their own infra dependencies — the Spatial plugin needs no compose-file edit, the AI plugin auto-renders its TEI snippet. |
| 4 | Upstream-compatible — `/shepard/api/...` paths frozen against `gitlab.com/dlr-shepard/shepard 5.2.0` | Unchanged. The v1 wire stays frozen; v6 lands at `/v2/`. |
| 5 | Operator-runnable without a specialist DBA — `docker compose up`, no Stardog/GraphDB cluster | **Now also: zero-egress AI by default + one-PG-N-schemas** (aidocs/105 §1, aidocs/97 §0). The local-AI MUST means a fresh install does AI without sign-ups; the schema-collapse means *one* Postgres to back up + tune, not three. |

**The restated slogan.** Pre-V6: *"Shepard is the RDM platform where
the ontology drives the UI — without giving up scale, ops simplicity,
or upstream compatibility."*

Post-V6: *"Shepard is the RDM platform with a substrate other
platforms can't match — time-varying geometry + sensor timeseries +
embedding search on one Postgres, with local-AI by default, with
the ontology driving the UI, with the v1 wire frozen for upgrade
safety — none of which requires a specialist DBA or external API
key."*

**Conditions that would change this.** Unchanged from the memory:
if upstream-compat drops (constraint 4 falls away), a slightly
different optimum opens. If ops-complexity becomes acceptable
(cluster ops available), a pure triplestore variant becomes
feasible. V6 *adds* one watch condition: **if PostGIS workloads
regularly exceed 30 s per query at MFFD scale, the side-B
isolation argument (separate Postgres per workload) re-opens** —
the `aidocs/105 §12` counter-evidence row that the user should
re-evaluate in 2027.

---

## §7 — External AAS landscape (BaSyx + Tractus-X + EDC)

The AAS-side platforms are **integration targets**, not direct
competitors. The 2025-2026 state:

### Eclipse BaSyx

BaSyx is the **open-source Industry 4.0 middleware AAS reference
implementation** [^basyx]. Current SDK stack: BaSyx Java V2 (AAS
v3-compliant), BaSyx TypeScript SDK (v3-compliant), BaSyx AAS Web
UI, Docker images for AAS Repository / Submodel Repository /
Registry / Environment. 744 commits / 274 issues / 604 reviews over
the last 12 months across 14 repositories — active maintenance.

**The integration shape for shepard.** Per `aidocs/52` AAS1 design
(parked), the question is "should shepard *be* an AAS repository
backend, or *integrate with* BaSyx as the AAS layer?" The V6
verdict makes the latter clearer:

- BaSyx is the AAS-Standard surface (registration, discovery,
  submodel CRUD, shell composition).
- Shepard is the *data-substrate* surface for the contents the AAS
  references (timeseries, brush traces, files, annotations,
  provenance).
- The integration is a BaSyx Submodel implementation that
  resolves submodel-element values from shepard's `/v2/...` REST
  + `/v2/timeseries-references/.../data` substrate.

This is the same shape as shepard's relationship to JupyterHub
(shepard is the data layer; JupyterHub is the compute layer) and to
Helmholtz Unhide (shepard is the catalogue; HKG is the federated
discovery layer). **Shepard does not need to be an AAS
implementation; it needs to be a first-class data-substrate AAS
content provider.**

### Eclipse Tractus-X / Catena-X

Tractus-X is the **automotive-industry data space reference
implementation** [^tractus-x]. The 2025 Saturn release combines
Tractus-X 25.06 + 25.09 with extended AAS support (IDTA 25-01
specs) and Dataspace Protocol updates. Direction: **federated data
sovereignty in supply chains**.

**The integration shape for shepard.** Aerospace is not automotive,
but the structural problem (data sovereignty across institutional
boundaries) is the same. The likely shepard angle is **shepard-as-a-
data-source via the Eclipse Dataspace Connector** — the EDC
adapter brokers shepard datasets into a Catena-X-like dataspace
without shepard itself implementing the EDC protocol. The AAS1-edc
parked row in `aidocs/16` captures this; V6 doesn't move it.

### Eclipse Dataspace Connector (EDC)

EDC is the **dataspace-protocol implementation framework** [^edc],
with DSP 2025-1 + DCP 1.0 support, active development (2,123
commits / 36 contributors / 16 repositories over 12 months).
**The integration shape for shepard:** the AAS1-edc row sits parked
because the EDC has no concrete shepard adopter yet. The right
move (post-V6) is to write an EDC connector that exposes shepard
`/v2/` datasets as DSP-discoverable assets — a thin Java module
sitting alongside the existing plugin SPI.

**Verdict for shepard:** *integrate with BaSyx + EDC; do not become
an AAS implementation*. The brush-sweep substrate + local-AI cuts
are more uniquely shepard's; AAS is a standard surface where
BaSyx is the reference implementation and re-implementing it is a
distraction from V6's flagship cuts.

---

## §8 — Adoption barriers (next 6 months)

What stops a DLR institute adopting today; what closes the gap.

| Barrier | Severity | What closes it | V6 status |
|---|---|---|---|
| Public hostname doesn't expose the API (`persona-digital-native-2026-05-24.md §0`) | CRITICAL | Caddy/NextAuth split documented + fixed | tracked, not V6-related |
| No screenshot pipeline in user docs | HIGH | D1b Playwright pipeline | queued |
| No Python SDK | HIGH | CG1 series + the empty SDK | queued |
| No reference installation walkthrough for "I'm an IME at ZLP" | HIGH | Quickstart + showcase notebook deploy | partial |
| Operator runbooks for substrate ops (PG collapse, Garage backup, Neo4j tune) | MEDIUM | PG-COLLAPSE-003 + sibling runbooks | scheduled (aidocs/105 §13) |
| 5-tuple channel identity (TS-IDc migration) | MEDIUM | `aidocs/platform/87` migration | designed |
| MCP surface narrow (a few tools; not enough for AI agent self-service) | MEDIUM | aidocs/100 §10 11-tool surface | designed (V6) |
| Brush-sweep capability not yet demo-able (the headline V6 cut) | MEDIUM | SPATIAL-V6-001 ships + MFFD TCP demo | gated on aidocs/105 (decision shipped) |
| Local AI Tier 0 sidecar not yet in default compose | MEDIUM | AI-V6-002 ships | gated on aidocs/105 (decision shipped) |
| Backup contract (PG + Neo4j + Mongo) | HIGH | PG-COLLAPSE-002 + BACKUP-CONTRACT-NEO4J-MONGO | scheduled (aidocs/105 §13) |

**The shape:** the V6 ADR (aidocs/105) is the keystone — once it
lands, the two V6 flagship features (SPATIAL + AI) unblock, the
backup contract becomes shippable, the operator-runbook trio
(restore + collapse + drill) joins the docs library. Six months
out, the realistic adoption-blocker list collapses to **screenshot
pipeline + Python SDK + Quickstart deploy walkthrough**.

---

## §9 — The "shapard" thesis check

The user's working hypothesis (from prior conversations): the v6
reshape is "shape > storage" — meaning shepard's identity moves
from "yet-another-RDM-platform-with-storage" to "the RDM platform
whose *substrate shape* differentiates."

**Honest check.** Does V6 actually deliver this?

Yes, with one caveat. The five V6 landings collectively shift the
identity from "a graph + a TS DB + some plugins" to "a coherent
substrate where shape, semantics, AI, and provenance compose at
write-time, not just at export-time." Specifically:

- `aidocs/data/90` says: brush traces are not a file payload; they
  are a *first-class shape* with a render contract (VIEW_RECIPE
  per SHACL) and a write contract (the green-field schema).
- `aidocs/integrations/97` says: AI is not an external service we
  call; it is a *capability tier* with a local default that ships
  with the substrate.
- `aidocs/strategy/105` says: storage is not N independent DBs;
  it is one PG with named schemas that joins cross-domain.
- `aidocs/semantics/100` says: annotation is not a free-text tag
  bag; it is a typed primitive that the UI + MCP + SPARQL + SHACL
  all read from the same store.
- `aidocs/43` says: the implementation's actual requirements are
  knowable, citable, challengeable — the descriptive shadow IS the
  contract.

The caveat: **the differentiator only lands if the implementation
actually ships**. The five SSOTs are `stage: feature-defined` not
`stage: deployed`. The pre-V6 framing's adoption gap (1,500-user
Coscine vs. small-pilot shepard) does not move on design docs
alone. The thesis is *correct on direction* — the V6 SSOTs lay the
substrate; whether it converts to adoption depends on whether
SPATIAL-V6-001 + AI-V6-002 + the annotation primitive + the local-
AI default actually ship in code + UI within the next 1-2 quarters.

The Pareto restatement in §6 stays defensible **today** because
the ADR (`aidocs/105`) is `status: ACCEPTED` — that's a substrate
commitment that goes beyond design. The other four SSOTs are
implementation-bound. If the user wants the "shapard" thesis to
become operationally true (not just designed), the priority order
is: PG-COLLAPSE-001 first (unblocks both flagships) → SPATIAL-V6-
001 (the killer demo) → AI-V6-002 (the zero-egress default) →
SA-001 (annotation primitive + MCP) → R-43 stays as the running
contract.

---

## §10 — Memory updates (surface for the user)

Edits to surface for `project_competitive_position.md` +
`project_pareto_position.md`. Do **not** apply automatically — these
are notes for the user to commit.

### `project_competitive_position.md`

**Add a new top-level section:** `## V6 verdict shift (2026-05-24)`
containing:

```
The 2026-05-21 framing ("complementary not competitive") still holds
on the surface, but five V6 SSOTs reshape the differentiator stack:

1. aidocs/data/90 — brush-sweep substrate: 15 manufacturing + NDT
   use cases collapsed to one (time, anchor, profile, measurements)
   row. None of the surveyed five competitors match this shape.
2. aidocs/integrations/97 — MUST-Tier-0 local AI: TEI sidecar with
   jina-v2-base-de ships in the default compose. Kadi4Mat is BYO,
   NOMAD is toolkit-scaffold, the rest are absent. Shepard is the
   only RDM platform where first-run AI works zero-egress.
3. aidocs/strategy/105 — POSTGRES-MULTITENANT ADR (ACCEPTED): one
   PG, four schemas (shepard_ts + shepard_spatial + shepard_ai +
   shepard_tables), cross-substrate SQL joins. No competitor's
   substrate supports this.
4. aidocs/semantics/100 — typed :SemanticAnnotation primitive +
   MCP CRUD + 3-click UI default. The MCP-first agent surface is
   genuinely new.
5. aidocs/43 — descriptive-shadow contract (rev-engineered
   requirements with citation, confidence, challenge handles).

Verdict shift: shepard is no longer "complementary"; it is
*substrate-different*. Use the substrate framing in 2026-2027
funding / conference / institute conversations.
```

**Update the top "Top actionable gaps" list:** the #1 priority
(`license` field) is now **shipped (LIC1/FAIR-1)**; move it to a
"Shipped since baseline" section. Promote items #2-6 up one rank;
add new top item: **adoption pivot — lean into substrate-different
framing vs. Coscine; integration story rather than competition**.

### `project_pareto_position.md`

**Update the §"Claim" block:** keep the five constraints; **refresh
the language of constraints 1, 2, 3, and 5** per the §6 table
above (no constraint added or removed).

**Update §"Why this matters":** add one line — *"Post-V6 (2026-05-
24): the substrate-different framing makes the five-constraint
claim sharper; none of the surveyed five competitors satisfy
constraint 2 (cross-substrate SQL joins) at all."*

**Update §"Conditions that would change this":** add the V6 watch-
condition — *"If PostGIS workloads regularly exceed 30 s per query
at MFFD scale (aidocs/105 §12 counter-evidence), the side-B
isolation argument re-opens; re-evaluate the schema-collapse vs
instance-split cut. Mitigate first with role-level
statement_timeout per PG-AUDIT-004."*

**Update §"Source":** *"Synthesised from architecture work 2026-
05-21 + V6 SSOT trinity 2026-05-24 (aidocs/90 + aidocs/97 +
aidocs/100 + aidocs/105 + aidocs/43); see
`aidocs/agent-findings/competitive-reassessment-2026-05-24.md` for
the verdict-shift narrative."*

### Reading list (new entries per `feedback_reading_list.md`)

Two-to-five surfaced-not-yet-pursued reading list additions to
`aidocs/reading-list.md`:

1. **`nomad-aitoolkit`** source (`github.com/FAIRmat-NFDI/
   nomad-aitoolkit`) — read the Jupyter-notebook AI scaffold
   pattern to understand the NOMAD audience's AI consumption
   shape.
2. **`nomad-catalysis`** plugin (`fairmat-nfdi.github.io/
   nomad-catalysis-plugin`) — the canonical NOMAD plugin example
   for shepard plugin authors to study.
3. **Coscine AIMS federated-search work** (TS4NFDI Incubator) —
   tracks the federated ontology-search direction that complements
   shepard's local annotation surface.
4. **Eclipse Tractus-X Saturn release (2025.06 + 2025.09)** — read
   the AAS / Dataspace Protocol integration shape for the
   shepard-as-EDC-data-source post-V6 design.
5. **openBIS 20.10.12 ELN model change** (lifted Object-under-
   Spaces restriction) — comparable simplification to shepard's
   `aidocs/56` v2 API simplification.

---

## §11 — Verdict in one paragraph

The V6 SSOT trinity (spatial substrate + local AI + PG multi-
tenant ADR + typed annotation + reverse-engineered requirements
contract) shifts shepard from "complementary peer of Kadi4Mat /
openBIS on a timeseries axis" to "substrate-different platform
where time-varying geometry, sensor timeseries, embedding search,
and tables compose joinably on one Postgres, with local AI by
default, with typed agent-CRUD semantic annotation, with a
descriptive contract that argues with itself." Adoption-count is
still the leading risk; community visibility in the Schlenz et al.
RDM handbook is still the gap to close; ELN UX completeness vs.
openBIS is still uneven. But the architectural moat is sharper:
**no surveyed competitor can match the substrate shape with a
single release; closing the gap requires multi-year platform re-
architecture each.** Lean into the substrate framing in
2026-2027; ship the V6 keystone implementations (SPATIAL-V6-001,
AI-V6-002, SA-001) in the next 1-2 quarters; pair with adoption
pivot toward integration narratives (shepard-above-Coscine,
shepard-as-AAS-data-substrate-via-BaSyx, shepard-as-EDC-data-
source) rather than head-to-head competition.

---

## §12 — Citations + references

[^kadi-cm]: Kadi4Mat Community Meeting 2025, 17-19 March 2025, HIFIS, https://events.hifis.net/event/2035/
[^kadi-front]: Kadi4Mat front page, https://kadi.iam.kit.edu/ — KadiAI + RDF + SHACL + RO-Crate features as of 2026-05-24.
[^coscine-news]: Coscine news (2025-2026), https://about.coscine.de/news — community-specific metadata (June 2026), API restructuring (March 2026), AIMS federated search (TS4NFDI).
[^coscine-adoption]: Coscine adoption figures, ecosystem-advocate.md baseline carried forward (1,500+ users / 138 institutions).
[^openbis-12]: openBIS 20.10.12 release announcement, https://community.openbis.ch/t/openbis-20-10-12-release/506
[^openbis-12-3]: openBIS 20.10.12.3 and 20.10.12.4 releases (Feb 2026), https://community.openbis.ch/t/openbis-20-10-12-3-and-20-10-12-4-releases/556
[^scicat]: SciCat 3.1.0 (2025-03-06) + facility list, http://www.scicatproject.org/ ; SciCatCon 2026, 30 Jun – 2 Jul 2026, MLZ Munich.
[^nomad-news]: NOMAD-FAIRmat news (2025-2026), https://fairmat-nfdi.eu/fairmat/news-fairmat — 1.4.0 alpha GUI, two MADICES 2025 exchange-format plugins, AI-ready dataset curation.
[^nomad-aitoolkit]: nomad-aitoolkit plugin (Jupyter-notebook AI scaffold), https://github.com/FAIRmat-NFDI/nomad-aitoolkit
[^fairmat-news]: FAIRmat AI-driven Materials Design symposium at DPG Spring Meeting 2025, https://www.fairdi.eu/fairmat/news-fairmat?ctx=ALL
[^fairdom]: FAIRDOM-SEEK news (2025-2026), https://seek4science.org — release cadence steady; no AI/provenance headlines.
[^basyx]: Eclipse BaSyx Java V2 + TypeScript SDK + AAS Web UI, https://eclipse.dev/basyx/ ; 744 commits / 274 issues / 604 reviews over 12 months per https://metrics.eclipse.org/projects/dt.basyx/
[^tractus-x]: Eclipse Tractus-X Saturn release (25.06 + 25.09), https://catenax-ev.github.io/blog-releasenotes/cx-saturn ; AAS v3 IDTA 25-01 specs.
[^edc]: Eclipse Dataspace Components (EDC), https://projects.eclipse.org/projects/technology.edc ; 2,123 commits / 36 contributors / 16 repositories over 12 months; DSP 2025-1 + DCP 1.0 support.
[^schlenz]: Schlenz, Bronger, Selzer, Nestler, Riem, Enahoro (2026). *Research Data Management — A Practical Introduction.* V1.1, CC BY-SA 4.0, 100 pp, Zenodo `10.5281/zenodo.18468308`. NFDI4ING-CADEN-funded. Bib: `schlenzRdmHandbook2026`.

---

**Same-PR tracker obligations (per CLAUDE.md standing rules):**

1. **This doc** at `aidocs/agent-findings/competitive-reassessment-2026-05-24.md`.
2. **Memory updates** surfaced in §10 — for the user to commit.
3. **Reading-list updates** surfaced in §10 → `aidocs/reading-list.md`.
4. **Doc-stage index** regen via `python3 scripts/regenerate-doc-stage-index.py`.
5. **No `aidocs/34` / `aidocs/44` / vision update needed** — this is a
   findings doc, not a feature shipping, and the V6 SSOTs it
   references already carry their own tracker rows.
