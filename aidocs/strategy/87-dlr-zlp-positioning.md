---
title: "DLR ZLP Augsburg — institutional positioning and the substrate Shepard serves"
subtitle: "(thesis chapter draft — introduction / strategic context)"
stage: feature-defined
last-stage-change: 2026-05-23
audience: [thesis, strategy, funding-reviewer]
---

# DLR ZLP Augsburg — institutional positioning and the substrate Shepard serves

*Thesis chapter draft. This is the **introduction-and-strategic-context**
chapter that grounds Shepard in the institutional mission of its host
centre, the DLR Centre for Lightweight Production Technology in Augsburg
(ZLP Augsburg). It is a sibling to the continuity-of-field chapter at
[`aidocs/strategy/86-shepard-predecessor-systems.md`](86-shepard-predecessor-systems.md):
86 explains **what came before Shepard in the same building**; this doc
explains **what the building is for, and therefore what Shepard has to
be for**.*

## 1. The paradigm shift that defines the centre's mission

The global aerospace industry is navigating an epochal paradigm shift.
Climate-neutral aviation, in combination with the announced ramp-up of
single-aisle production rates to as much as 100 aircraft per month
[@zlpKompetenzportfolio2026], demands a structural move away from
metallic primary structures towards high-performance thermoplastic
carbon-fibre-reinforced polymers (CFRP). In that disruptive
environment, the strategic positioning of the DLR Centre for Lightweight
Production Technology in Augsburg is unusually precise: it sits at the
joint between academic research and industrial series production, and
its self-described function is to *de-escalate industrial investment
risk* by validating new manufacturing technologies at Technology
Readiness Level 6 (TRL 6) — the rung of the TRL ladder that closes the
so-called "valley of death" between a working laboratory prototype and
certifiable flight hardware [@zlpKompetenzportfolio2026].

The flagship example of that bridging function is the Multifunctional
Fuselage Demonstrator (MFFD), an eight-metre-long thermoplastic primary
structure produced under industrial conditions at ZLP Augsburg and
awarded the JEC World Innovation Award 2025 in the Aerospace–Parts
category [@mffdJecAward2025]. MFFD is also the canonical real-world
data source feeding Shepard's first production deployment outside the
LUMEN demo seed (see [`aidocs/platform/87-timeseries-appid-migration.md`](../platform/87-timeseries-appid-migration.md)
for the technical side of that ingest).

## 2. The five research groups and the lossless-process-chain claim

The centre's scientific portfolio rests on five interleaved research
groups, each addressing a distinct stage of the industrial process
chain [@zlpKompetenzportfolio2026]:

| Group | Core competence | Strategic value |
| --- | --- | --- |
| Flexible Automation Systems | Reconfigurable robot cells, cooperating systems, mechatronic grippers | Mastery of high variant complexity at full-scale geometry (e.g. 8 m fuselage shells) |
| Assembly & Joining Technologies | Thermoplastic welding (ultrasonic, resistance), bonding | Replaces mechanical fasteners (rivets) with integral, material-bonded joint zones — significant weight reduction |
| Production-Integrated Quality Assurance | Inline sensorics, computer vision, real-time data processing | Shifts QA from post-process inspection to in-process "zero-defect" defect correction |
| Processes & Automation | Cost analysis, optimisation of investment cost and cycle time | Lowers the economic entry barrier to high-rate aerospace automation |
| Technical Centre for Production Processes | Validation on production-scale equipment, industrial plant management | Guarantees process stability under real industrial boundary conditions (TRL 6) |

The portfolio's load-bearing claim is **lossless coverage of the
industrial process chain** — every step from layup to inspection to
joining is represented by a research group with its own equipment, its
own metric, and its own data stream. That claim drives the substrate
problem Shepard exists to solve: a centre whose institutional self-image
depends on demonstrating *continuity* of the process chain cannot afford
*discontinuity* in the data trail that documents it. The data
substrate has to be as lossless as the physical chain.

## 3. The flagship cells as the data sources of record

The centre's research groups operate four flagship cells, each globally
distinctive at the equipment level:

- **Multi-Function Cell (MFZ)** — the central, highly flexible research
  platform for integrated production, assembly, and quality assurance
  of full-scale structures; a "real-world laboratory for the factory
  of the future."
