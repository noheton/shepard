# EU Machinery Regulation 2023/1230 — Shepard implications

*Author: regulatory analyst (Claude agent), 2026-05-21.
Primary source: Regulation (EU) 2023/1230 of the European Parliament and of
the Council of 14 June 2023 on machinery, OJ L 165, 29.6.2023, p. 1
(<https://eur-lex.europa.eu/eli/reg/2023/1230/oj>). Direct EUR-Lex HTML and PDF
extraction failed during this session (returned empty / binary streams); the
analysis below therefore relies on **secondary authoritative readings** —
TÜV Rheinland, TÜV SÜD, Pilz, Nemko, ATIC, tekom, Intertek, safetysoftware.eu,
EU-OSHA, and Rockwell Automation — cross-checked against the EUR-Lex
metadata and the Commission's adopted-text outline. Where the secondary
readings disagree, the more conservative reading is reported and flagged.
This is companion analysis to
`aidocs/agent-findings/easa-ai-regulatory-positioning.md` (aviation AI) and
`aidocs/agent-findings/easa-data-management-learning-assurance.md` (DM/LA
deep-dive); it does not duplicate those.*

## TL;DR

Regulation (EU) 2023/1230 — the Machinery Regulation — replaces Directive
2006/42/EC and **applies from 20 January 2027**; from that date every machine
or partly-completed machine "placed on the market" in the EU must conform.
The genuinely new content is concentrated in three places: (i) cybersecurity
and software-integrity as Essential Health and Safety Requirements
(EHSR 1.1.9, 1.2.1 of Annex III); (ii) the explicit treatment of
"safety components with fully or partially self-evolving behaviour using
machine learning approaches" as **high-risk machinery requiring third-party
notified-body conformity assessment** (new Annex I, Part A); and (iii) the
permission to provide instructions for use **in digital format**, with a
hard accessibility obligation of **"the expected lifetime of the machinery
and at least 10 years after placing on the market."** MFFD's AFP cells,
ultrasonic / resistance / stud welding cells, KUKA R20 robots and LBR cobots
**are in scope as machinery**; whether any individual cell lands in Annex I
high-risk turns on whether its safety functions involve ML — which for
MFFD's current configuration is **no**, so the cells fall under the
self-assessment route, but with full Annex III, Annex V and Annex VI
obligations regardless. Shepard already provides the spine (appId-stable
identifiers, Predecessor/Successor lineage, file/timeseries containers,
SemanticAnnotation, MinIO retention) that satisfies the *data substrate*
this regulation demands; the missing pieces are a **license/legal-status
field on DataObject**, a **machine-readable export of the technical file**
in the regulation's expected shape, and a **safety-software change log** with
the 5-year retention specifically required by EHSR 1.1.9. None of these are
hard to build; all of them are missing today. The one urgency: the
regulation is *adopted law*, not anticipated MOC — by 20 January 2027
MFFD's production environment owes a technical file in the new format, and
Shepard is the only candidate substrate that hosts the underlying data.

## 1. The regulatory landscape (regulation, recitals, annexes)

| Date | Event |
|---|---|
| 14 June 2023 | Regulation adopted by Parliament and Council |
| 29 June 2023 | Published OJ L 165 |
| **19 July 2023** | Entry into force (20 days after publication) |
| 20 January 2024 | Articles on notified-body designation (Arts. 26–42) apply |
| 29 May 2026 | Amending Regulation (EU) 2024/2748 (emergency procedures) applies |
| **20 January 2027** | **Full application; Directive 2006/42/EC repealed** |

**No grace period after 20 January 2027.** Unlike many EU product
regulations, 2023/1230 does **not** allow machinery placed on the market
before the application date to continue being sold against the old regime —
the cutover is on the placing-on-market date. Machinery placed on the
market **before** 20 January 2027 continues to be governed by Directive
2006/42/EC for its lifetime (no retroactive obligation). Machinery placed
on the market **on or after** 20 January 2027 must comply with 2023/1230
in full.

**Scope (Articles 1–5).** "Machinery" in the regulation is broader than in
the old Directive. The covered list includes: machinery (whole assemblies);
related products (interchangeable equipment, safety components, lifting
accessories, chains/ropes/webbing, removable mechanical transmission devices);
and "partly completed machinery." Industrial robots, robotic cells, AFP heads,
welding stations, KUKA R20 and KUKA LBR arms are **all machinery** in the
regulation's sense (or "partly completed machinery" for cells delivered for
integration). The whole MFFD production environment at DLR ZLP Augsburg is
in scope when next placed on the market — and that includes machinery
*substantially modified* after the application date (Article 18 substantial
modification clause: a substantial modification triggers a new conformity
assessment by the modifier, who then takes on manufacturer obligations).

