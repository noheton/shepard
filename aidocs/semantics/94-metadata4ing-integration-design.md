---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 94 — Deepening metadata4ing (m4i) integration into Shepard's semantic graph

**Status.** Design — ready for slice planning.
**Snapshot date.** 2026-05-23.
**Audience.** Contributors implementing the m4i deepening slices;
operators evaluating Shepard for NFDI4Ing-aligned engineering RDM;
DLR stakeholders asking "are we serious about metadata4ing or
are we just hand-waving the manifest?"

**Originating items.** User request 2026-05-23: deepen
metadata4ing's footprint in the semantic graph. m4i is already
partly wired in (UH1b feed inlines `m4i:hasProcessingStep` +
`m4i:hasIdentifier`; PROV1h content-neg ships;
`metadata4ing.ttl` stub seeded via ONT1b; renderer constants
shipped). This design proposes the remaining deepening slices.

**Cross-references** (consulted per
`feedback_backlog_consult_context.md`):

- `aidocs/semantics/95-shacl-templates-and-individuals.md` —
  SHACL substrate (the home for the proposed `m4i:DataObjectShape`)
- `aidocs/semantics/96-upper-ontology-alignment.md` — upper-ontology
  alignment (m4i alongside IOF / IAO / BFO)
- `aidocs/semantics/98-shapes-views-and-process-model.md` — process
  model + view recipes (m4i is one consumer of the process layer)
- `aidocs/semantics/65-admin-configurable-ontology-preseed.md` —
  N1c2 admin surface that already handles m4i bundle on/off + canonical refresh
- `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md`
  — n10s substrate that hosts the bundle
- `aidocs/integrations/67-unhide-publish-plugin.md` §4 — UH1b feed body
  already inlines m4i fragments (the implementation precedent for §4)
- `aidocs/workflows/64-provenance-architecture.md` §3.2 — PROV1h
  content-negotiation; `ProvJsonLdRenderer` is the existing m4i renderer
- `aidocs/strategy/73-dlr-stakeholder.md` + `aidocs/strategy/74-dlr-bt-stakeholder.md`
  — m4i as DLR-aligned vocabulary
- `aidocs/strategy/88-nfdi4ing-alignment.md` — NFDI4Ing org/funding-cycle
  positioning (Phase 2 measures F-1 + F-2); this doc is the
  vocabulary-operationalisation half, 88 is the political/institutional half
- `aidocs/strategy/90-hmc-phase-2-positioning.md` §4 — HMC WP-2 (semantic
  features extensions) names m4i deepening as the load-bearing commitment;
  same code path, two funding rationales
- `aidocs/44-fork-vs-upstream-feature-matrix.md` §7a (preseed) +
  §8 (provenance) + §18 (PROV1h)
- `aidocs/34-upstream-upgrade-path.md` ONT1b + PROV1h rows

External:
- Canonical OWL: <https://w3id.org/nfdi4ing/metadata4ing/> (303 →
  `nfdi4ing.pages.rwth-aachen.de/metadata4ing/metadata4ing/`)
- Source repo: <https://git.rwth-aachen.de/nfdi4ing/metadata4ing/metadata4ing>
- Latest version: **1.4.0** (released **2025-12-08**) — the
  same version the shipped bundle already pins. **No version bump needed.**
- DOI: <https://doi.org/10.5281/zenodo.7362037>
- NFDI4Ing Terminology Service: <https://terminology.nfdi4ing.de/ts/>
- HPMC sub-ontology: <https://nfdi4ing.de/5-23-2/>

---

## 1. What metadata4ing is, in one paragraph

Metadata4Ing (m4i) is the **NFDI4Ing-published ontology for
describing the generation of research data within a scientific
activity**, with a particular focus on engineering sciences. Its
**central class** is `m4i:ProcessingStep` (a subclass of
`prov:Activity`) which connects *inputs*, *outputs*, *methods*,
*tools*, and *participants*. The vocabulary is **engineering's
official extension of W3C PROV-O** within the NFDI federation —
adopting it earns Shepard alignment with every NFDI4Ing
infrastructure (Terminology Service, archetype data catalogues,
DataPLANT, NFDI-MatWerk) plus the broader Helmholtz Unhide /
DataCite metadata graphs. Latest stable: **1.4.0 (2025-12-08)**;
CC BY 4.0; canonical IRI `http://w3id.org/nfdi4ing/metadata4ing/`.

The minimum competitive story: an institute evaluating Shepard
against Kadi4Mat / SciCat / openBIS asks "does your platform
speak NFDI4Ing-canonical metadata, or do I have to write the
adapter myself?" — with this design, the answer is **"speaks it
natively; export, import, agent contract, and admin surface all
m4i-aware."**

---

## 2. Current state — where m4i is already wired in

