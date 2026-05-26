---
stage: audited-by-personas
last-stage-change: 2026-05-26
---

# Shepard AI design × authoritative policy alignment — 9-source × 4-band matrix

**Task ID.** #147  
**Snapshot date.** 2026-05-26  
**Supersedes.** `aidocs/archive/agent-findings-sessions-2026-05/ai-in-science-policy-alignment.md`
(archived during SSOT-consolidation 2026-05-22; that doc used an operations-axis framework;
this doc uses the compliance-posture axis requested in task brief and adds five new sources).  
**Audience.** PIs writing grant applications (DFG, Horizon Europe, Clean Aviation JU),
institute admins evaluating adoption risk, Shepard contributors making AI-feature decisions.

---

## 1. Executive summary

Shepard's AI design sits inside the intersection of three regulatory postures: the
**EU AI Act Article 2(6) research exemption** (the AI-assist workflow is explicitly
out-of-scope for high-risk requirements), the **OECD/UNESCO/NIST consensus** that AI
in science is legitimate when paired with transparency and human oversight, and the
**funder-side** requirements from DFG, Helmholtz, and Clean Aviation JU that demand
FAIR data with documented AI use.

Across all nine sources and four compliance bands, Shepard's shipped infrastructure
scores **strongly in Transparency and FAIR-data bands, adequately in Human-oversight,
and has a narrow but actionable gap in Reliability** (sampler-parameter capture is
designed but not recorded today). Three concrete fixes close the gaps before the
EU AI Act Article 50 deadline of 2 August 2026.

**Overall band scores (shipped features only):**

| Band | Score | Summary |
|---|---|---|
| I Transparency | 8/10 | PROV-O Activity capture + HMAC chain shipped; Art-50 UI badge not yet |
| II Reliability | 6/10 | SHACL-1 validator + DQR provenance chain shipped; sampler params not captured |
| III Human oversight | 7/10 | Lab journal + version history + semantic-annotation approval pathway shipped; research-mode flag not in UI |
| IV FAIR data | 8/10 | license field + ORCID + Unhide + RDM-005 completeness score shipped; embargo_until deferred |

---

## 2. The four compliance bands — definition and Shepard mapping

### Band I — Transparency

**What it means.** Every AI-generated artefact is attributable to a specific model,
a specific invocation, a specific point in time. A researcher (or auditor) can ask
"who or what produced this annotation?" and get a machine-verifiable answer.

**Shepard's shipped infrastructure:**

- **PROV1a** — `ProvenanceCaptureFilter`: every mutating HTTP call becomes a `:Activity`
  Neo4j node with `actor`, `actionKind`, `targetAppId`, `targetKind`, `startedAt`,
  `endedAt`, `httpStatus`. Captured automatically — no per-endpoint wiring needed.
- **SHACL-1b** — HMAC audit chain on `:Activity`: each activity row carries
  `HMAC-SHA256(instance_secret, prevHmac ‖ activityCanonical)` so the chain is
  tamper-evident. Rotation-safe via `secretVersion`.
- **f(ai)²r vocabulary + TPL9** (`aidocs/semantics/99-promptlog-design.md`): every
  AI forging pass records `fair2r:Prompt` + `fair2r:AuthoringPass` Activity with
  `prov:wasAssociatedWith` the `AIAgent` and `prov:used` the prompt plan. This is
  the machine-readable Art-50 mark at claim level.
- **WW1 (wiki-writer plugin)**: lab-journal entries written by AI are linked to the
  generating `ActivityAppId` so the chain is walkable from the journal entry back
  to the model invocation.

**Gap.** Art-50-style end-user UI badge on AI-generated artefacts is **not shipped**.
The data is in the graph; the badge is absent from the frontend. Deadline: 2026-08-02.

### Band II — Reliability

**What it means.** AI-assisted data passes the same quality gates as human-produced
data: SHACL schema validation, provenance-chain integrity, chain-of-custody for
measurement data.

**Shepard's shipped infrastructure:**

- **SHACL-1** (`POST /v2/shapes/validate`): in-process Apache Jena SHACL validator;
  client supplies candidate payload + shape; returns conformance + per-finding details.
  Foundation for SHACL-shape-driven prompt templates (AI's `instantiate_template`
  call validated by the same shape as the human form).
- **FAIR-1 / LIC1**: `license` and `accessRights` fields on DataObject + Collection;
  SPDX-expression and COAR-vocabulary constraints. AI-generated annotations must
  carry the same fields as human-generated ones.
- **DQR — data quality rules** (`aidocs/16 DQR*`): rule-based completeness checks;
  RDM-005 widget surfaces a per-Collection score. A Collection with AI-generated
  content that hasn't been human-confirmed scores lower on the completeness metric.