**Annex map.** The regulation has eleven annexes; the operationally important
ones for a research-data discussion are:

| Annex | Subject |
|---|---|
| **Annex I** | High-risk machinery (Part A — always notified-body; Part B — notified-body unless harmonised-standard self-assessment) — *replaces old Directive Annex IV* |
| **Annex II** | EU Declaration of Conformity content |
| **Annex III** | **Essential Health and Safety Requirements (EHSR)** — the safety floor |
| **Annex IV–V** | Conformity assessment modules (A internal control; B EU type-exam; C; G unit verification; H full QA) |
| **Annex IV (new sense)** | **Technical documentation** required to prove conformity |
| **Annex VI** | **Instructions for use** content and form |
| **Annex VIII** | Notified-body requirements |

(The numbering of Annex IV/V differs between commentaries because the new
regulation reshuffled the old Directive's annexes; the *technical
documentation list* is in what most secondary sources call **Annex IV** of
the new regulation, distinct from old-Directive Annex IV which has become
**new Annex I**. The Shepard team should sanity-check against the OJ text
before quoting annex numbers in any external deliverable — this report
cites the *content* rather than relying on annex numbering disambiguation.)

## 2. AI / self-evolving ML provisions — the new thing

This is the regulation's load-bearing novelty and the reason it could not be
done as an amendment to 2006/42/EC.

**Annex I, Part A** explicitly lists, as high-risk machinery requiring
mandatory third-party notified-body conformity assessment:

- "Safety components with fully or partially self-evolving behaviour using
  machine learning approaches ensuring safety functions"
- "Machinery having embedded systems with fully or partially self-evolving
  behaviour using machine learning approaches ensuring safety functions that
  have not been independently placed on the market" — i.e., a machine whose
  on-board ML *is* the safety component

(Both verbatim per multiple cross-checked secondary readings; the EUR-Lex
authentic text should be cited in any external use.)

The trigger is not "the machine contains AI." The trigger is "**a safety
function** is implemented by an ML component whose behaviour can evolve."
A vision system used for production-quality classification but with no
safety role is *not* in Annex I; an ML-based collision-avoidance gate on a
collaborative robot **is**.

**Annex III EHSR additions for ML-bearing machinery** (paraphrased from the
TÜV / Pilz / safetysoftware.eu cross-readings; verbatim citation needs OJ):

- The risk assessment must consider "the evolution of the machinery's
  behaviour during its lifetime" — i.e., a static FMEA is not sufficient;
  the manufacturer must analyse the trajectory of behaviour change.
- A **safe fallback** must exist: a state the machine can be returned to
  when the ML component fails its self-checks, lets confidence drop below
  a threshold, or is taken offline by the operator.
- The **training, validation and verification data sets** used during
  development must be documented, including provenance, and retained as
  part of the technical file (this is the data-management hook — see §6).
