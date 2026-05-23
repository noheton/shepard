---
stage: idea
last-stage-change: 2026-05-23
name: TPL3 — Upper-ontology bootstrap migration + starter kit
description: Implementation design for TPL3 (M1 milestone of the Shapes-as-Templates family). Specifies the V64 Cypher bootstrap migration seeding BFO 2020 + IOF Core + IAO + EMMO Core + CHAMEO + MSEO + m4i + f(ai)²r + PROV-O + dcterms + schema.org SemanticRepository nodes, plus a five-template instance starter kit (Measurement, Sample, ProcessStep, Document, Dataset). Companion to aidocs/semantics/96 (upper-ontology alignment design) and aidocs/semantics/95 (SHACL templates and individuals); preconditions for TPL4, TPL5, TPL10.
---

# 97 — TPL3 — Upper-ontology bootstrap migration + starter kit

**Status.** Design (`stage: idea`). Companion to `aidocs/semantics/96`
(upper-ontology alignment) and `aidocs/semantics/95` (SHACL templates
and individuals).
**Audience.** Contributors implementing the V64 Cypher migration and
the five starter shapes; operators activating the upper-ontology
substrate on an existing instance; reviewers cross-checking that the
plugin-first and operator-knob rules from `CLAUDE.md` are honoured.
**Snapshot date.** 2026-05-23.
**Position in the M1 milestone family.** TPL3 is the **bootstrap
prerequisite** for the rest of the Shapes-as-Templates programme:
TPL4 (backward-compat dual-write attribute promotion), TPL5
(git-based shape ingestion), TPL9 (f(ai)²r AI provenance triples),
TPL10 (DataQualityRequirement as first-class record) and TPL11
(independence-proof Cypher) all depend on the upper-ontology
substrate being present and the canonical `m4i:` / `prov:` /
`shepard-upper:` predicates being usable from day one.

This doc *implements* §8 of `aidocs/semantics/96` (the TPL3a–TPL3g
slice plan). Read aidocs/96 first; this doc fleshes out the
mechanism, the Cypher, the starter-kit shapes, and the operator
runbook.

---

## §1 — Goal

Bootstrap the Shepard semantic substrate with an **upper-ontology
alignment** so every domain ontology subsequently authored against
Shepard (CHAMEO, Material OWL, m4i, f(ai)²r, MFFD process, PLUTO
mission) hangs off a consistent foundation. Plus ship a
**five-template starter kit** a researcher can use immediately to
create their first valid DataObject without authoring any ontology.

Two complementary outcomes, one migration:

1. **Substrate layer** — ten `:SemanticRepository` nodes registered
   (one per ontology family), plus a small set of seeded
   subsumption edges that wire `shepard-upper:` into BFO + IAO + IOF
   Core + PROV-O.
2. **Researcher-facing layer** — five SHACL shapes loadable via the
   shape registry (`Measurement`, `Sample`, `ProcessStep`,
   `Document`, `Dataset`) so a researcher hitting Shepard for the
   first time has a non-empty `/v2/shapes` to pick from and can
   author a valid `Measurement` DataObject in under ten minutes
   without writing TTL.

The substrate is the **unblock**; the starter kit is the **demo**.
Both ship together because the substrate without the starter kit
gives the researcher nothing to click, and the starter kit without
the substrate has nowhere to attach.

**Researcher promise:** *first valid Measurement DataObject in under
ten minutes, no ontology authoring required.* This is the §6
acceptance criterion.

**Plugin-first justification.** Per `CLAUDE.md "always think
plugin-first"`: this lands **in-tree**, not as a plugin, because it
is the **SHACL substrate's bootstrap data** — the shape of every
class every plugin will subsequently extend. It falls under the
`§3 "in-tree by necessity"` exception (identity primitives every
plugin compiles against). Domain-specific ontologies (CPACS-OWL,
robotics ontologies, AAS submodel templates) stay plugin-shaped per
the §2 rule, but their parent class chain anchors here.

---

## §2 — Reuse-before-reimplement (per CLAUDE.md rule)

Per the `feedback_reuse_before_reimplement.md` memory rule, every
component is surveyed for an adopt-as-library / sidecar / build-own
verdict before any Cypher gets written. This section is the survey;
no component below is re-invented.

### 2.1 Upper ontology

| Component | Verdict | Justification |
|---|---|---|
| **BFO 2020** (`bfo:`) | **Adopt** — load as inline Turtle into n10s, IRI `http://purl.obolibrary.org/obo/bfo.owl` | OBO Foundry release; ISO/IEC 21838-2:2021; ~700 citations; 30 contributors; foundation of OBO + IOF (the two communities Shepard touches). See `aidocs/semantics/96 §2.1`. ROBOT-merged release is the right artefact to seed (not from-scratch axioms). |
| **IAO** (`iao:`) | **Adopt** — load as inline Turtle | OBO Foundry-curated; ~500 citations. Names the information-artefact classes Shepard actually stores (DataItem, DocumentPart, Plan). See `aidocs/semantics/96 §2.3`. |
| **IOF Core** (`iof:`) | **Adopt** — load as inline Turtle | NIST + Boeing + Autodesk + BAM backing; ~10 dependent ontologies; bridges BFO into manufacturing. See `aidocs/semantics/96 §2.2`. |
| **PROV-O** (`prov:`) | **Already adopted** — V49 seeded the predicates | N1b shipped this. The bootstrap only adds the explicit `prov:Activity rdfs:subClassOf bfo:Occurrent` axiom. |
| **EMMO Core (small)** (`emmo:`) | **Adopt small subset** — `~150` axioms | Contested in the materials community (`aidocs/semantics/96 §6.2`); seed the **Core** module only, not full ~10k-class EMMO. CC-BY-4.0. |

### 2.2 Domain bridges (seeded alongside, not in `shepard-upper:`)

