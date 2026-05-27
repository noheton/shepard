---
stage: idea
last-stage-change: 2026-05-27
---

# 112 — `shepard-plugin-mfg` design sketch

**Status.** Idea. No implementation exists yet.

**Scope.** `shepard-plugin-mfg` is the manufacturing-domain plugin for
Shepard. It encodes the vocabulary, entity kinds, and REST surface that
aerospace manufacturing users (AFP layup, welding cells, NDT inspection,
MES integration) need beyond what the core data model provides. It is the
natural host for compliance artefacts demanded by DIN EN 9100, ISO AP242,
and the EU Machinery Regulation 2023/1230.

## 1. Motivation

The MFFD use case (DLR ZLP Augsburg AFP + welding line) drives three
requirements that do not belong in core Shepard but recur across every
manufacturing deployment:

1. **Quality status vocabulary** — DRAFT / IN_REVIEW / READY is
   insufficient for shop-floor work. NCR-class statuses (NCR_OPEN,
   REWORK_REQUIRED, INSPECTION_PASS, INSPECTION_FAIL) must be
   first-class, not annotations.
2. **Typed predecessor relationships** — `REWORK_OF`, `REPAIR_OF`,
   `SUBSTANTIAL_MODIFICATION_OF` carry legal weight under EN 9100 and
   EU 2023/1230 Art. 18 (substantial modification triggers a new
   conformity assessment). Generic `PREDECESSOR_OF` loses this
   distinction.
3. **Equipment / machinery entity kind** — calibration certificates,
   EU Declaration of Conformity, instructions for use, and safety-software
   change logs all hang off a machinery entity that today has no
   structural representation in Shepard.

## 2. Plugin identity

| Field | Value |
|---|---|
| Plugin ID | `shepard-plugin-mfg` |
| Maven coordinates (planned) | `de.dlr.shepard.plugins:shepard-plugin-mfg` |
| Compose profile | `mfg` |
| Feature flag | `mfg.enabled` (default: `false`) |
| Admin REST prefix | `/v2/admin/mfg/config` |

## 3. New entity kinds (proposed)

| Kind | Description | Key fields |
|---|---|---|
| `EquipmentItem` | A physical machine or tool in scope of EU 2023/1230 | `serialNumber`, `model`, `annex1Scope` (boolean), `conformityAssessmentModule` |
| `SoftwareRelease` | A firmware / safety-PLC software version | `version`, `releaseDate`, `retentionUntil` (5-year from upload per EHSR 1.1.9) |
| `ConformityDeclaration` | EU Declaration of Conformity per Annex II | `memberState` (ISO 3166-1), `language` (ISO 639-1), `signatoryName`, `signedDate` |
| `NCRRecord` | Non-Conformance Report | `ncrId`, `severity`, `dispositionCode` |

All extend `DataObject` (appId, Predecessor/Successor chain, SemanticAnnotation).

## 4. New predecessor relationship types (proposed)

| Predicate | Meaning | Regulatory trigger |
|---|---|---|
| `REWORK_OF` | Successor is rework of predecessor; same line, same batch | EN 9100 §8.7 |
| `REPAIR_OF` | Successor is repair; may change design baseline | EN 9100 §8.7; potential Art. 18 trigger |
| `SUBSTANTIAL_MODIFICATION_OF` | Successor is substantial modification per EU 2023/1230 Art. 18 | Triggers new conformity assessment |
| `CORRECTIVE_ACTION_FOR` | Successor DataObject is the corrective action resolving predecessor NCR | EN 9100 §10.2 |

## 5. Vocabulary preseed

The plugin ships a migration that adds to the internal semantic
repository (alongside V49):

- `shepard:documentType` — controlled values: `instructions_for_use`,
  `technical_file`, `risk_assessment`, `test_report`,
  `conformity_declaration`, `software_release_note`, `ncr`
- `shepard:documentClass` — controlled values: `eu_type_examination`,
  `module_a_internal_control`, `module_b_type_exam`, `module_g_unit_verification`
- `meta:language` (ISO 639-1 code) — where to attach language metadata
  to comply with Annex VI per-Member-State language obligation
- `meta:memberState` (ISO 3166-1 α-2) — Member State of placement

See `aidocs/semantics/101-canonical-iris.md` for the IRI register.

## 6. Admin config

`:MfgConfig` Neo4j singleton (A3b pattern):