- The **boundaries of safe behaviour** (the operational design domain in
  EASA's vocabulary; the regulation does not use the term) must be defined
  and monitored.

**Relationship to the EU AI Act (Regulation (EU) 2024/1689).** The two
regulations have a designed handoff: the AI Act, Article 6(1) and Annex I,
treats AI systems that are safety components of products regulated by
2023/1230 as **high-risk AI** under the AI Act *as well*. Article 6 of the
AI Act and Annex I list 2023/1230 as one of the harmonisation regulations
that triggers Article 6(1) classification — meaning a single ML safety
component is simultaneously subject to:

1. Machinery Regulation Annex III EHSRs + Annex I notified-body assessment
2. AI Act Chapter III Section 2 obligations (risk management, data
   governance, technical documentation, record-keeping, transparency,
   human oversight, accuracy/robustness/cybersecurity)
3. AI Act post-market monitoring + serious incident reporting

The regulations are not in conflict — the AI Act Article 8(2) and
Article 102 provide a "single technical documentation" route: a manufacturer
may produce **one** technical file that satisfies both 2023/1230 Annex IV
and AI Act Annex IV. This is the data-management opportunity Shepard should
target.

**Relationship to the Cyber Resilience Act (Regulation (EU) 2024/2847).**
The CRA covers "products with digital elements"; the Machinery Regulation
covers "machinery." A robotic cell is both. The two regimes overlap on
cybersecurity (Machinery Regulation EHSR 1.1.9 vs CRA essential
requirements) but the CRA layers additional obligations: **Software Bill
of Materials (SBOM)**, formal vulnerability management process, security
updates "free of charge for at least 10 years or for the expected support
period, whichever is longer," and the staged incident reporting (24-hour
early warning, 72-hour full notification, 14-day corrective measure)
effective from 11 September 2026. The CRA's 10-year window aligns with
2023/1230's instructions-accessibility window — a happy coincidence.

## 3. Digital documentation requirements

The regulation **permits** instructions for use in digital format (this is
the big practical change vs. 2006/42/EC, which was paper-by-default).

Specific provisions (cross-referenced from tekom, instrktiv, Nemko, Pilz):

- **Format**: "digital printable format" — the regulation does not pin a
  specific MIME type but the user must be able to **print, download, and
  save** the instructions. PDF is the obvious satisfying format; HTML is
  acceptable if it satisfies the print/download/save trio.
- **Language**: instructions must be in the official language(s) of the
  Member State **where the machinery is placed on the market, made available
  on the market, or put into service** (Annex VI). This is per-deployment,
  not per-manufacturer. For MFFD shipped to a German operator: German. For
  the same cell shipped to a French operator: French. The same rule applies
  to the EU Declaration of Conformity (Annex II) — translated into the
  language(s) required by the Member State of placement.
- **Accessibility**: must remain accessible "for the expected lifetime of
  the machinery **and at least 10 years** after placing on the market."
- **Paper on request**: the distributor must be able to deliver a paper
  copy free of charge **within one month** of a user request.
- **Non-professional users**: for non-professional users a printed copy of
  the safety-critical information essential for commissioning and safe use
  is **mandatory regardless of digital provision** — this is the
  consumer-protection backstop.
- **URL stability**: the regulation does not literally prescribe a stable
  URL or PID, but the 10-year accessibility obligation effectively requires
  one. A manufacturer who hosts instructions at a URL that breaks in year
  5 has not satisfied the regulation. This is where Shepard's appId
  (UUID v7, stable for the lifetime of the platform) becomes a regulatory
  asset rather than just a developer-experience win.

**Cybersecurity logging — EHSR 1.1.9 specific to safety software.** The
regulation requires that "every conscious or unconscious intervention in
the safety software" be logged, and the **log files must remain available
for at least 5 years after uploading modified software**. This is a
separate retention regime from the 10-year instructions accessibility; it
is shorter (5y) but stricter on what it covers (every change to safety
software). For MFFD this means: every firmware update on the AFP head's
safety PLC, every parameter change on the ultrasonic welding cell's
emergency-stop logic, every reflash of the LBR's safety zone must produce
a log entry retained for 5 years.

## 4. Technical documentation requirements

The technical file (the substantive content of what most secondary readings
call new Annex IV) must include, paraphrased:

- General description of the machinery
- Overall drawing, control-circuit drawings, and the descriptions and
  explanations necessary for the operation of the machinery
- The full risk assessment (process, hazards identified, mitigations,
  residual risks)
- The standards applied, including harmonised standards (and where not
  applied, an explanation of the alternative solutions)
- Test reports and certificates
- A copy of the instructions for use (Annex VI compliant)
- Where relevant: declarations of incorporation for partly-completed
  machinery integrated into the assembly
- The **software documentation**: for safety-relevant software, the design
  documentation, test reports, validation, and the change/version log
- For ML-based safety components: the **training data lineage,
  validation/verification data lineage, model versioning, and the
  operational design domain** (the regulation's words for ODD differ but the
  concept is present)