| Component | Verdict | Justification |
|---|---|---|
| **CHAMEO** (`chameo:`) | **Adopt** — official Turtle release | Characterisation Methodology Ontology under EMMO umbrella; ~15 citations; relevant for MFFD defect characterisation. See `aidocs/semantics/96 §2.5`. |
| **MSEO** (`mseo:`) | **Adopt** — Mat-O-Lab / BAM + Fraunhofer release | Materials Science and Engineering Ontology; active maintenance; bridges materials-domain through IOF Core. |
| **m4i (metadata4ing)** (`m4i:`) | **Adopt v1.4.0** — already pinned by `aidocs/semantics/94` | NFDI4Ing; engineering process modelling. **Critical correctness gate**: the M4I-b finding (memory `feedback_model_inventory_maintained.md`) — Shepard's `ProvJsonLdRenderer` currently emits non-canonical `m4i:hasMethod`/`hasInput`/`hasOutput`. **The TPL3 bootstrap MUST seed and use the canonical predicates `m4i:realizesMethod`, `obo:RO_0002233` (has-input), `obo:RO_0002234` (has-output)**, not the non-canonical placeholders. M4I-b ships in the same window. |
| **f(ai)²r** (`f-ai-r:`) | **Adopt** — vendor ontology per `feedback_fair2r_integration` | github.com/noheton/f-ai-r vocabulary; every AI interaction = typed PROV-O Activity. Already integrated by V61. Bootstrap only adds the SemanticRepository node + explicit alignment to `prov:Activity`. |
| **DCAT 3** + **DataCite Kernel** + **schema.org subset** | **Adopt** — load as inline subset Turtle | Required by `dcat:Dataset`, `schema:Dataset` parents of the starter-kit Dataset shape. Subset only — schema.org full graph is 2k+ classes and unnecessary. |
| **Dublin Core Terms** (`dcterms:`) | **Already adopted** by N1b | PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL bundled at `backend/src/main/resources/ontologies/` per N1b. Bootstrap only adds the explicit SemanticRepository node so it shows up in `GET /v2/admin/semantic-repos` as a citable repo. |
| **QUDT** + **OM-2** | **Already adopted** by N1b | Unit + quantity vocabulary. The Measurement starter shape's `unit` slot references QUDT IRIs. |

### 2.3 Tooling — not reinvented

| Tool | Verdict |
|---|---|
| **n10s** (neosemantics) | **Already adopted** — N1a shipped. All ontology load goes through `n10s.rdf.import.inline` per N1b's pattern. |
| **OntologySeedService** | **Reuse** — N1b's classpath-read-and-import pattern is the load mechanism. TPL3a extends the manifest with the new bundles; it does not introduce a parallel loader. |
| **MigrationsRunner** | **Reuse** — fail-fast + idempotent semantics (post-A1e) already match the bootstrap requirement. No new migration framework. |
| **SHACL validation** | **Reuse** — task #127 (SHACL changeover PRs 2/5/6-8/9) is landing the validation runtime. TPL3 ships the shapes, not a new validator. |

**Net BUILD-OWN scope:** the alignment bridge axioms in
`shepard-upper:` (the ~50-line mapping wiring `shepard:Collection` →
`iao:DataSet` etc.) and the five SHACL starter shapes. Everything
else is adopt-as-library.

---

## §3 — Bootstrap migration (`V64__Bootstrap_upper_ontology.cypher`)

**Migration number.** V64. V60 was the SHACL placeholder NOOP; V61
the v15 PROV predicates; V62 is an intentional gap (skipped); V63
the legacy v1 bootstrap. The next-after-max is V64 (per the repo
convention: never fill gaps, always append).

**Path.** `backend/src/main/resources/neo4j/migrations/V64__Bootstrap_upper_ontology.cypher`

**Rollback file.** `backend/src/main/resources/neo4j/migrations/V64_R__Bootstrap_upper_ontology.cypher`
(per the V61_R / V14_R idiom — `_R__<descriptive>` is the established
shape).

**Idempotency.** All `MERGE` on `(:SemanticRepository {appId})` plus
`ON CREATE SET` for the immutable fields. Re-runs are no-ops.
Subsumption edges use `MERGE` on `(parent)-[:rdfsSubClassOf]->(child)`.

**Fail-fast.** Migration throws on any Cypher exception per
`MigrationsRunner` post-A1e semantics; the `MigrationsException`
propagates and aborts startup. No silent skip.

**Endpoint URL shape (IMPORT-SR2 lesson learned).** v15.18 IMPORT-SR2
discovered the backend `SemanticRepositoryValidator` rejects
`urn:`-shaped endpoints, requiring `http://` or `https://`. The
bootstrap writes **canonical IRIs** because Cypher writes go through
the OGM session directly, not the REST validator. The asymmetry is
intentional: REST callers get URL-normalisation (`https://noheton.org/shepard/repo/<name>`);
backend bootstrap uses the canonical IRI (`http://purl.obolibrary.org/obo/bfo.owl`).
Documenting it here closes the surprise for the operator who diffs
the migration against what the REST endpoint accepts.

### 3.1 SemanticRepository nodes (ten)

The migration MERGEs ten `:SemanticRepository` nodes, one per
ontology family. Each gets a stable `appId` (UUID v7 deterministic
where possible — the migration uses `randomUUID()` for the first
seeding then idempotent MERGE on `name`+`type`):

