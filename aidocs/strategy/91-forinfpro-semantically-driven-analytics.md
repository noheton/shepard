---
title: "ForInfPro and the semantically-driven analytics use case"
subtitle: "(thesis chapter draft — architecture / ontology motivation)"
stage: feature-defined
last-stage-change: 2026-05-23
audience: [thesis, architecture, ontology]
---

# ForInfPro and the semantically-driven analytics use case

*Thesis chapter draft. This is the **architecture-and-ontology
motivation** chapter. It uses the ForInfPro composite-infusion case — a
live DLR ZLP use case [@krebsForInfPro2026] — as the concrete vehicle
through which the abstract claim "ontology-driven RDM matters" becomes
operationally specific. The chapter is sibling to
[`aidocs/strategy/87-dlr-zlp-positioning.md`](87-dlr-zlp-positioning.md)
(which explains why this institute needs a substrate at all) and to
[`aidocs/semantics/95-shacl-templates-and-individuals.md`](../semantics/95-shacl-templates-and-individuals.md)
and [`aidocs/semantics/98-shapes-views-and-process-model.md`](../semantics/98-shapes-views-and-process-model.md)
(which carry the implementing-side detail).*

## 1. The DIKW pyramid as the chapter's organising frame

The ForInfPro deck opens — slide 3 — on Rowley's wisdom hierarchy
[@rowleyDikw2007]: the canonical Data → Information → Knowledge →
Wisdom progression, in the variant extended by the US Department of
Defense to include explicit context-capture. The deck's framing claim
is that being "data-driven" enables the *analytical justification of
decisions*, but only if context is captured and formalised alongside
the raw data [@krebsForInfPro2026]. Data alone, in that framing, is
not enough.

This DIKW scaffold is doing real work for the chapter. It names the
problem (raw data without context cannot climb the pyramid), names the
solution shape (context as a first-class object), and names the
direction the substrate has to push the data through (upward, from
data toward decision). Shepard's architecture is then the *answer* to
the DIKW question: it materialises the context layer as a queryable
graph of typed objects (Collections, DataObjects, References,
Annotations, semantic individuals) sitting on top of, but not buried
in, the heterogeneous data stores that hold the actual sensor traces
and files.

## 2. The "Context Gap" — the data-jungle problem named precisely

Slide 4 of the deck names the underlying failure mode with a directness
worth quoting [@krebsForInfPro2026]:

- *Isolated data silos.* Data is stored but disconnected.
- *Implicit knowledge.* Context exists only in the expert's mind
  ("Sensor 5 was the pressure valve").
- *Decay of information.* Without documentation, data becomes useless
  after two years.

The deck's positive counterpoint — what a "knowledge network" looks
like — is equally direct:

- *Explicit context.* Metadata is baked into the data object.
- *Machine-interpretable.* The system understands that "Value X" is a
  "Temperature" in "°C."
- *Interoperability.* Seamless data exchange between different tools
  (Shepard, Grafana, Python).

The thesis can carry this binary verbatim. The "two years" figure is
not a measured number; it is a folk metric inside the institute. It
nonetheless captures the right phenomenon: an unannotated CFRP
infusion run, once the experimentalist leaves or rotates off the
project, becomes operationally unrecoverable. Shepard's annotation
substrate and its provenance graph are the structural reply to that
operational fact.

## 3. ForInfPro as the live use case

ForInfPro is the ongoing ZLP composite-infusion research project
that motivates the deck's architecture. The use-case description on
slide 6 names the elements [@krebsForInfPro2026]:

- *Central data management system* — Shepard.
- *Linkage of different data systems* — the infusion machine, the
  flow-front detection system, additional sensor systems.
- *Time-series data and semantics* — the substrate has to handle
  high-rate sensor traces *and* the semantic overlay that makes them
  interpretable.
- *Mid-term goal: enabling data-based process control.*

The mid-term-goal line is the most thesis-load-bearing of the four.
A research-data system that enables process control is no longer just
a substrate for after-the-fact analysis; it is a substrate that
participates in the live loop. The architectural implication is that
query latency, ingestion lag, and the cost of formulating a constraint
on the data are not academic concerns. They are functional requirements
of the process the substrate serves. Shepard's pgvector-on-Postgres +
TimescaleDB substrate split (ADR-0024 and successors) is shaped by
exactly that requirement.

## 4. The contextualisation diagram — slide 7 as architectural canon

Slide 7 of the deck contains a diagram the thesis can treat as
canonical [@krebsForInfPro2026]. It shows, in the deck's own
vocabulary, the binding between containers (Infusion / Pump / Sensor /
Image) and a *single experiment data object* representing an infusion
run, with timeseries-references and file-reference edges connecting the
two sides. The diagram is explicitly contrasted between *Raw data* on
the container side and *Contextualised data* on the data-object side,
with the word "context" labelled in the middle.

This is the substrate's master picture. Containers hold the dense
data; data objects bind context; references are the linking machinery.
The thesis architecture chapter can take this diagram as the starting
point of its formalisation — every later refinement (Predecessor /
Successor edges, Annotations as first-class objects, SHACL templates,
the `appId` migration, plugin payload kinds) is best understood as
adding more axes of structure to the picture without changing its
fundamental shape.

The deck flags two practical truths against this picture that the
thesis should not lose:

- *References can be created manually — tedious.* The deck records, as
  of January 2026, that the manual creation of references is a real
  friction point. This is the motivating problem for the **process
  wizard** ([`aidocs/platform/47`](../platform/47-dev-experience-and-plugin-system.md)
  plugin SPI) and for the importer plugin design captured in the
  `project_importer_plugin.md` memory seed.
- *Process wizard from ecosystem provides alternative (WIP).* The deck
  records that the wizard was work-in-progress in early 2026. The
  thesis can cite this both as the live work and as the deck-attested
  motivation behind ongoing development.

## 5. Annotation, units, and constraints — the substrate goes domain-aware

Slide 8 of the deck records two specific annotation scenarios
[@krebsForInfPro2026]:

- *A time series not yet referenced, as stored in a container.* The
  annotation applies to the whole time series, without start or end.
- *A single time series from a time series reference.* The annotation
  applies to an interval (or a single point) of a single time series.

The deck shows two example predicates against a temperature channel:
*has unit — degrees Celsius* and *complies with — max constraint*.
This is small but structurally important. The substrate is being
asked to carry not just labels but **typed, unit-aware, constraint-aware
metadata** at the channel level. That requirement is what motivates the
`metadata4ing` (m4i) integration captured in the deepening design at
[`aidocs/semantics/94`](../semantics/94-m4i-metadata4ing.md), and it
is the requirement against which the `m4i:realizesMethod` /
`obo:RO_0002233` predicate-canonicalisation finding (MEMORY entry,
2026-05-23) becomes a thesis-cited correctness bug rather than an
implementation detail.

## 6. The dry-spot reasoning chain — semantics as decision support

Slide 9 makes the chapter's most concrete claim
[@krebsForInfPro2026]. Given the question *"There's a dry spot, what
could be the reason?"* the deck reasons up the ontology:

> Dry spots → suboptimal viscosity → resin temperature → measurable
> physical quantity → min/max constraints.

The deck's *goal* statement is direct: *the system should be able to
reason: "A dry spot could be caused by the resin being too cold at
time X."* This is the substrate's claim made operational. It is also
the precise shape of analytics Shepard's semantic layer is being
designed to support — a chain of typed predicates connecting a
phenomenon class to a measurable class to a physical-quantity class
to a constraint, with annotation predicates carrying the units and
bounds.

For the thesis, the dry-spot chain is also the canonical *evaluation
target*. If the substrate cannot answer that question — given a
populated ForInfPro Collection with a dry-spot annotation, can the
graph traverse to the cold-resin moment? — then the architectural
claim has failed. The fact that the deck names this exact reasoning
target makes it auditable.

## 7. The constraint-driven visualisation pay-off

Slide 10 closes the analytics arc with a small but high-value claim:
constraints annotated on a channel can be *programmatically used in
visualisation*. The deck illustrates this with a temperature plot in
which the annotated min/max thresholds appear as overlay bands. The
thesis can extend this point as follows: once the units and constraints
on a channel are typed in the substrate, every downstream view
(in-Shepard chart, Jupyter notebook, Grafana dashboard, exported plot,
LLM-summarised paragraph) inherits them without re-statement. The
shape's annotation *is* the visualisation specification.

This connects directly to the M1-VIEWS-AS-SHAPES-WAVE design
([`aidocs/semantics/98`](../semantics/98-shapes-views-and-process-model.md))
in which SHACL shapes are not only constraints on data but renderable
view recipes, and to the Trace3D acceptance test
(`project_trace3d_view.md` in working memory) in which a 3-D path plus
a colour-mapped scalar channel is an instance of exactly the
unit-aware, constraint-aware visualisation contract the ForInfPro deck
foreshadows.

## 8. What this chapter establishes for the thesis

For the eventual thesis, the ForInfPro deck delivers four things the
chapter exploits without further argument:

1. **The DIKW framing is a citable rationale, not a personal opinion.**
   The deck cites the Rowley 2007 paper directly; the thesis inherits
   the citation chain. This avoids the "why context matters" passage
   being a rhetorical flourish.
2. **The Context Gap is a primary-source-attested institutional
   problem statement.** The thesis does not have to argue from
   abstract principles that researchers lose track of their data; the
   deck records the failure mode as already-named at the institute.
3. **The slide-7 container/data-object diagram is the substrate's
   master picture.** Every later refinement of Shepard's data model
   is a refinement of that picture and can be presented as such.
4. **The dry-spot reasoning chain is an auditable evaluation target.**
   Shepard's semantic layer either supports that traversal in a
   populated ForInfPro Collection, or it does not. The chapter sets
   up the evaluation chapter cleanly.

The next thesis chapter (the semantic-substrate chapter,
[`aidocs/semantics/95`](../semantics/95-shacl-templates-and-individuals.md)
+ [`aidocs/semantics/98`](../semantics/98-shapes-views-and-process-model.md)
+ [`aidocs/semantics/94`](../semantics/94-m4i-metadata4ing.md)) takes
these four pieces as its starting line.

## Primary source

@krebsForInfPro2026 — *Semantically-Driven Data Analytics* (PPTX, 11
slides; co-authors named on slide 1: Florian Krebs, Roland Glück,
Felix Lettowsky, Patrick Kaufmann, et al.; speaker-date placeholder
"Florian Krebs, BT, 21.2.2025" on the DIKW slide). Uploaded to the AI
working memory of this project on 2026-05-23. Cited DIKW reference is
@rowleyDikw2007 (the canonical wisdom-hierarchy paper).

Citations resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib).