**Retention** of the technical file: **at least 10 years from the placing on
the market of the last unit of that machinery model**, per the regulation's
Annex on documentation. This means a manufacturer who ships a final unit
of an MFFD-tooling AFP head in 2030 must keep the technical file accessible
until 2040 at minimum.

**Audit surface**: market surveillance authorities (national; in Germany
typically the *Bundesanstalt für Arbeitsschutz und Arbeitsmedizin* /
state labour authorities, plus the *Berufsgenossenschaft IFA* technical
services, plus notified bodies like TÜV Rheinland / TÜV SÜD / DEKRA) can
demand the technical file at any time within the retention window. The
file must be producible within "a reasonable period" — typically
interpreted as days, not weeks.

## 5. High-risk machinery (Annex I) — does MFFD land here?

The new Annex I has two parts (renaming the old Directive Annex IV
split). Cross-referenced from ATIC, Pilz and tekom readings:

**Annex I, Part A** — always notified-body assessment:
- Removable mechanical transmission devices and their guards
- Vehicle servicing lifts
- Portable cartridge-operated fixing and other impact machinery
- **Safety components with fully or partially self-evolving behaviour using
  machine learning ensuring safety functions** (the AI category)
- **Machinery with embedded ML safety systems not independently marketed**
  (the AI category)

**Annex I, Part B** — 19 categories including circular/band saws, planers,
injection moulding, underground mining equipment, waste-compaction trucks,
lifting platforms >3 m, presence-detection protective devices, etc.

**MFFD assessment (current configuration, May 2026):**

| MFFD asset | In Annex I? | Reasoning |
|---|---|---|
| AFP cell (KUKA R20 + tape head) | **No (Annex I)** — Annex III applies fully | AFP head's safety is governed by conventional PLCs + Cat 3/4 safety circuits per ISO 13849; no ML in the safety chain. Vision/quality ML is *not* a safety function. |
| Ultrasonic welding cell | **No** | Conventional safety circuitry. |
| Resistance welding cell | **No** | Same. |
| Stud welding cell | **No** | Same. |
| LBR cobot clamping station | **No, but on the edge** | Cobot collision avoidance is a safety function; if it is implemented by the KUKA-supplied SafetyTool (deterministic, certified) it is *not* ML. If a future iteration uses learned collision avoidance, the cobot **moves into Annex I Part A** and becomes notified-body-mandatory. |
| Whole assembled MFFD line | **No** | Aggregate of non-Annex-I cells. |

**The honest assessment**: today, MFFD as built is *not* Annex I high-risk.
The full obligation set still applies (Annex III EHSRs, Annex IV technical
documentation, Annex VI instructions for use, EU Declaration of Conformity)
but the conformity-assessment route is internal control (Module A) rather
than notified-body type-exam (Module B). **This will change** the moment a
DLR-developed or partner-developed ML safety component enters the line — at
which point Shepard's data-management substrate becomes the natural place to
hold the training-data lineage and model-version trail that the notified
body will demand.

## 6. Mapping to Shepard capabilities

The regulation's data-substrate demands map cleanly onto Shepard primitives.
Status legend: ✓ shipped, ⊙ designed (TPL slice exists), ⊘ gap.

| Regulation requirement | Shepard mechanism | Status |
|---|---|---|
| Stable identifier for every machine, every test run, every artefact | `appId` (UUID v7) on every entity | ✓ shipped (A1, L2 series) |
| Lineage of training/validation/verification data for ML safety components | Predecessor/Successor on DataObject; SemanticAnnotation tagging role (training/val/test) | ✓ shipped graph; ⊙ designed for SHACL-validated role tagging (TPL1) |
| Risk assessment artefact storage | DataObject + FileContainer (PDF or structured) | ✓ shipped |
| Standards-applied register | Annotation key `appliedStandard`; pointer to ISO 13849 / EN ISO 12100 / IEC 62443 references | ⊘ no controlled vocabulary today; preseed needed |
| Software / firmware change log for safety software (5-year retention) | DataObject of kind `SoftwareRelease` + Predecessor chain; PROV-O via PROV1a captures who/when/why | ⊙ PROV1a designed; ⊘ controlled `SoftwareRelease` kind + 5-year retention policy via SM1 |
| Test reports, certificates | FileContainer + DataObject typing | ✓ shipped |
| Instructions for use (Annex VI compliant — 10-year accessibility) | FileContainer + Collection + appId-stable URL + retention policy (SM1) | ✓ data; ⊘ no enforcement of the 10-year retention as a policy yet |
| EU Declaration of Conformity per Member State language | DataObject with `language` and `memberState` annotations; sibling DataObjects per language | ⊘ no schema enforcement of the language metadata today |
| Single technical file aggregating all of the above, machine-readable | Collection as the technical file root; export to a CSAF / SBOM / schema.org-aligned shape | ⊘ no "technical file export" exists; this is a 1-sprint plugin |
| Auditor / market-surveillance access (read-only, time-bounded) | RBAC + auditor role | ⊘ no "auditor share" affordance today (sharing exists only as collection ACL) |
| License / IP / restricted-access marker on each artefact | (none) | ⊘ **#1 missing field — already identified in `project_competitive_position`** |