A reader who's just landed should not assume m4i is greenfield.
The fork already ships:

| Surface | What ships | Reference |
|---|---|---|
| **Bundle seed** | `metadata4ing.ttl` (stub, ~7 classes / 3 properties) SHA-256 pinned in `ontologies-manifest.json`, seeded into n10s on first start (ONT1b). Canonical URL `https://w3id.org/nfdi4ing/metadata4ing/1.4.0/` populated so N1c admin refresh can pull the full canonical TTL on demand. | `backend/src/main/resources/ontologies/metadata4ing.ttl` + `ontologies-manifest.json` entry id `metadata4ing` |
| **Admin surface** | Operators flip the bundle on/off at runtime, upload custom extensions (e.g. a lab-local `mffd-m4i.ttl`), and trigger per-bundle re-seed — all via `/v2/admin/semantic/ontologies/*` + `shepard-admin semantic ontologies …` CLI. | `aidocs/semantics/65 §2.4–2.6` |
| **PROV-O renderer** | `ProvJsonLdRenderer.renderMetadata4ing(...)` emits an m4i-flavoured JSON-LD shape with `m4i:ProcessingStep` / `m4i:InvestigatedObject` / `m4i:Person` typing on top of the PROV-O baseline. | `backend/src/main/java/de/dlr/shepard/provenance/services/ProvJsonLdRenderer.java` |
| **Content-negotiation** | `/v2/provenance/activities`, `/v2/provenance/entity/{appId}`, `/v2/provenance/count` accept `Accept: application/ld+json; profile=metadata4ing` (or short `profile=metadata4ing`) and switch to the m4i shape. RFC 7807 on unknown profile. | `ProvenanceRest.java` PROV1h |
| **Unhide feed** | UH1b feed entries inline a `m4i:hasProcessingStep` array — the per-Collection activity trail rendered as m4i nodes, sized via `shepard.unhide.feed.provenance-window` (default 5, cap 100). UH1c adds `m4i:hasIdentifier` carrying the KIP1a-minted PID. | `aidocs/integrations/67 §4.2`; `UnhideFeedService` |

That's a real footprint — m4i is not vapourware in this codebase.

**What's missing (the gap this design closes):**

1. **The renderer emits non-canonical predicates** — `m4i:hasMethod`,
   `m4i:hasInput`, `m4i:hasOutput` are written by
   `ProvJsonLdRenderer` but **do not exist** in the canonical m4i
   1.4.0 vocabulary. The correct predicates are
   **`m4i:realizesMethod`**, **`obo:RO_0002233`** (has input),
   **`obo:RO_0002234`** (has output) — the latter two reused from
   the OBO Relations Ontology (which Shepard already preseeds via
   ONT1a). Today's m4i output is technically a Shepard-local
   dialect, not interoperable m4i. Verified against
   `https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/metadata4ing/`
   on 2026-05-23.
2. **The stub bundle is ~7 classes vs ~150 in canonical 1.4.0** —
   only the headline classes / properties. SPARQL queries against
   the m4i graph today find the seven entries; classes like
   `m4i:Configuration`, `m4i:Variable` subtypes, `m4i:Tool` subtypes,
   units modelling — all absent from the seeded subset.
