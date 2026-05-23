---
stage: idea
last-stage-change: 2026-05-23
---

# 98 — Shepard as a thesis at a German university (perspective + viability)

**Status.** Personal-perspective doc — not a public claim.
**Snapshot date.** 2026-05-21.
**Author.** Florian Krebs (this fork's primary author; DLR ZLP Augsburg).
**ORCID.** [0000-0001-6033-801X](https://orcid.org/0000-0001-6033-801X).
This identifier is the citation anchor across every artefact discussed
below (Obscurity-Is-Dead paper, F(AI)²R reference repo, REBAR
infrastructure, this fork, MFFD showcase). The ORCID provides the
stable academic-identity link a thesis examiner can resolve to verify
the cumulative body of work — itself a piece of evidence that the
underlying methodology takes attribution seriously.
**Audience.** Florian + a hypothetical supervisor; later possibly the university's
admissions / supervision-matching office.

**Originating prompt.** User 2026-05-21: *"gauge chances to have the shepard
project as my thesis at a German uni including the 6.0 fork ai-assisted. write
a doc on that"*

This doc is a clear-eyed look at whether the work in this repo, including the
6.0 fork's substantial design + the AI-assisted methodology, can defensibly
sustain a German university thesis (Diplom-equivalent / Master's / Dr.-Ing. /
Dr. rer. nat.). It is **not** a thesis proposal yet; it identifies the shape
the proposal would have to take.

---

## 1. What's actually in this repo (the thesis-grade evidence)

Quantitatively, as of 2026-05-21:

| Artefact | Volume | Where |
|---|---|---|
| Design docs | ~5,500 lines across aidocs/40, 95, 96, 97, 41 | `aidocs/` |
| Agent findings (5 audits) | ~15,000 words | `aidocs/agent-findings/` |
| SHACL shape catalogue v1 | 1,596 TTL lines, 1,366 triples | `backend/src/main/resources/shapes/` |
| MFFD process ontology + analysis | 1 TTL + 1 Python + working report | `examples/mffd-showcase/` |
| Backend code (this session) | ~600 lines (MCP type-safety + auth refactor + tests) | `backend/src/main/java/de/dlr/shepard/...` |
| Production deployment | live at shepard.nuclide.systems | — |
| Plugin-first SPI design | covering ai, wiki-writer, mcp, video, importer | `aidocs/platform/47` + plugins/ |
| Adjacent published work | f(ai)²r + Obscurity-Is-Dead | github.com/noheton/ |
| **Prior author publications** | DLRK 2021 (Shepard architecture, Krebs lead) + ICRA 2024 (T-AFP calibration, Krebs co-author) + DLR magazin #140 (MFZ public exposition, Krebs co-author) | bib: `krebsDlrk2021`, `audetIcra2024`, `krebsJungDlrMagazin140` |

### 1.5 Prior author publications — the citation chain to the thesis itself

Three primary publications by the author anchor the thesis's
literature review back to its own subject:

- **[@krebsDlrk2021]** — *Systematische Erfassung, Verwaltung und
  Nutzung von Daten aus Experimenten*, DLRK 2021 Bremen. **The
  load-bearing peer-reviewed publication of record for Shepard's
  architecture.** Krebs is lead author, joint with all four named
  Zenodo software-citation authors (Haase, Glück, Kaufmann,
  Willmeroth on [@haaseShepard2021]). The paper specifies the
  polyglot-persistence stack (Neo4j + InfluxDB + MongoDB), the
  Collection / DataObject / Container / Reference data model
  (citing the RDA Research Data Collections WG recommendation),
  the JWT/OAuth2/API-key authentication model, the OPC-Router +
  Data-Reference-Generator (DRG) shop-floor ingest stack, and the
  thermoplastic AFP use case at the ZLP Fiber Placement Cell (FPZ).
  The §5 outlook anticipates git integration, CAD/PDM integration,
  search/filter expansion, and access-rights management — **all of
  which this fork has since shipped or designed**. This is the
  inheritance line: the thesis builds on a peer-reviewed paper the
  thesis author wrote five years earlier, and can quote the
  forward-looking §5 against what the fork has subsequently
  delivered. The DLR-side citation chain from precursor → thesis
  is therefore *closed within the author's own publication record*.

- **[@audetIcra2024]** — *Iterative Robot Calibration with
  Accuracy Saturation Termination and Application for AFP
  Processing of a Thermoplastic Aerostructure with Real-time
  Elastic Path Compensation*, ICRA 2024 (Audet, Fortin, Côté,
  Vistein, Brandt, **Krebs**, Monsarrat). Krebs is seventh-named
  co-author on the DLR side, with NRC Canada lead. Evidence of the
  Fiber Placement Cell's continuing peer-reviewed scientific
  output stream; the cell in the ICRA paper is explicitly the same
  KUKA KR 120 HA + AFPT MTLH + Laserline LDM laser configuration
  the DLRK 2021 paper described as Shepard's demonstrator. The
  thesis can cite this as evidence that the cell described in the
  Shepard architecture paper continues to produce peer-reviewed
  scientific results, validating the Shepard-as-substrate claim
  empirically rather than as a design intention.

- **[@krebsJungDlrMagazin140]** — *Hightech auf vier Säulen —
  Glanzstück im DLR Augsburg: multifunktionale Roboteranlage für
  die Fertigung von Flugzeugbauteilen*, DLR magazin #140 (Roboter
  themed issue, 2013). Krebs + Jung. Public-facing institutional
  exposition of the MFZ (Multifunktionale Roboterzelle), the cell
  the Krebs 2010 MFZ-Steuerungskonzept deck
  ([@krebsMfzSteuerung2010]) specified. Bookend to the 2010
  design-thinking deck: same author, same cell, three years later,
  operational hardware. Useful as the **citable public-domain
  source** for cell-level descriptions when thesis chapters need
  to reach beyond internal DLR documents.

**The citation chain from precursor to thesis is now intact at
three depths:**

1. *Pre-design* — Krebs 2010 MFZ-Steuerungskonzept
   ([@krebsMfzSteuerung2010]) → Krebs+Jung 2013 DLR magazin
   ([@krebsJungDlrMagazin140]): same author, design-intent to
   operational-hardware, three-year span.
2. *Architecture* — Krebs 2020 CUBE iDMS final presentation
   ([@krebsIdms2020]) → Krebs et al. 2021 DLRK
   ([@krebsDlrk2021]): same architect (lead author), iDMS
   prototype to peer-reviewed Shepard publication, one-year span.
3. *Operational* — Krebs et al. 2021 DLRK
   ([@krebsDlrk2021]) → this thesis (2026→): same author, paper to
   thesis, five-year span. The §5 outlook of the 2021 paper enumerates
   what the thesis demonstrates has since been delivered.

This is a **publication record of one author writing
continuously about the same evolving system across 16 years**.
The thesis is not citing a system; it is the next instalment in
an established author-track.

This is **dissertation-volume work** by any standard German university yardstick:
- Master's thesis target: typically 60-120 pages over 6 months. Already exceeded.
- Doctoral dissertation (Dr.-Ing. / Dr. rer. nat.): typically 100-250 pages over
  3-5 years. The volume here would be 3-4 years of focused work compressed
  by AI-assisted methodology into a much shorter calendar window — which is
  itself the methodological contribution.

The **AI-assisted methodology is the meta-contribution.** F(AI)²R has the
verification ladder; this fork validates it operationally; the audit trail of
"who or what produced which artefact" is present and queryable.

---

## 2. The four most-likely thesis framings (defensible)

### A. *"Ontology-driven research data management for regulated AI in aerospace manufacturing"* (Dr.-Ing.)

**Pitch.** Shepard's ontology-first architecture closes three converging
regulatory mandates (EASA Concept Paper Issue 02, EU Machinery Regulation
2023/1230, EU AI Act Article 50) for one demonstrator (MFFD). The thesis
documents the architecture, validates it on the MFFD bridge-welding showcase,
and contributes the F(AI)²R adoption pattern + the ontology-as-UI pattern as
generalisable infrastructure.

**Discipline fit.** Engineering / aerospace systems / digital manufacturing.
Strong overlap with DLR-internal research programmes; possibly co-supervised
between DLR ZLP and a uni with a digital-thread / Industrie-4.0 chair.

**Likely supervisors / institutes.** TU Munich (MIRMI / Industrie 4.0),
RWTH Aachen (NFDI4Ing leadership, m4i), TU Hamburg-Harburg (CPACS legacy
+ MBSE), University of Augsburg (geographic match, possibly the
strongest candidate via DLR ZLP affiliation).

**Risk.** Aerospace-regulation theses can drown in compliance detail.
Frame: "the ontology mechanism is the contribution, regulation is the
test environment."

### B. *"AI-assisted research methodology — exportable transcripts, verification ladders, and the F(AI)²R pattern"* (Dr. rer. nat. or Dr. phil.)

**Pitch.** The methodology behind this work IS the thesis. F(AI)²R is the
contribution; Shepard + Obscurity-Is-Dead are the empirical evidence. The
thesis documents how a single researcher with LLM collaboration produces
dissertation-volume engineering work in compressed calendar time; what
disciplines this methodology requires; what fails; and what the verification
contract has to look like.

**Discipline fit.** Computer science / digital humanities / philosophy of
science. Sits between STS (Science and Technology Studies) and methodology
research. Could land at HU Berlin, FU Berlin, Universität Hamburg, Bayreuth.

**Likely supervisors.** Anyone working on AI-in-science empirically.
RDA (Research Data Alliance) FAIR4ML / FAIR4RS contributors. Helmholtz
Metadata Collaboration's "AI-augmented research" line.

**Risk.** This is the *most original* framing but the least
institutionally-conventional. Some chairs will love it; some won't.

### C. *"SHACL shape-driven UIs for research data management"* (Dr.-Ing.)

**Pitch.** Narrower than A: the technical contribution is "ontology declares
form / view / facet / validation / agent-contract, platform renders all from
one source-of-truth." The thesis catalogs the widget map, demonstrates against
MFFD, and benchmarks ontology-driven UI cost vs hand-coded UI cost at multiple
scales.

**Discipline fit.** Computer science / software engineering. Closest to
existing university chairs (RWTH Aachen i5, TUM CS, Stuttgart VIS).

**Risk.** Less original — SHACL forms have been worked on for years (HfT
Stuttgart's shacl-form, etc.). To stand out, the contribution would need to
be (a) the multi-renderer aspect (forms + views + MCP + export), (b) the
scale measurements, (c) the worked-example density (MFFD as the demonstrator).

### D. *"Cross-institute research-data infrastructure: the DLR Shepard ecosystem"* (Dr.-Ing.)

**Pitch.** Documents an entire DLR-internal ecosystem (8-institute REBAR
+ multi-institute MFFD + per-institute Shepard deployments) as a working
case study of how cross-institute RDM can scale. The thesis would consist
of the empirical study + the architectural lessons + the proposed
generalisations.

**Discipline fit.** Information science / aerospace engineering. Likely
RWTH or TUM via NFDI4Ing.

**Risk.** Most dependent on DLR willingness to be public about internal
ecosystem details. Could become a co-authored institutional paper instead.

---

## 3. The AI-assisted methodology question

> *"can a thesis that was AI-assisted to this degree pass German academic
> rigor?"*

Honest assessment, by axis:

| Axis | State as of 2026-05 |
|---|---|
| **Authorship attribution** | F(AI)²R-style PROV-O audit trail is exactly what German promotion regulations are starting to require. DFG's *"Leitlinien zur Sicherung guter wissenschaftlicher Praxis"* (2019) already mandates traceability; Hochschulrektorenkonferenz (HRK) 2024 position paper on AI in science specifically calls for "exportable transcripts, prompt records, decision logs". This is exactly what F(AI)²R formalises. |
| **Originality (Eigenleistung)** | The bar is "you understand what was produced and could defend it". The volume of design decisions (constraint sets, plugin folding, capability ontology) demonstrates this. The Obscurity-Is-Dead paper is a published reference for the methodology itself — citable evidence of methodological awareness. |
| **Examiner comfort** | Variable. Some chairs will be fascinated; some will reject categorically. The right move is to **disclose openly upfront** and pick a supervisor who is curious rather than gate-keeping. Pre-conversations matter. |
| **Reproducibility** | The repo IS the thesis. Build pipelines, transcripts, decision logs, working demonstrators all under git. Reviewers can re-run, re-check, re-question. This is *better* than most theses. |

**Practical recommendation.** Lead with F(AI)²R as the disclosure framework
in the proposal. Make the AI-assisted methodology the explicit subject of
§1, not a footnote at the end. Pick a supervisor who has co-authored AI-RDM
papers (NFDI4Ing, HMC, RDA membership signals openness).

---

## 4. The "what's missing for thesis-grade" gap analysis

| Required for thesis | State today | Effort to close |
|---|---|---|
| Coherent narrative arc | aidocs/95 + 96 + 97 + 41 collectively cover it; needs a stitching introduction (§1) and conclusion (§N) | ~3 weeks of focused writing |
| Literature review | Implicit in agent findings; needs to be formal-ised | ~4 weeks |
| Empirical evaluation | MFFD showcase exists, runs, generates results; needs scale-up to ~5 production runs + measurement | ~2 months (gated on MFFD data drops) |
| Comparison to state of the art | Already drafted in audit catalogues; needs synthesis | ~2 weeks |
| Defence-ready presentation | Slide decks + the f(ai)²r conference deck = scaffolding exists | ~2 weeks |
| Published peer-reviewed paper(s) | Obscurity-Is-Dead exists in preprint form; one more peer-reviewed venue would strengthen | ~6 months pipeline (conference review cycle) |
| Supervisor + university affiliation | NOT YET ARRANGED | The critical gap |

**Calendar estimate from today to defensible Dr.-Ing. submission:** 18-24
months full-time, or 3-4 years part-time alongside DLR work. The volume
of work to convert what exists into a defensible thesis is **substantially
less** than starting from scratch.

### 4.1. Positioning vs the community-standard RDM handbook

The published German engineering-research RDM community-standard
is now **Schlenz, Bronger, Selzer, Nestler, Riem and Enahoro
(2026), _Research Data Management: A Practical Introduction_,
Zenodo 18468308, V1.1, CC BY-SA 4.0** (ref `schlenzRdmHandbook2026`
in [`docs/_data/references.bib`](../docs/_data/references.bib)).
NFDI4Ing-CADEN-funded, CoRDI-2025-launched, 100 pp,
DFG-codes-cited. The author team maintains JuliaBase + SciMesh
(Bronger, FZJ) and Kadi4Mat (Selzer / Nestler / Riem, KIT) — the
three free ELNs the handbook profiles. **A thesis examiner from
RWTH Aachen, KIT, Karlsruhe, FZJ, the Helmholtz Association or
any NFDI4Ing-affiliated chair will probably already own this
handbook** and will read Shepard against it.

Distilled from the actual handbook (full PDF read 2026-05-23),
the seven claims that connect to Shepard's design choices:

| # | Handbook claim | Shepard position | Where it lands in the thesis |
|---|---|---|---|
| 1 | **The DMP is the project's load-bearing planning document** — six sub-headings (§ 4.1 -- 4.6) align to DFG funder expectations. Most researchers prefer "a simple text file that can be modified gradually" over heavyweight tools like RDMO. | Shepard's Collection-level attribute schema can project into the same six headings; `shepard-plugin-dmp` is the (queued) productisation. The thesis frames Shepard as the **substrate-aware DMP companion** — the DMP describes intent, Shepard captures the execution against it. | Framing A § "Compliance integration"; Framing C § "DMP-as-shape" (SHACL shape over Collection attributes). |
| 2 | **FAIR = Findability, Accessibility, Interoperability, Reusability** (Wilkinson 2016 letters), but the handbook's § 2.2.3 subsection-heading slips to "Processability". This is editorial inconsistency, not framework divergence. | Shepard's FAIR scoring uses the canonical four letters; documenting the handbook's slip is itself a small *clarification contribution* the thesis can cite. | Thesis Lit-review § FAIR; spend one paragraph naming the slip + the canonical reading. |
| 3 | **Data quality** (§ 6) is systematic / statistical errors + outlier detection. CADEN is building **AI-supported intrinsic data analysis with automatic outlier detection** in ELN, not yet released. | Shepard's `shepard-plugin-ai` quality-flagging is the directly-overlapping work (see `project_ai_data_arranger.md`). The honest thesis position is **alignment + handoff**: integrate Bronger's CADEN AI output upstream when it ships rather than duplicate it. | Framing A § "AI quality plugin"; explicitly cite the handbook's § 6 forward-reference to subsequent edition. |
| 4 | **SciMesh** (§ 7.1 -- 7.4) is the in-NFDI4Ing standard for ELN-to-ELN data exchange: HTTP GET on sample URIs returns RDF; opaque mass data carries multihash in URL fragment (`<base>base<version><multihash>`). Sample stays homed at its issuing ELN; collaborators federate via cross-instance GETs. | Shepard's Garage CAS substrate (multihash IS the content-address) + per-DataObject n10s repository + HMC WP-3 federation prototype implement the same shape. **`shepard-plugin-scimesh`** (REST adapter, HTTP GET on `appId` → SciMesh RDF) is a small surface-area / large-alignment-payoff plugin candidate — the thesis writes it up as the empirical bridge to the consortium. | Framing C or D § "Federation implementation"; one chapter of the thesis. |
| 5 | **MetaData4Ing is positioned by the handbook as "more for IT experts / application developers"** (§ 7.5) while SciMesh sits at the researcher-facing layer. The two are presented as alternatives, not a stack. | Shepard's m4i alignment (`aidocs/semantics/94`) targets the application/JSON-LD-render layer; speaking SciMesh at the researcher-facing layer is additive. **The handbook implicitly leaves room for "a platform that does both"** — that platform is Shepard. | Framing A § "Ontology stack" — Shepard occupies a slot the handbook describes but does not fill. |
| 6 | **ELNs profiled by the handbook are evaluated along five implicit axes**: open-source license, customisability to existing workflows, sample/experiment/process model fit, REST-API completeness, federation-readiness via SciMesh. Demo accounts are public (JuliaBase at `demo.juliabase.org`, password `12345`). | Shepard is **not** an ELN — but the thesis examiner will apply these five axes anyway. The honest scorecard: open-source ✓ (Apache 2.0), customisability mid (plugin-first SPI but heavier than a JuliaBase Python process-class), process-model fit very high (Predecessor/Successor + PROV-O), REST completeness high (v2 surface), federation-readiness designed-not-shipped. **The 10-minute demo gate** (per `feedback_three_audience_docs.md`) is the equivalent of the handbook's `demo.juliabase.org` — and Shepard already has it at `shepard.nuclide.systems`. | Framing C § "Related work — ELN comparison"; one table. |
| 7 | **Publication walk-through** (§ 8.2) uses Zenodo (the same path this handbook itself takes). § 8.4 frames published research data as **AI training material** — "garbage in, garbage out" is the handbook's preferred slogan. Permanent storage (§ 9) names Coscine (KIT/RWTH) as the recommended administrative-storage layer. | Shepard's Unhide feed + RO-Crate export + KIP-DOI mint are the equivalent of the handbook's "data to Zenodo" walk-through; the `shepard-plugin-publisher` design (per `project_competitive_position.md` gap analysis) is a direct alignment slice. Shepard above Coscine (modelling-on-storage) is the integration shape; no work scheduled but the option is open. | Framing A § "FAIR publication path"; Framing D § "Coscine-as-substrate stack". |

The honest thesis-positioning sentence: *"This handbook is the
frame Shepard implements, not the frame Shepard departs from.
Where the handbook leaves a slot un-filled (industrial-scale
process data + multi-substrate + large-timeseries), Shepard
already occupies it; where the handbook names a slot
(JuliaBase / eLabFTW / Kadi4Mat at the ELN layer), Shepard
peer-federates via SciMesh rather than competes."*

The handbook's CoRDI 2025 launch venue is the obvious target
for the thesis's first NFDI4Ing-facing paper — **CoRDI 2026
or 2027 should carry a Shepard contribution** (cf. § 5.2 of
`aidocs/strategy/90` for the publication calendar).

---

## 5. Strategic recommendation

If you genuinely want this to be your thesis:

1. **This month:** quietly reach out to 2-3 potential supervisors. Lead
   with F(AI)²R + the regulatory-convergence angle (Framing A). Bring
   the MFFD demonstrator, not just the design docs.
2. **Next 3 months:** pick one supervisor, formalise the
   `Exposé` (5-10 page outline). Frame A is the most defensible; Framing
   B is the most novel. Decide which appetite the supervisor has.
3. **Year 1:** turn aidocs/95 + 96 + 97 + 41 into actual thesis chapters.
   Most of the writing is done — needs editing-into-thesis-voice, lit
   review, and stitching.
4. **Year 2:** empirical work — operate MFFD at scale; collect
   measurements; close the regulatory pipe end-to-end.
5. **Year 3:** thesis writing, defence preparation.

**Honest probability assessment** (gut, no data):
- "Could this become a successful thesis?" — **high (75-85%)** if a willing
  supervisor materialises
- "Will a willing supervisor materialise?" — **moderate (40-60%)** —
  depends entirely on whom you ask and how you frame it
- "Will the AI-methodology be a deal-breaker for some chairs?" — **yes,
  for some** — pick the supervisor accordingly

---

## 6. Co-authored alternative

If the thesis route is too slow / risky, the alternative is **co-authored
peer-reviewed publication** of the contributions:

| Contribution | Venue suggestions |
|---|---|
| Ontology-driven RDM architecture | *Data Intelligence* (MIT Press), *Semantic Web Journal*, ESWC |
| F(AI)²R methodology | *Patterns* (Cell Press), *Nature Computational Science*, FAIR-IM workshops |
| MFFD digital-thread case study | JEC Composites Magazine, CEAS Aeronautical Journal, ICCM |
| Regulatory-evidence-pack architecture | EASA AI Days, AIAA SciTech, EUDAT conferences |

A 4-paper portfolio over 18 months covers similar ground without the thesis
overhead. Per the HRK 2024 guidance, a cumulative-thesis option (Sammeldissertation)
combines this — three peer-reviewed papers + a synthesis become the
dissertation.

---

## 7. The one-line answer

> *Yes, this work is thesis-volume and thesis-grade. The constraint is
> not the work; it's the institutional fit. Lead with F(AI)²R as
> methodological transparency, pick a curious supervisor, frame around
> regulatory-convergence-via-ontology, and you have a defensible
> Dr.-Ing. in ~24 months.*

Co-authored cumulative path (Sammeldissertation) is the lower-risk
fallback that produces the same outcome on a similar timeline.

---

## 8. What I'd do this week

1. List 5 supervisors at 3 universities whose recent papers cite
   FAIR / RDM / AI-in-research / MBSE. Initial coffee conversation, no
   formal proposal yet.
2. Pull the Obscurity-Is-Dead preprint into a clean arXiv submission
   (just the .tex source + a stable Zenodo DOI) — citation hygiene
   prerequisite for the thesis.
3. Get the MFFD demonstrator live + clickable at
   `shepard.nuclide.systems/collections/...` (the immediate-next-step
   priority anyway).
4. Draft a 1-page Exposé — 90% of the framing is already in §2 of
   this doc. Pull it into a separate `aidocs/expose-shepard-thesis.md`
   when ready to share externally.
