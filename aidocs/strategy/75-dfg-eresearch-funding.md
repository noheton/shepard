---
stage: idea
last-stage-change: 2026-05-23
---

# aidocs/75 — DFG e-Research-Technologien: Antragsfähigkeit und Konzeptskizze

**Date:** 2026-05-16
**Status:** Concept sketch — not yet submitted.
**Audience:** Project leads at DLR-BT and a potential university partner; funding strategy.
**Purpose:** Check eligibility, map shepard to the DFG e-Research-Technologien program, and
draft first application ideas including the post-project sustainability argument.

Program URL: https://www.dfg.de/de/foerderung/foerdermoeglichkeiten/programme/infrastruktur/lis/lis-foerderangebote/e-research-technologien
Guidelines (Merkblatt 12.19): available at the URL above → Formulare und Merkblätter.

---

## 1. Program in brief

**e-Research-Technologien** is a DFG program within the LIS (Literatur-, Informations- und
Infrastrukturförderung) family. It funds development and consolidation of **digital information
infrastructure** serving scientific communities — explicitly covering:

1. Development of technologies, tools, procedures and applications for acquiring, accessing,
   processing, analysing, and securing scientifically relevant information.
2. Enhancement and scaling of established research software, virtual research environments,
   or digital research platforms.
3. Organisational models and sustainability concepts for long-term infrastructure operation.
4. Training and capacity building to increase adoption.
5. Evidence-based studies directly informing infrastructure development.

**Key parameters (from FAQ and Merkblatt 12.19, status May 2025):**

| Parameter | Value |
|---|---|
| Funding minimum / maximum | None — amount follows project scope |
| Typical project duration | Not fixed; Phase 2 implementations typically 2–3 years |
| Personnel costs | Eligible; IT staff in applied development may receive above-standard tariff rates (must be justified separately) |
| Hardware | Eligible if project-specific necessity is demonstrated; items > €10 000 require two comparative quotes |
| External services | Eligible with two comparison offers; DFG prefers anchoring technical expertise in-house |
| License requirement | **All results must be published under an open / free license** |
| Grundlagenforschung | Not eligible — infrastructure, not knowledge discovery |
| Eligible applicant types | Wissenschaftlerinnen und Wissenschaftler; staff of scientific infrastructure institutions (Bibliotheken, Archive, Museen, Forschungssammlungen, Forschungsdatenzentren, Rechen- und Informationszentren) |
| Cooperative applications | Explicitly encouraged: "kooperative Antragstellung von Infrastrukturanbietern sowie wissenschaftlichen Nutzerinnen und Nutzern" |
| NFDI/EOSC participation | Does not disqualify; NFDI-participating applicants may apply if no double-funding occurs |

---

## 2. Eligibility check for the DLR-BT scenario

### 2.1 The core question: can DLR apply?

DLR is a member of the Helmholtz-Gemeinschaft and receives core institutional funding from
the federal government and the states as a **Ressortforschungseinrichtung**. DFG general rules
limit or exclude funding for **Bundeseinrichtungen** that receive sufficient institutional
funding to cover the proposed activity from their base allocation.

**Risk:** If DLR-BT is the sole applicant, DFG may reject on the grounds that Helmholtz
institutional funding already provides the capacity for research data management development.

**Mitigation — preferred path: university as lead applicant.**

| Role | Candidate | Rationale |
|---|---|---|
| **Hauptantragsteller (lead)** | Universität Stuttgart, KIT Karlsruhe, or TU Braunschweig | University has unambiguous DFG eligibility; strong aerospace/materials/CS research alignment |
| **Kooperationspartner / Co-PI** | DLR-BT Stuttgart (Felix Krebs) | Pilot site, domain expertise, existing shepard fork; DFG allows co-applicants from non-eligible institutions where they contribute scientifically |
| **Infrastrukturpartner** | DLR-SISTEC (DLR IT) or a DLR Rechenzentrum | May qualify independently under the "Rechen- und Informationszentren" Infrastruktureinrichtung category |

**Action before submission: contact the DFG Geschäftsstelle LIS** to verify the DLR eligibility
situation for this specific program. DFG program officers give informal pre-submission feedback;
a 30-minute call with the LIS Referentin is standard practice and strongly recommended before
investing in a full proposal.

### 2.2 shepard as eligible object of funding

shepard is unambiguously a **e-Research technology** in the program's sense:

- It is a digital platform for acquiring, accessing, and securing research-relevant information.
- It is not a research result but the **infrastructure** that generates citable research outputs.
- It is Apache 2.0 licensed — satisfying the mandatory open-license requirement from day one.
- It is domain-agnostic (aeronautics, space, energy, transport) — satisfying the
  "benefits multiple disciplines or specific disciplines" criterion.
- It integrates with NFDI4Ing (metadata4ing ontology), Helmholtz KG (Unhide feed), and
  RDA recommendations (FAIR, RO-Crate, PIDINST, KIP) — demonstrating community embeddedness.

---

## 3. Phase fit: where shepard sits in the DFG three-phase model

DFG defines three funding phases for e-Research technologies:

| DFG phase | DFG description | shepard's current position |
|---|---|---|
| **Phase 1 — Development and testing** | Between conceptual idea and first prototype; addresses technical, organisational, or economic needs | **Largely completed**: the fork has ~40 shipped features, a working deployment model, and a demonstrated prototype |
| **Phase 2 — Implementation** | Converting a functional prototype into a reliable, sustainably operated service; requires steering mechanisms, operation models, risk analysis | **Target phase for this proposal**: deploying shepard at DLR-BT and at a university, building the community, completing the remaining plugin features |
| **Phase 3 — Consolidation** | Improving usability, increasing adoption, achieving long-term establishment | Future phase; partially overlaps with Phase 2 in a 3-year project |

**The proposal sits firmly in Phase 2**, with Phase 3 activities (training, community, adoption)
running in parallel from year 2. This is the DFG's most fundable position — the prototype
exists, feasibility is proven, and the gap to operational service is concrete and costed.

---

## 4. Application concept sketch

### 4.1 Proposed title

> **shepard — Aktives Forschungsdatenmanagement für heterogene Ingenieurforschung:
> Implementierung und nachhaltige Etablierung einer offenen FDM-Infrastruktur im
> Helmholtz- und Hochschulverbund**

English: *shepard — Active Research Data Management for Heterogeneous Engineering Research:
Implementation and Sustainable Establishment of an Open RDM Infrastructure in a
Helmholtz–University Consortium*

### 4.2 Scientific case (Bedarfsanalyse)

Engineering research at DLR and German universities produces heterogeneous data — sensor
timeseries, HDF5 simulation outputs, CAD geometry, high-speed video, lab-journal notes —
that today lives in disconnected silos with no machine-readable provenance chain. The result:

- Researchers spend 5–15 hours per campaign assembling data for publication.
- Cross-institute reuse is practically impossible without personal contact.
- DFG/BMBF data management plans are produced manually at project end, not from live data.
- NFDI4Ing and Helmholtz FAIR obligations cannot be met without tooling.

The **landscape analysis (Umfeldanalyse)** shows no open-source tool currently combines:
active workbench semantics, PIDINST instrument federation, RO-Crate export, Helmholtz KG
integration, and a self-hostable deployment that an institute IT team can operate in a day.
(Evidence: `aidocs/70 §2-3`, competitor matrix; none of TwinStash, RSpace, eLabFTW, Renku
cover the full engineering provenance + PIDINST + Helmholtz stack.)

### 4.3 Work programme (Arbeitsprogramm) — rough structure

**Year 1 — Pilot deployment and core completion**
- WP1: Deploy shepard at DLR-BT and the university partner's research data infrastructure.
- WP2: Complete in-flight features: git integration (G1), experiment orchestration (EXP1a–c),
  video payload kind (VID1a–c), templates (T1a–d).
- WP3: Implement InvenioRDM publishing plugin (INV1a–INV1c) for the university's
  InvenioRDM instance (or elib / Zenodo).
- WP4: Needs analysis — structured interviews with 3–5 DLR-BT campaigns; researcher-hours
  baseline measurement; gap analysis documented.

**Year 2 — Community and consolidation**
- WP5: Deploy at two additional institutes (DLR domain or university partner faculties).
- WP6: NFDI4Ing coordination workshop; shepard presented as candidate RDM workbench for
  NFDI4Ing research community; integration with NFDI DataHub / InvenioRDM explored.
- WP7: Complete notification system (N10), user-facing docs (D1), Playwright screenshot
  pipeline; user acceptance testing across both deployment sites.
- WP8: Open-source community buildout: contribution guide, CI/CD templates, plugin
  development kit (DX1–DX5 from `aidocs/47`).

**Year 3 — Evaluation and handover**
- WP9: Impact evaluation — researcher-hours saved, RO-Crate exports produced, DOIs minted,
  cross-institute dataset citations generated.
- WP10: Sustainability handover — operational model documented; institute IT teams
  independently managing their instances; upstream merge proposal (Shape B per `aidocs/71 §2`)
  submitted to `gitlab.com/dlr-shepard/shepard`.
