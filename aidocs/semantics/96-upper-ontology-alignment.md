# 96 — Upper-ontology alignment (BFO 2020 + IOF Core + IAO + PROV-O)

**Status.** Design — companion to `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 7.
**Snapshot date.** 2026-05-21.
**Audience.** Contributors writing domain ontologies for Shepard;
researchers building their own models; reviewers asking "is this
serious ontology infrastructure or a vanity project?"
**Status of evidence.** The choice of anchors below is grounded in
the DLR ontology audit at
`aidocs/agent-findings/dlr-ontology-catalog.md` (Quadrant ★ entries).
The advisor critique flagged BFO as "harder than the doc admits";
this doc takes that seriously — see §6 honest caveats.

**Originating items.** Part 7 of aidocs/95 proposes upper-level
alignment for Shepard's ontology layer. The DLR ontology audit
identified BFO 2020 + IOF Core as the well-positioned anchors (ISO
standard, NIST stewardship, OBO Foundry + IOF adoption). This
document specifies the alignment.

---

## 1. Why upper-level alignment matters

Without an upper layer, every domain ontology Shepard hosts is an
island: `mffd:BridgeWelding` and `pluto:MissionPhase` can both
exist as classes, but a SPARQL query asking "show me all processes
across all domains" returns nothing because each domain coined its
own term for "process".

With an upper layer, both classes ultimately extend a shared root
(`bfo:Process`), so cross-domain queries work, and Shepard's
domain ontologies interoperate with the **1,500+ OBO Foundry
ontologies** plus the **growing IOF (Industrial Ontologies
Foundry) family** plus everything else that aligns.

Concretely, alignment buys:

- **Cross-domain SPARQL** — "find every Activity that consumed a
  Material of type X" works across MFFD and PLUTO uniformly
- **FAIR Interoperable score** — alignment to W3C-style standards
  is a measurable axis in FAIR self-assessment
- **Funding-body legitimacy** — Clean Aviation JU, Horizon Europe,
  EOSC explicitly call out "alignment to BFO / OBO Foundry / IOF"
  as a signal that an infrastructure investment is serious
- **Future-proofing** — domain ontologies evolve; the upper layer
  is the stable contract that survives renames

What it does **not** buy:

- Free FAIR compliance (that's a separate axis)
- Automated reasoning (Shepard does not run OWL DL reasoners)
- Universal queryability without effort (the alignment must be
  curated and reviewed; bad alignments produce bad queries)

---

## 2. The chosen anchors

After the audit's adoption + DLR-relevance analysis, four anchors
land in the ★ Must-Have quadrant and become the basis of
`shepard-upper:`.

### 2.1 BFO 2020 — Basic Formal Ontology

- **Standard:** ISO/IEC 21838-2:2021
- **Repo:** <https://github.com/BFO-ontology/BFO-2020> — ~30 contributors
- **Adoption:** ~700 Scholar citations; foundation of OBO Foundry
  (biomedical) AND IOF (industrial) — the two communities Shepard
  touches
- **Role for Shepard:** the philosophical foundation. Every Shepard
  class transitively extends a BFO root.

BFO's core split:

- **`bfo:Continuant`** — things persisting through time
  - Material, Instrument, Collection, DataObject, Sample
- **`bfo:Occurrent`** — processes and events
  - TapeLayup, BridgeWelding, Investigation, Experiment, Activity

Shepard contributors do *not* need to internalise BFO's full
philosophical apparatus (Generically Dependent vs Specifically
Dependent Continuants, etc.). They need only to choose
"Continuant or Occurrent" when creating a new top-level class —
analogous to choosing "noun or verb" in everyday writing.

### 2.2 IOF Core — Industrial Ontologies Foundry

- **Repo:** <https://github.com/iofoundry> — ~15 contributors
- **Adoption:** Released 2023; ~10 dependent ontologies; institutional
  backing from NIST, Boeing, Autodesk, BAM
- **Role for Shepard:** the manufacturing-domain bridge.
  IOF Core extends BFO with industry-relevant classes:
  `iof:Material`, `iof:ManufacturingProcess`, `iof:Equipment`,
  `iof:Specification`, `iof:Activity` (specialising `bfo:Occurrent`)
- **Why for Shepard:** MFFD lives in this space natively;
  alignment to IOF Core means MFFD's process ontology is
  automatically queryable against Boeing's, NIST's, BAM's, etc.

### 2.3 IAO — Information Artifact Ontology

- **Repo:** OBO Foundry-curated
- **Adoption:** Standard OBO Foundry vocabulary; ~500 citations
- **Role for Shepard:** the information-artifact bridge.
  IAO has the classes that describe what RDM platforms actually
  store: `iao:DataItem`, `iao:DocumentPart`, `iao:Plan`,
  `iao:Settings`, `iao:Image`, `iao:DataSet`.
- **Why for Shepard:** `shepard:Collection`, `shepard:DataObject`,
  `shepard:FileBundle` are all information artefacts. IAO names
  them in BFO-aligned terms.

### 2.4 PROV-O — already in V49

- **Status:** W3C Recommendation 2013
- **Role for Shepard:** activity / provenance / lineage. Already
  shipped; alignment is just the explicit declaration
  `prov:Activity rdfs:subClassOf bfo:Occurrent`.

### 2.5 Optional bridges (domain-specific, not in upper)

These don't go in `shepard-upper:` but are seeded alongside as
domain bridges:

- **EMMO Core (small)** — European Materials Modelling Council;
  CC-BY-4.0; bridge to materials-physics vocab
- **CHAMEO** — Characterisation Methodology Ontology; under EMMO
  umbrella; ~15 citations to 2022 paper; relevant for MFFD defect
  characterisation
- **MSEO** — Materials Science and Engineering Ontology
  (Mat-O-Lab, BAM+Fraunhofer); active; bridges materials-domain
  through IOF Core
- **m4i (Metadata4Ing)** — NFDI4Ing; engineering process modelling

Per the audit's house rule: **seed EMMO Core + CHAMEO + MSEO,
NOT full EMMO** (~10k+ classes; bootstrap weight unwarranted).

---

## 3. The `shepard-upper:` namespace

The alignment layer itself is small — ~50 axioms, no new domain
content, only `rdfs:subClassOf` and `owl:equivalentClass`
statements bridging Shepard's core concepts to the anchors above.

### 3.1 Core Shepard ⊑ BFO mappings

```turtle
@prefix shepard: <http://semantics.dlr.de/shepard-upper#> .
@prefix bfo:     <http://purl.obolibrary.org/obo/BFO_> .
@prefix iao:     <http://purl.obolibrary.org/obo/IAO_> .
@prefix iof:     <https://www.industrialontologies.org/ontology/core/Core/> .
@prefix prov:    <http://www.w3.org/ns/prov#> .

