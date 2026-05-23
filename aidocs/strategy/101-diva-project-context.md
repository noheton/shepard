---
stage: idea
last-stage-change: 2026-05-23
audience: [strategy, funding-reviewer, thesis-substrate, plugin-author]
---

# 101 — DIVA: Drone Integration via AAS (project context)

**Audience.** Funding reviewers (LuFo VII-2 Disruptive Technologien)
asking how DIVA positions Shepard inside a DLR-internal aviation
consortium; thesis readers (§10 future-work / outlook in
`aidocs/98-thesis-perspective.md`) tracing how Shepard's substrate
extends from CFRP-fuselage manufacturing into the operational-UAS
domain; plugin authors evaluating whether AAS-as-application-protocol
(not just AAS-as-data-store) is on the near-term roadmap.

**Status.** **idea**. Source is a one-page **initiale Projektideenskizze
(V01)** by Krebs (DLR BT / ZLP Augsburg), 2026-05 [@krebsDIVA2026]. TRL
target 1-2. The skizze is the earliest formal artefact in DIVA's
lifecycle; this doc captures the project shape and Shepard's named role
*at proposal-draft stage* so that any subsequent Shepard work touching
DIVA can be traced back to the originating framing.

This document is the **DLR-internal sibling** to
`aidocs/strategy/92-aerospace-x-regulatory-context.md`. Aerospace-X
covers the DLR-and-industry aerospace dataspace federation (BDLI /
Airbus / supply-chain). DIVA is the *purely DLR-internal* aviation
research consortium that — per the skizze's own framing — transfers
AAS technologies "aus der Lieferkette (Aerospace-X) direkt in den
operativen Flugbetrieb" [@krebsDIVA2026]. Both projects share the
AAS substrate; they differ in domain (manufacturing supply-chain vs.
operational airspace) and consortium shape (industry-led vs.
research-only).

This document complements:

- `aidocs/strategy/100-shepard-bt-zlp-rollout-plan.md` — the
  *manufacturing-cell* rollout that consumes the BT/ZLP capability
  inventory [@krebsInventur2025]. DIVA is **not** a wave of that
  rollout; DIVA is a **parallel application axis** (UAS / lower
  airspace) on the same Shepard substrate. See §4 below.
- `aidocs/integrations/52-aas-backend-integration.md` — the AAS
  payload-kind integration design that DIVA's AP 2 (Submodell-
  Architektur) consumes.
- `aidocs/98-thesis-perspective.md` §10 — DIVA's TRL 1-2 forward
  framing positions it as **outlook material** in the thesis, not
  shipped-evidence (§6 case study).

---

## 1. The load-bearing fact

The DIVA skizze names DLR-BT/ZLP's contribution to the consortium
explicitly:

> **BT / ZLP (Bauweisen und Strukturtechnologie):**
> AAS-Repository-Infrastruktur (**SHEPARD**).

[@krebsDIVA2026, §5]

This is a **first-class strategic citation** for Shepard: in a
five-institute DLR consortium spanning Flugsystemtechnik,
KI-Sicherheit, Datenwissenschaften, Instandhaltung-und-Modifikation,
and Bauweisen, Shepard is *the* named deliverable from BT/ZLP. The
project's success criterion — a working AAS repository serving four
work-packages — runs through Shepard.

This citation matters for thesis-substrate purposes: it is an
**institutional commitment** at proposal-draft level that Shepard
is the AAS-repository infrastructure of record for BT/ZLP's
participation in a TRL-1-2 LuFo-tracked project. Distinct from
internal MFFD use (single use case, single cell), this names
Shepard as cross-cell, cross-institute infrastructure in a
forward-looking aviation programme.

---

## 2. The DIVA shape (one-page distillation)

**Full title.** *DIVA — Drone Integration via AAS*. Reference: LuFo
VII-2, Disruptive Technologien, Aufgabe 3. Status: V01 initial idea
skizze. TRL target 1-2. Scope: **ausschließlich wissenschaftliche
Einrichtungen** (research-only consortium, no industrial partners).

### 2.1 Problem framing

Three barriers in current UAS operations [@krebsDIVA2026, §2]:

1. **Fragmentation** — UTM systems, operators, authorities in
   isolated digital silos.
2. **Missing identity** — no machine-readable, certification-relevant
   system identity per UAS.
3. **Proprietary standards** — herstellerspezifische protocols
   (e.g. MAVLink) preclude fleet-level interoperability.

### 2.2 The technical concept

Four technological layers form a resilient digital ecosystem
[@krebsDIVA2026, §3]:

| Layer | Content | Shepard's stake |
|---|---|---|
| **3.1 Digital visibility / e-conspicuity** | Total digital visibility beyond positional data: technical state + mission context | Substrate for time-series state + file/CAD context, federated across actors |
| **3.2 Proactive AAS (Typ 3)** | AAS as digital representation that *encapsulates* ML models + sensor fusion and exposes results over standard interfaces | AAS-repository host; AAS-payload-kind (see `aidocs/integrations/52`) |
| **3.3 Sovereign data spaces** | Gaia-X + EDC mechanisms for controlled, policy-based data/service exchange | Federation roadmap (see HMC WP-3 in `aidocs/strategy/90 §5`) |
| **3.4 PHM 4.0 + autonomous MRO** | Health monitoring; AAS autonomously negotiates maintenance/logistics with other airspace actors | Provenance-of-decisions substrate (PROV-O + f(ai)²r predicates) |