## 7. The convergence with EASA + EU AI Act

Three frameworks now apply to MFFD-class work:

| Framework | Status May 2026 | Application | Demand on data substrate |
|---|---|---|---|
| **EU 2023/1230 Machinery Regulation** | **Adopted law** | 20 Jan 2027 | Technical file; instructions 10y; safety-software change log 5y; ML training data lineage if safety-relevant |
| **EU AI Act 2024/1689** | Adopted law; staggered application | Article 50 transparency: 2 Aug 2026. High-risk AI (incl. AI Act Annex I-listed harmonised regulations like 2023/1230): 2 Aug 2027 | Same technical-file shape (Art. 8(2), Art. 102 single-file route); + post-market monitoring + serious incident reporting |
| **EASA AI Concept Paper Issue 02 → RMT.0742** | **Anticipated MOC**, not yet adopted | Target AMC under Part 21: 2028 | DM-01…DM-08 (data quality), DA-01…DA-09 (data acquisition/preparation), LM/EXP families. Stricter than the machinery regulation on independence proofs and bias coverage. |

**Where they overlap:**

- All three demand **training/validation/verification data set lineage**
  with provenance. Shepard's Predecessor/Successor chain plus PROV-O
  capture is the structural answer; the regulatory shapes differ in the
  attributes they want attached, not in the underlying graph.
- All three demand a **technical file** that is machine-readable and
  exportable. AI Act Annex IV is the most detailed; the machinery Annex IV
  is shorter but overlapping; EASA is the strictest on independence proofs
  but does not formally require a "file" in the EU-product-regulation
  sense (it requires Means of Compliance Records, MoCRs, which are
  structurally similar).
- All three demand **change/version logging** of safety software with
  multi-year retention.
- All three permit/encourage **digital documentation** with stable
  identifiers; the machinery regulation is the most explicit about the
  10-year accessibility floor.

**Where they diverge (and Shepard could host the union):**

- The machinery regulation focuses on the **machine as deployed**;
  EASA focuses on the **ML model as certified**; the AI Act focuses on
  the **AI system as placed on the market**. A single MFFD line involves
  all three perspectives. The graph structure where DataObject(machine) →
  Successor → DataObject(deployment) → Predecessor ← DataObject(model)
  ← Predecessor ← DataObject(dataset) captures all three with one model.
- The machinery regulation's 5-year safety-software log retention is
  *shorter* than the AI Act's likely retention (which references
  "throughout the lifetime of the AI system"). Shepard's retention policy
  engine (SM1) should default to the longer of the applicable windows,
  not the shorter — operator-configurable per the runtime-knob doctrine
  in CLAUDE.md.
- Language requirements: machinery regulation is per-Member-State;
  EASA is English (aviation lingua franca); AI Act is per-Member-State.
  This is a metadata-tagging problem, not a translation problem.

**The single-substrate story.** A DLR institute that uses Shepard as its
research-data backbone gets, structurally, a foundation that satisfies the
*data-evidence* obligations of all three regulations from one model. It
does not get the *assurance properties* (no platform does — those are
done by the engineering team in the model and the operator in the field).
This is the same honest positioning that
`easa-ai-regulatory-positioning.md` adopts: Shepard is a credible evidence
platform, not an assurance engine.