- **Fiber Placement Cell (FPC)** — specialised in Automated Fiber
  Placement (AFP). Laser- or infrared-based in-situ consolidation of
  thermoplastic tapes (CF/PEEK, LM-PAEK) eliminates the autoclave step,
  cutting takt time substantially.
- **Thermoplast Cell (TPC)** — industrial hot press for short-cycle
  processes. The portfolio reports peak interlaminar shear strength
  (ILSS) of 109 MPa at five-minute hold times (390–400 °C),
  versus 71 MPa for in-situ AFP and the 30-minute cycle of conventional
  Vacuum-Bag-Only (VBO) for a comparable 107 MPa figure.
- **Technology Testing Cell (TEC)** — Kuka Quantec robots on an 8 m
  linear axis providing the geometric reach required for full-scale
  parts.

Each of these cells is also a **data source of record**. The FPC's AFP
process produces multi-channel sensor traces and high-resolution gap/lap
imagery; the TPC's hot-press cycles produce dense thermal and pressure
profiles; the MFZ produces robot-trajectory logs from multiple
cooperating systems; the TEC's 8 m axis produces metrology streams. In
the language of the thesis's architecture chapter
([`aidocs/strategy/86`](86-shepard-predecessor-systems.md) §3, citing
the iDMS prototype's contribution), these are the timeseries that have
to be ingested at MHz resolution, contextualised with provenance, and
made queryable by domain.

### 3.1 The robotics platform: KUKA throughout

The Kuka Quantec robots in the TEC are not the only KUKAs at the
centre. The robotics platform across the cells is **KUKA-dominant**,
and the manufacturing-research stack is built against KUKA's
control-system family. This is institutionally important enough
to have its own internal technology-strategy talk — the 2025-07-23
*KUKA.CNC Robotics* deck by Krebs [@krebsKukaCnc2025]
documents the centre's evaluation of the KUKA.CNC integration
option (KUKA robots fronted by Siemens Sinumerik 840D control)
versus the conventional KRL+RoboDK path the centre operates today.

The deck's technical findings are concrete:

- KUKA.CNC requires **KSS 8.6 or higher** on the KUKA controller;
  the IIQKA.OS path is undocumented and likely incompatible.
- The integration uses the X63 EMI interface (the same connector
  family that RoboTeam uses) over PROFINET IRT / ProfiSAFE; on
  the Sinumerik side it requires custom 840D machine-data
  configuration (motors, kinematics, M-commands).
- Sinumerik 840D Sl with CNC-SW 4.9.x — and the newer
  Sinumerik ONE — are *not* compatible with the "Sinumerik Run
  MyRobot / Machining with KUKA robots" stack; Siemens's
  strategic direction (the "MTR / Run My Robot / direct control"
  family) is moving toward by-passing the KUKA controller
  entirely, which the deck flags as a **medium-term
  compatibility risk**.

The deck's strategic conclusion is more interesting than the
technical one. The argument for adopting KUKA.CNC is named as
*"CNC as compatibility factor towards Airbus, ASA, PAG et al."* —
i.e. the customer-side machine-tool world speaks CNC, and a
research centre that wants to hand its results to industry will
have a friction tax to pay if it does not. The argument against
is the cost: **approximately €100k of additional equipment cost,
mandatory off-line programming and simulation environment, new
post-processors for Sinumerik, training cost, and re-development
of existing tool integrations**. The deck closes on a paradigm
tension between **CNC (deterministic, programmed)** and
**autonomous production (KOKO + automated planning)** — the
centre's research direction tilts toward the latter, which makes
the case for CNC adoption weaker than it would be in a pure
production environment.

For Shepard, the KUKA-platform claim is the load-bearing one:
the trajectory logs, force-torque streams, and TCP-pose data that
Shepard's MFFD ingest moves into TimescaleDB are KUKA-controller
outputs. The thermal-trail Trace3D view (the canonical M1 wave
acceptance test, see `aidocs/semantics/98`) operates on a KUKA
KR 120 HA stream specifically. The integration shape Shepard
ships against today is **KRL + RoboDK + filtered force-torque
via real-time PC**, not KUKA.CNC. If the centre ever does adopt
KUKA.CNC, the timeseries-attribute namespace would gain a new
controller-frame qualifier; this is a **forward-compatibility
note** for the M1 wave's attribute design.

### 3.2 The metrology platform: Spatial Analyzer + Leica trackers

The TEC's "metrology streams" deserve their own enumeration. The
ZLP metrology stack is documented in Krebs's *Integrating
Metrology, Spatial Analyzer and Industry 4.0* deck
[@krebsMetrologyI42025]:

- **Spatial Analyzer Ultimate** (Hexagon/New River Kinematics)
  as the unified measurement-software environment.
- **2× Leica AT 901 LR** laser trackers — the long-range
  high-accuracy reference instruments for full-scale fuselage
  geometry.
- **T-Probe, T-MAC, T-SCAN 5** — the contact / hand-held /
  surface-scanning attachments that extend the tracker to
  different measurement geometries.

The day-to-day applications named in the deck are *geometric
dimensioning and tolerancing of finished parts or assemblies,
process-specific measurements (e.g. thermal expansion of moulds),
and surface-geometry reconstruction*. These are the **as-built
verification stream** that closes the Pareto loop with the
*as-designed* CAD model — the substrate of the DigECAT digital-twin
work named in the centre's competence portfolio.

The deck's *advanced application* — **in-situ elastic
calibration of robots (IREC)** — is a concrete research result
that ties Shepard, the metrology stack, and the AFP cell into a
single demonstrator. IREC replaces the AFP compaction roller with
a calibration apparatus carrying a Leica metrology marker; an
18-DOF lumped kinetostatic model running in real time on a
Linux-Xenomai PC corrects the KUKA KR 120 HA TCP via the RSI
interface every 4 ms. The published numbers are sharp: on the
double-curvature MFFD mould, with 280 N lateral / 1100 N normal
load cases simulating process forces, IREC delivered an
**89.30% reduction in mean TCP deviation** and a **70.19%
reduction in maximum deviation**, keeping the trajectory inside
the **±0.3 mm aerospace tolerance**. This is the kind of headline
metric the case-study chapter (`project_thesis_idea.md` §6) can
quote without qualification — it is a real published outcome,
not a synthetic-demo claim, and the data trail behind it lives
in Shepard.

The metrology stack is therefore not a peripheral instrument
family for Shepard; it is one of the centre's most data-rich
sources, and its integration shape (Spatial Analyzer's native
project format, the Leica tracker's binary stream, the IREC
real-time correction telemetry) is on the medium-term roadmap
for a dedicated payload kind. See
`aidocs/integrations/96-metrology-spatial-analyzer.md` for the
integration design.

## 4. Shepard named on the institutional roster

The competence portfolio is itself the smallest, most definitive piece
of evidence that Shepard is no longer a side experiment at ZLP. Its
§4 "Enabling Technologies: Digitalisation and the Integrated Factory"
opens with three named systems — and Shepard is the first
[@zlpKompetenzportfolio2026]:

> *Das Datenmanagementsystem "shepard": In Analogie zum "Hüten"
> (Herding) komplexer Datenmengen fungiert shepard als
> Open-Source-Plattform für heterogene Forschungsdaten. Es verknüpft
> Sensordaten mit Metadaten und stellt sicher, dass komplexe
> Experimente KI-gestützt ausgewertet werden können.*

The other two named systems in the same section are the **DigECAT
digital twin** project — "as-built" geometry matched against
simulation models to enable lifecycle business models such as
pay-per-flight-hour — and **inline quality assurance** via computer
vision and laser triangulation. Shepard, in this reading, is not the
digital twin and is not the inline QA; it is the **substrate underneath
both of them**. The portfolio framing — *connecting sensor data to
metadata so that complex experiments can be analysed with AI assistance*
— is precisely the framing Shepard's own vision document presents
([`aidocs/42-vision.md`](../42-vision.md)).

The naming is institutionally significant. The eLib record for Shepard
is sparse [@dlrZlpShepard], but the internal positioning document is
explicit. The thesis can cite both as primary sources: external
visibility lags internal commitment.

A second internal artefact, the 29-slide **DLR ZLP Augsburg —
Data Driven Intelligence** deck [@zlpDataDrivenIntelligence2025],
sharpens the institutional naming. Its §4 ("Digital strategy
building blocks") arranges the centre's digital ambitions in a
concentric stack — *Data management → Analytics → Standardisation
→ Exchange* — and puts data management at the centre, labelled
explicitly as the *"backbone of data-driven processes and culture"*.
Shepard is named on the next slide as the integrated data
management system, with the centre's gloss
*"s* torage for *he* terogeneous *p* roduct *a* nd *r* esearch
*d* ata"* and the goal phrase **active research data** picked out
in red — a deliberate distinction from passive archive systems.
The portfolio sentence and the Data-Driven-Intelligence stack
make the same institutional claim from two angles: Shepard is the
substrate, and the substrate is what makes the data-driven posture
possible at all.

## 5. The epistemology behind "data-driven" — the DIKW commitment

The Data-Driven-Intelligence deck spends an entire slide on what
"data-driven" actually means at ZLP [@zlpDataDrivenIntelligence2025],
and the answer is methodologically load-bearing for the thesis.
The deck reproduces the **DIKW pyramid** (Data → Information →
Knowledge → Wisdom → Decisions) and accompanies it with three
short claims:

1. *"Data-driven enables analytical justification of decisions."*
2. *"But data alone is not enough — context and meaning need to be
   captured as well."*
3. *"Data + context + meaning → enables powerful and targeted
   analytics."*

That triplet is precisely the design principle Shepard operationalises:
the substrate is not a data lake (data without context) and not an
ontology server (context without data), but the binding layer where
sensor traces, process metadata, and ontology-grounded meaning are
linked into the same graph and queried together. The thesis's
methodological chapter (`project_thesis_idea.md` §5) takes the DIKW
commitment as the philosophical premise that justifies Shepard's
architectural decisions: multi-substrate by *type* (DIKW levels
have different storage needs), graph-organised by *context*
(annotations attach to nodes), ontology-grounded by *meaning*
(SHACL templates + m4i + IOF as the semantic substrate).

The Industry-4.0-to-5.0 comparison table on the next slide
[@zlpDataDrivenIntelligence2025] reinforces this in a second
register: the move from "use of data and analytics to optimise
processes" (4.0) to "creation of sustainable, environmentally
friendly manufacturing processes" combined with "importance of
human interaction and collaboration" (5.0) is the institutional
expression of the same shift the thesis describes from a
*human-out-of-the-loop* RDM (data warehouse) to a *human-and-AI-
in-the-loop* substrate (collaboration-aware provenance, dataset
forging, f(ai)²r predicates). Shepard's design is on the 5.0 side
of that table; the deck records the institutional endorsement.

## 6. The data-capture interface — what the substrate has to talk to

The Data-Driven-Intelligence deck includes two architecture
diagrams that show *how the substrate connects to the shop floor*,
and they are the most precise statement of Shepard's integration
requirements that exists outside the codebase itself
[@zlpDataDrivenIntelligence2025]:

- **Slide 9 — the canonical Shepard architecture.** A two-tier
  diagram: a *Shopfloor* layer (multiple `Dev` boxes, OPC UA,
  Edge Collector) connects via HTTP to a *REST API* fronting a
  *Graph organisational database*, which in turn fans out via
  database-specific interfaces to five storage substrates (Time
  series, Git, Files, Structured Documents, "…"). A separate
  *Web Interface* connects to the same REST API and is annotated
  with *Custom Analytics*. The slide records four basic design
  considerations spelled out by the deck verbatim: highly modular
  design; web-based management UI; REST API for complex analytics;
  specific data store per data type — and explicitly *"no
  one-size-fits-all"*. The closing **key concept** sentence — *"each
  data element is related to a vertex in the graph-based
  organisational database"* — is the architectural premise Shepard's
  Neo4j-anchored design (`aidocs/data/00-model-inventory.md`)
  derives from.
- **Slide 10 — the (raw) data-capture interface view.** A
  protocol-explicit diagram: PLC Cell, KRC4 (Kuka robot
  controller), PLC MTLH, TPS Controller, and a sensor *Head* are
  connected via Cell ProfiNet (`PN_C`), AFPT ProfiNet (`PN_D`),
  AFPT EtherCAT (`EC_S`, `EC_M`), Safety, fast UDP, ADS, OPC UA,
  REST, and TCP/IP. A bottom *Data collection* bar bundles two
  Context Provider + OPC Router blocks (each with UA + REST), and
  the entire assembly funnels into a single purple Shepard
  cylinder. This is the **shop-floor reality** Shepard's plugin SPI
  has to keep speaking with: not a clean REST world but a polyglot
  industrial-protocol substrate where the substrate's value is
  precisely its ability to absorb the protocol diversity into one
  graph.
- **Slide 11 — data contextualization.** The third figure draws
  the *process-context* attachment: PLC Cell + PLC MTLH + KRC4
  feed timeseries (e.g. OPC UA) into a *Data Aggregator*; a
  separate *Context Provider* tracks which process step is active
  by observing the *leading component*; the Context Provider plus
  the TPS Controller (which produces event-oriented artefacts —
  images, point-clouds) all converge on the REST API. The slide's
  annotation summarises the design: *continuous data acquisition*
  (prevents out-of-context data loss); *contextualization through
  observation of the leading component*; *contextualized storage
  of event-oriented artefacts*; *analytics access via the same
  REST surface where results return* (Jupyter, Grafana). This is
  the architectural commitment that *context is not metadata an
  operator types in later; context is captured at ingestion by a
  named subsystem with a defined responsibility*.

Together the three slides form the **architecture-chapter figure
triplet** for the thesis (§3 of the outline in
`project_thesis_idea.md`): the high-level data-flow shape (slide
9), the shop-floor protocol reality (slide 10), and the
context-capture pattern (slide 11). The companion case-study
slide on the MFFD AFP demonstrator (slide 12) shows the same
architecture in operation — a live `shepard_cube2-IR Image`
heat-map rendered next to the AFP robot photo — and is a
candidate figure for the §6 evaluation chapter. The GTlab/ADAPT
slide (slide 14) records a second use-case beyond MFFD —
Shepard as the data-storage backend for model-based systems
engineering on a turbine pre-design platform — which the
discussion chapter can cite as evidence the substrate generalises
across domains.

## 7. Regional and policy context — the Bavarian dataspace agenda

ZLP Augsburg is one site in a wider Bavarian data-infrastructure
landscape, and a 2026 expert panel on data-spaces convened in Bavaria
[@bayernFachpanel2026] sketches the macro-environment in which Shepard
operates. The panel's SWOT analysis names the relevant forces:

- *Strengths* — strong industrial base (large industry + a strong
  Mittelstand); existing competence ecosystem (HTA universities,
  research institutes, talent); recognised understanding that
  digitalisation and AI are an opportunity.
- *Weaknesses* — no unified or simple onboarding to data spaces;
  data domains not interoperable end-to-end; data-competence gap in
  SMEs; slow administrative inertia.
- *Opportunities* — confidential computing and privacy-enhancing
  technologies; regulation as enabler (EU data strategy); geopolitical
  momentum for digital sovereignty.
- *Risks* — monopolistic dominance of US products; fragmentation
  (regional Sonderwege); empty promises and disillusionment.

The panel's six action fields — Lived Data Culture; Activate Expertise;
Access to Infrastructure; Sovereignty and Resilience; Regulation and
Standards; Bavaria Acting Globally — read almost as a requirements
specification for what an open-source, ontology-driven, federation-ready
research-data substrate has to be. Shepard's design choices —
European-jurisdiction object storage (Garage; ADR-0024), open standards
(SHACL, PROV-O, m4i, AAS), plugin-extensible adapters, no dependency on
US-cloud lock-in — track those action fields directly. The panel's
phrase *"Datenstecker — Plug-and-play-Zugang"* (data plug — plug-and-play
access) is, in effect, the design goal of Shepard's plugin SPI.

The thesis does not need to argue, in this chapter, that Shepard *is*
the answer the Bayern panel asks for. It only needs to record that the
strategic environment in which the substrate is being built is
explicitly demanding the shape Shepard already has, and that this
alignment is not coincidental — both responses derive from the same
2024–2026 European-sovereignty turn.

## 7.5 The federal-funding-cycle frame — HGF research-policy targets for PoF V

Above the Bavarian dataspace panel sits the larger institutional
frame that authorises ZLP's funding cycle: the **forschungspolitische
Ziele** (research-policy targets) of the Helmholtz Association for
the fifth Programme-Oriented Funding period **PoF V (2028–2034)**, as
addressed to HGF in the version-V cross-association document of
2025-09-19 [@hgfFoPoZi5_2025]. Three of the document's binding targets
align directly with what Shepard provides as substrate and demand the
shape it already has:

- **Datenmanagement as a binding cross-association target.** The
  Zuwendungsgeber (federal funding body) explicitly expects from
  the Helmholtz Association including its centres *"the definition
  of a joint and cross-cutting architecture concept for the data
  management infrastructure (data acquisition, data analysis /
  aggregation, data provision per FAIR principles, and IP
  management)"*, with the infrastructure required to *"ensure a
  scalable integration of data-management demonstrators also for
  partners from science, industry and society"*
  [@hgfFoPoZi5_2025]. This is, in administrative-language form, the
  same requirements profile that Shepard's substrate-split design
  (Neo4j entity graph + Postgres/Timescale + MongoDB + Garage) plus
  plugin SPI is built to satisfy. The mandate names FAIR explicitly
  and frames IP-aware operation as a co-equal concern — both load-
  bearing for Shepard's positioning between research and industry.
- **NFDI integration is required, not optional.** The same target
  obliges the Association to *"fundamentally include work and
  recommendations already produced by NFDI and to closely coordinate
  further development with NFDI's relevant bodies"*
  [@hgfFoPoZi5_2025]. The alignment work documented in
  [`aidocs/strategy/88-nfdi4ing-alignment.md`](88-nfdi4ing-alignment.md)
  is not a contribution-by-courtesy; it is responding to a binding
  funding-cycle obligation that PoF V will measure against.
- **Datenresilienz** as an explicit target — *"the safeguarding of
  data resilience to guarantee the availability, integrity and
  access to data even in the case of unforeseen events,
  disruptions or failures"* [@hgfFoPoZi5_2025]. Shepard's policy
  posture on this (no US-cloud lock-in, European-jurisdiction
  object storage via Garage per ADR-0024, an explicit exit-strategy
  obligation inherited from `dlrCloudStrategie2019`) is not a
  preference; it is the response to a PoF V target.

In the same document, two adjacent binding targets — *Nachhaltigkeit*
(the Association is required to develop a joint sustainability
strategy, including operational measures for climate-neutrality and
the sustainable operation of research infrastructures) and *Pakt für
Forschung und Innovation* (PFI) continuity through PoF V — frame the
constraints under which the data-management target has to be met.
The sustainability frame in particular reaches into Shepard's
operational substrate through the LLM-energy-cost reporting (see
[`aidocs/sustainability/00-energy-estimation-log.md`](../sustainability/00-energy-estimation-log.md))
and the European-jurisdiction storage choice (Garage in EU rather
than US-cloud blob storage).

For the thesis, the PoF V research-policy document is the
**funding-cycle-level evidence** that the substrate Shepard provides
is on-policy at the highest institutional layer above the centre:
the Bavarian dataspace panel (§7) sets the regional shape; HGF's
PoF V targets set the federal-Association-level shape; both
converge on the same requirements profile that Shepard's plugin-
first, FAIR-by-default, IP-aware, resilient-by-design architecture
already meets.

## 8. The strategic outlook: ZEUS and the move toward hydrogen-class structures

The portfolio's closing section names the next research front as
**ZEUS**: thermoplastic technologies adapted to the specific
requirements of hydrogen-powered aircraft, with the material
recyclability of thermoplastics as a circular-economy lever
[@zlpKompetenzportfolio2026]. The figure reported is small but
specific — 124 tonnes of CO₂ avoided to date, with the path toward
emission-free flight defined as the long-horizon ambition.

For the thesis, ZEUS is a signal rather than a chapter. It tells the
reader that ZLP's data-substrate problem is not bounded by the MFFD
demonstrator: the next generation of work will produce data streams
from new joining techniques, new material systems (hydrogen-compatible
composites), and new lifecycle questions (recyclability as a
first-class metric). A substrate designed only for MFFD-class CFRP
would be obsolete before the thesis is bound. A substrate designed
around an open ontology, an SPI, and an explicit policy of
admin-configurable extension is not. That is the architectural
defence Shepard's plugin-first stance ([`aidocs/platform/47`](../platform/47-dev-experience-and-plugin-system.md))
has to make against the ZEUS-horizon objection.

## 9. What this chapter establishes for the thesis

In the eventual thesis, this material answers four questions a reader
will arrive with:

1. **Why this institute?** Because ZLP Augsburg is the TRL 6 bridge
   between aerospace research and series production for the
   thermoplastic-CFRP paradigm shift, and that role generates the
   exact kind of heterogeneous, full-scale, multi-cell data stream
   that off-the-shelf RDM tooling cannot absorb.
2. **Why does this institute need a substrate at all?** Because its
   institutional self-image — *lossless coverage of the industrial
   process chain* — fails the moment the data trail that documents
   the chain becomes lossy.
3. **Is the substrate part of the centre's official story?** Yes — the
   competence portfolio names Shepard explicitly as one of three
   enabling technologies for the "integrated factory," alongside the
   DigECAT digital twin and the inline-QA computer-vision systems.
4. **Is the environment around the centre asking for this shape?**
   Yes, on two institutional layers. **Regionally**, the Bavarian
   Fachpanel on data spaces (§7) independently converges on
   requirements that Shepard's plugin-first, open-standards,
   European-sovereignty-aware architecture already meets.
   **Federally**, HGF's research-policy targets for PoF V (2028–2034)
   (§7.5) bind the Association including ZLP to a joint
   data-management architecture concept implementing FAIR principles,
   IP-aware operation, NFDI alignment and data resilience — which is
   the requirements profile Shepard's substrate split and plugin SPI
   are built to satisfy [@hgfFoPoZi5_2025].

The next thesis chapter (the architecture chapter — drafted in part at
[`aidocs/strategy/86`](86-shepard-predecessor-systems.md) §3 and in
[`aidocs/platform/47`](../platform/47-dev-experience-and-plugin-system.md))
takes those four answers as given and starts to design against them.

## Primary sources

The institutional positioning above is grounded in five primary
sources uploaded by the author to the AI working memory of this
project on 2026-05-23:

- @zlpKompetenzportfolio2026 — *Wissenschaftliches Kompetenzportfolio:
  DLR Zentrum für Leichtbauproduktionstechnologie (ZLP) Augsburg* (PDF,
  3 pp.). The portfolio's author is not identified on the document
  itself; PDF metadata records only the renderer ("Skia/PDF m147 Google
  Docs Renderer"). It is cited here as an institutional position
  statement, not as a personally-authored work.
- @zlpDataDrivenIntelligence2025 — *DLR ZLP Augsburg — Data Driven
  Intelligence* (PDF, 29 pages, generated 2025-01-27). Institutional
  presentation deck. The thesis-figure triplet of §6 (slides 9, 10,
  11) and the DIKW slide of §5 (slide 7) are drawn from this source.
  Slides 8 and 12--14 record the digital-strategy stack, the MFFD
  case-study figure, and the GTlab/ADAPT second use-case.
- @bayernFachpanel2026 — *Ergebnis Fachpanel: Aufbau und Nutzung von
  Datenräumen, 20.03.2026* (PPTX, 16 slides). Bavarian dataspace
  expert panel.
- @hgfFoPoZi5_2025 — *Forschungsbereichsübergreifende
  forschungspolitische Ziele für die PoF V (2028–2034)*
  (DOCX, cross-association research-policy target document
  addressed to HGF, dated 2025-09-19). Names *Datenmanagement* as
  a binding cross-association target with explicit FAIR + IP +
  NFDI-alignment + Datenresilienz obligations on the centres;
  the funding-cycle-level frame for §7.5. Sibling targets:
  *Nachhaltigkeit* (joint sustainability strategy + climate-neutral
  operation) and *Pakt für Forschung und Innovation* continuity
  through PoF V.
- @dlrZlpShepard — the external eLib / DLR project page for Shepard,
  retained for the contrast: external visibility lags internal
  positioning.

Citations resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib);
see [`/bibliography`](/bibliography) on the live site.