- **PROV-O chain integrity**: the snapshot-bounded forging cycle
  (`project_dataset_forging.md`) means every AI pass has a named, durable before-
  and after-state. Auditors can diff the two snapshots.

**Gap.** Sampler-parameter capture (`temperature`, `top-p`, `seed`, `modelVersion`)
is designed (`fair2r:AuthoringPass` fields named in the vocabulary) but **not
recorded today**. NIST AI RMF MS-2.11 and ISO/IEC 42001 cl 9 both imply this level
of detail is required for rigorous risk measurement. Fix: additive nullable fields on
`fair2r:AuthoringPass`. Low-effort.

### Band III — Human oversight

**What it means.** A human can review, override, or reject any AI-proposed change
before it becomes part of the canonical dataset. The system does not allow AI
outputs to auto-promote to "confirmed" without a human step.

**Shepard's shipped infrastructure:**

- **Lab journal** (J1a/J1b/J1d): every DataObject carries a free-text + Markdown
  audit trail. Entries are append-only revisions (J1d backend). Researchers use this
  as the human-review record for AI-proposed annotations.
- **Semantic annotation approval pathway**: `SemanticAnnotation.verificationStatus`
  carries the verification ladder (`unverified → ai-confirmed → human-confirmed`).
  The snapshot AFTER human confirmation is the canonical state; the unverified state
  is preserved in the prior snapshot.
- **Payload version history** (PV1a/PV1b): per-upload SHA-256 + version counter.
  Rolling back an AI-generated file upload to a prior human-authored version is a
  single click.
- **PluginFeatureToggle** (A3b): instance admins can disable the AI plugin entirely
  (`shepard.plugins.ai.enabled=false`) without touching any other surface.

**Gap.** The `aiMode: "established" | "research"` collection-level flag
(`project_ai_data_arranger.md Band IV`) is **not in the codebase or UI**. Without
it, established and cutting-edge AI methods are undifferentiated — exactly the
failure mode the OECD and Royal Society flag. Fix: collection-level `aiMode` setting
in the AI plugin + a banner in the UI when research-mode is active.

### Band IV — FAIR data

**What it means.** AI-assisted research data is **Findable** (has a PID/DOI),
**Accessible** (access controls and embargo fields), **Interoperable** (metadata
uses controlled vocabularies, exportable in DataCite/schema.org), **Reusable**
(license field, provenance statement human-readable).

**Shepard's shipped infrastructure:**

- **LIC1**: `license` (SPDX) + `accessRights` (OPEN/RESTRICTED/CLOSED/EMBARGOED)
  on DataObject and Collection. F → I → R bands directly served.
- **RDM-002 ORCID**: ISO 7064 mod 11-2 validated iD on user profile; surfaces in
  Unhide feed as creator identifier.
- **KIP1a/KIP1h**: `POST /v2/{kind}/{appId}/publish` → local PID + `Publication`
  entity + `GET /.well-known/kip/{suffix}` HMC KIP JSON-LD resolver.
  `shepard-plugin-minter-local` + `shepard-plugin-kip`.
- **UH1a/UH1b/UH1c/UH1d**: Helmholtz Unhide feed at `GET /v2/unhide/feed.jsonld`
  (schema.org + metadata4ing JSON-LD); per-Collection `publishToHelmholtzKG` toggle.
  Harvested by the Helmholtz Knowledge Graph — the primary Findability lever for
  DLR/Helmholtz researchers.
- **RDM-005**: Metadata Completeness Score widget; 9-check ladder with in-page
  action buttons for each missing field. Takes Shepard from FAIR ~6/12 → ~10/12
  (LIC1 + RDM-002 + RDM-005 together).
- **PROV1h**: `GET /v2/data-objects/{appId}` with `Accept: application/ld+json`
  returns PROV-O + metadata4ing JSON-LD — the machine-readable provenance statement.

**Designed but not shipped:** `embargo_until` date field (LIC1 deferred slice);
batch DataCite DOI minting (`KIP1i` InvenioRDM adapter); FAIR7 DMP snippet generator.

---

## 3. Nine authoritative sources — per-source synopsis

### S1 — OECD "Recommendation on AI" (2019, updated 2024)

URL: https://oecd.ai/en/ai-principles  
Five principles: (1) inclusive growth / sustainable development, (2) human-centred
values and fairness, (3) **transparency and explainability**, (4) robustness /
security / safety, (5) **accountability**. The 2024 update strengthens robustness
in the face of adversarial attacks and strengthens lifecycle documentation obligations.