3. **DataObject / Collection itself has no m4i view** — only the
   provenance trail does. A caller asking
   `Accept: application/ld+json; profile=metadata4ing` against
   `/v2/data-objects/{appId}` gets the default JSON, not an m4i shape.
   This is the most-asked-for deepening (the entire RDM evaluation
   conversation hinges on "can I get an m4i representation of my
   dataset?").
4. **No SHACL shape pins the m4i export shape** — UH1b's body is
   coded into the renderer rather than declared as a shape under
   the substrate proposed in `aidocs/semantics/95`. Means there
   is no machine-readable contract for "what m4i triples Shepard
   guarantees per DataObject."
5. **Federation surface unset** — Shepard is not registered with
   the NFDI4Ing Terminology Service nor with any m4i catalogue;
   operator-runbook silent on the path.
6. **HPMC sub-ontology absent** — m4i now has a workflow-based
   HPMC sub-ontology for high-performance measurement and
   computation. Shepard's MFFD + LUMEN HPMC-adjacent use cases
   would benefit from it, but it's not in the preseed library.

---

## 3. Concept inventory + Shepard-side mapping

m4i 1.4.0's headline vocabulary mapped against what Shepard
already models. Bold marks the high-priority deepening targets.

### 3.1 Classes

| m4i class | Definition | Shepard analog | Mapping today | Mapping after this design |
|---|---|---|---|---|
| `m4i:ProcessingStep` | Specific action undertaken during research; subclass of `prov:Activity` | `:Activity` (PROV-O capture); also conceptually a `DataObject` representing a process step (MFFD AFP layup, ultrasonic weld) | `:Activity` rendered as `m4i:ProcessingStep` in PROV1h | `:Activity` AND DataObjects of process-step subtype both expose the type (per `aidocs/semantics/98` process model) |
| **`m4i:Method`** | Abstract description of a method for analysis, generation, transformation | none today — coded in `:Activity.actionKind` enum | render `:Activity.actionKind` as a typed `m4i:Method` reference; allow first-class `:Method` nodes per `aidocs/semantics/98` | **slice M4I-d** |
| **`m4i:Tool`** | Object that helps an agent perform an action | partly: instrument fields in MFFD seed (`bench`, `afp_robot`); but no first-class graph node | promote operator-named instruments (LUMEN `bench`, MFFD `afp_robot`) to `m4i:Tool` individuals — once the controlled-vocab table lands per `aidocs/semantics/14` | **slice M4I-d** |
| **`m4i:Variable`** + subtypes (`NumericalVariable`, `TextVariable`, `BooleanVariable`) | A typed variable observed / produced during a step | currently Annotation key/value freeform — no typed-variable model | annotation keys promoted to `m4i:Variable` instances when bound to a SHACL shape predicate; numeric variables get QUDT unit hookup (already preseeded) | **slice M4I-c**, **M4I-d** |
| `m4i:Configuration` | Configuration parameters of a tool / method | none — closest is shape-bound annotations | future; not in v1 deepening |
| **`m4i:InvestigatedObject`** | Entity being investigated; subclass of `prov:Entity` | DataObject (when it represents a physical specimen — MFFD frame 3, LUMEN TR-004) | rendered today on the entity side of PROV1h m4i output | **slice M4I-b** (correct relation predicate to `m4i:investigates`) |
| `m4i:Person` | Person agent; subclass of `prov:Person` | shepard `User`; in MFFD seed via `test_engineer` annotation | rendered as `m4i:Person` in PROV1h | unchanged; could surface ORCID via `m4i:hasIdentifier` (slice M4I-c) |
| `m4i:Organization` | Organisation agent; subclass of `prov:Organization` | none — shepard tracks users not orgs | future; UH1 `:UnhideConfig.contactEmail` is the operator hint, but not modelled as an `m4i:Organization` |

### 3.2 Object properties (the ones the renderer actually emits)

| m4i property | Status in canonical 1.4.0 | Status in Shepard today | Action |
|---|---|---|---|
| **`m4i:realizesMethod`** | Defined — `m4i:ProcessingStep → m4i:Method` | NOT emitted — renderer emits **`m4i:hasMethod`** which **does not exist** in canonical m4i | **slice M4I-b — fix predicate (CRITICAL)** |
| **`m4i:hasEmployedTool`** | Defined — `m4i:ProcessingStep → m4i:Tool` | not emitted | **slice M4I-d** add to renderer |
| **`m4i:investigates`** | Defined — `m4i:ProcessingStep → m4i:InvestigatedObject` | not emitted (only the inverse-ish target-entity reference) | **slice M4I-b** make this the entity side of the read-action path |
| `m4i:investigatesProperty` | Defined — points to the property under investigation | not emitted | future |
| **`m4i:hasParameter`** + `m4i:hasParameterSet` | Defined — process / tool parameter modelling | not emitted | **slice M4I-d** + ties to `aidocs/semantics/98` process shapes |
| `m4i:hasParticipant` | Defined — agents participating in step | not emitted — renderer uses `prov:wasAssociatedWith` (acceptable; both compose) | future; the PROV-O form is canonical for now |
| **`m4i:hasInput`** | **NOT in m4i** — canonical is `obo:RO_0002233` (`has input`) | emitted as `m4i:hasInput` (undefined predicate) | **slice M4I-b — fix predicate (CRITICAL)** |
| **`m4i:hasOutput`** | **NOT in m4i** — canonical is `obo:RO_0002234` (`has output`) | emitted as `m4i:hasOutput` (undefined predicate) | **slice M4I-b — fix predicate (CRITICAL)** |
| **`m4i:hasIdentifier`** | Defined — opaque identifier carrier | emitted in UH1c per-entity feed | unchanged; verify shape against canonical |
| **`m4i:hasProcessingStep`** | Defined — Collection → step composition | emitted in UH1b per-entity feed | unchanged |
| `m4i:hasResearcher` | Defined (m4i extension; subproperty of `prov:wasAssociatedWith`) | not emitted | future |
| `m4i:hasNumericalVariable` | Defined — typed numeric output | not emitted | **slice M4I-d** when annotations promote to typed variables |

The **two bug-shaped findings** in this section are M4I-b's
remit: `m4i:hasMethod`, `m4i:hasInput`, `m4i:hasOutput` are
emitted today but **are not predicates that exist** in the
canonical ontology. A SPARQL store consuming the feed sees
them as freestanding properties from the `m4i:` namespace —
they do not break a parser, but they break interop ("the m4i
profile" promise is degraded).

### 3.3 Coverage estimate

The shipped stub is 7 classes + 3 properties; canonical 1.4.0
publishes **~150 terms total** (classes + properties + variable
subtypes + HPMC extensions). Stub coverage is ~7%.

After M4I-a (stub → fetch canonical TTL on first seed), coverage
goes to 100% in the n10s graph — SPARQL against m4i terms
returns the real vocabulary, not the headline subset. Operator
cost: zero (the manual `shepard-admin semantic ontologies seed
metadata4ing` is the existing CLI; M4I-a flips the default to
fetch-on-seed via `canonicalUrl` rather than ship-bundled stub).

---

## 4. Recommended deeper integration — six slices

Slices ordered by structural payoff vs effort. Each slice's
acceptance criteria, dependencies, and a sentence on why it
earns its keep.

### 4.1 M4I-a — promote stub → canonical TTL on first seed

**Ship:** Modify `OntologySeedService` (or just the manifest entry)
to fetch the canonical Turtle from
`https://w3id.org/nfdi4ing/metadata4ing/1.4.0/` at first start
when `canonicalUrl` is set and the `fetchOnSeed` flag is `true`
on the entry. Stub stays in the JAR as the offline-fallback,
matching the NASA Thesaurus pattern (N1e) already shipped.

**Acceptance criteria:**
- After fresh start: `MATCH (c:Resource) WHERE c.uri STARTS WITH "http://w3id.org/nfdi4ing/metadata4ing/" RETURN count(c)` returns ≥ 100 (was ~10 with stub)
- Offline-fallback path: when the canonical URL is unreachable, seed falls back to the bundled stub TTL and logs a WARN; startup succeeds.
- Refresh idempotent — re-seeding doesn't duplicate triples (n10s standard).

**Dependencies:** none — pattern is shipped (N1e NASA Thesaurus).
**Size:** S (1 day).
**Why it earns its keep:** SPARQL queries against the m4i graph
suddenly resolve to the real vocabulary, not the stub. Zero
operator action required. Sets the precedent the rest of the
slices ride on.

**Citation:** `aidocs/semantics/65 §2.3` (canonical-URL pattern);
`aidocs/44 §7a` (N1e precedent).

### 4.2 M4I-b — fix non-canonical predicates in `ProvJsonLdRenderer` (CRITICAL)

**Ship:** Rewrite the m4i branch of `ProvJsonLdRenderer.render(...)`
to emit canonical predicates:

- `m4i:hasMethod` → **`m4i:realizesMethod`**
- `m4i:hasInput` → **`obo:RO_0002233`** (require `obo:` prefix in `@context`)
- `m4i:hasOutput` → **`obo:RO_0002234`** (require `obo:` prefix in `@context`)
- The investigated-object side of the entity reference gains
  the inverse direction: when the entity is the subject of
  investigation (read action on a DataObject-typed thing), emit
  `m4i:investigates` from the activity to the entity, in
  addition to the existing `obo:RO_0002233` for input semantics.

The `@context` block extends with `"obo": "http://purl.obolibrary.org/obo/"`
when profile=m4i. PROV-O profile output is unchanged.

**Acceptance criteria:**
- SHACL self-test: the m4i-flavoured output of `/v2/provenance/activities?profile=metadata4ing` validates against an m4i shape that asserts `sh:property` rules on `m4i:realizesMethod` + `obo:RO_0002233` + `obo:RO_0002234`.
- Existing PROV-O profile tests still pass byte-for-byte.
- Integration test against UH1b feed: the inlined `m4i:hasProcessingStep` array carries `m4i:realizesMethod` (not the old name) at every position.
- A v6 migration note appears in `aidocs/34` flagged BREAKING for m4i consumers that depended on the old non-canonical names (low risk — no known consumer, but the discipline is the point).

**Dependencies:** none. Drop-in renderer change.
**Size:** S (0.5 day code + 0.5 day SHACL self-test).
**Why it earns its keep:** Today's "m4i-profile" output is a
Shepard-local dialect. The fix makes the promise true — the
profile name and the predicates match. This is the smallest
slice with the largest correctness gain. A funder asking
"is your m4i export interoperable?" gets a yes only after M4I-b.

**Citation:** `aidocs/semantics/65 §2.6`; this doc §2 + §3.2.

### 4.3 M4I-c — `m4i:DataObjectShape` (SHACL contract for DataObject m4i export)

**Ship:** Land a SHACL `sh:NodeShape` named `m4i:DataObjectShape`
under the `aidocs/semantics/95` substrate, declaring the
mandatory + optional m4i triples Shepard emits per DataObject
when content-neg requests the m4i profile. Add
`Accept: application/ld+json; profile=metadata4ing` support to
`GET /v2/data-objects/{appId}` (mirroring PROV1h on the
provenance resources). A new `M4iDataObjectRenderer` consumes
the SHACL shape + the DataObject's graph state and produces the
m4i JSON-LD body.

**Mandatory m4i triples per DataObject:**

```turtle
:do/<appId>
    a m4i:InvestigatedObject ;
    dcterms:identifier "<appId>" ;
    dcterms:title "<DataObject.name>" ;
    schema:dateCreated "<createdAt>" .
```

**Optional / shape-derived triples:**

```turtle
:do/<appId>
    m4i:hasIdentifier [
        a m4i:Identifier ;
        m4i:identifierValue "<KIP1a PID>" ;
        m4i:hasIdentifierType "Handle"
    ] ;
    obo:RO_0002233 :do/<predecessor-appId>     # has input (predecessor chain)
    obo:RO_0002234 :do/<successor-appId>       # has output (successor chain)
    prov:wasGeneratedBy :activity/<appId> ;    # PROV-O linkage to most-recent :Activity
    m4i:realizesMethod :method/<actionKind> ;  # m4i extension (when actionKind known)
    schema:keywords [...annotations as concept references...] .
```

**Acceptance criteria:**
- The SHACL shape ships at `backend/src/main/resources/shapes/m4i-data-object-shape.ttl` and is loaded into n10s on startup.
- `GET /v2/data-objects/{appId}` with the m4i profile header returns a JSON-LD body that pyShacl validates against the shape with zero violations.
- UH1b's renderer is refactored to consume the SHACL shape (single source of truth) — body equivalence under semantic comparison (Jena), not byte-comparison.
- Coverage test: every DataObject in the MFFD + LUMEN seed Collections validates.

**Dependencies:** M4I-a (canonical bundle), M4I-b (correct predicates).
Conceptually slots under SHACL substrate (aidocs/semantics/95)
but ships before the full substrate by reading the shape file at
startup the same way ontology bundles are loaded.

**Size:** M (3–4 days).
**Why it earns its keep:** This is the slice that turns m4i from
"the provenance shape" into "the dataset description shape" —
the actual question every NFDI4Ing-aligned researcher asks of an
RDM platform. Plus: the shape becomes the **contract**, so the
renderer is generated, not coded.

**Citation:** `aidocs/semantics/95-shacl-templates-and-individuals.md`
parts 1 + 4; `aidocs/integrations/67 §4.2` (precedent renderer);
`aidocs/workflows/64 §3.2` (content-neg pattern).

### 4.4 M4I-d — promote `:Activity.actionKind` / instruments / annotations to first-class m4i individuals

**Ship:** Three sub-slices, each independent:

**M4I-d-1:** When `:Activity.actionKind` is set, mint a stable IRI
`shepard:method/<kind>` per kind (`POST` → `shepard:method/POST`,
`PUT` → `shepard:method/PUT`, etc.) on first use, with
`a m4i:Method` + `rdfs:label`. The renderer references this IRI
instead of a string blob. Operators can later refine the labels
via the admin custom-bundle path.

**M4I-d-2:** When an `:Activity.targetKind` corresponds to a
shaped DataObject subtype (per `aidocs/semantics/98` process
model), the renderer cites `m4i:hasEmployedTool` to a
`shepard:tool/<typename>` IRI. The link substantiates a graph
walk "find all activities that used tool X" via SPARQL.

**M4I-d-3:** When a DataObject carries Annotations whose keys
match a SHACL shape's `sh:property → m4i:hasNumericalVariable`
slot, the renderer emits typed `m4i:NumericalVariable` blank
nodes with QUDT-typed values. Free-text annotations stay as
`schema:keywords` (the unstructured fallback).

**Acceptance criteria:**
- The 7 distinct `actionKind` values land as `shepard:method/*` individuals in the graph after startup (one-time bootstrap).
- LUMEN seed: TR-004 vibration annotation (a numeric quantity with units) renders as `m4i:NumericalVariable` with a `qudt:Unit` reference (g rms).
- SPARQL contract test: `?step m4i:realizesMethod ?m. ?m a m4i:Method.` returns ≥ 1 row over the MFFD seed.

**Dependencies:** M4I-c (shape contract for "which annotations are numeric variables vs keywords"); QUDT (already preseeded).
**Size:** M (3 days, split as 1+1+1).
**Why it earns its keep:** The single richest "graph search"
deepening — a researcher's question "find all bridge-welding
processes that used the LBR iiwa tool with weld current > 1200 A"
finally has a SPARQL answer.

**Citation:** `aidocs/semantics/98 §process model`;
`aidocs/semantics/14 §controlled vocabularies`;
`backend/src/main/resources/ontologies/qudt.ttl` (preseeded).

### 4.5 M4I-e — operator runbook for NFDI4Ing federation surfaces

**Ship:** A `docs/reference/nfdi4ing-federation.md` page (and a
short companion `docs/help/register-with-nfdi4ing.md`) walking
an operator through:

1. Confirming Shepard's `/v2/admin/semantic/ontologies` lists
   `metadata4ing` enabled.
2. Pointing the NFDI4Ing Terminology Service at the SPARQL
   surface (designed for future `aidocs/semantics/98` v2; today
   the operator self-registers the canonical bundle).
3. Pointing the Unhide harvester at `/v2/unhide/feed.jsonld` —
   already an UH1a / UH1b shipped capability; this doc surfaces
   the m4i triples in the feed for the NFDI4Ing-side reader's
   benefit.
4. (Forward-looking) Registering the institute with NFDI4Ing
   Archetype Doris for HPMC use cases (LUMEN, MFFD instrumented
   campaigns).

**Acceptance criteria:**
- The doc walks a real operator (MFFD ZLP Augsburg or
  Lampoldshausen) through the steps in ≤ 30 minutes.
- Doc links and references verified live as of doc-write date.
- A "what's next" section that does NOT promise unshipped
  federation (Terminology Service auto-sync etc.) and clearly
  marks them as roadmap.

**Dependencies:** none — pure operator docs.
**Size:** S (1 day).
**Why it earns its keep:** Federation is half-existing-capability,
half-operator-discovery; the runbook is the bridge. Without
M4I-e, no operator finds the path.

**Citation:** `aidocs/integrations/67-unhide-publish-plugin.md §7`;
this doc §6.

### 4.6 M4I-f — preseed the m4i HPMC sub-ontology bundle

**Ship:** Add a new bundle `metadata4ing-hpmc` to the preseed
manifest. The sub-ontology models high-performance measurement
and computing workflows — directly relevant to LUMEN (hot-fire
hi-rate DAQ) and MFFD AFP (multi-sensor robot telemetry). Same
shape as ONT1b: SHA-256-pinned TTL stub in
`backend/src/main/resources/ontologies/metadata4ing-hpmc.ttl`,
canonical URL pointing at the NFDI4Ing publication,
`required=false`, default-on (operator can disable).

**Acceptance criteria:**
- New bundle entry passes the existing manifest-schema test.
- Stub TTL parses + n10s ingests it; key classes (e.g.
  `m4i:HpmcWorkflow`) addressable via SPARQL.
- N1c admin refresh against the canonical URL succeeds.
- Documented in `docs/reference/semantic-repositories.md`.

**Dependencies:** none (parallel to M4I-a).
**Size:** S (0.5 day stub + 0.5 day docs).
**Why it earns its keep:** Closes the HPMC gap for LUMEN +
MFFD HPMC-adjacent workflows. Optional preseed so non-HPMC
operators pay nothing.

**Citation:** <https://nfdi4ing.de/5-23-2/>; `aidocs/semantics/65 §1`.

---

## 5. Acceptance criteria for "m4i-compliant" — what we claim publicly

After all six slices ship, Shepard can claim m4i-compliance
along these axes (and *only* these — claims beyond this list
are roadmap, not shipped):

1. **Vocabulary present** — the canonical m4i 1.4.0 OWL is
   loaded into the internal semantic repository (M4I-a) and
   the HPMC sub-ontology is optionally available (M4I-f).
2. **Provenance shape canonical** — every `:Activity` rendered
   as JSON-LD with `profile=metadata4ing` uses canonical m4i
   predicates (`m4i:realizesMethod` etc.) and reuses OBO RO for
   `has input` / `has output` (M4I-b).
3. **DataObject shape canonical** — every DataObject is
   addressable via the m4i profile and validates against the
   shipped `m4i:DataObjectShape` SHACL contract (M4I-c).
4. **Process modelling present** — `m4i:Method`, `m4i:Tool`,
   `m4i:NumericalVariable` individuals are reachable via SPARQL
   for the seeded MFFD + LUMEN datasets (M4I-d).
5. **Federation runbook present** — operators can register a
   Shepard instance for NFDI4Ing-aware harvesting via the docs
   (M4I-e).

What is **NOT** claimed:

- Full ~150-term coverage in writeable shapes (only the
  high-value 20–30 terms ship as shapes; the rest live in the
  vocabulary, addressable via SPARQL).
- Automated NFDI4Ing Terminology Service sync (operator runbook
  only; auto-sync queued for `aidocs/semantics/98` v2).
- m4i over upstream `/shepard/api/...` — m4i is `/v2/` only, per
  the API-version policy (`CLAUDE.md §API-version policy`).
- Validation against an externally-curated m4i SHACL profile
  (NFDI4Ing publishes vocabulary, not a profile shape — Shepard
  authors the shape).

---

## 6. NFDI4Ing federation — what Shepard gains by being m4i-compliant

NFDI4Ing's federated infrastructure expects m4i-aligned
metadata as the entry pass. The benefits are:

| Federation surface | URL / standard | What Shepard gains by being m4i-compliant |
|---|---|---|
| **NFDI4Ing Terminology Service** | `terminology.nfdi4ing.de/ts/` (SPARQL endpoint) | Shepard's vocabularies (m4i extensions, MFFD process ontology, LUMEN sensor vocab) discoverable from the central registry; queries against the TS can join with Shepard-published metadata |
| **NFDI4Ing Metadata Profile Service** | `nfdi4ing.de/1-24-2/` | Shepard-published m4i metadata accepted as a profile carrier by participating archetype services |
| **Archetype Doris (HPMC RDM)** | `nfdi4ing.de/archetypes-3/doris/` + HOMER crawler | LUMEN + MFFD HPMC workflows ingestible via Doris-shaped harvest using the m4i HPMC sub-ontology (M4I-f) |
| **Helmholtz Unhide (via DLR Helmholtz membership)** | `unhide.helmholtz-metadaten.de` | Already shipped (UH1a/b/c); slice M4I-b makes the feed body canonically m4i-compliant |
| **DataCite / Zenodo** | DOI minting via `aidocs/66` KIP1a | m4i shape composes with schema.org + DataCite already; M4I-c standardises the DataObject-side output |
| **OpenAIRE / EOSC** | EOSC marketplace | An EOSC-registered service is expected to publish metadata against either DataCite or an alignment; m4i alignment cited as an interoperability axis |
| **DBpedia / Wikidata** | OWL alignment | Future bridge (out of v1 deepening) — m4i terms cross-reference to existing concepts |

This is concrete payoff, not handwaving — the existing UH1b
shipped surface plus the proposed M4I-a + M4I-b + M4I-c gets
Shepard into every federation surface that asks for canonical
m4i triples.

### 6.1 Cross-reference to other vocabularies in Shepard

m4i overlaps + complements other preseeded bundles. The boundary:

- **m4i vs PROV-O** — m4i extends; provenance core stays in
  PROV-O. `m4i:ProcessingStep ⊑ prov:Activity`.
- **m4i vs Dublin Core** — m4i for engineering process; DC for
  publication metadata. Dataset title / creator / date stay in
  `dcterms:`.
- **m4i vs schema.org** — m4i for engineering process modelling;
  schema.org for catalogue / search-engine discovery (Google
  Dataset Search, OpenAIRE). Both ship in UH1b body.
- **m4i vs IOF Core / BFO** — IOF Core for manufacturing process
  classes (`iof:ManufacturingProcess`); m4i for the
  research-process-instance-level (`m4i:ProcessingStep`). MFFD
  models AFP / welding via IOF Core as a class hierarchy + m4i
  individuals describing actual runs. See
  `aidocs/semantics/96 §5` worked example.
- **m4i vs CHAMEO / Material OWL** — material characterisation
  (CHAMEO) + material descriptors (Material OWL) compose with
  m4i `m4i:investigates` linking a `ProcessingStep` to a
  material instance.
- **m4i vs CPACS (DLR aerospace)** — orthogonal. CPACS is
  aircraft-configuration vocabulary; m4i is process vocabulary.
  A CPACS-shaped aircraft model can be the `m4i:investigates`
  target of an `m4i:ProcessingStep`.
- **m4i vs f(ai)²r** — complementary. f(ai)²r captures AI / human
  / collaborative provenance modes; m4i captures research
  process steps. Both attach to the same `:Activity` node; a
  step done with AI assistance carries both
  `a m4i:ProcessingStep` and `fair2r:wasGeneratedByAi` triples.
  See `project_ai_human_collab_provenance.md` in agent memory.

### 6.2 NFDI4Ing federation prerequisites — operator checklist

(Lifted into the operator runbook M4I-e ships):

1. Shepard instance public on a stable URL (operator infra)
2. m4i bundle enabled (default-on per ONT1b)
3. Unhide feed flipped on if Helmholtz-affiliated (`shepard-admin unhide enable`)
4. Operator registers the institute with NFDI4Ing TS / Archetype Doris (manual; out of band)
5. (Optional) HPMC bundle enabled if HPMC use case applies

---

## 7. Open questions

The slices proposed in §4 don't address these; capturing them
explicitly so they don't bleed into the design.

### 7.1 How are operator-coined m4i extensions versioned?

When a lab uploads a `lab-vocab.ttl` extending m4i (per
`aidocs/semantics/65 §2.3` custom-bundle path), the lab's terms
are local. If the lab participates in a federation that expects
canonical terms only, the lab terms aren't discoverable. **Open:**
does Shepard provide a mechanism to "promote" lab terms via
proposal to NFDI4Ing canonical, or stay strictly local? Likely
no — the federation owns its vocabulary.

### 7.2 Do we map upstream-frozen v1 `/shepard/api/...` to m4i?

Per `CLAUDE.md §API-version policy`, no — m4i lives on `/v2/`
only. But: a researcher's existing v1 import scripts won't
benefit from m4i export shape. Workaround: operators wanting
m4i export migrate the consumer-side to `/v2/data-objects/`.
Documented in M4I-e runbook.

### 7.3 Is m4i compatible with the future SHACL substrate move?

`aidocs/semantics/95` proposes a SHACL substrate where domain
shapes ARE the source of truth. M4I-c's `m4i:DataObjectShape`
SHACL file is the first such shape. The Q is whether the m4i
shape stays in the JAR / classpath, or whether it gets uploaded
via the admin custom-bundle path on a per-deployment basis. The
current proposal keeps M4I-c's shape in the JAR (canonical, not
operator-customisable); operator extensions land via the
custom-bundle path. Reviewers may push back — open for
substrate-move discussion.

### 7.4 What's the cost of M4I-d-3 on annotation write throughput?

Promoting annotation keys to m4i `Variable` instances at
write-time may add latency to high-rate annotation paths (e.g.
PV-import). Mitigation: lazy promotion — the m4i view is
computed at read time from the shape contract, not at
write time. Confirmed cheap for read-side rendering; needs a
perf benchmark before M4I-d-3 ships at scale (LUMEN
high-rate sensor channel annotations).

### 7.5 Does HPMC sub-ontology (M4I-f) bring transitive dependencies?

m4i HPMC sub-ontology may itself depend on other vocabularies
(NFDI-MatWerk, MaRDI numerical analysis terms). The bundle path
in `aidocs/semantics/65` supports this — operators can preseed
additional dependencies, but Shepard should document which deps
M4I-f assumes when it ships. Light research needed before
finalising the bundle's manifest entry.

### 7.6 Should `m4i:Person` carry ORCID via `m4i:hasIdentifier`?

The current renderer emits `prov:Person` with
`shepard:username` only. If the User profile entity carries
ORCID (per `aidocs/36 §user profile`), the m4i `Person` shape
could nest `m4i:hasIdentifier` with `m4i:identifierType "ORCID"`.
Easy win, but waits on U1 profile ORCID shipping. Logged as
follow-up.

---

## 8. Out of scope (explicit)

- **OWL DL reasoning** at runtime — Shepard does not reason
  (`aidocs/semantics/96 §6.5`); m4i is metadata, not inferred.
- **Auto-discovery of m4i terms** from operator data — humans
  curate; Shepard validates.
- **Auto-sync with NFDI4Ing TS** — operator runbook only in v1
  (the federation API isn't stable yet).
- **m4i over v1 `/shepard/api/...`** — `/v2/` only.
- **Operator-coined m4i term federation** — local-only;
  operator drives the canonical-promotion process out of band.

---

## 9. Phasing

Recommended landing order (each slice atomic, with clear
acceptance criteria from §4):

| Order | Slice | Size | Rationale |
|---|---|---|---|
| 1 | **M4I-a** — stub → canonical TTL | S (1 d) | foundational; precedent exists (N1e) |
| 2 | **M4I-b** — fix non-canonical predicates | S (1 d) | bug fix; CRITICAL for interop claim |
| 3 | **M4I-c** — `m4i:DataObjectShape` + DataObject m4i export | M (3–4 d) | structural deepening; depends on M4I-a + M4I-b |
| 4 | **M4I-e** — federation runbook | S (1 d) | operator-facing closure |
| 5 | **M4I-d** — promote actionKind / annotations / tools | M (3 d) | richer graph; parallel to M4I-e |
| 6 | **M4I-f** — HPMC sub-ontology bundle | S (1 d) | targeted (HPMC-using operators only) |

Total: **~10–11 days** of focused work for the full deepening.

---

## 10. Changelog

| Date | What changed | Why |
|---|---|---|
| 2026-05-23 | Initial design — six slices proposed (M4I-a through M4I-f); concept inventory grounded against canonical m4i 1.4.0; bug-shaped finding on `m4i:hasMethod` / `hasInput` / `hasOutput` surfaced and routed to M4I-b. | User request 2026-05-23: deepen metadata4ing integration. |

---

**Authorship.** Drafted 2026-05-23. Slices land via the backlog
queue under `aidocs/16 §M4I`. The bug-shaped predicate finding
in §3.2 / M4I-b should land first regardless of slice ordering —
it's correctness, not deepening.