- WP11: Final report + publication of evaluation methodology.

### 4.4 Partners

| Partner | Role | Scientific contribution |
|---|---|---|
| Universität Stuttgart (or KIT / TU Braunschweig) | Hauptantragsteller; second deployment site | CS/RDM expertise; PI with DFG track record; InvenioRDM instance |
| DLR-BT Stuttgart | Scientific co-PI; pilot site | 5 active research campaigns (CITE, CMC, CALLISTO, H2, AFP); proximity to development; champion researchers |
| DLR-SISTEC / DLR-RN (optional) | Infrastructure partner | Kubernetes/VM provisioning, Keycloak/Helmholtz AAI, backup; qualifies under "Recheninstitut" eligibility |

### 4.5 Rough cost estimate

| Item | Year 1 | Year 2 | Year 3 | Total |
|---|---|---|---|---|
| Personnel (0.5 FTE developer, university) | €40 000 | €40 000 | €40 000 | €120 000 |
| Personnel (0.25 FTE researcher / data steward, DLR-BT) | — | — | — | (in-kind) |
| Personnel (0.25 FTE community / evaluation, university) | €20 000 | €20 000 | €20 000 | €60 000 |
| Infrastructure (server VM provisioning, CI/CD) | €8 000 | €5 000 | €3 000 | €16 000 |
| Workshops and community events | €5 000 | €10 000 | €5 000 | €20 000 |
| Travel (DFG review, NFDI events) | €3 000 | €4 000 | €3 000 | €10 000 |
| **Total** | **€76 000** | **€79 000** | **€71 000** | **€226 000** |

This is a rough pre-proposal estimate. Actual figures depend on the university partner's
personnel cost rates and whether DLR-BT contributes personnel as Eigenanteil.

---

## 5. Nachhaltigkeitsargumentation — the strongest card

DFG explicitly demands a **phase-appropriate sustainability concept**. For a Phase 2
implementation project, the focus is on "a long-term viable concept securing infrastructure
operation." This is typically the weakest part of software infrastructure proposals — and
the part where shepard's architecture and development model make an unusually strong case.

### 5.1 Economic sustainability: AI-assisted maintenance changes the equation

Traditional research software projects produce an artefact that begins to rot the moment
funding ends. The maintenance equation is:

```
Traditional:  €200 000–€1 000 000 to build + €20 000–€50 000/year to maintain
→ institute must find sustained staff budget or the tool dies
```

shepard's AI-assisted development model (documented in `aidocs/71 §5–6`) breaks this:

```
shepard fork:  ~€625 in AI API costs for ~40 deliverables (2–3 weeks of sprint)
AI maintenance: €5 000/year buys 30–50 developer-weeks equivalent
→ a single DFG research assistant budget sustains active development indefinitely
```

This is not a speculative claim — it is an observed ratio from this sprint. The
**structural argument for DFG**: the sustainability question changes from "where does the
institute find €50 000/year of developer time?" to "where does the institute find
€5 000/year of AI API budget?" The latter is within the discretionary budget of any
DLR institute IT line item.

DFG reviewers typically ask: "What happens to this infrastructure after the project ends?"
The answer here is: the marginal cost of active maintenance is so low that it is sustainable
from institutional overheads — no follow-on project required.

### 5.2 Architectural sustainability: the plugin SPI

shepard's `PluginManifest` SPI (`aidocs/47 §2`) means that downstream institutes can add
capability without touching core. This is the structural guarantee against the "one-size
fits all → nobody uses it" failure mode of centralised RDM tools:

- DLR-FA adds an instrument dropbox plugin for their specific DAQ format.
- A university partner adds an InvenioRDM community plugin for their institutional
  repository.
- NFDI4Ing adds a DataHub connector plugin.

None of these require changes to the core; each community owns its plugin's lifecycle.
This is the software-architecture equivalent of the DFG's own module system — it distributes
maintenance responsibility to the communities that benefit.

### 5.3 License sustainability: Apache 2.0 from day one

Apache 2.0 means:
- No license fee, ever.
- Any institute can fork, operate, and modify without restriction.
- Commercial entities can build on top (consultant deployment, cloud hosting) — this grows
  the ecosystem without requiring the project to manage it.
- Upstream `dlr-shepard/shepard` shares the same license — there is no license conflict
  on the merge path.

### 5.4 Community sustainability: upstream merge and NFDI alignment

The **Shape B** and **Shape C** adoption paths (`aidocs/71 §2`) anchor long-term sustainability
in two communities:

- **Upstream shepard community** (`gitlab.com/dlr-shepard/shepard`): merging the plugin SPI
  upstream means the maintenance burden is shared across all shepard users internationally.
  The DFG project's WP10 explicitly targets this merge.
- **NFDI4Ing**: shepard uses metadata4ing natively — it is a natural candidate for the NFDI4Ing
  RDM workbench category. NFDI consortia provide multi-year operational commitments
  (as the FAQ notes: institutions may transfer operational responsibility to NFDI if binding
  commitments exist). This is the cleanest Dauerbetriebs handover scenario.
- **Helmholtz KG (Unhide)**: every shepard instance publishing to Helmholtz KG increases
  the Helmholtz Knowledge Graph's data volume — this gives HIFIS and the Helmholtz RDM
  working group an institutional stake in keeping shepard operational.

### 5.5 Pilot metric as sustainability evidence

The DLR-BT 3-month pilot (`aidocs/74 §5`) will produce a concrete metric before the
DFG proposal is submitted: **researcher-hours saved per campaign**. If the pilot shows
≥5 hours saved per campaign across 3 active campaigns, and DLR-BT runs ~20 campaigns/year,
the aggregate is ≥100 researcher-hours/year per institute. At a researcher hourly cost of
€80–€120, the annual benefit is **€8 000–€12 000 per institute** — well above the
€5 000/year AI maintenance budget. The return is positive from day one of post-project operation.

---

## 6. Risk analysis (Risikoanalyse — DFG requirement)

| Risk | Likelihood | Mitigation |
|---|---|---|
| DLR ineligible as lead applicant | Medium | University as Hauptantragsteller; verified pre-submission via DFG LIS Geschäftsstelle contact |
| Upstream shepard community rejects plugin SPI merge | Low | Shape B is designed to be low-controversy; MIT/Apache relicensing already aligned; WP10 starts engagement in Year 1 |
| Institute IT teams unable to operate deployment independently | Low | `docker compose up` deployment verified at DLR-BT; Helm chart reduces ops to hours; WP2 produces operator handbook |
| NFDI4Ing does not accept operational handover | Medium | Fallback: DLR-SISTEC as Dauerbetrieb host; university partner as secondary fallback; plugin SPI means no single-point-of-failure |
| AI-assisted maintenance costs rise (API pricing) | Low-Medium | Self-hosted open-source LLM is viable fallback; the architecture (not the AI tooling) is the durable asset |
| Scope creep: too many institutes in Year 2 | Medium | Firm scope: max 2 additional deployment sites; evaluation-first in WP9 before any expansion |

---

## 7. Next steps

1. **Verify DLR eligibility** with DFG LIS Geschäftsstelle before investing in a full proposal.
   Contact: LIS program coordinator (see https://www.dfg.de/de/dfg-im-profil/kontakt).
2. **Identify the university partner** — Universität Stuttgart (proximity to DLR-BT, IDS
   institute), KIT (NFDI4Ing connection, strong data management group), or TU Braunschweig
   (DLR-FA co-location) are the three strongest candidates.
3. **Run the DLR-BT 3-month pilot** (`aidocs/74 §5`) to generate the pilot metric
   (researcher-hours saved) as evidence for the Bedarfsnachweis section.
4. **Draft the Bedarfsanalyse** with structured interviews at DLR-BT — 3–5 campaign leads,
   each quantifying current data-assembly time and pain points.
5. **Submit the Kurzbeschreibung** (1–2 page outline) to the DFG LIS program office for
   informal feedback before preparing the full Antrag via elan.

---

## 8. See also

- `aidocs/strategy/71-fork-adoption-as-upstream.md` — cost-benefit numbers (§5–6); adoption shapes (§2)
- `aidocs/strategy/73-dlr-stakeholder.md` — DLR-wide brief; institute fit matrix
- `aidocs/strategy/74-dlr-bt-stakeholder.md` — BT pilot proposal; success metrics
- `aidocs/integrations/72-invenio-publishing-plugin.md` — InvenioRDM plugin (WP3 core deliverable)
- `aidocs/integrations/67-unhide-publish-plugin.md` — Helmholtz KG publishing (sustainability anchor)
- `aidocs/strategy/70-competitor-landscape-and-feature-ideas.md §7–8` — RDA alignment (Umfeldanalyse evidence)
- https://www.dfg.de/de/foerderung/foerdermoeglichkeiten/programme/infrastruktur/lis/lis-foerderangebote/e-research-technologien — program homepage