```cypher
// V64__Bootstrap_upper_ontology.cypher
// TPL3a — register the upper-ontology + domain-bridge SemanticRepositories.
// Companion: aidocs/semantics/96 + aidocs/semantics/97.
//
// Idempotency: MERGE on (name, type) leaves an admin-customised endpoint alone.
// Fail-fast: bare Cypher; any error propagates MigrationsException.
//
// Rollback: V64_R__Bootstrap_upper_ontology.cypher.

// ---- 1. Upper ontology ----
MERGE (bfo:SemanticRepository {name: 'BFO 2020', type: 'EXTERNAL'})
ON CREATE SET
  bfo:BasicEntity,
  bfo.appId    = randomUUID(),
  bfo.endpoint = 'http://purl.obolibrary.org/obo/bfo.owl',
  bfo.deleted  = false,
  bfo.createdAt= timestamp(),
  bfo.iri      = 'http://purl.obolibrary.org/obo/bfo.owl',
  bfo.tier     = 'upper',
  bfo.bundled  = true;

MERGE (iao:SemanticRepository {name: 'IAO', type: 'EXTERNAL'})
ON CREATE SET
  iao:BasicEntity,
  iao.appId    = randomUUID(),
  iao.endpoint = 'http://purl.obolibrary.org/obo/iao.owl',
  iao.deleted  = false,
  iao.createdAt= timestamp(),
  iao.iri      = 'http://purl.obolibrary.org/obo/iao.owl',
  iao.tier     = 'upper',
  iao.bundled  = true;

MERGE (iof:SemanticRepository {name: 'IOF Core', type: 'EXTERNAL'})
ON CREATE SET
  iof:BasicEntity,
  iof.appId    = randomUUID(),
  iof.endpoint = 'https://www.industrialontologies.org/ontology/core/Core/',
  iof.deleted  = false,
  iof.createdAt= timestamp(),
  iof.iri      = 'https://www.industrialontologies.org/ontology/core/Core/',
  iof.tier     = 'upper',
  iof.bundled  = true;

MERGE (emmo:SemanticRepository {name: 'EMMO Core (small)', type: 'EXTERNAL'})
ON CREATE SET
  emmo:BasicEntity,
  emmo.appId    = randomUUID(),
  emmo.endpoint = 'https://emmo-repo.github.io/EMMO/emmo-core.ttl',
  emmo.deleted  = false,
  emmo.createdAt= timestamp(),
  emmo.iri      = 'https://emmo-repo.github.io/EMMO/emmo-core',
  emmo.tier     = 'upper',
  emmo.bundled  = true;

// ---- 2. Domain bridges ----
MERGE (chameo:SemanticRepository {name: 'CHAMEO', type: 'EXTERNAL'})
ON CREATE SET
  chameo:BasicEntity,
  chameo.appId    = randomUUID(),
  chameo.endpoint = 'https://emmo-repo.github.io/domain-characterisation-methodology/chameo.ttl',
  chameo.deleted  = false,
  chameo.createdAt= timestamp(),
  chameo.iri      = 'https://emmo-repo.github.io/domain-characterisation-methodology/',
  chameo.tier     = 'domain',
  chameo.bundled  = true;

MERGE (mseo:SemanticRepository {name: 'Material OWL (MSEO)', type: 'EXTERNAL'})
ON CREATE SET
  mseo:BasicEntity,
  mseo.appId    = randomUUID(),
  mseo.endpoint = 'https://purl.matolab.org/mseo/mid',
  mseo.deleted  = false,
  mseo.createdAt= timestamp(),
  mseo.iri      = 'https://purl.matolab.org/mseo/mid',
  mseo.tier     = 'domain',
  mseo.bundled  = true;

MERGE (m4i:SemanticRepository {name: 'metadata4ing (m4i)', type: 'EXTERNAL'})
ON CREATE SET
  m4i:BasicEntity,
  m4i.appId    = randomUUID(),
  m4i.endpoint = 'https://w3id.org/nfdi4ing/metadata4ing/1.4.0/',
  m4i.deleted  = false,
  m4i.createdAt= timestamp(),
  m4i.iri      = 'https://w3id.org/nfdi4ing/metadata4ing/1.4.0/',
  m4i.tier     = 'domain',
  m4i.bundled  = true,
  m4i.version  = '1.4.0';

MERGE (fair2r:SemanticRepository {name: 'f(ai)²r', type: 'EXTERNAL'})
ON CREATE SET
  fair2r:BasicEntity,
  fair2r.appId    = randomUUID(),
  fair2r.endpoint = 'https://github.com/noheton/f-ai-r',
  fair2r.deleted  = false,
  fair2r.createdAt= timestamp(),
  fair2r.iri      = 'https://noheton.org/f-ai-r#',
  fair2r.tier     = 'domain',
  fair2r.bundled  = true;

// ---- 3. Pre-existing (N1b) bridges — register as repo nodes so the
//      admin REST surface lists them; the actual triples were loaded
//      by OntologySeedService in N1b. MERGE on name avoids duplicates.
MERGE (prov:SemanticRepository {name: 'PROV-O', type: 'EXTERNAL'})
ON CREATE SET
  prov:BasicEntity,
  prov.appId    = randomUUID(),
  prov.endpoint = 'http://www.w3.org/ns/prov-o',
  prov.deleted  = false,
  prov.createdAt= timestamp(),
  prov.iri      = 'http://www.w3.org/ns/prov#',
  prov.tier     = 'upper',
  prov.bundled  = true;

MERGE (dct:SemanticRepository {name: 'Dublin Core Terms', type: 'EXTERNAL'})
ON CREATE SET
  dct:BasicEntity,
  dct.appId    = randomUUID(),
  dct.endpoint = 'http://purl.org/dc/terms/',
  dct.deleted  = false,
  dct.createdAt= timestamp(),
  dct.iri      = 'http://purl.org/dc/terms/',
  dct.tier     = 'domain',
  dct.bundled  = true;

// (schema.org subset is bundled by N1b; same MERGE-on-name pattern omitted
// for brevity but present in the actual migration.)
```

### 3.2 `shepard-upper:` alignment axioms

The actual axioms live in a Turtle file
(`backend/src/main/resources/ontologies/shepard-upper.ttl`) that
`OntologySeedService` imports via `n10s.rdf.import.inline`. The
migration's job is to *register the bundle* (the Turtle file ships
with the JAR per N1b); the Java-side seeding hook does the load on
startup. The ~50 axioms are exactly those listed in
`aidocs/semantics/96 §3.1` — not duplicated here.

### 3.3 Operator runtime knob (per CLAUDE.md "always surface operator knobs")

The honest call: **`shepard.semantic.bootstrap.*` is deploy-time
only**. Per the CLAUDE.md exception list, it falls under
*"pre-startup ordering invariants"* — the bootstrap runs before the
backend's REST surface is up, so a runtime PATCH endpoint cannot
modify what has already been MERGEd into Neo4j.