The conceptual innovation is to use AAS **not as a data store** but
as **an active, standardised application protocol** for decentralised
swarm coordination — moving AAS from supply-chain (Aerospace-X) into
operational flight [@krebsDIVA2026, §1].

### 2.3 Work-packages

Four APs [@krebsDIVA2026, §4]:

- **AP 1 — Protokolltheorie**: AAS suitability analysis for real-time
  communication between autonomous UAS.
- **AP 2 — Submodell-Architektur**: aviation-specific submodels
  (e.g. *DroneIdentity*, *AirspaceSlot*).
- **AP 3 — Labordemonstrator**: 3-5 UAS swarm simulation (ROS 2 /
  Gazebo) in a "Delivery by Drone" scenario.
- **AP 4 — Analyse & Prognose**: ML-based state prognosis and
  predictive maintenance from AAS data.

### 2.4 Consortium (research-only)

Five DLR institutes [@krebsDIVA2026, §5]:

- **FT (Flugsystemtechnik)** — system architecture, drone platform,
  UTM interface.
- **KI (KI-Sicherheit)** — certifiable AI for multi-agent
  coordination.
- **DW (Datenwissenschaften)** — AAS data modelling, semantic
  interoperability.
- **MO (Instandhaltung und Modifikation)** — digital product passport,
  predictive maintenance.
- **BT / ZLP (Bauweisen und Strukturtechnologie)** —
  AAS-Repository-Infrastruktur (**Shepard**).

**No external industrial partners.** This is the principal contrast
to Aerospace-X (cf. `aidocs/strategy/92` — 30+ companies/associations
under Airbus-Operations leadership). DIVA is the DLR-internal,
research-only sibling.

### 2.5 Expected outcome

A *Referenzarchitektur* for a dataspace-capable, MRO-integrated
airspace. All findings published as open access and contributed to
international standardisation (IDTA, EUROCAE) [@krebsDIVA2026, §6].

---

## 3. Why Shepard fits DIVA's BT/ZLP slot

Shepard is named as the AAS-repository for DIVA's BT/ZLP
contribution because the fork already has:

1. **AAS payload-kind in the SPI roadmap** — see
   `aidocs/integrations/52-aas-backend-integration.md`. AAS submodels
   become first-class payloads; AP 2's *DroneIdentity* /
   *AirspaceSlot* submodels are directly addressable through this
   surface.
2. **Plugin-first architecture** — five-institute consortium means
   five teams may bring their own AAS extensions, ML models, or
   provenance shapes. The plugin SPI (`aidocs/platform/47`) is the
   structural fit: each institute's AP becomes a plugin module
   without forking the core.
3. **Provenance-of-decisions substrate** — DIVA's AP 4 (ML-based
   state prognosis) and the proactive AAS (Typ 3) framing require
   *visible AI provenance*. The f(ai)²r vocabulary
   (`project_fair2r_integration.md`) + per-Activity PROV-O capture
   already implemented give DIVA the AI-Act-Art-50 distinguisher
   for free.
4. **Federation roadmap aligned** — DIVA's §3.3 calls for Gaia-X +
   EDC sovereign data spaces. Shepard's HMC WP-3 (cross-institute
   federation; `aidocs/strategy/90 §5`) is exactly this; DIVA gives
   it a concrete operational testbed beyond MFFD.
5. **TRL-1-2 fit** — DIVA's research-only TRL target matches
   Shepard's current shipped maturity (in-flight at ZLP, design-doc
   coverage across 250+ aidocs, audited via 30+ persona findings).

---

## 4. Answers to the explicit framing questions

The user (2026-05-23) asked three questions about DIVA's
relationship to the rollout plan and thesis structure.

### 4.1 Is DIVA the Wave 4-5 vehicle for the BT/ZLP rollout plan?

**No.** The rollout plan (`aidocs/strategy/100`) sequences Shepard
adoption across **manufacturing cells** (FPZ, TPZ, IQZ, MFZ,
Cutterzentrum…) — all CFRP / large-structure production. DIVA's
domain is **UAS / lower airspace operations** — a different
application domain entirely.

DIVA is best understood as a **parallel application axis on the
same substrate**:

```
                  Shepard substrate (one codebase, one deployment model)
                 /                                                  \
   Manufacturing axis                                  Operational-aviation axis
   (rollout plan, doc 100)                             (DIVA, this doc)
   - MFFD AFP layup (Wave 0)                          - AAS-Repository for DIVA
   - TPZ thermoplast (Wave 1)                         - AP 2 submodel storage
   - IQZ NDT (Wave 1)                                 - AP 3 swarm-sim payloads
   - … (Waves 2-4)                                    - AP 4 PHM/MRO timeseries
```

