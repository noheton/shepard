---
title: "Shepard inside the DLR management context — institutional stack, governance frame, compliance gates"
subtitle: "(strategy chapter — briefing source for the Compliance Agent role)"
stage: feature-defined
last-stage-change: 2026-05-23
audience: [thesis, strategy, compliance-reviewer, dlr-management, operator]
---

# Shepard inside the DLR management context — institutional stack, governance frame, compliance gates

*Strategy chapter. This document situates Shepard inside the
**institutional, governance, and regulatory stack** that authorises
its existence and constrains its evolution at DLR. It is the
companion piece to the technical and ontological strategy chapters:
where [`aidocs/strategy/86-shepard-predecessor-systems.md`](86-shepard-predecessor-systems.md)
explains what came before, [`aidocs/strategy/87-dlr-zlp-positioning.md`](87-dlr-zlp-positioning.md)
explains why this institute needs the substrate, and
[`aidocs/strategy/89-genai-methodology-and-reflexivity.md`](89-genai-methodology-and-reflexivity.md)
asks whether the project's AI-use practice aligns with the author's
stated position, **this chapter asks: which directives bind Shepard,
which strategies authorise it, who decides it stays, and which
compliance gates does it actually have to pass?** The chapter also
serves as the **briefing source for a Compliance Agent role** added
to the specialised-agent lineup in `/opt/shepard/CLAUDE.md`.*

---

## 1. The institutional stack around Shepard

Shepard does not float in isolation. It runs inside a four-layer
institutional stack, each layer of which carries its own mandate,
strategy, and reporting obligation. From outermost to innermost:

1. **Helmholtz Association** (Helmholtz-Gemeinschaft Deutscher
   Forschungszentren e.V.) — Germany's largest research association,
   bundles 18 research centres including DLR. Sets cross-centre
   research-programme structure (PoF-V), funding rhythm
   (programme-oriented funding, evaluated every 7 years), and
   cross-centre policy frames (Helmholtz Open Science Policy,
   Helmholtz Metadata Collaboration HMC, Helmholtz AI). Issues the
   *Handlungsempfehlungen zum Einsatz von Künstlicher Intelligenz*
   (v1.0, 2024-09-18) [@helmholtzKiEmpfehlungen2024] that bind all
   Helmholtz centres.

2. **DLR** (Deutsches Zentrum für Luft- und Raumfahrt) — the
   Helmholtz centre that hosts Shepard. Sets DLR-wide cross-cutting
   policy: the *Cloud-Strategie des DLR* (v1.0, 2019-09-26)
   [@dlrCloudStrategie2019] + Annex *Nutzungsszenarien*
   [@dlrCloudStrategieAnhang2019], the *Rahmenrichtlinie
   Software-Engineering* (QMH-DLR-VA004, Ausgabe 6, 2025-07-01)
   [@dlrRahmenrichtlinieSE2025], the *Leitfaden zur Einführung
   und Nutzung KI-basierter Lösungen im DLR* (v1.00, 2024-12-17)
   [@dlrKiLeitfaden2024], and the *Unser Leitbild zu Künstlicher
   Intelligenz* mission statement [@dlrKiLeitbild]. The CIO
   (A. Bernhardt) holds policy-issuance authority for software
   engineering and IT veto rights for external cloud use.

3. **ZLP Augsburg** (Zentrum für Leichtbauproduktionstechnologie) —
   the DLR institute that operates Shepard for its specific
   research-data mission. Its mandate (composite manufacturing at
   single-aisle rates, green aviation, MFFD) is captured in
   [`aidocs/strategy/82-zlp-augsburg-stakeholder.md`](82-zlp-augsburg-stakeholder.md)
   and [`aidocs/strategy/87-dlr-zlp-positioning.md`](87-dlr-zlp-positioning.md).
   ZLP's *Leitung der Organisationseinheit* is the body that —
   per the SE-Rahmenrichtlinie §4.1.2 — bears strategic
   responsibility for Shepard's continued development and
   nominates its *Software-Engineering-Ansprechperson* (SEA).

4. **Shepard** — the substrate itself, with its own internal
   roles (Software Maintainer per the SE-Rahmenrichtlinie §4.1.7,
   contributor team, operators, plugin-author ecosystem). The
   *Verantwortliche Person für eine Software (Software Maintainer)*
   is the named accountable individual for the artefact.

A non-obvious bridge: a **fifth implicit layer** is being
constructed at the DLR-wide level — the *DLR Datenstrategie*,
currently in draft (v60, 2026-05-23) [@dlrDatenstrategieV60_2026].
Once finalised, the Datenstrategie's four objectives (Datenökosystem
/ Interdisciplinary research / Predictive analytics decision support
/ Data culture and competence) will be a binding strategic frame
that sits between layers 2 (DLR) and 3 (ZLP) and tells every DLR
institute how its research-data substrate must look. Shepard is one
of the substrates whose existence the Datenstrategie either
authorises explicitly (by name in the §5 measures list, currently
TBD) or implicitly (by satisfying the "FAIR principles binding"
clause in §"Maßnahmen Ziel 2").