The **F(AI)²R** community proposal (FAIR for AI, with explicit
provenance) is the bridge concept here: it asks for FAIR data principles
*plus* the PROV-O substrate that the regulators have converged on. F(AI)²R
is not regulator-endorsed; treating it as a regulator's word would be a
positioning error. But the artefacts F(AI)²R proposes — PROV-O capture,
training/val/test annotation, model card linking — are *exactly* the
intersection of what the three frameworks demand. Shepard targeting
F(AI)²R is targeting the union of regulatory obligation.

## 8. Worked example — MFFD by 20 January 2027

Scenario: DLR ZLP Augsburg ships an upgraded MFFD AFP cell to an Airbus
partner on 1 February 2027. The cell uses a KUKA R20 + a custom tape head;
no ML in the safety chain (yet); ML is used only for offline tape-layup
quality classification on the inspected ply.

**What must exist on 1 February 2027** for that cell to be lawful EU-market
machinery:

1. **EU Declaration of Conformity** in German (Member State of placement),
   per Annex II; signed by the legal manufacturer (DLR or the spinoff that
   commercialised the line); referencing the harmonised standards applied
   (likely ISO 12100 risk assessment, EN ISO 13849-1 safety functions,
   IEC 62443-3-3 / -4-2 cybersecurity, EN ISO 10218-1/-2 robot integration).
2. **Instructions for use** (Annex VI) in German, digitally hosted at a
   URL that will remain reachable until **at least 1 February 2037**, with
   the print/download/save trio satisfied. A paper copy must be deliverable
   within one month on request.
3. **Technical file** retained until at least 1 February 2037 (10 years
   from placing the last unit on the market):
   - Risk assessment (PDF + structured)
   - Standards-applied register
   - Software documentation for the safety PLC
   - **Change log for every safety-software intervention since
     commissioning, retained 5 years from each change** — per EHSR 1.1.9
   - Test reports (commissioning, periodic re-validation)
   - Drawings, parts list, supplier conformity (KUKA's robot, the safety
     PLC vendor)
   - For the quality-classification ML: the model is *not* in the safety
     chain, so the heavy AI Act / Machinery Regulation safety-ML
     obligations do not apply — but if the model output drives a process
     decision, the AI Act may still apply as a non-safety high-risk system
     depending on the classifier's role. (Out of scope here; this needs
     legal review per use case.)
4. **Cybersecurity posture**: software/data integrity protections per
   EHSR 1.1.9 / 1.2.1; logged interventions; vulnerability handling
   process (becomes CRA-formal from 11 September 2026 — Shepard's audit
   trail via PROV1a is the natural recording point).
5. **What a notified body or market-surveillance inspector asks for**: a
   single bundle, machine-readable, that contains all of the above.
   Shepard's Collection is the natural root; a "technical file export"
   plugin is the missing piece (see §10).

**Shepard's role in this scenario:**

- Holds the underlying data: risk assessment PDFs, test reports, code
  versions, change logs, training data, all under stable appIds.
- Holds the relationships: Predecessor/Successor chain shows that a
  particular cell rev N+1 was built from cell rev N plus a safety-PLC
  patch; the patch links to its source code commit; the validation tests
  link to the change.
- Holds the retention policy: SM1 storage management with a retention
  rule of "Machinery Regulation: keep 10 years + lifetime; Safety
  software log: 5 years per change" — configurable per Collection.
- Holds the audit affordance: a read-only auditor share that exposes a
  Collection to a market-surveillance inspector without granting write or
  delete (gap — see §9).
- Exports the technical file: a structured, machine-readable bundle
  in the regulation's expected shape (gap — see §9).

## 9. Gaps Shepard does NOT cover today

In priority order (highest-leverage first):

1. **License / legal-status field on DataObject.** Already #1 priority in
   `memory:project_competitive_position`; the Machinery Regulation
   compounds the urgency because the EU Declaration of Conformity, the
   instructions for use, and the technical file all carry distinct legal
   statuses (some confidential, some public, some publishable under a
   specific licence) that today have no structural representation.
2. **Member-State / language metadata schema.** Annex VI language is
   per-Member-State; today annotation keys are freetext. A controlled
   vocabulary `meta:language` (ISO 639-1) and `meta:memberState`
   (ISO 3166-1 α-2) is one PR.