# Shepard core entities are information artefacts (continuants)
shepard:Resource              rdfs:subClassOf  bfo:Continuant .
shepard:Collection            rdfs:subClassOf  iao:DataSet .
shepard:DataObject            rdfs:subClassOf  iao:DataItem .
shepard:Annotation            rdfs:subClassOf  iao:Annotation .
shepard:FileBundle            rdfs:subClassOf  iao:Document .
shepard:Container             rdfs:subClassOf  bfo:GenericallyDependentContinuant .
shepard:TimeseriesContainer   rdfs:subClassOf  shepard:Container .
shepard:StructuredDataContainer rdfs:subClassOf shepard:Container .

# Shepard activity entities are occurrents (PROV-O already aligned)
shepard:Activity              rdfs:subClassOf  prov:Activity .
prov:Activity                 rdfs:subClassOf  bfo:Occurrent .

# Shepard agents (per Part 4 of aidocs/95)
shepard:Agent                 rdfs:subClassOf  prov:Agent .
shepard:User                  rdfs:subClassOf  iof:Person .

# Shape and template (per Part 1 of aidocs/95)
shepard:Shape                 rdfs:subClassOf  iao:Plan .
shepard:Template              owl:equivalentClass  shepard:Shape .
```

That's the lot — 12 alignment lines that wire everything Shepard
already has into the BFO/IAO/IOF/PROV-O stack.

### 3.2 Domain-ontology authoring expectation

When a researcher (or institute) writes a domain ontology for
Shepard, they MUST extend a `shepard-upper:` root. Concretely:

```turtle
# WRONG — orphan class
mffd:BridgeWelding  a owl:Class ;
    rdfs:label "Bridge Welding" .

# RIGHT — extends shepard-upper
mffd:BridgeWelding  rdfs:subClassOf  shepard:Activity ;
    rdfs:label "Bridge Welding" .
```

Both `shepard:Activity ⊑ prov:Activity ⊑ bfo:Occurrent` and the
domain chain `mffd:BridgeWelding ⊑ mffd:ProcessStep ⊑ mffd:ManufacturingActivity`
arrive at the same root via different paths — which is fine; the
reasoner (or the curator) collapses them.

Domain shapes ship in TPL1; they validate at submission time that
the extending class points to a known `shepard-upper:` root.

---

## 4. The "opt-in cognition, mandatory alignment" UX principle

A researcher building a domain ontology in Shepard sees this in
the form:

```
Create new class:
  Name:         Bridge Welding
  Parent class: [▾ pick from ontology]
                  ◯ Material
                  ◯ Process
                  ◯ Equipment
                  ◯ Specification
                  ◯ Measurement
                  ◯ Information artefact
                  …