The institutional stack is the answer to "who decides Shepard
stays?": every layer can in principle pull the plug, but each
operates on a different time horizon and via a different mechanism.
Helmholtz pulls the plug by changing the PoF-V programme structure
that funds the host centre. DLR pulls the plug by a CIO veto or
Rahmenrichtlinie change. ZLP pulls the plug by reassigning the
*Verantwortliche Person*. Shepard's contributors pull the plug by
ceasing to contribute. The first three are slow (years); the last
is fast (months). The Software Maintainer + SEA + SEB chain is the
formal accountability spine.

## 2. The AI-governance framework

Three documents stack on top of each other to form the AI-governance
frame Shepard must satisfy:

**Helmholtz Handlungsempfehlungen zum Einsatz von KI** (v1.0,
2024-09-18) [@helmholtzKiEmpfehlungen2024] is the binding
recommendation set adopted by the Helmholtz-Mitgliederversammlung.
It names five risk categories — (4.1) data-protection violation /
unintended information leakage, (4.2) copyright and IP infringement,
(4.3) misinformation and lack of scientific information integrity,
(4.4) bias from training data, (4.5) violations of AI-specific
regulation — and prescribes good-practice baselines: AI-generated
content must be reviewed (Überprüfen), AI use must be documented
transparently (Transparenz), responsible non-use applies where
sensitive data is at stake, and European-hosted services should be
preferred. The five risk categories form the **first axis of the
Compliance Agent audit checklist** (§6.A below). Authorship is
explicitly reserved for natural persons; AI systems cannot be
co-authors.

**DLR Leitbild zu Künstlicher Intelligenz** (undated, one-pager
mission statement) [@dlrKiLeitbild] names ten values that bind DLR
KI use: *Aufgeschlossenheit, Bewusstsein, Menschenorientierung,
Kompetenz, Ethik, Rechtssicherheit/Compliance, Effizienz,
Souveränität, Transparenz, Alleinstellung*. The Leitbild's
operational claim — *"Wir sind Vorreiter in der Nutzung von und
Forschung an KI-basierten Lösungen. Wir setzen KI selbstverständlich
und verantwortungsbewusst ein, um Routineaufgaben und komplexe
Probleme kreativ zu lösen"* — positions DLR as **an institution that
expects** its researchers and projects to use AI responsibly, not as
one that grudgingly tolerates AI use. This is a non-trivial
positioning: under the DLR Leitbild, **a project that uses AI
heavily (like this thesis) is doing what the institution explicitly
asked for**, provided the ten values are respected.

**DLR Leitfaden zur Einführung und Nutzung KI-basierter Lösungen im
DLR** (v1.00, 2024-12-17, F. Köster + A. Raulf authoring, IT-LA
review, 594. VS approval) [@dlrKiLeitfaden2024] is the operational
guidance that translates the Leitbild values into a checklist. It
aligns with EU AI HLEG ethics guidelines, the federal AI strategy,
and OECD AI principles. Seven value-pairs are named: Menschenzentrierung
+ Gemeinwohl / Fairness + Diskriminierungsfreiheit / Transparenz +
Erklärbarkeit / Privatsphäre + Schutz / Sicherheit + Robustheit /
Ökologische Nachhaltigkeit / Beherrschbarkeit + Verantwortung. The
Leitfaden's §2.3 makes explicit that **DSGVO, EU AI Act, Digital
Services Act, Data Act, and Data Governance Act all apply to AI use
at DLR**, and that DLR procurement processes + export-control rules
**must be checked before** an AI system is used (because the server
location of an external AI service is often unknown ⇒ assume
export). The Leitfaden's checklist (§3) is the **second axis of the
Compliance Agent audit checklist**.

### Three-way alignment check (the reflexivity finding)

The methodology chapter at
[`aidocs/strategy/89-genai-methodology-and-reflexivity.md`](89-genai-methodology-and-reflexivity.md)
already runs a two-way comparison between Krebs's stated AI position
(the *Generative KI* tutorial deck [@krebsGenKi2026]) and this
project's observed AI-use practice. The current chapter adds a third
axis: **the institutional position** captured in the DLR Leitbild
[@dlrKiLeitbild] and operationalised by the Leitfaden
[@dlrKiLeitfaden2024].

A three-way alignment table (Krebs ↔ DLR Leitbild ↔ observed):

