# SHACL shape catalogue

This directory ships the **first concrete SHACL shape catalogue**
for Shepard's ontology-driven UI design (per
`aidocs/semantics/95-shacl-templates-and-individuals.md` and the
upper-ontology alignment in `aidocs/semantics/96`).

The shapes are loaded into the `:SemanticRepository` at startup by
the **V## bootstrap migration**
(`backend/src/main/resources/neo4j/migrations/V##__bootstrap_shacl_shape_catalogue.cypher`,
shipped together with `aidocs/34` row TPL1d / TPL3 / TPL9 / TPL10 /
TPL14 / TPL17 / TPL18). Each `.ttl` file is parsed by Apache Jena
and round-tripped into typed `:Shape` / `:PropertyShape` nodes.

## Files

| File | What it shapes | Implementation slice |
|---|---|---|
| `shepard-core-shapes.ttl` | Collection, DataObject, Container (Timeseries/File/StructuredData) | TPL1 |
| `fair2r-shapes.ttl` | F(AI)²R AI provenance — AuthoringPass, AuditPass, Claim, Prompt, AIAgent | TPL9 |
| `dqr-shapes.ttl` | Data Quality Requirement (EASA DM-02/04/05/07, DA-04) | TPL10 |
| `rep-shapes.ttl` | Regulatory Evidence Pack — LAP + ODD + DQRs + train/val/test + independence proof + sign-off | TPL14 |
| `ledger-anchor-shapes.ttl` | Distributed-ledger anchor (Bloxberg / OpenTimestamps / …) | TPL17 |
| `pipeline-shapes.ttl` | PipelineRun + Task (reproducible compute graph) | TPL18 |
| `mffd-shapes.ttl` | MFFD worked example: MFFDCampaign → ProcessStep → BridgeWelding + CalibrationCertificate + NDTGate | TPL1d |
| `mini-shapes.ttl` | Cross-domain mini-shape library: NCR, SignOff, VerificationActivity | TPL1d |

## Dependency tree