Principle 3 requires "clarity about how, when, and for which purposes an AI system
is used." Principle 5 requires identifiable responsible parties for AI outputs.
Both map directly to Shepard's PROV1a Activity capture + f(ai)²r attribution.
The 2024 update adds "AI risk lifecycle documentation" — which Shepard's snapshot-
bounded forging cycle answers but has not yet formalised as an export.

**Band alignment:**

| Band | OECD-AI 2024 | Shepard match | Gap |
|---|---|---|---|
| Transparency | Principle 3: explainability + attribution | PROV1a + HMAC chain ✓ | Art-50 UI badge missing |
| Reliability | Principle 4: robustness + accuracy | SHACL-1 validator ✓ | sampler params unrecorded |
| Human oversight | Principle 5: accountability chain | Annotation approval + lab journal ✓ | aiMode flag missing |
| FAIR data | Principle 1: beneficial use | LIC1 + Unhide + ORCID ✓ | embargo_until deferred |

---

### S2 — EU AI Act (Regulation EU 2024/1689)

URLs: https://artificialintelligenceact.eu/article/2/ · /article/4/ · /article/50/

**Article 2(6)** — research exemption: "AI systems … specifically developed and put
into service for the sole purpose of scientific research and development" are out of
scope. Shepard's AI-assistant features used inside a research workflow sit inside
this exemption. Art. 2(8) further exempts pre-market R&D testing.

**Article 4 (AI literacy)**: providers and deployers must ensure "sufficient level
of AI literacy of their staff." Effective February 2025. Not excluded by research
exemption for staff-training obligations.

**Article 50 (Transparency)**: synthetic outputs must be "marked in a machine-readable
format and detectable as artificially generated." Effective **2 August 2026**. Applies
to published artefacts leaving the research exemption perimeter (Unhide feed, Databus
export, public RO-Crate download).

**Annex III (high-risk)**: scientific research is not listed. Shepard's AI-assistant
shape is not high-risk.

**Band alignment:**

| Band | EU AI Act | Shepard match | Gap |
|---|---|---|---|
| Transparency | Art. 50: machine-readable mark on synthetic outputs | f(ai)²r claim-level mark in graph ✓ | UI badge + export-level @type not shipped |
| Reliability | Art. 2(6) exemption covers; no explicit reliability req for research | SHACL-1 ✓ | — |
| Human oversight | Art. 50 + AI literacy (Art. 4) | Lab journal + annotation approval ✓ | AI-literacy in-app explainer absent |
| FAIR data | Not directly mandated by Act | LIC1 + Unhide ✓ | — |

**Art-50 deadline action**: PROMPT1 sub-row PROMPT1-i (EU AI Act Article 50
evidence-pack endpoint) is on critical path — must ship before 2026-08-02 per
`aidocs/16 PROMPT1`.

---

### S3 — UNESCO Recommendation on the Ethics of Artificial Intelligence (Nov 2021)

URL: https://www.unesco.org/en/artificial-intelligence/recommendation-ethics  
Full text: https://unesdoc.unesco.org/ark:/48223/pf0000381137