| Dimension | Krebs (GenKI deck) | DLR Leitbild + Leitfaden | Observed practice (this project) | Divergence? |
|---|---|---|---|---|
| Human in driver seat | Yes — explicit | Yes — *Menschenorientierung* / "Mensch steht im Mittelpunkt" | Yes — every commit + design decision human-authored, AI as co-pilot | aligned |
| Transparency of AI use | Strong — train researchers to disclose | Required by Leitbild *Transparenz* + Leitfaden §4.6 | Strong — `aidocs/agent-findings/` records every dispatch; commit messages cite AI co-author; CLAUDE.md memory rules log all sessions | aligned (and arguably exceeds the standard) |
| Open-source preference | Yes — train on / contribute to | Yes — Leitbild *Transparenz* says "Open Source Ansätze" | Yes — Shepard fork is GPL-compatible / open; plugin SPI assumes OSS | aligned |
| European hosting preference | Implicit — sovereignty mentioned | Strong — Helmholtz §5 "in Europa gehostete" preferred; Leitbild *Souveränität* | **Partial divergence** — Claude (this project's primary AI co-pilot) is Anthropic, US-hosted; SAIA/GWDG (German) is preferred for DLR per memory `project_ai_plugin_config.md` but is the *configured default for plugin users*, not the project's authoring tool | **DIVERGENCE — disclosed but real** |
| AI co-authorship excluded | Implicit | Explicit — Helmholtz §4.3 "Autorenschaft kann nur durch natürliche Personen übernommen werden" | Aligned — commits cite Claude as `Co-Authored-By` (a convention; not academic authorship); thesis attributes to Krebs alone | aligned |
| Avoid sensitive-data leakage to external AI | Implicit | Strong — Helmholtz §4.1, Leitfaden §4.7 | Aligned for this project — Shepard is open source; no MFFD-internal IP is shared with Claude (synthetic seed data only); real MFFD data flows are operator-side, not author-side | aligned (with care needed if real MFFD data ever enters the authoring loop) |
| Eco-sustainability of AI use | Mentioned briefly | Strong — Leitfaden lists ökologische Nachhaltigkeit as one of seven value-pairs | **Partial divergence — measurement gap** — SUST1 logs Shepard's own energy/CO₂ per commit but **does not yet log the energy/CO₂ of the Claude inference calls that produced the commits**; the AI co-pilot's footprint is invisible | **DIVERGENCE — measurement debt** |
| Methodological documentation of AI use | Strong | Required by Leitbild *Bewusstsein* + Leitfaden §4.6 | Strong — `aidocs/strategy/89` exists, this chapter (`93`) exists, `aidocs/agent-findings/` is the working log | aligned |

**Two honest divergences surface**:

- **European-hosting divergence.** The authoring AI co-pilot is
  Anthropic's Claude (US-based), not a German/EU service. The
  Helmholtz recommendations and DLR Leitfaden both prefer
  European-hosted services. This is not a violation (Helmholtz
  recommends, not mandates; the Leitfaden §2.3 names export-control
  as the check, which for an open-source thesis project on public
  code is a low concern). But it **is** a divergence between stated
  policy preference and observed practice, and it deserves to be
  named rather than papered over. The mitigation: the Shepard
  product itself defaults to SAIA/GWDG (German) for the in-product
  AI plugin per `project_ai_plugin_config.md`, so the divergence is
  scoped to the thesis-authoring tool, not the thing being shipped.
  The thesis defence reflexivity question — *"why this AI rather
  than that one?"* — has an honest answer: Claude's 1M-context +
  Sonnet/Opus-4 capability set was the empirically-best choice in
  2026 for the multi-document architectural reasoning this project
  needs. A different choice would have given a different (likely
  thinner) thesis.

- **Eco-sustainability measurement gap.** Shepard logs its own
  energy and CO₂ per commit (`aidocs/sustainability/`); the AI
  co-pilot's inference footprint is not yet logged. This is the
  inverse of the previous divergence: the institution mandates
  awareness of AI's ecological footprint
  [@dlrKiLeitfaden2024 §2.2], and the project's own observability
  posture (OBS-MFFD1 per memory) is *make every interaction
  measurable*. The gap is **a SUST-AI-LOG backlog row** waiting to
  be opened in `aidocs/16` — same shape as SUST1, but the cost
  signal is the AI inference call, not the container compute.

These divergences are **methodologically load-bearing** for the
thesis defence (per the parent's framing in the working-memory
expansion of 2026-05-23): they show that the reflexivity exercise
**did** find something rather than confirming charitable alignment
across the board. A reflexivity that finds nothing is suspicious;
this one finds two concrete items.

## 3. The software engineering compliance frame

The **Rahmenrichtlinie Software-Engineering** (QMH-DLR-VA004,
Ausgabe 6, valid from 2025-07-01) [@dlrRahmenrichtlinieSE2025]
defines four Anwendungsklassen (application classes 0–3) for any
software produced inside a DLR Organisationseinheit. The classes
are cumulative: each higher class includes the recommendations of
the lower class.

| Class | Description | Shepard fit |
|---|---|---|
| **AK 0** | Personal use, small scope, no internal/external redistribution | Below Shepard's scope |
| **AK 1** | "Should be usable by uninvolved parties in a defined scope" — explicitly **the floor for research software** ("die einzuhaltenden Mindestanforderungen in Bezug auf die gute wissenschaftliche Praxis"). Other researchers / institutes can pick it up and continue development. | **Shepard's current declared class** (multiple consumers: ZLP Augsburg DLR, ZLP DLR Stuttgart via FORInFPro, BT, prospective adopters). |
| **AK 2** | "Likely to be developed long-term + sustainably"; foundation for product status | **The class Shepard's contributor base aspires to** — the AI-policy chapter 89, the SE network, and the v60 Datenstrategie's data-steward network all converge on AK 2 |
| **AK 3** | "Critical software / product character" | Out of current scope — would require AS-9100-style aerospace certification |

§4.2.4 of the Rahmenrichtlinie defines the **minimum documentation
requirements** for AK 2 and AK 3 software as well as AK 1 software
with "längerfristige Weiterentwicklungsperspektive":

| Required field | Shepard equivalent | Status |
|---|---|---|
| Name | "shepard" / "shepard-fork at nuclide.systems" | present (in `README.md`, `aidocs/42-vision.md`) |
| Zweck (purpose) | Research data management substrate; ontology-driven RDM for engineering research | present (`aidocs/42-vision.md`) |
| Verantwortliche Person (Software Maintainer) | F. Krebs (this fork); upstream maintained at gitlab.com/dlr-shepard | present (memory `userEmail`; CLAUDE.md) |
| Anwendungsklasse | AK 1 declared, AK 2 aspirational | **needs to be made explicit** — should appear in `aidocs/00-index.md` and `README.md` |
| Einschätzung des Reifegrades | "Reifegrad" maturity assessment of the codebase | **needs to be made explicit** — could be derived from `aidocs/44` (feature-matrix) but should be summarised as a single field |

The §4.1 *Zuständigkeiten* chain maps onto Shepard as:

| Role | Who, at Shepard scope |
|---|---|
| DLR-Vorstand | benennt SEB (DLR-wide; not Shepard-specific) |
| Leitung der Organisationseinheit | ZLP-leitung; strategically responsible |
| CIO DLR | A. Bernhardt; policy-issuance authority |
| Software-Engineering-Beauftragter (SEB) | T. Schlauch (per Rahmenrichtlinie cover page) |
| Software-Engineering-Ansprechperson (SEA) for ZLP | **TBD — should be named explicitly** (likely Krebs or a ZLP DM colleague) |
| Verantwortliche Person für die Software | **F. Krebs (this fork)** |

The *mitgeltende Unterlagen* (§5) include **QMH-DLR-VA014 Open
Science** and **QMH-DLR-VA015 Forschungsdatenmanagement (FDM)** — the
latter is directly Shepard-shaped territory (Shepard is the
substrate that helps DLR comply with QMH-DLR-VA015). Mapping
Shepard's compliance against VA014 and VA015 line-by-line is **a
COMP-SE-MAP backlog row** that should be opened in `aidocs/16` (the
two source documents need to be pulled from the DLR intranet
QMH portal first; not in the working-memory artefact set).

## 4. The cloud and hosting authorisation frame

The **Cloud-Strategie des DLR** (v1.0, 2019-09-26, IT-LTG authored,
IT-Lenkungsausschuss reviewed) [@dlrCloudStrategie2019] is the
DLR-wide framework that authorises (or prohibits) any cloud-based
service. The headline shift is in §2: DLR moved from a perceived
"Cloud-Verbot" to a **Chancen-Risiken-Abwägung** posture in 2019.
External cloud use is no longer forbidden by default but is gated
on a structured Vorabprüfung process (§4 of the strategy + Annex
*Nutzungsszenarien* [@dlrCloudStrategieAnhang2019]).

The §5.3 *Risikostufen* (risk tiers) are the operative
classification:

| Risk tier | Description | Permission posture |
|---|---|---|
| **Niedrig** | Public data; pre-publication data where unintended leakage causes no harm | External cloud uncritical; register entry recommended |
| **Mittel** | Project data without special classification, low protection need | External cloud allowed; appropriate protection level required; IT-Sicherheitsbeauftragter must be informed |
| **Hoch** | Important DLR-internal data; personenbezogene Daten; data with legal retention/deletion obligations | **External cloud only via Einzelfallprüfung + IT-Sicherheitsbeauftragter approval**; **external clouds outside Germany "often unsuitable"** |
| **Sehr hoch** | Secret-classified data; business-critical data; essential DLR-core services; Partner-IP that would be harmed by external exposure | **External cloud forbidden** |

The **CIO and IT-Sicherheitsbeauftragter both hold Veto-Recht**
(§5.3 point 4) — either can prohibit a cloud deployment if it
threatens DLR IT-security or violates the Rahmenrichtlinie.
Escalation runs through the Vorstand.

### Mapping Shepard's hosting against the strategy

Shepard is deployed at Hetzner Falkenstein DE
(per `aidocs/sustainability/01-methodology.md` SUST-PROD-REGION-VERIFY).
Hetzner is a German-domiciled provider with German-hosted data
centres — the Cloud-Strategie §"Empfehlungen" specifically
recommends German-hosted providers where available (lower legal +
IT-security + export-control risk).

Per §5.3 data classification:

- The **public demo Shepard instance** (`shepard.nuclide.systems`)
  hosts synthetic LUMEN/MFFD seed data — **Niedrig** tier. External
  cloud use uncritical; the only Cloud-Strategie compliance asks are
  (a) entry in the DLR-internal cloud-services register (per §5.3
  point 9) and (b) verification that no real MFFD-internal IP is
  ever placed on this instance.

- An **operator-side Shepard at ZLP** that ingests **real MFFD
  data** would carry **Hoch** tier (DLR-internal data; potentially
  Partner-IP with no external-use clearance; personenbezogene Daten
  via user identities). At Hoch tier, hosting at Hetzner DE is
  **conditionally allowed but requires Einzelfallprüfung + IT-Sicherheitsbeauftragter
  approval**. The Annex §4.3 *Dedizierte externe
  Wissenschafts-Clouds* (which Shepard is) refers to §4.2
  guidance — Confidentiality "not given" without additional
  encryption; integrity not guaranteed; exit-strategy mandatory.

- A **DLR-internal Shepard at the institute** (on DLR network,
  operated by DLR IT) would not trigger §5 at all — it's covered by
  §6 (internal cloud services), which has lighter constraints (SLAs
  for availability, clear ops responsibilities). This is the cleanest
  posture for production MFFD use and is the assumed deployment
  shape for the DLR-internal MFFD pilot.

The **Cloud-Strategie point 8** mandates **Exit-Strategie + container
solutions** for external cloud use — Shepard's docker-compose
deployment already satisfies this structurally (the substrate is
container-packaged + portable; an exit to a different host is a
config change, not a rewrite). This should be **made explicit** in
`aidocs/ops/49-deploy.md` (or equivalent) — a one-paragraph
"Cloud-Strategie §8 exit-strategy fitness" assertion that an
operator can cite during their own Vorabprüfung.

## 5. The funding and evaluation cadence

Shepard is not directly PoF-V funded; it is institutional
infrastructure financed out of ZLP's base budget plus contributions
from project commitments (HMC Phase 2 work-packages per
[`aidocs/strategy/90-hmc-phase-2-positioning.md`](90-hmc-phase-2-positioning.md);
NFDI4Ing F-1 + F-2 measures per
[`aidocs/strategy/88-nfdi4ing-alignment.md`](88-nfdi4ing-alignment.md);
FORInFPro per [`aidocs/strategy/91-forinfpro-semantically-driven-analytics.md`](91-forinfpro-semantically-driven-analytics.md)).

Two evaluation rhythms apply:

- **Helmholtz PoF-V centre evaluation** — every 7 years; reviews
  centre research-programme alignment + delivery. Shepard appears
  as institutional infrastructure that helps the host centre satisfy
  Open Science + FAIR commitments. The visibility test: does
  Shepard's adoption pattern show up in PoF-V evaluation evidence
  (number of datasets, citations, FAIR compliance reports)? The
  Helmholtz Datenstrategie v60 §"Zusammenfassung / Ausblick" puts
  this question explicitly: *"Werden die Ziele und Maßnahmen
  nachgehalten?"* — Shepard is one of the things that needs to be
  trackable.

- **Project-specific evaluations** — Fachpanel-style reviews at the
  programme / project level (FORInFPro, MFFD, NFDI4Ing F-1, HMC2
  WPs each have their own cadence). The 2026-03-20 Fachpanel
  artefact [@bayernFachpanel2026] is one such review point;
  the strategic-context discussion that the panel surfaces is the
  evidence that Shepard's roadmap is **shaped by** rather than
  parallel to the funding-evaluation rhythm.

The **DLR Datenstrategie v60 draft** [@dlrDatenstrategieV60_2026]
introduces a new evaluation expectation that does not yet have a
binding home but will: an institutional-level data-stewardship
network (§"Maßnahmen Ziel 4") that captures, validates, and reports
on FAIR compliance at the institute level. Shepard, as the
substrate that **operationalises** FAIR at ZLP, will be one of the
things this network looks at. The Datenstrategie editorial note —
*"Werden die Ziele und Maßnahmen nachgehalten? … Übersichtstool: Um
die Strategien zusammenlaufen; Verantwortung mit David Abteilung"*
— is the live editorial debate about how this oversight gets done.
Shepard's own observability posture (OBS-MFFD1; per memory `feedback_shepard_measures_itself.md`)
gives the substrate a chance to be that *Übersichtstool* for its
own deployment.

## 6. Implications for Shepard — the Compliance Agent audit lenses

This is the briefing source for the **Compliance Agent role** added
to `/opt/shepard/CLAUDE.md` (Role 11 — authored separately by the
parent dispatcher). The Compliance Agent applies the following
audit lenses to any Shepard PR, design doc, or proposed feature.
Each lens is **concrete enough** to produce a "Shepard does / does
not / partially satisfies" verdict without re-reading the source
PDFs.

### 6.A — Helmholtz AI Recommendations checklist [@helmholtzKiEmpfehlungen2024]

| # | Lens | Where it bites Shepard |
|---|---|---|
| 4.1 | No sensitive-data leakage to external AI services | Shepard ingests research data; if the in-product `shepard-plugin-ai` ships, the plugin's default provider matters — SAIA/GWDG (German, hosted EU) is the configured default per `project_ai_plugin_config.md`. Verdict: **partially satisfied** — config exists, but a default-allowlist of plugin-AI providers (whitelist German/EU; explicit warning for US-hosted) would harden this. |
| 4.2 | Copyright / IP protection on AI-generated content | f(ai)²r AI-provenance vocabulary marks AI-authored individuals in the graph; license field on DataObject is the gap (memory `project_competitive_position.md`). Verdict: **partially satisfied** — provenance shape exists, license enforcement gap is open. |
| 4.3 | No misinformation; scientific information integrity | Shepard records provenance (PROV-O, m4i); the human-vs-AI-vs-collaborative provenance modes (per memory `project_ai_human_collab_provenance.md`) make AI-generated content visibly distinguishable. Verdict: **satisfied (structurally) — the substrate makes this visible by design**. |
| 4.4 | Bias from training data | Out of scope for Shepard's substrate role (Shepard does not train models); applies to the prospective `shepard-plugin-ai`. Verdict: **deferred to plugin design** — the plugin must document its provider's bias-disclosure posture. |
| 4.5 | EU AI Act compliance | EU AI Act Article 50 (transparency obligation for AI-generated content) is satisfied by the f(ai)²r provenance vocabulary (see `aidocs/agent-findings/easa-ai-regulatory-positioning.md`). Verdict: **satisfied (for transparency); other Articles (high-risk classification, conformity assessment) do not apply because Shepard is data infrastructure, not a high-risk AI system itself.** |

### 6.B — DLR Leitbild values [@dlrKiLeitbild] + Leitfaden checklist [@dlrKiLeitfaden2024]

| # | Value | Lens applied to Shepard |
|---|---|---|
| Menschenorientierung | Human in driver seat at every action | The Shepard UI requires explicit confirmation for AI-suggested edits; the agentic ingest workflow per memory `project_mffd_import_workflow.md` has a warmup gate + ESCALATION ladder; **lens passes**. |
| Bewusstsein | Conscious decision-making about AI use | Per `feedback_autonomous_mode_protocol.md` three-tier sure/unsure/escalate; `aidocs/agent-findings/` records every dispatch; **lens passes**. |
| Kompetenz | Continuing learning + capability | `aidocs/reading-list.md` + the reading rhythm; **lens passes**. |
| Ethik | Ethical use along good scientific practice | f(ai)²r provenance + license field + AI-authored markers; **mostly passes — license field is the open gap**. |
| Rechtssicherheit / Compliance | Comply with all applicable law | This document is the audit instrument; **structurally passing — instrument now exists**. |
| Effizienz | Common standards + sustainable processes | Shepard is the common substrate; SUST1 logs efficiency; **lens passes**. |
| Souveränität | Technological independence | Open-source GPL-compatible stack; plugin SPI; container-deployable anywhere; **lens passes**. |
| Transparenz | Reproducibility + open-source approach | All Shepard code public; design docs in aidocs/; provenance is first-class; **lens passes (and exceeds the standard)**. |
| Alleinstellung | Recognise + protect uniqueness of research output | Shepard is the substrate that makes the uniqueness *legible* (provenance, citations, PIDs); **lens passes (substrate enables, doesn't enforce)**. |
| Aufgeschlossenheit | Open-minded use of AI | The CLAUDE.md memory + this thesis are evidence the project lives this value; **lens passes**. |

### 6.C — SE-Rahmenrichtlinie [@dlrRahmenrichtlinieSE2025]

| Section | Lens | Verdict |
|---|---|---|
| §4.1 | Roles + accountability chain identified | **Partial — SEA for Shepard at ZLP needs explicit naming** (COMP-SE-ROLES backlog row). |
| §4.2.4 | Documentation for AK 2 + long-term AK 1 software | **Mostly satisfied — Name / Zweck / Maintainer present; Anwendungsklasse + Reifegrad need to be made explicit in `aidocs/00-index.md`** (COMP-SE-DOCS backlog row). |
| Mitgeltende Unterlagen | Compliance against VA014 Open Science + VA015 Forschungsdatenmanagement | **Pending — source documents need to be pulled from DLR intranet QMH portal first** (COMP-SE-MAP backlog row). |

### 6.D — Cloud-Strategie [@dlrCloudStrategie2019]

| Section | Lens | Verdict |
|---|---|---|
| §5.3 Risikostufen | Data classification correct for each deployment? | **Yes for the nuclide demo (Niedrig); operator-side deployments will need their own Einzelfallprüfung at the institute** — Shepard publishes guidance for operators in `docs/install.md` so they can run their own. |
| §5.3 §8 Exit-Strategie | Container-portable? | **Yes — docker-compose deployment**; should be **made explicit** as a "Cloud-Strategie §8 exit-strategy fitness" paragraph in deploy docs. |
| §5.3 §9 Register entry | The DLR-internal cloud-services register has Shepard? | **Open — the nuclide demo needs an entry**; operator-side deployments at DLR are covered by the institute's own entry. |
| §10 Export-Kontrolle | If deployed outside Germany, export-control rules apply | **Open — the nuclide demo is hosted in Germany (Falkenstein), so this is fine; if a US/non-EU demo is ever set up, this gates it**. |

### 6.E — Other regulatory frames

| Frame | Where it bites | Verdict |
|---|---|---|
| **EU AI Act Art. 50** transparency for AI-generated content | f(ai)²r provenance ⇒ AI-authored markers in the substrate | **satisfied structurally** |
| **EU AI Act Art. 4** AI-literacy obligation for organisations using AI | DLR-wide; this chapter contributes evidence | **partially satisfied — Shepard's KI-Leitbild-aligned posture documented; operator training docs are the open piece** |
| **DSGVO / GDPR** personal-data protection | User identities + audit logs + provenance trails | **structurally satisfied via permission system and audit trail**; data retention policy per data type is the gap (`project_storage_management.md` SM1 design) |
| **DSGVO Art. 25** privacy-by-design | Plugin SPI lets per-deployment privacy posture vary | **satisfied — admin-configurable retention + notification opt-in (NTF1 design)** |
| **EU Software / Open-Source licence compatibility** | GPL/AGPL/SSPL excluded per CLAUDE.md security gate | **satisfied — `.github/dependency-review-config.yml` enforces** |
| **Helmholtz Open Science Policy** | "as open as possible, as closed as necessary" | **satisfied — fork is open; restricted deployments are per-operator choice** |
| **DIN EN 9100** aerospace QMS | Shepard's traceability shape per memory `project_mffd_domain_context.md` | **partially satisfied — current statuses (DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED) lack NCR/FAILED/REJECTED** (per `aidocs/agent-findings/manufacturing-quality.md`) |
| **ISO 27001** / **BSI C5** cloud provider certification (Cloud-Strategie §"Empfehlungen") | Hetzner certification status to verify | **open — verify Hetzner has ISO 27001 or BSI C5** (small backlog item) |
| **EASA Learning Assurance** for ML-functional validation | REBAR uses Shepard as substrate per memory `project_rebar_integration.md` | **structurally satisfied — REBAR's certification needs are met by Shepard's traceability shape; REBAR is the validator** |

### 6.F — DLR Datenstrategie v60 anticipated lenses

These lenses are **anticipatory** — the Datenstrategie is in draft;
they may shift before final adoption.

| Section (v60) | Anticipated lens | Verdict |
|---|---|---|
| Ziel 1 Datenökosystem | Shepard fits the "System of Systems" + federated metadata + controlled vocabularies pattern | **satisfied (structurally — see `aidocs/strategy/88-nfdi4ing-alignment.md` and `aidocs/semantics/`)** |
| Ziel 2 Interdisziplinäre Forschung | Shepard's catalogue + FAIR compliance | **satisfied (structurally); operator adoption is the volume measure** |
| Ziel 3 Prädiktive Analysen | Shepard is the *substrate* for predictive analytics, not the analytics tool itself | **out of scope (substrate role)** |
| Ziel 4 Datenkultur | Shepard contributes to data culture by being usable + adoptable; data-steward role per Datenstrategie ⇒ same as SEA/Software Maintainer role per SE-Rahmenrichtlinie | **satisfied — the substrate; Data-Steward network is institutional, not substrate-side** |

## 7. What the Compliance Agent does in a PR review

A Compliance Agent dispatched on a PR walks through the §6 lenses
in order and produces a per-lens verdict + the smallest possible
fix. The agent's output shape is the same as other persona findings:

```
## What I found
## Opportunities  (= where Shepard genuinely passes a tough lens — quote it for funding evidence)
## Ideas         (= small fixes that close a gap)
## Real-world impact (= which auditor/funder/operator notices this)
## Gaps & blockers (= the open lenses, e.g. license field, SEA naming, ISO 27001 verification)
## What surprised me
```

The agent is **strict but not maximalist**: passing 28 of 30 lenses
is a green review; the two open ones get explicit fixes in the
backlog or in the PR. The agent does *not* gate on "no findings"
because no shipped feature has zero findings — the agent gates on
"every finding has either a fix or a backlog row".

The agent also exposes the **"opposing-lens" view** per memory
`feedback_agents_argue_and_consult.md`: when a Helmholtz §4.5
(EU AI Act compliance) lens is satisfied but an SE-Rahmenrichtlinie
§4.2.4 documentation lens fails, the agent surfaces the trade-off
honestly rather than averaging it to "mostly green".

## 8. Open compliance gaps — backlog rows

The lens-walk in §6 surfaces specific gaps. These are tracked as
`aidocs/16` rows (to be opened in a follow-up); naming them here so
the audit chain is auditable:

| ID | Description | Source lens |
|---|---|---|
| **COMP-SE-ROLES** | Name the Software-Engineering-Ansprechperson (SEA) for Shepard at ZLP explicitly | §6.C / SE-Rahmenrichtlinie §4.1 |
| **COMP-SE-DOCS** | Add Anwendungsklasse (AK 1, aspirational AK 2) + Reifegrad to `aidocs/00-index.md` + `README.md` | §6.C / SE-Rahmenrichtlinie §4.2.4 |
| **COMP-SE-MAP** | Map Shepard's compliance against QMH-DLR-VA014 (Open Science) + QMH-DLR-VA015 (FDM) line-by-line | §6.C / mitgeltende Unterlagen |
| **COMP-CLOUD-REGISTER** | Register the nuclide demo in the DLR-internal cloud-services register | §6.D / Cloud-Strategie §5.3 point 9 |
| **COMP-CLOUD-EXIT** | Make the §8 exit-strategy fitness explicit in `docs/install.md` or `aidocs/ops/49-deploy.md` | §6.D / Cloud-Strategie §5.3 point 8 |
| **COMP-AI-PROVIDER-ALLOWLIST** | `shepard-plugin-ai` default allow-list: German/EU first; explicit warning on US-hosted providers | §6.A / Helmholtz §4.1 |
| **COMP-LICENSE-FIELD** | Ship the `license` field on DataObject (already in memory `project_competitive_position.md` as #1 gap) | §6.A / Helmholtz §4.2 |
| **SUST-AI-LOG** | Log AI inference energy + CO₂ alongside Shepard's own compute footprint | §2 reflexivity / DLR Leitfaden §2.2 |
| **COMP-DSGVO-RETENTION** | Per-payload-kind retention policy ⇒ ties to SM1 storage management design | §6.E / DSGVO Art. 5 + 17 |
| **COMP-HETZNER-CERT** | Verify Hetzner's ISO 27001 + BSI C5 certifications, cite in deploy docs | §6.E / Cloud-Strategie §"Empfehlungen" |
| **COMP-NCR-STATUS** | Extend DataObject status vocabulary with FAILED / NCR_OPEN / REJECTED | §6.E / DIN EN 9100 + `aidocs/agent-findings/manufacturing-quality.md` |

## 9. What this chapter is and isn't

**This chapter is** the institutional + governance + regulatory
context in which Shepard exists, distilled into a Compliance Agent
briefing source. It is the answer to "put Shepard in the
management context" (per the working-memory expansion of 2026-05-23)
and the briefing source for the Role-11 Compliance Agent.

**This chapter is not** a substitute for legal advice, a binding
compliance certification, or a complete audit. The named lenses are
the ones a Compliance Agent applies; an actual external audit
(EASA, BSI, an EU AI Act notified body) would apply more, with
deeper evidence demands. The verdicts in §6 are the **project's
own honest self-assessment**, recorded so an auditor can either
agree (good) or disagree (in which case the chapter's structure
makes the disagreement easy to localise).

**The chapter's update cadence**: any time a referenced source
changes (Datenstrategie v60 → final; SE-Rahmenrichtlinie Issue 7;
a new Helmholtz AI recommendation; a new DLR cloud strategy v2.0),
the corresponding §1–§5 section bumps and §6 lenses get reviewed.
The aidocs `stage:` tag will move from `feature-defined` to
`audited-by-personas` once the Compliance Agent has run a first
full pass over an actual Shepard PR.

---

## References

Cited via `docs/_data/references.bib`. Sources newly added in this
PR:

- `helmholtzKiEmpfehlungen2024` — Helmholtz, *Handlungsempfehlungen
  zum Einsatz von Künstlicher Intelligenz*, v1.0 (2024-09-18).
- `dlrKiLeitbild` — DLR, *Unser Leitbild zu Künstlicher Intelligenz*
  (one-pager mission statement).
- `dlrKiLeitfaden2024` — DLR, *Leitfaden zur Einführung und Nutzung
  KI-basierter Lösungen im DLR*, v1.00 (2024-12-17, Köster + Raulf).
- `dlrRahmenrichtlinieSE2025` — DLR, *Rahmenrichtlinie Software-Engineering*,
  QMH-DLR-VA004 Issue 6 (2025-07-01).
- `dlrCloudStrategie2019` — DLR, *Cloud-Strategie des DLR*, v1.0
  (2019-09-26).
- `dlrCloudStrategieAnhang2019` — DLR, *Anhang zur Cloud-Strategie:
  Nutzungsszenarien*, v1.0 (2019-09-26).
- `dlrDatenstrategieV60_2026` — DLR, *Datenstrategie des DLR*, v60
  working draft (2026-05-23).
- `bayernFachpanel2026` — DLR, *Ergebnis Fachpanel 2026-03-20*
  (already cited in earlier docs).

Cross-cited (already in bib): `krebsGenKi2026`, `oecdAiScience2024`.