What CAN be runtime-mutable (and SHOULD be per the rule) is the
**post-bootstrap admin layer**: the existing N1c2 admin endpoint
`/v2/admin/semantic/ontologies` already lets an operator disable a
seeded ontology at runtime (`POST .../disable` flips an `enabled`
flag the n10s connector consults). TPL3 adds **no new admin REST
endpoint**: the ten new bundles slot into N1c2's existing list and
are managed identically. Deploy-time `shepard.semantic.preseed-ontologies.skip-bundles`
(N1b, already shipped) is the skip knob — set
`shepard.semantic.preseed-ontologies.skip-bundles=emmo,chameo` to
skip just those two on a memory-constrained instance.

A `:BootstrapConfig` singleton in the A3b shape was considered and
**rejected**: nothing about bootstrap is mutable after first run.
The fail-fast Cypher migration is the canonical ordering invariant;
adding a runtime knob over the top is gold-plating.

### 3.4 Rollback file

`V64_R__Bootstrap_upper_ontology.cypher` deletes the ten registered
SemanticRepository nodes and the subsumption edges. It does **not**
remove the loaded Turtle triples from n10s — that requires a separate
`CALL n10s.graphconfig.dropConfig()` + re-import, which is destructive
and operator-driven (the runbook covers it). The rollback file is
idempotent (`DETACH DELETE` is a no-op on missing rows):

```cypher
// V64_R__Bootstrap_upper_ontology.cypher
// Rollback for TPL3a. Removes the ten SemanticRepository registrations
// only; does NOT drop n10s triples (operator decision — see runbook §5).
MATCH (r:SemanticRepository)
WHERE r.tier IN ['upper', 'domain']
  AND r.bundled = true
  AND r.name IN [
    'BFO 2020','IAO','IOF Core','EMMO Core (small)',
    'CHAMEO','Material OWL (MSEO)','metadata4ing (m4i)',
    'f(ai)²r','PROV-O','Dublin Core Terms'
  ]
DETACH DELETE r;
```

---

## §4 — Starter-kit shape templates (five)

Five SHACL shapes ship at `backend/src/main/resources/shapes/`,
loaded into the shape registry at startup (the existing TPL1
infrastructure). Each shape:

- Extends a `shepard-upper:` root (per `aidocs/semantics/96 §3.2`)
- Has at most six mandatory slots (basic-mode friendly)
- Carries `sh:order` + `sh:group` so the form renderer renders
  predictably (per `aidocs/semantics/95 Part 2`)
- Includes one worked example DataObject instance committed as a
  test fixture

The five (matched to the task spec; relationship to `aidocs/96 §7`
clarified at the end of this section):

### 4.1 `Measurement` — single timestamped numeric observation

Extends `m4i:Measurement` + `qudt:QuantityValue`.

**Reuse note.** m4i v1.4.0 defines `m4i:Measurement` as a subclass
of `prov:Entity`; this shape adds the `qudt:QuantityValue`
constraint so units are mandatory. The predicate **must be
`m4i:realizesMethod`** (canonical per M4I-b finding), not the
non-canonical `m4i:hasMethod` Shepard's `ProvJsonLdRenderer`
historically emitted. M4I-b ships in the same window.

```turtle
@prefix sh:      <http://www.w3.org/ns/shacl#> .
@prefix shepard: <http://semantics.dlr.de/shepard-upper#> .
@prefix m4i:     <http://w3id.org/nfdi4ing/metadata4ing#> .
@prefix qudt:    <http://qudt.org/schema/qudt/> .
@prefix obo:     <http://purl.obolibrary.org/obo/> .

shepard:MeasurementShape  a sh:NodeShape ;
    sh:targetClass m4i:Measurement ;
    rdfs:subClassOf shepard:DataObject ;
    rdfs:label "Measurement"@en ;
    rdfs:label "Messung"@de ;
    sh:property [
        sh:path m4i:realizesMethod ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:nodeKind sh:IRI ;
        sh:order 1 ; sh:group shepard:BasicGroup ;
    ] ;
    sh:property [
        sh:path qudt:numericValue ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:datatype xsd:double ;
        sh:order 2 ; sh:group shepard:BasicGroup ;
    ] ;
    sh:property [
        sh:path qudt:hasUnit ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:nodeKind sh:IRI ;
        sh:order 3 ; sh:group shepard:BasicGroup ;
    ] ;
    sh:property [
        sh:path prov:generatedAtTime ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:datatype xsd:dateTime ;
        sh:order 4 ; sh:group shepard:BasicGroup ;
    ] .
```

**Example DataObject instance** (worked):

```json
{
  "name": "Vibration peak — TR-004",
  "shape": "shepard:MeasurementShape",
  "attributes": {
    "m4i:realizesMethod": "http://semantics.dlr.de/mffd-process#AccelerometerMeasurement",
    "qudt:numericValue": 12.4,
    "qudt:hasUnit": "http://qudt.org/vocab/unit/G",
    "prov:generatedAtTime": "2024-06-02T14:32:08.000Z"
  }
}
```

**Recommended attribute keys:** `m4i:realizesMethod`, `qudt:numericValue`,
`qudt:hasUnit`, `prov:generatedAtTime`, optionally
`m4i:hasMeasurementUncertainty`, `prov:wasAssociatedWith`.

**UI rendering hint:** form (per `aidocs/95 §2`); the unit slot
renders as an autocomplete picker against the QUDT SemanticRepository.

### 4.2 `Sample` — physical specimen with provenance

Extends `chameo:Sample` + `bfo:MaterialEntity`.

```turtle
shepard:SampleShape  a sh:NodeShape ;
    sh:targetClass chameo:Sample ;
    rdfs:subClassOf shepard:DataObject ;
    rdfs:label "Sample"@en ;
    rdfs:label "Probe"@de ;
    sh:property [ sh:path dcterms:identifier ;  sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ; sh:order 1 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path iof:hasMaterial ;     sh:minCount 1 ; sh:nodeKind sh:IRI ; sh:order 2 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path prov:wasDerivedFrom ; sh:nodeKind sh:IRI ; sh:order 3 ; sh:group shepard:AdvancedGroup ] ;
    sh:property [ sh:path dcterms:created ;     sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:dateTime ; sh:order 4 ; sh:group shepard:BasicGroup ] .
```