```

The labels are friendly. Behind the picker, "Process" maps to
`shepard:Activity ⊑ prov:Activity ⊑ bfo:Occurrent`. The researcher
never has to type `bfo:Process` or know what a `bfo:Occurrent` is.

Optional "Show advanced" reveals the full chain:

```
mffd:BridgeWelding  ⊑ shepard:Activity
                    ⊑ prov:Activity
                    ⊑ bfo:Occurrent
```

This is the line the funding-body slides will quote. The
researcher reading it for the first time can shrug; the funder
reading it sees alignment, end of conversation.

---

## 5. Worked example — `mffd:BridgeWelding` end-to-end

Continuing the MFFD process ontology from Part 7 of aidocs/95:

```turtle
@prefix shepard: <http://semantics.dlr.de/shepard-upper#> .
@prefix mffd:    <http://semantics.dlr.de/mffd-process#> .
@prefix bfo:     <http://purl.obolibrary.org/obo/BFO_> .
@prefix iof:     <https://www.industrialontologies.org/ontology/core/Core/> .
@prefix qudt:    <http://qudt.org/schema/qudt/> .

# Domain ontology layer
mffd:ManufacturingActivity  rdfs:subClassOf  iof:ManufacturingProcess .
                            #  ⊑ iof:Process ⊑ bfo:Process ⊑ bfo:Occurrent

mffd:ProcessStep            rdfs:subClassOf  mffd:ManufacturingActivity .

mffd:BridgeWelding          rdfs:subClassOf  mffd:ProcessStep ;
                            rdfs:label "Bridge Welding"@en ;
                            rdfs:label "Brückenschweißen"@de .

# Process parameters (subclass of iof property)
mffd:WeldCurrent            rdfs:subClassOf  iof:ProcessParameter ;
                            qudt:hasUnit qudt:Ampere .

mffd:WeldPressure           rdfs:subClassOf  iof:ProcessParameter ;
                            qudt:hasUnit qudt:Pascal .

# Instances (per Part 4 — named individuals)
mffd:campaign-2026-001-step-5  a mffd:BridgeWelding ;
    rdfs:label "MFFD Campaign 2026-001 — Bridge Welding step" ;
    prov:wasAssociatedWith mffd:operator-fritz-mueller ;
    prov:used mffd:material-cf-lmpaek-batch-21 ;
    prov:generated mffd:frame-3-welded-2026-05-21 ;
    mffd:hasWeldCurrent [ qudt:numericValue 1200 ; qudt:hasUnit qudt:Ampere ] .