3. **Safety-software change log with 5-year retention.** Requires
   PROV1a (designed) + SM1 retention policy (designed) + a controlled
   `SoftwareRelease` DataObject kind (not yet specced) + the audit-trail
   surface that PROV1a is intended to ship.
4. **Technical file export plugin** (`shepard-plugin-technical-file` or as
   part of `shepard-plugin-publisher`). Exports a Collection as a
   structured bundle in a shape an auditor can ingest. The AI Act
   Annex IV shape is the most demanding; if Shepard satisfies that it
   satisfies the machinery and EASA equivalents.
5. **Auditor share affordance.** Read-only, time-bounded, attributable
   access for a notified body or market-surveillance officer.
   Today's RBAC is binary (member or not); auditor share is a new
   role-like primitive.
6. **Retention policy enforcement** beyond the existing storage cap. SM1
   designs the retention engine; needs first migration to land.
7. **Standards-applied controlled vocabulary preseed.** ISO 13849-1,
   EN ISO 12100, IEC 62443, EN ISO 10218 — the harmonised standards a
   machinery manufacturer cites. Goes alongside the existing semantic
   bootstrap migration (V49).
8. **Training/validation/verification data role tagging via SHACL** — the
   TPL1 slice planned in `aidocs/semantics/95`. Already designed;
   blocks the EASA DM-04 line as well.
9. **Substantial modification trigger record.** When a Collection
   undergoes a substantial modification (per Article 18), the regulation
   demands a fresh conformity assessment. Today Shepard has no concept
   of "this is the substantial modification event" — Predecessor/
   Successor is too thin to carry the legal trigger.
10. **EU Declaration of Conformity as a typed DataObject** (not just a
    PDF in a FileContainer) — so the appId is the DoC identifier, the
    signed-by/signed-when/standards-cited are queryable.

## 10. Recommended actions

| # | Action | Estimated effort | Blocks |
|---|---|---|---|
| 1 | Add `license` field to DataObject + Collection (controlled vocabulary: SPDX + DLR-internal classes) | 1 sprint, backend + frontend + migration | FAIR, Unhide, technical-file export |
| 2 | Add `meta:language` + `meta:memberState` to the semantic bootstrap migration | 1 day | Annex VI compliance |
| 3 | Specify `SoftwareRelease` DataObject kind (or annotation) + 5-year retention via SM1 | 1 sprint (depends on SM1 shipping) | EHSR 1.1.9 compliance |
| 4 | Design doc: `shepard-plugin-technical-file` — export a Collection in Machinery / AI Act Annex IV shape | 1 design doc + 2 sprints to build v1 | Convergence story |
| 5 | Design doc: auditor share role + read-only time-bounded access | 1 design doc + 1 sprint | Market-surveillance audit |
| 6 | Preseed standards-applied controlled vocabulary (ISO 13849-1, EN ISO 12100, IEC 62443, EN ISO 10218) via V49-style migration | 1 day | Standards register completeness |
| 7 | Add a `aidocs/96-machinery-regulation-tpl-slice.md` design doc that lays out the above as a coordinated TPL slice — sibling to the SHACL TPL1 slice and the EASA-derived `aidocs/95` deep-dive | 1 day writing | Cohesion |
| 8 | Update `aidocs/44-fork-vs-upstream-feature-matrix.md` with rows for: license field; technical-file export; auditor share; software-release kind; 5-year/10-year retention; substantial-modification record | 0.5 day | Tracker currency per CLAUDE.md |
| 9 | Update `aidocs/42-vision.md` "Where it's going" section with a one-paragraph regulatory-substrate framing | 0.5 day | Vision currency per CLAUDE.md |
| 10 | Notify DLR ZLP Augsburg (MFFD legal/safety owner) and DLR-LY (the EASA contact, who can route to the right national authority) that the 20 January 2027 application date implies a 2026 deadline for the substrate to be in place — and that Shepard is the candidate substrate | 1 conversation | Buy-in |