**Example:** `{ "name": "CF-LMPAEK batch 21 coupon A", "shape": "shepard:SampleShape", "attributes": { "dcterms:identifier": "MFFD-CPN-21-A", "iof:hasMaterial": "http://semantics.dlr.de/mffd-materials#cf-lmpaek", "dcterms:created": "2024-05-12T09:00:00Z" } }`

**UI rendering hint:** form. The material IRI slot renders against
the Material OWL SemanticRepository.

### 4.3 `ProcessStep` — manufacturing / experimental step

Extends `m4i:ProcessingStep` + `bfo:Process`.

```turtle
shepard:ProcessStepShape  a sh:NodeShape ;
    sh:targetClass m4i:ProcessingStep ;
    rdfs:subClassOf shepard:DataObject ;
    rdfs:label "Process step"@en ;
    rdfs:label "Prozessschritt"@de ;
    sh:property [ sh:path rdfs:label ;          sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ;     sh:order 1 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path obo:RO_0002233 ;      sh:nodeKind sh:IRI ; sh:order 2 ; sh:group shepard:BasicGroup ] ;   # has input (canonical m4i)
    sh:property [ sh:path obo:RO_0002234 ;      sh:nodeKind sh:IRI ; sh:order 3 ; sh:group shepard:BasicGroup ] ;   # has output
    sh:property [ sh:path m4i:realizesMethod ;  sh:minCount 1 ; sh:maxCount 1 ; sh:nodeKind sh:IRI ; sh:order 4 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path prov:startedAtTime ;  sh:datatype xsd:dateTime ; sh:order 5 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path prov:endedAtTime ;    sh:datatype xsd:dateTime ; sh:order 6 ; sh:group shepard:BasicGroup ] .
```

**Recommended attribute keys** include `prov:wasAssociatedWith`
(operator), `iof:Equipment` reference. **UI rendering hint:** form;
inputs / outputs render as multi-select against the Collection's
existing DataObjects (per `aidocs/95 §5` `sh:node`).

**Note on canonical predicates.** `obo:RO_0002233`/`RO_0002234` are
the OBO Relations Ontology canonical IRIs that m4i v1.4.0 references
(per `aidocs/semantics/94` M4I-b finding). Authoring SHACL against
these IRIs ensures the data validates against external m4i consumers
without renaming.

### 4.4 `Document` — file artefact with author + license

Extends `fabio:Document` + `iao:Document` + `dcterms:`.

```turtle
shepard:DocumentShape  a sh:NodeShape ;
    sh:targetClass iao:Document ;
    rdfs:subClassOf shepard:DataObject ;
    rdfs:label "Document"@en ;
    rdfs:label "Dokument"@de ;
    sh:property [ sh:path dcterms:title ;       sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ; sh:order 1 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:creator ;     sh:minCount 1 ;                 sh:nodeKind sh:IRI ;     sh:order 2 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:license ;     sh:minCount 1 ; sh:maxCount 1 ; sh:nodeKind sh:IRI ;     sh:order 3 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:issued ;      sh:datatype xsd:dateTime ;                                sh:order 4 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:format ;      sh:datatype xsd:string ;                                  sh:order 5 ; sh:group shepard:AdvancedGroup ] .
```

The `dcterms:license` slot pairs with the LIC1 backlog item (license
field on DataObject). Until LIC1 ships, the renderer picks a sensible
default (CC-BY-4.0) and surfaces it as an editable field.

**UI rendering hint:** form. The license picker renders against an
SPDX SemanticRepository (TPL3 does not seed SPDX — that's LIC1's
job; until then, freetext-with-suggestion).

### 4.5 `Dataset` — collection of related observations

Extends `dcat:Dataset` + `schema:Dataset`.

```turtle
shepard:DatasetShape  a sh:NodeShape ;
    sh:targetClass dcat:Dataset ;
    rdfs:subClassOf shepard:DataObject ;
    rdfs:label "Dataset"@en ;
    rdfs:label "Datensatz"@de ;
    sh:property [ sh:path dcterms:title ;        sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ; sh:order 1 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:description ;  sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ; sh:order 2 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcat:distribution ;    sh:nodeKind sh:IRI ;                                     sh:order 3 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:creator ;      sh:minCount 1 ; sh:nodeKind sh:IRI ;                     sh:order 4 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcat:keyword ;         sh:datatype xsd:string ;                                  sh:order 5 ; sh:group shepard:BasicGroup ] ;
    sh:property [ sh:path dcterms:license ;      sh:minCount 1 ; sh:maxCount 1 ; sh:nodeKind sh:IRI ;     sh:order 6 ; sh:group shepard:BasicGroup ] .
```

**UI rendering hint:** detail-page view (per `aidocs/95 §2`); the
`dcat:distribution` slot renders the existing container thumbnails
(via the TBN1 thumbnail SPI, `aidocs/data/88`).

### 4.6 Relationship to `aidocs/96 §7`

The two starter kits are **complementary**, not duplicates:

| Starter kit | Audience | Output | Ships in TPL3 |
|---|---|---|---|
| **This §4** — five SHACL shapes | Researcher creating a **DataObject instance** | A validated DataObject in five clicks | **YES** — TPL3 ships these |
| **aidocs/96 §7** — six-option picker for new ontology authoring | Researcher / institute authoring a **new domain ontology** | A TTL skeleton extending a `shepard-upper:` root | TPL3d (deferred) — see §7 |

The shape-instance kit (this §4) is the **demo-day artefact** —
researchers see something they can click on first visit. The
ontology-author starter (aidocs/96 §7) is the **deep ramp** — once a
researcher has used the five-shape starter for a quarter and outgrown
it, the picker helps them author their own. Both are valuable;
TPL3 ships §4 first.

---

## §5 — Operator activation runbook (`docs/admin/runbooks/upper-ontology-bootstrap.md`)