`sh:node` is **inline composition** ("create new child inline");
`sh:class` is **reference existing** ("autocomplete over existing
individuals"). Both shapes ultimately validate the same way at
submission time. Lazy-expansion in the UI prevents cycles at render
time; SHACL itself accepts cyclic data (aidocs/95 §8).

```
mffd:MFFDCampaignShape       (composes via sh:node)
   └─ mffd:ProcessStepShape  (composes via sh:and, sh:node)
        ├─ mffd:NDTGateShape          (sh:node — inline child)
        │     ├─ mffd:raisedNCR  ───►  shepard:NCRShape         (sh:class)
        │     └─ mffd:ndtReport  ───►  shepard:FileContainerShape (sh:class)
        ├─ mffd:CalibrationCertificateShape (sh:class — pick existing)
        │     └─ mffd:certificateDocument ─► shepard:FileContainerShape
        └─ mffd:BridgeWeldingShape    (sh:and ProcessStepShape; adds weld-param slots)

shepard:DQRShape          ──► shepard:DataObjectShape (sh:class targetDataset)
shepard:DQRShape          ──► prov:Activity           (sh:class signOffActivity)

shepard:RegulatoryEvidencePackShape
   ├─ shepard:DQRShape                (sh:class hasDQR, minCount 1)
   ├─ shepard:DataObjectShape         (sh:class train/val/test)
   ├─ prov:Activity                   (sh:class validationActivity + signOffActivity)
   ├─ shepard:LearningAssurancePlan   (sh:class hasLearningAssurancePlan)
   └─ shepard:OperationalDesignDomain (sh:class hasOperationalDesignDomain)

fair2r:ClaimShape         ──► prov:Activity         (no-parentless-claim invariant)
fair2r:AuthoringPassShape ──► fair2r:AIAgentShape   (sh:class)
fair2r:AuthoringPassShape ──► fair2r:PromptShape    (sh:class)

shepard:LedgerAnchorShape ──► prov:Entity           (sh:class anchors)

shepard:PipelineRunShape  ──► shepard:TaskShape     (sh:class hasTask)
shepard:PipelineRunShape  ──► shepard:StructuredDataContainerShape (sh:class manifest)
shepard:TaskShape         ──► shepard:TaskShape    (sh:class dependsOn — self-reference)

shepard:SignOffShape, shepard:NCRShape,
shepard:VerificationActivityShape   — mini-shapes consumed by
                                      the domain shapes above
```

## Loading

The shapes are loaded by `SemanticShapeLoader` (TPL1a) as part of
`MigrationsRunner` startup:

```
classpath:/shapes/*.ttl  →  Apache Jena RDFParser  →  :SemanticRepository
                                                   →  :Shape / :PropertyShape nodes
                                                   →  shape cache (5 ms cold / <0.1 ms hot)
```

Idempotency is per-shape-IRI; re-running the migration replaces a
shape only when the inbound `dcterms:hasVersion` is higher than the
stored value (aidocs/95 §8). Instances pinned to the old version
keep validating against it until an admin runs the explicit
"template updated → migrate" action.

## Conventions

- **Namespaces** — `shepard:` for `http://semantics.dlr.de/shepard-upper#`,
  `mffd:` for `http://semantics.dlr.de/mffd-process#`, `fair2r:` for
  `https://noheton.org/f-ai-r/ns#`. Standards: `bfo:`, `iao:`,
  `iof:`, `prov:`, `qudt:`, `dcterms:`, `sh:`, `xsd:`, `owl:`,
  `rdfs:`.
- **Versioning** — every shape carries `dcterms:hasVersion "1.0.0"`.
  Bumps follow semver: PATCH for cosmetic changes (rdfs:label),
  MINOR for additive properties, MAJOR for any tightening of an
  existing constraint.
- **sh:message** — every property with a non-obvious constraint
  carries a human-readable error string surfaced by the form
  renderer (TPL1c).
- **sh:order** — drives column order in list views, field order in
  forms, and tab order in detail views (aidocs/95 Part 2).
- **Enums** — `sh:in (...)` for closed vocabularies (ledger names,
  NDT results, lifecycle statuses, validation outcomes).
- **Numeric bounds** — `sh:datatype xsd:float` + `sh:minInclusive` /
  `sh:maxInclusive`. The MFFD `BridgeWeldingShape` bounds mirror
  the `mffd:Constraint` triples in
  `examples/mffd-showcase/ontology/mffd-process.ttl §4`.

## Validation

To smoke-test the catalogue locally (Apache Jena 4.x):

```sh
riot --validate backend/src/main/resources/shapes/*.ttl
shacl validate --shapes backend/src/main/resources/shapes/*.ttl \
               --data  backend/src/main/resources/shapes/*.ttl
```

The catalogue is self-consistent: each shape's `sh:targetClass`
resolves either to a class declared in the same file or to one
already loaded from the upper ontology / `mffd-process.ttl`.

## See also

- `aidocs/semantics/95-shacl-templates-and-individuals.md` —
  architecture; Parts 1, 4, 5, 6, 7, 14e (F(AI)²R), 14f
  (ledger-anchor), 15 (slices)
- `aidocs/semantics/96-upper-ontology-alignment.md` — BFO 2020 +
  IOF Core + IAO + PROV-O alignment that these shapes sit on
- `examples/mffd-showcase/ontology/mffd-process.ttl` — the MFFD
  domain ontology these MFFD shapes reference
- `aidocs/agent-findings/easa-data-management-learning-assurance.md`
  — DQR / REP design grounding
- `aidocs/agent-findings/eu-machinery-regulation-2023-1230.md` —
  retention windows + technical-file content driving the REP shape
- `aidocs/agent-findings/industrial-robotics-ontology-audit.md` —
  CORA-as-SKOS, MSEO welding link informing mffd:Equipment +
  mffd:hasUnit choices