**The minimum viable path for MFFD compliance via Shepard** is items 1, 2,
6 in this list, plus the SM1 + PROV1a + TPL1 slices already on the
roadmap. That set could be shipped by Q3 2026, leaving Q4 2026 for MFFD
to populate the substrate with the real data. Everything else (auditor
share, technical-file export, software-release kind) is genuinely useful
but is *not* on the critical path for January 2027 — the regulation
demands that the data exists and is accessible; it does not demand a
particular export format until an auditor calls.

**The institutional ask.** The Machinery Regulation's enforcement
authority for Germany is the *Bundesanstalt für Arbeitsschutz und
Arbeitsmedizin* (BAuA) + the relevant *Land* labour authority + the
*Berufsgenossenschaft IFA* + notified bodies (TÜV / DEKRA). DLR ZLP
Augsburg should engage with at least one of these in advance of
20 January 2027 to confirm the format expected for the technical file
and the instructions-of-use accessibility URL — different inspectors will
have different tolerance for "a URL hosted by a research-data platform"
vs. "a URL hosted by the manufacturer." The Shepard team should be in
that conversation so the substrate can be shaped to whatever the
inspector wants.

## Sources

Primary (cited but not directly retrievable in this session):
- Regulation (EU) 2023/1230, OJ L 165, 29.6.2023 —
  <https://eur-lex.europa.eu/eli/reg/2023/1230/oj>
- Regulation (EU) 2024/1689 ("EU AI Act"), OJ L of 12.7.2024
- Regulation (EU) 2024/2847 ("Cyber Resilience Act"), OJ L of 20.11.2024
- Directive 2006/42/EC (the regulation it replaces)

Secondary (cross-checked authoritative readings):
- [Nemko — Guide to the 2027 EU Machinery Regulation](https://digital.nemko.com/regulations/eu-machinery-regulation)
- [Pilz — Machinery Regulation 2027 (manufacturer-operator brief)](https://www.pilz.com/en-US/support/law-standards-norms/manufacturer-machine-operators/machinery-regulation)
- [ATIC — Regulation (EU) 2023/1230](https://www.atic-ts.com/european-new-machiney-regulation/)
- [tekom — New Machinery Regulation (EU) 2023/1230 (PDF; binary in this session)](https://www.tekom.de/fileadmin/tekom.eu/Downloads/Member_Area/New_Machinery_Regulation_final.pdf)
- [instrktiv — Machinery Regulation requirements on user manuals](https://instrktiv.com/en/blog/law-and-legislation/machinery-regulation-requirements-user-manual/)
- [TÜV Rheinland — New Machinery Regulation EU 2023/1230](https://www.tuv.com/world/en/new-machinery-regulation-eu-2023-1230.html)
- [TÜV SÜD — New Machinery Regulation: What You Need to Know](https://www.tuvsud.com/en-gb/resource-centre/blogs/uk/testing-and-certification-blog/new-eu-machinery-regulation-what-you-need-to-know)
- [Intertek — Essential Safety Requirements: What You Need to Know](https://www.intertek.com/blog/2025/07-03-new-eu-machinery-regulation/)
- [safetysoftware.eu — Machine safety now includes software, data and cyber risk](https://safetysoftware.eu/en/blog/machinery-regulation-eu-2023-1230-machine-safety-now-includes-software-data-and-cyber-risk)
- [GetReady Compliance — Machinery Regulation × Cyber Resilience Act](https://getreadycompliance.eu/machinery-regulation-cyber-resilience-act-guide/)
- [EU-OSHA — Regulation 2023/1230/EU machinery](https://osha.europa.eu/en/legislation/directive/regulation-20231230eu-machinery)
- [Rockwell Automation — Guide to the Machinery Regulation (EU) 2023/1230 (PDF)](https://literature.rockwellautomation.com/idc/groups/literature/documents/sp/oem-sp123_-en-p.pdf)

Companion analyses (Shepard):
- `aidocs/agent-findings/easa-ai-regulatory-positioning.md` (aviation AI)
- `aidocs/agent-findings/easa-data-management-learning-assurance.md` (DM/LA)
- `aidocs/agent-findings/easa-ai-compliance.md` (older gap analysis)
- `aidocs/semantics/95-shacl-templates-and-individuals.md` (TPL1 slice)
- `memory:project_competitive_position` (license-field gap)
- `memory:project_mffd_domain_context` (MFFD process chain, standards)