Per `feedback_admin_runbooks_pattern.md`: single-page, numbered
steps, host indicator per step, expected stdout, rollback per step,
end-state verification. The runbook lives under
`docs/admin/runbooks/upper-ontology-bootstrap.md` and is part of the
**same PR** as the V64 migration.

**Pre-conditions.** Shepard backend ≥ v15.20 (post-TPL3), n10s
plugin installed in Neo4j, `shepard-admin` CLI accessible to the
operator, `instance-admin` role present, Garage S3 healthcheck green.

**Step-by-step.**

1. **[shell on admin host]** Verify Shepard is healthy:
   ```bash
   curl -fsS https://shepard.example.org/healthz
   ```
   Expected: `200 OK` JSON `{"status":"UP","version":"15.20",...}`.

2. **[backend container]** Run the V64 migration (it runs automatically
   on next backend restart; this is the manual path):
   ```bash
   docker exec shepard-backend java -jar /deployments/quarkus-run.jar \
     -Dshepard.migrations.cypher.target=V64
   ```
   Expected stdout: `MigrationsRunner: applied V64__Bootstrap_upper_ontology.cypher (10 nodes merged, 14 axioms loaded)`.
   On error: see step 9 (rollback).

3. **[admin host]** Verify the ten repositories registered:
   ```bash
   shepard-admin semantic repos --output=json | jq '.repositories | length'
   ```
   Expected: `11` (the ten new + the pre-existing INTERNAL n10s repo).

4. **[admin host]** Verify the upper-ontology axioms loaded into n10s:
   ```bash
   shepard-admin semantic sparql --output=json --query='SELECT ?s WHERE { ?s rdfs:subClassOf <http://semantics.dlr.de/shepard-upper#DataObject> } LIMIT 5'
   ```
   Expected: at least five rows including `iao:DataItem`, `m4i:Measurement`,
   `chameo:Sample`, `iao:Document`, `dcat:Dataset`.

5. **[admin host]** Verify the five starter-kit shapes registered:
   ```bash
   curl -fsS -H "Authorization: Bearer $TOKEN" \
     https://shepard.example.org/v2/shapes | jq '[.shapes[] | select(.name | test("Shape$"))] | length'
   ```
   Expected: `5` (Measurement, Sample, ProcessStep, Document, Dataset).

6. **[browser]** Smoke test — create a `Measurement` DataObject through
   the UI:
   - Log in as a regular user
   - Navigate to *Collections → Create DataObject*
   - Pick *Shape: Measurement* from the picker
   - Fill the four mandatory slots (method, value, unit, time)
   - Click *Save*
   - Expected: green toast `DataObject created; SHACL validation passed`.

7. **[admin host]** Verify the new DataObject's triples reach n10s:
   ```bash
   shepard-admin semantic sparql --query='SELECT ?o WHERE { <urn:shepard:dataobject:NEW_APPID> <http://qudt.org/schema/qudt/numericValue> ?o }'
   ```
   Expected: one row, the value entered in step 6.

8. **[admin host]** Provenance check — the V64 migration appears in
   the audit trail:
   ```bash
   shepard-admin audit --kind=Migration --output=json | jq '.activities[] | select(.targetAppId == "V64")'
   ```
   Expected: one Activity row, actor `system`, timestamp matches step 2.

9. **[admin host]** **Rollback (only if needed)** — reverts step 2:
   ```bash
   docker exec shepard-backend java -jar /deployments/quarkus-run.jar \
     -Dshepard.migrations.cypher.target=V64_R
   shepard-admin semantic repos --output=json | jq '.repositories | length'
   ```
   Expected after rollback: `1` (only the INTERNAL n10s repo remains).
   **Note:** the rollback does NOT remove the loaded n10s triples;
   to drop them as well, run `CALL n10s.graphconfig.dropConfig()` then
   re-run `OntologySeedService` against the truncated config. This is
   destructive — only do it on a non-production instance.

**End-state verification.** After steps 1-7 succeed, the instance is
ready for TPL4 (backward-compat dual-write) and TPL5 (git-based
ingestion). Researchers can author `Measurement`, `Sample`,
`ProcessStep`, `Document`, and `Dataset` DataObjects against the
seeded upper ontology without writing any TTL.

**Reference versions** at time of writing:
- Shepard backend: v15.20 (post-TPL3 minimum)
- BFO 2020 release: `2024-09-01` (sha256 captured in `ontologies-manifest.json`)
- m4i: `1.4.0` (per `aidocs/semantics/94`)
- CHAMEO: `2024-Q3` release
- All sha256 hashes pinned in `backend/src/main/resources/ontologies/ontologies-manifest.json`

---

## §6 — Acceptance criteria

The TPL3 PR ships when **all** of the following hold:

1. **All 10 semantic repositories registered** —
   `GET /v2/admin/semantic-repos` returns exactly 11 (10 new + 1
   pre-existing INTERNAL). Each new node has `tier IN ['upper','domain']`,
   `bundled = true`, and a non-empty `iri` field.
2. **5 starter-kit shapes loadable** — `GET /v2/shapes` includes
   `MeasurementShape`, `SampleShape`, `ProcessStepShape`,
   `DocumentShape`, `DatasetShape`; each is visible in the
   Collection→Create-DataObject UI picker.
3. **SHACL validation passes for example instances** — the five
   committed test-fixture DataObjects (one per shape) validate
   without error against their respective shapes (`SHACL2`-style
   tests, opt-in via the new `tpl3-acceptance` Maven profile).
4. **Rollback migration reverts cleanly** — `V64_R` removes all
   ten registered nodes; no orphan edges; no n10s state mutation.
5. **New researcher onboarding ≤ 10 min to first valid Measurement
   DataObject** — measured against a clean instance, no prior
   Shepard knowledge, following the casual-user task page
   `docs/help/your-first-measurement.md` (ships in the same PR).
   This is the **demo-day acceptance bar**: a Clean Aviation JU
   reviewer should be able to land on `shepard.example.org`, follow
   the help page, and have a validated Measurement DataObject before
   their coffee cools.