The two axes share Shepard but consume different parts of its
capability set. Rollout-plan items don't slot into DIVA APs and
DIVA APs don't fold into the rollout plan's waves. They are
**complementary axes**, not stacked phases.

If the rollout plan ever extends to *flight-test / UAS structural
testing* under BT, DIVA could supply the operational-data side of
that loop — but that's a possible future bridge, not a current
framing.

### 4.2 Does DIVA need its own thesis chapter or fit existing §6?

**Outlook material (§10), not case-study (§6).** The thesis
case-study chapter (`aidocs/98-thesis-perspective.md`) needs
**shipped evidence** — running code, deployed substrate, captured
provenance, ingested data. DIVA is **TRL 1-2**, **V01 skizze**, no
funding award yet. Promoting DIVA into §6 would oversell its
maturity and weaken the chapter.

The honest fit is **§10 / future-work / outlook**, alongside
HMC WP-3 federation and the Aerospace-X regulatory roadmap. The
thesis can credibly say:

> "Shepard's design is positioned for next-generation aviation
> consortia. In the DIVA Projektideenskizze [@krebsDIVA2026], DLR
> Institute BT names Shepard as its AAS-repository contribution
> across a five-institute consortium covering UAS swarm
> coordination, certifiable multi-agent AI, autonomous MRO, and
> sovereign data spaces — extending the substrate validated on
> manufacturing (Chapter 6) into operational aviation."

That's a single paragraph in §10, not a chapter. If DIVA gains
funding and enters AP execution, *that* would become §6-eligible
material in a follow-on thesis or a published case study.

### 4.3 What external partners does DIVA bring in?

**None.** The skizze states unambiguously: *Ausschließlich
wissenschaftliche Einrichtungen*. DIVA's consortium is five DLR
institutes (FT, KI, DW, MO, BT/ZLP). No industrial partners, no
external bodies named at V01 stage. The "external partners" lateral
column on slide 23 of the BT/ZLP inventory [@krebsInventur2025]
remains populated by **other projects** (MFFD industrial partners,
Aerospace-X 30+ industry consortium), not by DIVA.

This is intentional and informative:

- DIVA is the **DLR-internal incubation track** for AAS-as-protocol
  research.
- The presumed downstream path is: research outcomes flow into
  IDTA / EUROCAE standardisation [@krebsDIVA2026, §6], from which
  industrial actors pick them up — possibly through Aerospace-X
  channels, possibly through national UTM rollouts.

In thesis-positioning terms: DIVA is the **research substrate**;
Aerospace-X is the **industry consortium**. The two together
illustrate the academic-to-industry gradient that German
publicly-funded research is expected to bridge.

---

## 5. Discipline notes (per CLAUDE.md library-doc rule)

This doc deliberately **cross-references** the rollout plan
(`aidocs/strategy/100`) and the capability inventory entry
[@krebsInventur2025] **without re-deriving** their content. The
single fact this doc imports from the inventory — that BT/ZLP is
a defined entity with named cells and cross-cutting technologies
— is enough context to position DIVA. Cell taxonomy lives in
doc 100 §1; this doc cites it once and moves on.

The same discipline applies in the reverse direction: doc 100
should add a one-line forward reference to this doc near §3
(rollout sequence) or §6 (out-of-scope domains) noting that
DIVA is a sibling application-axis, not a wave. A single edit
to doc 100 will land in the same commit as this doc.

---

## 6. Open questions (parked for V02 of the skizze)

These are not blockers for the doc; they are the empty cells V01
intentionally leaves for the consortium to fill in V02:

- **Funding envelope and duration.** V01 doesn't state a value or
  a timeline. LuFo VII-2 calls typically run 24-36 months.
- **Industrial partner gateway.** "Research-only" at V01 is a TRL
  fit; a V02 could plausibly add Airbus Defence/Helicopters,
  HENSOLDT, or Airbus UpNext as observer-tier industrial
  stakeholders without breaking the "ausschließlich
  wissenschaftliche Einrichtungen" framing for the funded
  consortium itself.
- **AAS-as-protocol formal standard.** DIVA proposes IDTA
  standardisation; the existing IDTA submodel catalogue does not
  yet include UAS-operational submodels. *DroneIdentity* and
  *AirspaceSlot* would be net-new IDTA submodels — DIVA's AP 2
  output is therefore also an IDTA contribution path.
- **Connection to EASA Part-21 / Special Condition framework.**
  DIVA's certification angle (e-conspicuity, certifiable KI)
  intersects with EASA Special Condition for VTOL and Light-UAS;
  V01 doesn't position against any specific Part-21 path. A V02
  pre-meeting with EASA would clarify which submodels need
  certification-relevant fields.
- **Relationship to U-Space regulation.** EU Regulation 2021/664
  (the U-Space regulatory framework) is the binding constraint on
  any operational drone integration; DIVA's airspace-slot framing
  must align with U-Space service-provider obligations. V02
  should cite the U-Space regulation explicitly.

These are V02 review items, not V01 gaps.