```

This single triple-cluster:
- Is queryable as `bfo:Process` (returns it alongside every other
  occurrent in the graph)
- Is queryable as `iof:ManufacturingProcess` (returns it alongside
  every Boeing / NIST / BAM-modelled manufacturing process)
- Is queryable as `mffd:BridgeWelding` (the domain-specific name)
- Carries QUDT-typed numeric measurements (1200 A) that any QUDT
  consumer can convert to its preferred units
- Names the operator, material, and output frame as `prov:` typed
  relations — feeding the F(AI)²R audit trail (Part 15)
- Survives DataCite export (Part 11) because every IRI is stable
  and the classes have rdfs:label in multiple languages

---

## 6. Honest caveats

Per the advisor critique, these are the things the doc does not
hand-wave:

### 6.1 BFO alignment is non-trivial

The advisor flagged correctly: getting BFO alignment right takes
**ontologist review**. OBO Foundry has documented years of fixing
wrong alignments. A misclassification (e.g. modelling a `Process`
as a `Continuant`) propagates downstream and is expensive to fix.

Mitigation: **Part 7 of aidocs/95 requires an ontologist
reviewer before TPL3 implementation lands.** No code emits a
`shepard-upper:` axiom until at least one external reviewer
(ideally OBO/IOF community member, alternatively a DLR ontologist
or an NFDI4Ing terminology contributor) has signed off on §3.1.
If no such reviewer is available within the timeline, scope
TPL3 back to **PROV-O + IAO alignment only** (well-understood,
low-risk) and defer BFO + IOF Core to a later version.

### 6.2 EMMO is a contested bet

EMMO has been criticised in the materials community for the
"mereosemiotic" framework not being mainstream BFO. MaterialDigital
and NFDI-MatWerk represent alternative approaches. The audit
explicitly recommended seeding EMMO Core + CHAMEO + MSEO — *small
subset*, not the full ~10k+ class ontology. Stick to that.

### 6.3 The "opt-in cognition" UX claim is unverified

The friendly picker described in §4 sounds good but hasn't been
tested with real researchers. Pre-TPL1c usability study (even an
informal one with 3 ZLP / Lampoldshausen researchers) needs to
confirm "Process / Material / Equipment / …" labels feel natural
in their work context. If they don't, the labels change.

### 6.4 Alignment evolves

BFO 2020 → BFO 2025 (hypothetical future release) may break
existing axioms. The git-ingestion pattern (TPL5) handles this:
shape versions pin to commit SHAs; instances pinned to the
pre-break shape continue to validate; admins decide when to
migrate.

### 6.5 Reasoning is out of scope

Shepard does NOT run OWL DL reasoning. Alignments are declarative
metadata; consumers (SPARQL queries, external reasoners) can use
them. Shepard validates against SHACL shapes, not against OWL
class inferences. This is a feature (performance) not a bug.

---

## 7. Domain-ontology starter kit

A "template-of-templates" Shepard ships in TPL3 alongside the
upper-ontology bootstrap migration:

```
New domain ontology — what are you modelling?

   ◯ A process / activity / event           → starter: shepard:Activity hierarchy
   ◯ A material / substance                 → starter: iof:Material hierarchy
   ◯ An instrument / equipment              → starter: iof:Equipment hierarchy
   ◯ A measurement / characterisation       → starter: chameo:Measurement hierarchy
   ◯ An information artefact (doc / dataset)→ starter: iao:InformationContentEntity
   ◯ A process parameter / specification    → starter: iof:ProcessParameter hierarchy

   [Other / I'll write from scratch]
```

Picking a starter generates a skeleton TTL with:
- The right `shepard-upper:` parent chain
- A placeholder for the researcher's domain term
- An empty SHACL shape stub matching the chosen kind
- A `dcterms:contributor` line pre-filled with the user's ORCID
- Comments pointing at examples

The starter kit lowers the floor for non-ontologists. It produces
a syntactically valid, upper-aligned domain ontology with zero
ontology expertise on the researcher's part.

---

## 8. Implementation slices (TPL3 expansion)

| Slice | Scope | Days |
|---|---|---|
| **TPL3a** | V## bootstrap migration loading BFO 2020 + IOF Core + IAO + (optional) EMMO Core + CHAMEO + MSEO. Idempotent + fail-fast + V##_R__ rollback per CLAUDE.md migration rules. | 2 |
| **TPL3b** | `shepard-upper:` ontology — the ~50-axiom alignment layer. Shipped in the same migration. | 1 |
| **TPL3c** | Ontologist review of §3.1 mappings — gate, not work | 0 (external) |
| **TPL3d** | Domain-ontology starter kit UI (per §7) — 6-option picker, TTL skeleton generator, ORCID pre-fill | 3 |
| **TPL3e** | SHACL validation rule: every new domain class MUST `rdfs:subClassOf` a `shepard-upper:` root. Enforced at shape submission. | 1 |
| **TPL3f** | Worked example seeded in V##: `mffd:` process ontology per §5; demonstrates the chain. | 1 |
| **TPL3g** | Companion doc finalisation: this file (aidocs/96) plus pointer in aidocs/95 Part 7 | 0.5 |

**Total: ~8.5 days** for TPL3, **gated** on TPL3c (ontologist
review). If that gate fails, scope back to PROV-O + IAO alignment
only (~3 days, no external review needed).

---

## 9. Out of scope

- **OWL DL reasoning** at runtime (see §6.5)
- **Cross-platform ontology federation** (e.g. ESCO + Eurostat
  ontologies for human-resource and economic classification —
  legitimate but not aerospace-research relevant)
- **Linguistic alignment** beyond English + German labels (other
  languages added per use case, not pre-empted)
- **Automated alignment discovery** (tools like LogMap exist but
  produce noisy mappings; Shepard prefers curated)

---

## 10. References

- BFO 2020: <https://github.com/BFO-ontology/BFO-2020>; ISO/IEC 21838-2:2021
- IOF Core: <https://github.com/iofoundry>
- IAO: <https://github.com/information-artifact-ontology/IAO>
- PROV-O: <https://www.w3.org/TR/prov-o/>
- EMMO + CHAMEO + MSEO: <https://github.com/emmo-repo/EMMO>, <https://github.com/emmo-repo/domain-characterisation-methodology>, <https://github.com/Mat-O-Lab/MSEO>
- Companion doc: `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 7
- Audit grounding: `aidocs/agent-findings/dlr-ontology-catalog.md` § Quadrant ★

---

**Authorship.** Drafted 2026-05-21. Open questions in §6 require
external ontologist review before TPL3 implementation lands.