6. **Canonical-predicate compliance** — the `Measurement` and
   `ProcessStep` shapes use `m4i:realizesMethod`, `obo:RO_0002233`,
   `obo:RO_0002234` (per M4I-b finding), **not** the non-canonical
   `m4i:hasMethod`/`hasInput`/`hasOutput`. Verified by a unit test
   that diffs the shape Turtle against a fixed canonical-IRI allowlist.
7. **Audit-trail capture** — V64's application generates one
   `:Activity` row (PROV1a) with `kind = Migration`, `targetAppId = V64`,
   `actor = system`. The same row is queryable via
   `shepard-admin audit --kind=Migration`.
8. **Coverage gate** — JaCoCo for the new `de.dlr.shepard.semantic.bootstrap.*`
   package ≥ 70% line / 60% branch (per CLAUDE.md "new code targets
   ≥ 70%").
9. **Documentation parity** —
   - Reference docs: `docs/reference/shapes.md` gains a *Starter Kit*
     section with one paragraph per shape.
   - User docs: `docs/help/your-first-measurement.md` ships (the
     10-minute acceptance bar's reference document).
   - Admin docs: `docs/admin/runbooks/upper-ontology-bootstrap.md`
     (per §5).
   - Vision (`aidocs/42`): **not updated** — TPL3 is substrate
     bootstrap, not a user-visible payload kind. The vision change
     belongs with TPL4's first researcher-facing dual-write feature.
   - Feature matrix (`aidocs/44`): TPL3 row flipped `📐 designed` →
     `🚧 in-flight` on PR open; → `✓ shipped` on PR merge.
   - Upgrade ledger (`aidocs/34`): one row added under "TPL3 —
     upper-ontology bootstrap" with the V64 migration citation +
     operator-runbook pointer.
   - Model inventory (`aidocs/data/00-model-inventory.md §8`): one
     row added for V64.

---

## §7 — Dependencies + sequencing

### 7.1 Depends on

- **#127 SHACL changeover** (PRs 2/5/6-8/9) — the SHACL validation
  runtime. TPL3's five shapes need the validator to be present
  before they can be useful. **Strict dependency** (TPL3 cannot
  ship before #127 is merged).
- **m4i integration design** (`aidocs/semantics/94`) — the m4i
  v1.4.0 pin + the M4I-b canonical-predicate fix. TPL3 ships the
  bootstrap; M4I-b ships the predicate-rename in the same PR
  family. **Strict dependency** (the predicate rename has to be in
  Shepard's emitters before the shape mandates the canonical IRIs).
- **N1b (preseed ontologies)** — shipped. The `OntologySeedService`
  + `ontologies-manifest.json` machinery TPL3 extends.
- **N1c2 (admin REST surface for semantic repos)** — shipped. TPL3
  does not add new admin endpoints; it just registers more bundles.
- **PROV1a (provenance capture filter)** — shipped. The migration's
  `:Activity` row is automatic.

### 7.2 Blocks

- **TPL4** (backward-compat dual-write attribute promotion) —
  TPL4's promotion targets are `dcterms:` and `m4i:` predicates;
  the SemanticRepository nodes those reference must exist before
  promotion runs. Blocked on TPL3.
- **TPL5** (git-based shape ingestion) — TPL5 needs the upper
  ontology present to anchor the imported domain shapes. Blocked
  on TPL3.
- **TPL9** (f(ai)²r AI provenance triples) — TPL9 writes against
  the `f-ai-r:` SemanticRepository. Blocked on TPL3.
- **TPL10** (DataQualityRequirement first-class record) — TPL10
  models DQRs as instances of an upper-ontology class. Blocked on
  TPL3.
- **TPL3d ontology-author starter kit** (per `aidocs/96 §7`) —
  the six-option picker for **new domain ontology** authoring (vs.
  this doc's §4 five shapes for **new instance** authoring). TPL3d
  is deferred; TPL3 ships §4 first.

### 7.3 Sister work

- **ONT1d — ontology picker for semantic repositories** (#72) —
  the UI surface that picks among the ten newly-registered repos
  when an annotation picker fires. Can land before or after TPL3;
  TPL3 makes ONT1d's list 11× more populated.
- **ONT-AI-MAP1** — AI-assisted ontology mapping. TPL3's seeded
  repos are the mapping sources/targets ONT-AI-MAP1 operates on.
  Independent dispatch; sequencing flexible.
- **EDGE1** — shepard Edge. The bootstrap migration must run on
  Edge instances too (default-on); the runtime knob to skip individual
  bundles (`shepard.semantic.preseed-ontologies.skip-bundles`) lets
  Edge skip EMMO + CHAMEO to save memory.

---

## §8 — Backlog rows for `aidocs/16-dispatcher-backlog.md`

The TPL3a–TPL3g slice plan from `aidocs/semantics/96 §8` is the
authoritative breakdown. TPL3 implements §8 of that doc; this
section pre-fills the rows for the dispatcher backlog so the PR's
in-flight stage is tracked surface-by-surface.

| ID | Description | Effort | Status | Notes |
|---|---|---|---|---|
| **TPL3a** | `V64__Bootstrap_upper_ontology.cypher` + `V64_R__Bootstrap_upper_ontology.cypher` — MERGE ten `:SemanticRepository` nodes (BFO 2020, IAO, IOF Core, EMMO Core (small), CHAMEO, MSEO, m4i v1.4.0, f(ai)²r, PROV-O, dcterms). Idempotent + fail-fast per `MigrationsRunner` post-A1e. Endpoint URL shape: canonical IRIs (not REST-validator-normalised — asymmetry documented in this doc §3). | M | queued | This row tracks the Cypher migration only. Pairs with TPL3b (the Turtle file). |
| **TPL3b** | `backend/src/main/resources/ontologies/shepard-upper.ttl` — the ~50-axiom alignment layer per `aidocs/semantics/96 §3.1`. Loaded into n10s via `OntologySeedService` extension at startup. Manifest entry + sha256 pinning. | S | queued | Smallest TPL3 slice; mechanical translation of §3.1. |
| **TPL3c** | **Gate, not work** — ontologist review of `aidocs/semantics/96 §3.1` mappings. At least one external reviewer (OBO Foundry / IOF community / NFDI4Ing terminology contributor) signs off on the alignment axioms before TPL3b lands. If review unavailable, scope back to PROV-O + IAO only (per `aidocs/96 §6.1`). | 0 (external) | gated | The highest-risk gate; the rest of TPL3 is mechanical once this clears. |
| **TPL3d** | Domain-ontology author starter kit (per `aidocs/96 §7`) — 6-option picker, TTL skeleton generator, ORCID pre-fill. **Deferred** from the TPL3 demo PR; ships as a follow-up once the §4 instance starter has run with real researchers and produced friction lessons. | M | queued | Per §4.6 of this doc — instance-starter ships first; ontology-author-starter follows. |
| **TPL3e** | SHACL validation rule (`shepard:UpperAlignmentShape`) — every new domain class submitted via the shape registry MUST `rdfs:subClassOf` a `shepard-upper:` root. Enforced at shape submission time; surfaced as RFC 7807 `urn:problem-type:shepard:shape-not-upper-aligned`. | S | queued | The fence that keeps the substrate honest after the bootstrap. |
| **TPL3f** | Five starter-kit shapes (Measurement, Sample, ProcessStep, Document, Dataset) per §4 of this doc — Turtle in `backend/src/main/resources/shapes/`; loaded by the shape registry at startup; five test fixtures committed; one casual-user task page `docs/help/your-first-measurement.md` covering the 10-min acceptance bar. | M | queued | The demo-day artefact. |
| **TPL3g** | Reference + user + admin docs (per `feedback_three_audience_docs.md`) — `docs/reference/shapes.md` *Starter Kit* section, `docs/help/your-first-measurement.md`, `docs/admin/runbooks/upper-ontology-bootstrap.md`. Aidocs trackers (`aidocs/34`, `aidocs/44`, `aidocs/data/00-model-inventory.md`) updated in the same PR. | S | queued | Sister-PR shape, NOT a follow-up. |

**Effort total.** S + M + 0 + M + S + M + S = **~8-10 person-days**
on the implementer side, plus the ontologist review (TPL3c) which is
calendar-blocked not effort-blocked. Matches the `aidocs/96 §8`
estimate (~8.5 days).

---

## §9 — Out of scope

- **OWL DL reasoning at runtime** — per `aidocs/96 §6.5`. Alignment
  axioms are declarative metadata; external SPARQL consumers may
  use them.
- **Full EMMO** (~10k classes) — EMMO Core only.
- **Full schema.org** (~2k classes) — Dataset / CreativeWork subset only.
- **Domain-specific ontologies** (CPACS-OWL, robotics ontologies, AAS
  submodel templates) — these stay plugin-shaped per CLAUDE.md
  plugin-first rule. TPL3 provides the upper-ontology anchors they
  extend.
- **License vocabulary** (SPDX) — LIC1's responsibility. Until LIC1
  ships, `dcterms:license` on the `Document` and `Dataset` shapes
  is freetext-with-CC-BY-4.0-default.
- **Automated ontology alignment discovery** (LogMap / OntoAligner) —
  ONT-AI-MAP1's scope. TPL3 ships the canonical alignment by hand
  per `aidocs/96 §3.1`.
- **Edge / offline operation specifics** — EDGE1's responsibility.
  The bootstrap migration is designed to run identically on Edge;
  the `skip-bundles` knob lets Edge skip heavy bundles.

---

## §10 — References

- `aidocs/semantics/95-shacl-templates-and-individuals.md` — the
  parent design for SHACL shapes / templates / individuals (TPL1).
- `aidocs/semantics/96-upper-ontology-alignment.md` — the
  upper-ontology design this doc implements (§8 of aidocs/96 is the
  TPL3 slice plan).
- `aidocs/semantics/94-metadata4ing-integration-design.md` — m4i
  v1.4.0 pin + M4I-b canonical-predicate fix.
- `aidocs/semantics/65-admin-configurable-ontology-preseed.md` —
  N1c admin REST surface for semantic repos (TPL3 reuses this).
- `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md` —
  n10s + OntologySeedService machinery.
- `aidocs/16-dispatcher-backlog.md` — TPL3a..TPL3g rows ship here.
- `aidocs/34-upstream-upgrade-path.md` — TPL3 row + V64 citation
  + operator runbook pointer.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — TPL3 row
  status flip.
- `aidocs/data/00-model-inventory.md` — V64 row in §8.
- `aidocs/agent-findings/dlr-ontology-catalog.md` — the audit
  grounding `aidocs/96 §2` cited.
- BFO 2020: <https://github.com/BFO-ontology/BFO-2020>; ISO/IEC 21838-2:2021
- IAO: <https://github.com/information-artifact-ontology/IAO>
- IOF Core: <https://github.com/iofoundry>
- EMMO + CHAMEO + MSEO: <https://github.com/emmo-repo/EMMO>,
  <https://github.com/emmo-repo/domain-characterisation-methodology>,
  <https://github.com/Mat-O-Lab/MSEO>
- m4i v1.4.0: <https://w3id.org/nfdi4ing/metadata4ing/1.4.0/>
- f(ai)²r: <https://github.com/noheton/f-ai-r>
- PROV-O: <https://www.w3.org/TR/prov-o/>
- DCAT 3: <https://www.w3.org/TR/vocab-dcat-3/>
- DataCite Metadata Kernel: <https://schema.datacite.org/>
- QUDT: <https://qudt.org/>
- OBO Relations Ontology (`obo:RO_*`): <http://purl.obolibrary.org/obo/ro.owl>

---

**Authorship.** Drafted 2026-05-23 (`stage: idea`). The next
stage transition (`idea` → `feature-defined`) blocks on:
(a) the TPL3c ontologist review per `aidocs/96 §6.1` and (b) the
SHACL substrate (#127) being landable. Both gates are in the
M1-milestone critical path; this doc lands first so reviewers
have a concrete spec to evaluate.