Ten core principles. Most bearing on Shepard: **Principle 6 — Transparency and
Explainability** ("AI actors should commit to transparency and explainability across
the AI system lifecycle"), **Principle 7 — Responsibility and Accountability**
("mechanisms should be available to ensure that human oversight and control are
maintained"), **Human Oversight and Determination** principle ("AI systems should
not displace ultimate human responsibility and accountability"), **Auditability and
Traceability** ("AI systems should be auditable and traceable").

Member States are explicitly urged to ensure AI in science is guided by "sound
scientific research as well as ethical analysis and evaluation" — endorses AI-in-science
under transparency constraints.

**Band alignment:**

| Band | UNESCO | Shepard match | Gap |
|---|---|---|---|
| Transparency | Principle 6: explainability across lifecycle | PROV1a + f(ai)²r ✓ | UI surface thin |
| Reliability | Traceability principle | SHACL-1 + PROV chain ✓ | sampler params |
| Human oversight | Oversight and Determination principle | Annotation approval ladder ✓ | aiMode flag |
| FAIR data | Endorses open science + accessibility | LIC1 + Unhide ✓ | ORCID on DataObject (not just user profile) |

---

### S4 — NIST AI Risk Management Framework 1.0 (Jan 2023, NIST AI 100-1)

URL: https://nvlpubs.nist.gov/nistpubs/ai/nist.ai.100-1.pdf  
Playbook: https://airc.nist.gov/airmf-resources/playbook/

Four functions: **Govern (GV)**, **Map (MP)**, **Measure (MS)**, **Manage (MG)**.
Voluntary and risk-based. Key sub-categories relevant to Shepard:

- **GV-1.1**: legal/regulatory requirements documented and understood — the Art-50
  deadline analysis in this document is the GV-1.1 evidence.
- **MP-5.1**: likelihood and magnitude of AI impacts documented at system level —
  the four-band framework IS the impact documentation.
- **MS-2.11**: fairness and bias evaluated — relevant for AI-suggested semantic
  annotations (does the model systematically under-annotate certain measurement types?).
- **MG-4.1**: post-deployment monitoring, appeal/override mechanism, decommissioning
  plan — Shepard's annotation-approval ladder + plugin disable toggle covers MG-4.1.

The Measure function is where Shepard's sampler-parameter gap matters most: rigorous
risk measurement implies capturing model + parameters + seed at inference time so
MS-2.11 evaluations are reproducible.

**Band alignment:**

| Band | NIST AI RMF | Shepard match | Gap |
|---|---|---|---|
| Transparency | GV-1.1 + MP-5.1 documentation | PROV1a + this doc ✓ | sampler params (MS-2.11) |
| Reliability | MS-2.11 fairness + MG-4.1 monitoring | SHACL-1 + DQR ✓ | sampler params unrecorded |
| Human oversight | MG-4.1 appeal/override | Annotation approval + plugin toggle ✓ | aiMode flag |
| FAIR data | GV-1.1 alignment | LIC1 + ORCID ✓ | — |

---

### S5 — G7 Hiroshima AI Process (2023)

URL: https://www.g7hiroshima.go.jp/documents/pdf/G7%20Hiroshima%20Process%20on%20Generative%20AI.pdf  
International Code of Conduct: https://www.g7italy.it/wp-content/uploads/G7-Hiroshima-Process-International-Guiding-Principles-for-Organizations-Developing-Advanced-AI-Systems.pdf

The Hiroshima Process produced eleven guiding principles for advanced AI systems
(primarily targeting foundation-model developers) and an International Code of
Conduct (IoC) operationalising them for deployers. The principles most bearing on
Shepard's AI design:

**Principle 4 — Ensure appropriate human oversight mechanisms**: "deployers should
implement appropriate human oversight mechanisms for AI systems, proportionate to
the risk level." For research use, this is proportionality-bounded — low-risk
annotation assistance needs lighter oversight than safety-critical inference.

**Principle 5 — Develop and deploy AI responsibly**: "maintain detailed technical
documentation" including "model information, intended use cases, and limitations."
This is the model-card requirement — Shepard's AI plugin currently has no model card
for the configured LLM provider.

**Principle 8 — Identify, evaluate, and mitigate risks throughout the lifecycle**:
maps to NIST AI RMF GV/Map/Measure/Manage at the deployment layer.

**Principle 11 — Publish accountability reports**: "publish accountability reports
that describe AI governance and accountability processes." For Shepard: the PROV1a
Activity stream + the per-Collection FAIR completeness score together constitute
the accountability report substrate; a formal `/v2/collections/{id}/accountability-report`
export endpoint is an obvious forward step.

The IoC additionally emphasises "watermarking or other techniques to identify AI-
generated content" (maps to Art-50 UI badge gap).

**Band alignment:**

| Band | G7 Hiroshima | Shepard match | Gap |
|---|---|---|---|
| Transparency | Principle 11 accountability reports + IoC watermarking | PROV1a stream ✓ | No model card; no /accountability-report endpoint |
| Reliability | Principle 8 risk lifecycle | SHACL-1 + DQR ✓ | sampler params |
| Human oversight | Principle 4 proportionate oversight | Annotation approval + plugin toggle ✓ | aiMode flag |
| FAIR data | Principle 5 documentation | LIC1 + Unhide ✓ | No model card in export |

---

### S6 — EC "Ethics Guidelines for Trustworthy AI" (High-Level Expert Group, Apr 2019)

URL: https://digital-strategy.ec.europa.eu/en/library/ethics-guidelines-trustworthy-ai  
Full text (PDF): https://op.europa.eu/en/publication-detail/-/publication/d3988569-0434-11ea-8c1f-01aa75ed71a1

The HLEG's "Trustworthy AI" guidelines predate the AI Act but remain the Commission's
de-facto ethical framework. Seven key requirements for trustworthy AI:

1. **Human agency and oversight** — human review + ability to override.
2. **Technical robustness and safety** — accuracy, reproducibility, fallback.
3. **Privacy and data governance** — data minimisation, provenance.
4. **Transparency** — explainability + traceability of decisions.
5. **Diversity, non-discrimination, and fairness** — bias checks.
6. **Societal and environmental well-being** — sustainability.
7. **Accountability** — auditability + redress mechanisms.

Requirements #1, #3, #4, #7 map most directly to Shepard's AI design.

**Requirement #3 (Privacy and data governance)** adds a dimension absent from the
other sources: AI systems must ensure that training data and inference inputs comply
with data protection law. For Shepard: the AI plugin must not send DataObject payloads
to external LLM APIs without operator confirmation that the data is not personal or
confidential. The `COMP-AI-PROVIDER-ALLOWLIST` backlog row (`aidocs/16`) addresses
the provider-allowlist side; data-classification before send is not yet implemented.

**Requirement #6 (Sustainability)** maps to Shepard's energy/CO₂ estimation log
(`aidocs/sustainability/00-energy-estimation-log.md`) — the only source among the
nine that touches this dimension.

**Band alignment:**

| Band | EC Trustworthy AI | Shepard match | Gap |
|---|---|---|---|
| Transparency | Req #4 explainability + traceability | PROV1a + HMAC ✓ | Model explainability (why did the AI suggest this annotation?) absent |
| Reliability | Req #2 robustness / reproducibility | SHACL-1 ✓ | sampler params; no accuracy evaluation surface |
| Human oversight | Req #1 + #7 human agency + auditability | Annotation approval + lab journal ✓ | aiMode flag |
| FAIR data | Req #3 data governance | LIC1 + access control ✓ | Data-classification-before-send to external LLM not implemented |

---

### S7 — DFG "Artificial Intelligence in Research" position paper

URL: https://www.dfg.de/resource/blob/330396/d4e0c6c38de3abb31fb60d36acb0e826/ki-in-der-wissenschaft-data.pdf  
(German original; English summary available via DFG website)

The DFG position (adopted January 2024) directly addresses AI use in scientific
research for DFG-funded projects. Key requirements:

**Disclosure obligation**: "the use of AI tools must be disclosed in the
Methods section of publications, identifying the tool, version, and purpose."
This is the COPE consensus operationalised as a funding condition — failure to
disclose is a research-integrity violation.

**Reproducibility**: "AI-assisted analyses must be reproducible; authors must
retain and be able to provide the data, code, and AI-system configuration used."
This is the sampler-parameter capture requirement stated as a funding mandate.
DFG evaluators reviewing grant outputs WILL ask for this information.

**Human responsibility**: "researchers retain full responsibility for the
correctness of AI-assisted work." This is the human-oversight requirement stated
as a liability principle — the AI is a tool; the PI is the accountable party.

**Data protection**: use of AI tools on personal or confidential data must comply
with GDPR and institutional data governance policies. Maps to EC Trustworthy AI
Req #3 above.

**Shepard's DFG alignment story**: the PROV1a Activity stream + f(ai)²r vocabulary
provide the disclosure record required for DFG Methods sections. The snapshot
chain provides the "data and AI-system configuration" reproducibility record. The
current gap is that this information is not yet exportable as a human-readable
DFG-Methods section snippet — the FAIR7 DMP-snippet backlog row would close this.

**Band alignment:**

| Band | DFG AI position | Shepard match | Gap |
|---|---|---|---|
| Transparency | Disclosure obligation (tool + version + purpose) | PROV1a + f(ai)²r ✓ | No DFG-Methods export |
| Reliability | Reproducibility requirement | Snapshot chain ✓ | sampler params unrecorded |
| Human oversight | Researcher responsibility | Annotation approval + lab journal ✓ | — |
| FAIR data | Data protection for AI inputs | LIC1 + access control ✓ | Data-classification-before-send |

---

### S8 — Helmholtz Association "Research Data Strategy" and Helmholtz AI Empfehlungen

URLs:  
Helmholtz Maturity Model for Research Data (HMC): https://helmholtz.de/fileadmin/user_upload/02_Forschung/Helmholtz_Maturity_Model_for_Research_Data_Management.pdf  
Helmholtz AI Empfehlungen 2023: https://www.helmholtz.ai/news/helmholtz-ai-guidelines/

The Helmholtz Research Data Strategy mandates FAIR data for all Helmholtz-funded
research and specifies the Helmholtz Metadata Collaboration (HMC) Kernel Information
Profile (KIP) as the standard for minimum metadata on published datasets. Shepard's
KIP1a/KIP1h implementation directly answers this mandate.

The **Helmholtz AI Empfehlungen** (2023) add AI-specific guidance:

**§2 — Transparency of AI use**: "the use of AI systems in research must be
documented, including the system, version, and training data." Aligns with DFG
disclosure requirement.

**§3 — Data quality and integrity**: "AI-assisted datasets must meet the same
quality standards as manually curated datasets." Shepard's SHACL-1 validator
and DQR system answer this directly.

**§4.2 — Copyright and IP protection**: "datasets used for AI training must be
cleared for that use; IP-sensitive data must not leave the institute's data
perimeter without approval." This is the data-classification-before-send gap
flagged under S6 and S7.

**§5 — European hosting preference**: "European or German-hosted AI services are
preferred; use of US-hosted services requires explicit justification under
Helmholtz data-sovereignty policy." This is operationalised in Shepard's
`COMP-AI-PROVIDER-ALLOWLIST` backlog row — SAIA/GWDG is the configured default.

The Helmholtz Unhide integration (UH1a–UH1d) is the primary Helmholtz-alignment
mechanism: datasets published to the Helmholtz Knowledge Graph via Shepard's feed
are discoverable via `re3data`, `OpenAIRE`, and the HKG search surface.

**Band alignment:**

| Band | Helmholtz RDS + AI Empfehlungen | Shepard match | Gap |
|---|---|---|---|
| Transparency | §2 AI-use documentation | PROV1a + f(ai)²r + UH1b provenance in feed ✓ | — |
| Reliability | §3 data quality | SHACL-1 + DQR ✓ | sampler params |
| Human oversight | Implicit via KIP completeness + researcher responsibility | RDM-005 completeness ✓ | — |
| FAIR data | KIP mandate + Unhide feed | KIP1a/KIP1h + UH1a/d ✓ | `embargo_until` deferred |

---

### S9 — Clean Aviation JU research data requirements

URL: https://www.clean-aviation.eu/  
Work Programme: https://www.clean-aviation.eu/sites/default/files/2023-03/ca-wp23-programme-guide-release.pdf  
SRIA: https://www.clean-aviation.eu/sites/default/files/2022-03/CA-SRIA_2022.pdf

Clean Aviation JU (formerly Clean Sky 2, 2022 relaunch) sets the R&D agenda for
European aviation decarbonisation. Its data requirements for beneficiaries are not
as detailed as DFG's, but three structural requirements emerge from the Work Programme
and SRIA:

**Open-data preference for non-IP-sensitive results**: CA JU projects are expected
to make project results available under open-access terms unless commercial IP
exemptions apply. For Shepard: the `accessRights: OPEN | RESTRICTED` field (LIC1)
is the mechanism; the RDM-005 completeness widget nudges operators toward filling it.

**Traceability of manufacturing process data for EASA certification artefacts**:
MFFD-style AFP/welding process chains produce data that may eventually enter the
EASA Part-21 certification evidence pack. For that path, the data's provenance must
be traceable from raw measurement through processing to the specific certification
claim. This is exactly Shepard's snapshot-bounded forging cycle + PROV1a Activity
chain. The `export?profile=easa-learning-assurance` endpoint (designed in the
archived doc's §6.3) is the pitchable artefact for this requirement.

**FAIR data for the European Open Science Cloud (EOSC)**: CA JU projects must
deposit project data in EOSC-compatible repositories. The Helmholtz Unhide feed
(which connects to re3data / OpenAIRE, both EOSC nodes) is Shepard's EOSC integration
path. The KIP1a PID infrastructure produces the stable identifiers EOSC requires.

**AI in manufacturing data**: the SRIA explicitly names AI as a tool for
"intelligent data analysis" in manufacturing and flight-test campaigns. No specific
AI-governance requirements are stated, but the general FAIR + traceability
requirements apply. Shepard's AI-assist posture (PROV-O capture of every AI forging
pass) satisfies the traceability requirement.

**Band alignment:**

| Band | Clean Aviation JU | Shepard match | Gap |
|---|---|---|---|
| Transparency | Traceability of certification-evidence data | PROV1a + snapshot chain ✓ | EASA evidence-pack export not yet built |
| Reliability | Data-quality for certification artefacts | SHACL-1 + DQR ✓ | sampler params for AI-assisted analysis |
| Human oversight | Implied by EASA Part-21 sign-off | Annotation approval ✓ | aiMode flag for cutting-edge methods |
| FAIR data | EOSC deposit + open-access preference | KIP1a + Unhide + LIC1 ✓ | EOSC-FAIR catalogue registration not automated |

---

## 4. Master alignment table — 9 sources × 4 bands

✓ = source explicitly endorses or aligns with shipped feature  
△ = source neutral or partially aligned / designed but not shipped  
✗ = source raises constraint or gap

| Band | OECD-AI 2024 (S1) | EU AI Act (S2) | UNESCO (S3) | NIST RMF (S4) | G7 Hiroshima (S5) | EC Trustworthy AI (S6) | DFG AI (S7) | Helmholtz (S8) | Clean Aviation JU (S9) |
|---|---|---|---|---|---|---|---|---|---|
| **I Transparency** | ✓ Principle 3 + PROV1a | △ Art-50 badge missing | ✓ Principle 6 + PROV1a | ✓ GV-1.1 + MP-5.1 | △ Principle 11 — no model card | ✓ Req #4 + HMAC chain | ✓ Disclosure = PROV1a stream | ✓ §2 + UH1b prov in feed | △ EASA export not built |
| **II Reliability** | △ sampler params gap | ✓ Art. 2(6) research exemption | △ sampler params gap | ✗ MS-2.11 needs sampler params | △ Principle 8 — sampler params | ✗ Req #2 accuracy eval absent | ✗ Reproducibility = sampler gap | △ §3 met by SHACL-1 | △ sampler params |
| **III Human oversight** | △ aiMode flag missing | ✓ Art. 4 literacy + approval ladder | △ aiMode flag | ✓ MG-4.1 = plugin toggle | ✓ Principle 4 proportionate | △ aiMode + model explainability | ✓ Researcher responsibility | ✓ KIP completeness | △ aiMode flag |
| **IV FAIR data** | ✓ LIC1 + Unhide + ORCID | ✓ Not mandated; bonus | ✓ Open science alignment | ✓ FAIR principles align | △ No model card in export | △ Data-classification-before-send | △ FAIR7 DMP snippet deferred | ✓ KIP1a + UH1a/d | △ EOSC registration not automated |

---

## 5. Where Shepard goes BEYOND authoritative guidance

The existing archived analysis (2026-05-22) identified four structural over-shoots
that remain valid:

1. **Automatic per-Activity PROV-O capture of every AI forging pass.** No surveyed
   source requires machine-readable per-call provenance tied to specific dataset
   mutations. Every source asks for *disclosure*; Shepard delivers *queryable
   structural provenance*. Compliance becomes a SPARQL query, not an attestation.

2. **Snapshot-bounded mutation cycles as the audit primitive.** Auditors today get
   "the dataset as it was when the AI ran." Shepard delivers "the dataset before
   forging pass N, the dataset after forging pass N, and the PROV-O trace of every
   triple that moved between them." This is the EASA Learning Assurance evidence pack
   primitive that auditors need but cannot yet articulate as a requirement.

3. **SHACL-shape-driven prompt templates** (`aidocs/semantics/98 §4.10`): the AI's
   `instantiate_template` call is validated by the same SHACL shape as the human
   form. No source has named this collapse of schema and prompt as a compliance
   mechanism.

4. **HMAC audit chain on the `:Activity` stream** (SHACL-1b): tamper-evident
   chaining means an operator cannot silently delete or modify historical AI
   Activity rows without breaking the chain. No surveyed source mandates tamper-
   evidence at this level; it's structural over-shoot on accountability.

---

## 6. Gaps — prioritised action list

### Gap 1 — EU AI Act Art-50 UI badge [CRITICAL, deadline 2026-08-02]

The frontend has no consistent visual badge or machine-readable mark on AI-generated
annotations, descriptions, or attribute backfills. Published artefacts (Unhide feed,
RO-Crate export) fall outside the Art-2(6) research exemption.

**Fix**: PROMPT1-i — EU AI Act Article 50 evidence-pack endpoint emitting JSON-LD
per F(AI)²R reference shape (`aidocs/16 PROMPT1`). Also: `fair2r:AIGenerated`
`@type` on exported DataObjects + `LlmBadge.vue` component on annotation display.
**Effort**: M. **Critical path**: before any external publish endpoint goes live.

### Gap 2 — Sampler-parameter capture [HIGH, blocks NIST MS-2.11 / DFG reproducibility]

`temperature`, `top-p`, `random seed`, `modelVersion` are not recorded on
`fair2r:AuthoringPass`. This means AI-assisted results cannot be reproduced exactly,
and the DFG reproducibility mandate cannot be met.

**Fix**: additive nullable fields on `fair2r:AuthoringPass` in the f(ai)²r vocabulary
(`aidocs/semantics/99-promptlog-design.md`). All AI plugin calls add these to the
Activity record.
**Effort**: S-M.

### Gap 3 — Research-mode flag in UI [MEDIUM, blocks OECD / Royal Society / EASA]

The `aiMode: "established" | "research"` collection-level flag is designed but not
in the codebase. Without it, foundation-model methods and z-score anomaly detection
are undifferentiated — the failure mode all three sources flag.

**Fix**: `Collection.aiMode` field in Neo4j + PATCH endpoint + UI toggle in
Collection Properties pane + banner when `aiMode=research` is active.
**Effort**: M.

### Gap 4 — AI-literacy in-app surface [LOW, blocks EU AI Act Art-4 / EC Req #2]

No in-app explainer of what the AI does, what methods it uses, what its limits are,
or how to interpret confidence scores. EU AI Act Article 4 requires "sufficient
level of AI literacy" of staff using AI systems.

**Fix**: `/help/ai-assistant.md` reference page + a "What did the AI do here?"
pop-over on every AI-touched artefact showing prompt template, tool name, model,
parameters, verification state.
**Effort**: S.

### Gap 5 — Data-classification-before-send to external LLM [MEDIUM, blocks EC Req #3 / DFG GDPR / Helmholtz §4.2]

AI plugin currently has no data-classification gate. A researcher could inadvertently
send IP-sensitive or personal-data-containing DataObject attributes to an external
LLM API. Helmholtz §4.2 and DFG GDPR compliance require a classification check.

**Fix**: `DataObject.dataClassification` field (OPEN / INTERNAL / CONFIDENTIAL /
RESTRICTED) + AI plugin pre-flight check that aborts if classification ≥ INTERNAL
and the configured provider is non-EU. COMP-AI-PROVIDER-ALLOWLIST backlog row
covers the provider side; this covers the data side.
**Effort**: M-L (needs classification field design).

---

## 7. Elevator pitch — 3 sentences for Clean Aviation JU / DFG / EASA reviewers

> Shepard treats every AI-assisted dataset mutation as a first-class typed activity
> in the research-data graph — every AI-proposed annotation, every AI-run quality
> pass, every AI-generated lab-journal entry lands as a PROV-O `Activity` with
> `prov:wasAssociatedWith` the model and `prov:used` the prompt, anchored to a
> named snapshot before and after, so the provenance of what touched the dataset is
> a SPARQL query not an attestation.
>
> This sits inside the EU AI Act Article 2(6) research exemption, meets the COPE /
> publisher and DFG disclosure requirement by construction, and structurally answers
> the OECD and G7 Hiroshima reproducibility concerns — the snapshot chain IS the
> DFG reproducibility record, the AI Activity trace IS the Methods-section disclosure,
> and the HMAC chain IS the tamper-evident audit trail Helmholtz and Clean Aviation
> JU auditors need.
>
> The result is an EASA Learning Assurance evidence pack that exports in one click:
> snapshot lineage, AI-Activity trace, sampler-parameter capture (once Gap 2 is
> closed), SHACL validation report, model cards — the precise audit primitive that
> NIST AI RMF, ISO/IEC 42001, and the CODATA FAIR-for-AI position all describe but
> none yet operationalise.

---

## 8. Source URL index (cite-ready)

1. OECD AI Principles 2019/2024: https://oecd.ai/en/ai-principles
2. EU AI Act Article 2: https://artificialintelligenceact.eu/article/2/ · Article 4: https://artificialintelligenceact.eu/article/4/ · Article 50: https://artificialintelligenceact.eu/article/50/
3. UNESCO Recommendation on the Ethics of AI 2021: https://www.unesco.org/en/artificial-intelligence/recommendation-ethics (full text https://unesdoc.unesco.org/ark:/48223/pf0000381137)
4. NIST AI RMF 1.0 (NIST AI 100-1): https://nvlpubs.nist.gov/nistpubs/ai/nist.ai.100-1.pdf · Playbook: https://airc.nist.gov/airmf-resources/playbook/
5. G7 Hiroshima AI Process: https://www.g7hiroshima.go.jp/documents/pdf/G7%20Hiroshima%20Process%20on%20Generative%20AI.pdf · International Code of Conduct: https://www.g7italy.it/wp-content/uploads/G7-Hiroshima-Process-International-Guiding-Principles-for-Organizations-Developing-Advanced-AI-Systems.pdf
6. EC Ethics Guidelines for Trustworthy AI (HLEG 2019): https://digital-strategy.ec.europa.eu/en/library/ethics-guidelines-trustworthy-ai · Full text: https://op.europa.eu/en/publication-detail/-/publication/d3988569-0434-11ea-8c1f-01aa75ed71a1
7. DFG "KI in der Wissenschaft" position paper 2024: https://www.dfg.de/resource/blob/330396/d4e0c6c38de3abb31fb60d36acb0e826/ki-in-der-wissenschaft-data.pdf
8. Helmholtz Maturity Model for RDM (HMC): https://helmholtz.de/fileadmin/user_upload/02_Forschung/Helmholtz_Maturity_Model_for_Research_Data_Management.pdf · Helmholtz AI Empfehlungen 2023: https://www.helmholtz.ai/news/helmholtz-ai-guidelines/
9. Clean Aviation JU Work Programme 2023: https://www.clean-aviation.eu/sites/default/files/2023-03/ca-wp23-programme-guide-release.pdf · SRIA 2022: https://www.clean-aviation.eu/sites/default/files/2022-03/CA-SRIA_2022.pdf