| Field | Default | Purpose |
|---|---|---|
| `retentionWindowSafetyLogYears` | `5` | EHSR 1.1.9 safety-software change log retention |
| `retentionWindowTechnicalFileYears` | `10` | Machinery Regulation Annex IV documentation retention |
| `annex1DefaultScope` | `false` | Presume no Annex I scope unless set per EquipmentItem |

Admin endpoints:
- `GET /v2/admin/mfg/config`
- `PATCH /v2/admin/mfg/config`

## 7. Dependencies

- Core Shepard: `PROV1a` (Activity capture) — for audit trail on status changes
- Core Shepard: `SM1` (storage management) — for retention policy enforcement
- Core Shepard: `TPL1` (SHACL templates) — for EquipmentItem shape validation
- Optional: `shepard-plugin-publisher` — for technical-file export to external auditors

## 8. Open questions

- Should `EquipmentItem` be a sub-type of `DataObject` or a separate
  Neo4j label altogether? Sub-type preserves the appId + annotation
  surface; a separate label is cleaner for querying.
- Should `NCRRecord` live in-plugin or be a core concept? EN 9100
  applies to all DLR manufacturing; making NCR core enables
  cross-collection NCR dashboards without plugin dependency.
- `SUBSTANTIAL_MODIFICATION_OF` predicate needs a legal review: the
  modifier takes on full manufacturer obligations (Art. 18); Shepard
  must not make that determination automatically.

---

## EU Machinery Regulation 2023/1230 compliance opportunities

EU Machinery Regulation 2023/1230 (repealing Machinery Directive
2006/42/EC, applicable from 2027-01-14) introduces new obligations
for documentation traceability and digital risk assessment. Shepard's
data model is a natural host for the compliance artefacts.

Full analysis: `aidocs/agent-findings/eu-machinery-regulation-2023-1230.md`.
The table below summarises the gap/action mapping relevant to
`shepard-plugin-mfg`.

| Regulation requirement | Shepard capability | Gap / action |
|---|---|---|
| Art. 10 — Technical documentation retained 10 years | DataObject + Collection with immutable provenance chain | No gap — PROV-O chain satisfies retention; `retentionWindowTechnicalFileYears` config in `:MfgConfig` adds enforcement |
| Art. 14 — Instructions in digital form, machine-readable | FileReference + structured metadata on instruction documents | Gap: no mandatory `document_type` = `instructions_for_use` annotation; add to ShepardTemplate `EquipmentItem` (T1i) and to the `shepard:documentType` vocabulary preseed (§5 above) |
| Art. 23 — Risk assessment as part of technical documentation | DataObject with `risk_assessment_type` annotation + linked FileReference | Gap: no dedicated RiskAssessment entity; DataObject + SemanticAnnotation is adequate for v1; add `shepard:documentType = risk_assessment` to vocabulary |
| Annex III — Declaration of conformity as digital twin component | FileReference + `doc_type = declaration_of_conformity` annotation | No gap for storage; `ConformityDeclaration` entity kind (§3) makes it queryable rather than an opaque PDF |
| Annex VII — EU type-examination documentation | FileContainer with `document_class = eu_type_examination` attribute | Gap: no machine-readable link between EquipmentItem entity and its EU type cert in Neo4j; `DESCRIBED_BY` edge from EquipmentItem → ConformityDeclaration closes this |
| Art. 50 — Cybersecurity requirements (substantial modification triggers new conformity) | Activity chain records who changed what; `VersionableEntity.revision` tracks changes | Gap: no explicit `modification_type = substantial_modification` predicate; add `SUBSTANTIAL_MODIFICATION_OF` as typed-predecessor kind (§4 above) |

The most impactful v1 action is adding `shepard:documentType` and
`shepard:documentClass` as first-class SemanticAnnotation vocabulary
terms in the MFFD process ontology (`aidocs/semantics/101-canonical-iris.md`)
and pre-seeding them in the V49 bootstrap (or a successor migration in
this plugin).

## Sources

- `aidocs/agent-findings/eu-machinery-regulation-2023-1230.md` — full
  regulatory analysis (stage: `audited-by-personas`, 2026-05-23)
- `aidocs/integrations/92-mffd-real-data-import-strategy.md` — MFFD
  import context
- `aidocs/semantics/95-shacl-templates-and-individuals.md` — TPL1 slice
- EU Machinery Regulation 2023/1230, OJ L 165, 29.6.2023 —
  <https://eur-lex.europa.eu/eli/reg/2023/1230/oj>
