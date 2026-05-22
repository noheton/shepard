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
