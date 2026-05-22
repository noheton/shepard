---
stage: fragment
last-stage-change: 2026-05-23
---

# 98 — Shapes, views, and the MFFD process model

*Synthesis date: 2026-05-22.*

This doc replaces the lost trio:

- `aidocs/semantics/98-mffd-process-shapes.md` (lost)
- `aidocs/platform/100-mffd-views-workspace.md` (lost)
- `aidocs/platform/101-view-shapes-and-spi.md` (lost)

The "98" slot is **reused intentionally** — the seven persona reviews
(`aidocs/agent-findings/persona-review-*.md`) already cite "98";
reusing the slot keeps inbound references valid. The trio collapses
to one doc because, as the API Scrutinizer review demonstrated, seven
proposed view-concepts (`View`, `ViewShape`, `ViewProvider`, `Slicer`,
`Persona`, `Workspace`, `ViewBundle`) all fold into `:ShepardTemplate`
with a new `templateKind` enum value. There was never enough surface
to justify three docs.

The doc was committed in stub form before any enrichment — see
`feedback_agent_worktree_must_commit.md` for the loss story this
guards against.

---

## 0. Reading order

Before reading this doc:

1. `aidocs/semantics/95-shacl-templates-and-individuals.md` — the
   substrate. Parts 1 (shapes as templates), 2 (shapes as views),
   3 (shapes as agent contracts), 4 (named individuals), and 15
   (F(AI)²R AI provenance) are the load-bearing sections. This doc
   refines them; it does not replace them.
2. `aidocs/semantics/96-upper-ontology-alignment.md` — why
   BFO/IAO/IOF/PROV-O were chosen as the four-pillar upper ontology.
3. The seven persona reviews this doc is derived from
   (`aidocs/agent-findings/persona-review-{ontologist,rdm,api-scrutinizer,ai-opportunities,digital-native,reluctant-senior,ime-aqe}.md`).
   Each carries cite-grade findings against either the lost trio
   (which they couldn't read) or the live shapes on disk. This doc
   converts the union of those findings into a single coherent
   design.
4. The shipped touchpoint:
   `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesValidateRest.java`
   — its javadoc at line 28 already declares the catalogue location
   (`/v2/templates?kind=view`). This doc honours that decision; any
   inbound reference to `/v2/views` is a contradiction with shipped
   code.
5. Cross-cutting constraints from project memory:
   - `feedback_shacl_single_source_of_truth.md` — SHACL is the
     authoritative source for domain data shape; Neo4j carries
     identity + operational data only.
   - `feedback_ontology_first.md` — before naming a new
     plugin/shape/concept, check whether an existing primitive
     already covers it. This is the rule the trio collapse honours.
   - `feedback_agent_clarify_first.md` — `[NEEDS-CLARIFICATION]`
     blocks must list options + lean, never bare questions.
   - `feedback_agent_worktree_must_commit.md` — why this doc was
     committed before it was complete.

---

## 1. Shape system (replaces lost doc 98)

### 1.1 The catalogue lives at `/v2/templates?kind=...`

**Decision.** The shape catalogue is reached via
`GET /v2/templates?kind=view|template|individual`, not a new
`/v2/views` resource. This is **already shipped** in
`ShapesValidateRest.java` line 28 ("this endpoint does not maintain
a shape catalog — Template (`/v2/templates?kind=view`) carries
that.") A new `/v2/views` prefix would contradict committed code.

The API Scrutinizer review attacked seven concept proposals and
found zero survived scrutiny. Every CRUD operation a view registry
would want — versioning, soft-retire, allow-list, instantiation
provenance, bulk import/export, audit — is already specified on
`:ShepardTemplate` in `aidocs/workflows/54-templates-as-first-class-entity.md`.
The only addition we make:

```
TemplateKind ::= DATAOBJECT_RECIPE | COLLECTION_RECIPE
              | EXPERIMENT_RECIPE | PROCESS_RECIPE
              | VIEW_RECIPE              ← new (this doc)
```

That's a five-line enum edit, not a new resource group, a new Neo4j
label, a new JAX-RS resource class, a new OpenAPI tag, a new MCP
tool generator path, or a new permissions surface.

### 1.2 The one new endpoint: `POST /v2/shapes/render`

The single operation that has a defensible case for a new endpoint
is **render-a-shape-against-an-individual**. It does not fit on
`/v2/templates/{id}/...` cleanly because it is stateless and
side-effect-free (the same property as `/v2/shapes/validate`).
Place it next to validate:

```
POST /v2/shapes/render
Content-Type: text/turtle (shape) or application/json (request)

Request:
  {
    "templateAppId": "...",      // the shape to render
    "focusShepardId": "...",     // the individual to project
    "mediaType": "application/json"
  }

Response 200:
  {
    "projected": {               // shape-driven projection
      "fields": [
        {"label":"Track Number","value":1,"datatype":"xsd:integer",
         "iri":"mffd:trackNumber","widget":"integerInput",
         "group":"identity","order":10}
      ],
      "groups": ["identity","weldParams","ndt","calibration"]
    },
    "verification": { ... },     // F(AI)²R verification status
    "validationReport": { ... }  // sibling SHACL report (optional)
  }
```

Sibling endpoint. Stateless. Same media-type story as
`/v2/shapes/validate`. No new resource group.

### 1.3 Named individuals as the predicate-key registry

Per `aidocs/95 §4`, every DataObject, Collection, Container, and
Activity gets a stable `shepard:` IRI
(`shepard-instance/<id>/dataobject/<shepardId>`). This is the
**single substrate decision that makes everything else work** —
embeddings, graph-features, anomaly markers, cross-source joins
all key off the same IRI; the 5-tuple channel-identity problem
collapses upstream of the AI layer.

The Ontologist review surfaced a critical PROV-O typing bug here:
`shepard-core-shapes.ttl` line 107 uses `prov:wasInformedBy` on
`DataObjectShape`, but PROV-O declares `wasInformedBy` with
`rdfs:domain prov:Activity` and `rdfs:range prov:Activity`. With
`shepard:DataObject ⊑ iao:DataItem ⊑ bfo:Continuant`, the typing
fails an OWL DL reasoner.

**Fix (this doc).** Use a typed predicate split:

- `prov:wasDerivedFrom` (Entity → Entity) for DataObject lineage.
- `prov:wasInformedBy` (Activity → Activity) for the `:Activity`
  log (already correct via PROV1a).
- `shepard:reworkOf rdfs:subPropertyOf prov:wasRevisionOf` for the
  typed rework predicate that distinguishes "same physical part,
  repaired" from "new version replacing the old". This is the
  load-bearing IME/AQE finding — without it the anomaly → repair →
  re-test chain is not reconstructable in under five minutes.

The named individuals registry becomes the join target for every
typed predicate above; no new node-types needed.

### 1.4 F(AI)²R provenance as a SHACL invariant

Per `aidocs/95 Part 15`, every `fair2r:Claim` carries
`prov:wasGeneratedBy minCount 1` enforced via SHACL. This makes it
**structurally impossible** to land an AI annotation without an
Activity. The AI Opportunities review confirmed this captures the
attribution dimension correctly.

The gap the AI review surfaced: **sampling-params reproducibility**.
Today `X-AI-Agent`, `X-AI-Prompt-Hash`, `X-AI-Verification` capture
*who* and *what prompt*; they do **not** capture *how* (temperature,
top-p, top-k, seed, sampler). Without those, you cannot bisect a
regression in model behaviour or pass an EASA Learning Assurance
evidence pack via REBAR.

**Fix (this doc).** Extend TPL9b additively with:

- `X-AI-Temperature` (decimal, 0..2)
- `X-AI-Top-P` (decimal, 0..1)
- `X-AI-Top-K` (integer, ≥1)
- `X-AI-Seed` (integer)
- `X-AI-Sampler` (string, e.g. `nucleus`, `top-k`, `greedy`)
- `X-AI-Max-Tokens` (integer)

These map to additive properties on the `fair2r:AuthoringPass`
shape; full sampling configuration also lands inside the
`fair2r:Prompt` entity body for provider-specific extras. Cheap
additive header pass; one extension to the SHACL shape.

### 1.5 Composition pattern (the "Lego" claim)

The shipped catalogue (`mffd-shapes.ttl` lines 44–370 per the
Ontologist review) genuinely composes via `sh:and` + `sh:node` /
`sh:class`:

```
MFFDCampaignShape
  ├ sh:node ProcessStepShape (×7)
  │   ├ sh:node CalibrationCertificateShape
  │   ├ sh:node NDTGateShape
  │   └ sh:node BridgeWeldingShape  (when step = bridgeWelding)
  └ sh:node SignOffShape
```

Reusable mini-shapes (`NCRShape`, `SignOffShape`,
`VerificationActivityShape`) plug into any parent. This pattern
stays; it is the structural good thing the catalogue already does.

### 1.6 QUDT unit round-trip

The Ontologist review caught this: `mffd-process.ttl` line 138 binds
`mffd:hasUnit unit:A` at the **class** level, but
`mffd-shapes.ttl` lines 150–158 declare the matching property as
`sh:datatype xsd:float` only. **The unit travels with the class, not
the value.** A persisted measurement is a bare `5.0`, not a
`qudt:QuantityValue [qudt:numericValue 5.0 ; qudt:hasUnit unit:A]`.
This is a FAIR-Interoperable regression.

**Fix (this doc).** Replace `sh:datatype xsd:float` on every
quantitative property with `sh:node` → `QuantityValueShape`
(defined once in `mini-shapes.ttl`), with `sh:nodeKind sh:IRI` on
the unit slot. The change is mechanical (one shape per parameter)
but must happen before MFFD real data ships; otherwise every weld
measurement loses its unit on ingestion.

---

## 2. Views as shapes (collapsed from lost 100 + 101)

### 2.1 A view is a `:ShepardTemplate` with `templateKind = VIEW_RECIPE`

No new node type. No new SPI. No new Vue-loading mechanism. A view
is *identified by* its shape, *parameterised by* a target focus-class
or focus-node, and *rendered by* the generic Vuetify renderer using
SHACL render hints (`sh:group`, `sh:order`, `dash:editor`).

The API Scrutinizer review attacked seven view-concepts and zero
survived. This doc adopts the verdict:

| Proposed concept | Survives? | Maps to |
|---|---|---|
| `View` | No | `:ShepardTemplate` with `templateKind = VIEW_RECIPE` |
| `ViewShape` | No | The same SHACL `sh:NodeShape`; "view" vs "form" is a render-time choice, not a node-type |
| `ViewProvider` (SPI) | No | Manifest-only; SHACL hints + optional SRI URL for custom JS bundles (§2.3) |
| `Slicer` | No | SHACL filter shape or pre-projected SPARQL query template |
| `Persona` | No | Existing `Role` (A0) + `U1c` user prefs cover it |
| `Workspace` | No (for now) | Saved view + saved filter + persisted layout in user-prefs. Re-propose only with a non-view consumer. |
| `ViewBundle` | No | `:ShepardFile` referenced from the template body if a custom render is needed at all |

**This means**: every endpoint group an `/v2/views` prefix would
have introduced (CRUD, list, version, import, export, allow-list)
is already on `/v2/templates`. The single render operation lands as
`POST /v2/shapes/render` (§1.2).

### 2.2 Rendering = SHACL focus-node selection + projection

The render pipeline:

1. Client posts `(templateAppId, focusShepardId)` to
   `/v2/shapes/render`.
2. Backend resolves `templateAppId → :ShepardTemplate.body` (Turtle).
3. Backend resolves `focusShepardId → individual IRI` from the
   named-individuals registry (§1.3).
4. Backend uses Jena SHACL to (a) validate the focus against the
   shape and (b) project the shape-declared properties into a flat
   wire format: `[{label, value, datatype, iri, widget, group,
   order}]`.
5. Backend returns the projection + validation report.

The backend code reuses `de.dlr.shepard.v2.shapes.validator.JenaShaclValidator`
infrastructure (already shipped). New code lives in a single class
`ShapesRenderRest` next to `ShapesValidateRest`.

### 2.3 Custom renderers: SRI URL, not Java SPI

For the 95% case (label/group/order/widget rendering against
SHACL-shape-declared properties), the generic Vuetify renderer
suffices. For domain-specific renderers (e.g. a chemistry plugin's
`chem:moleculeWidget`), the SHACL render-hint vocabulary is
extensible at the **ontology layer**, not via a Java SPI:

- Default: `dash:editor sh:TextEditor` / similar → built-in widget.
- Override: a render hint `shepard:customRendererURL` paired with
  `shepard:customRendererSRI` lets a plugin ship a Vue bundle hosted
  at any URL; the frontend fetches with SRI digest verification.

This honours the Scrutinizer's "deployment-unit semantics" finding:
the backend is Java, the renderer is JS; a Java SPI shipping Vue
components confuses the deployment unit. URL + SRI does not.

The Java SPI seam — if it ships at all — exists only to register
**new render-hint vocabularies**, not to ship arbitrary Vue code.
That is the existing `OntologyContributor` plumbing (`aidocs/65`
ONT1c), not a new SPI.

### 2.4 Workspace = saved view + saved filter + persisted layout

Per the Scrutinizer finding: "Workspace" only earns existence with a
non-view consumer. Until that consumer arrives, a workspace is a
**user-pref-scoped tuple** `(templateAppId, filterSpec, columnPrefs,
layoutPrefs)` persisted in the existing user preferences entity. No
new top-level resource.

The Reluctant Senior persona's complaint here is the load-bearing
one: shape authors decide `sh:group` partitioning. A user wants to
override the grouping per-session and have it stick. **Workspace =
the override store, scoped to the user.** That is exactly the
saved-view pattern; not a new primitive.

### 2.5 Default landing view: table, not detail-page tabs

The Reluctant Senior review verdict: a Collection's default landing
view should be a **table** with sortable/filterable/exportable
columns whose default set comes from the shape but whose actual set
is per-user. The "detail page with seven tabs" should be one click
away, not the default.

This doc adopts the verdict. The shape's `sh:order` / `sh:group`
metadata becomes the **default column set + grouping**; the user's
workspace overrides both. Per-user column preferences persist.

---

## 3. MFFD process model

### 3.1 Process parameters as instances, not classes

The Ontologist review surfaced the load-bearing fork: `mffd-process.ttl`
§4 declares 16 weld-controller parameters (`mffd:CM_I`, `mffd:CM_t`,
`mffd:W1_I` … `mffd:BridgePosition`) as `owl:Class subClassOf
mffd:ProcessParameter`. **This breaks**:

1. It doesn't scale. The next welder has 32 channels; AFP has 40+.
2. The shape (`BridgeWeldingShape`) treats `mffd:W1_I` as a *property*
   (`sh:path mffd:W1_I sh:datatype xsd:float`), but the ontology
   declares it as a *class*. Shape and ontology disagree.
3. The QUDT unit binding (§1.6 above) lives on the class, not the
   value — units are lost on write.

**Fix (this doc).** Adopt the IOF-style instance + taxonomy pattern:

- `mffd:ProcessParameter` is a single class.
- `mffd:WeldCurrentParameterType`, `mffd:WeldDurationParameterType`, …
  are SKOS-style `mffd:parameterType` taxonomy nodes.
- A measurement is a `ProcessParameter` instance with:
  - `mffd:parameterType mffd:WeldCurrentParameterType`
  - `mffd:hasValue [qudt:numericValue "17.43"^^xsd:float ;
                    qudt:hasUnit unit:A]`
  - `prov:wasGeneratedBy <activity-iri>`

The shape becomes a single `ProcessParameterShape` reused across all
seven process steps. Adding the next welder's 32 channels is a TTL
edit, not 32 new OWL classes.

(The SOSA `sosa:Observation` pattern was considered but deferred —
overkill for discrete per-step parameters; right answer for the
high-rate sensor side and the timeseries-appId migration tracked in
`aidocs/platform/87`.)

### 3.2 Rework lineage: typed `shepard:reworkOf`

The IME/AQE review traced an EN 9100 §8.7 audit blocker: today the
seed (`examples/mffd-showcase/seed.py`) wires the rework loop
through bare `prov:wasInformedBy`, which is the same predicate used
for normal predecessor lineage. An auditor cannot distinguish a
rework retry (same physical part, repaired) from a normal forward
step (next process stage) without parsing NCR text.

**Fix (this doc).** A typed predicate, namespaced in `shepard:`
because rework is a cross-domain quality concept, not a MFFD-only one:

```turtle
shepard:reworkOf
  a owl:ObjectProperty ;
  rdfs:subPropertyOf prov:wasRevisionOf ;
  rdfs:domain shepard:DataObject ;
  rdfs:range shepard:DataObject ;
  rdfs:comment "Asserts that the subject DataObject is a rework
                retry of the object DataObject (same physical part,
                repaired). Use shepard:replaces for a replacement
                that is a new physical part." .

shepard:replaces
  a owl:ObjectProperty ;
  rdfs:subPropertyOf prov:wasRevisionOf ;
  rdfs:comment "Subject DataObject replaces the object DataObject
                (new physical part). Distinct from shepard:reworkOf." .
```

The W3C-PROV chain stays valid (both are sub-properties of
`prov:wasRevisionOf`); domain-specific tooling can render the rework
edge with a different style (orange, "rework" label) than the
normal grey lineage edge. The lineage graph view becomes
auditor-defensible.

### 3.3 NCRShape, CalibrationCertificateShape, NDTGateShape, SignOffShape, LedgerAnchorShape

The IME/AQE review confirmed all five concepts are structurally
present in the shipped catalogue. The fix is **not** "add new
node-types" but **"enforce the invariants in SHACL"**. The catalogue
today describes the right shapes; it does not say what must hold.

The four invariants that take the catalogue from "data-entry form"
to "control system":

#### 3.3.1 `NDT-FAIL ⇒ NCR exists`

Today the comment in `NDTGateShape` (line 369) says "If `ndtResult
= FAIL`, `raisedNCR` should reference the NCR…". This is a human
guideline. SHACL doesn't enforce it; a FAIL gate with no NCR
validates clean — exactly the EN 9100 §8.7 gap the catalogue exists
to close.

```turtle
NDTGateShape sh:sparql [
  sh:message "NDT result is FAIL but no NCR has been raised." ;
  sh:select """
    SELECT $this WHERE {
      $this mffd:ndtResult \"FAIL\" .
      FILTER NOT EXISTS { $this mffd:raisedNCR ?ncr }
    }
  """
] .
```

#### 3.3.2 `Inspector ≠ operator` (§7.5.3 independence)

```turtle
NDTGateShape sh:sparql [
  sh:message "NDT inspector cannot be the same person as the step operator." ;
  sh:select """
    SELECT $this WHERE {
      $this mffd:inspector ?insp .
      ?step mffd:hasNDTGate $this ; mffd:operator ?insp .
    }
  """
] .
```

#### 3.3.3 `Calibration validity at time of use`

```turtle
ProcessStepShape sh:sparql [
  sh:message "Calibration cert has expired before this step ended." ;
  sh:select """
    SELECT $this ?validUntil ?endedAt WHERE {
      $this mffd:hasCalibrationCert ?cert ;
            prov:endedAtTime ?endedAt .
      ?cert mffd:validUntil ?validUntil .
      FILTER (?validUntil < ?endedAt)
    }
  """
] .
```

#### 3.3.4 `NCR closure requires SignOff anchored to a LedgerAnchor`

```turtle
NCRShape sh:sparql [
  sh:message "NCR is CLOSED but has no SignOff anchored to a LedgerAnchor." ;
  sh:select """
    SELECT $this WHERE {
      $this shepard:ncrStatus \"CLOSED\" .
      FILTER NOT EXISTS {
        ?signoff a shepard:SignOff ; prov:used $this .
        ?anchor a shepard:LedgerAnchor ; shepard:anchors ?signoff .
      }
    }
  """
] .
```

These are SHACL-SPARQL constraints. Jena supports them; SHACL-1 PR-1
already ships `JenaShaclValidator`. Concept already in place; one
sprint to land all four rules.

### 3.4 Disposition vocabulary (EN 9100 §8.7.1)

The IME/AQE review pointed out the current 4-state `ncrStatus` enum
(`OPEN | IN_PROGRESS | CLOSED | REJECTED`) drops the §8.7.1
distinction between rework / repair / scrap / regrade /
use-as-is/concession. **An auditor cares about which disposition was
chosen**.

**Fix (this doc).** Two-axis vocabulary:

- `shepard:ncrStatus ∈ {OPEN, UNDER_REVIEW, DISPOSITIONED, CLOSED, REJECTED}`
- `shepard:ncrDisposition ∈ {REWORK, REPAIR, SCRAP, REGRADE, USE_AS_IS}`
  mandatory iff status = DISPOSITIONED.

SHACL rule: `if ncrStatus = DISPOSITIONED then ncrDisposition minCount 1`.

The UI surfaces the disposition as a single dropdown with quick-buttons
("Mark rework" / "Mark scrap") for the common cases; the SHACL rule
fails forms that pick a disposition without committing the parent
status, and vice versa.

---

## 4. Persona-derived constraints (the hard requirements)

This section enumerates the **must-haves** the persona reviews
surfaced. Each is a one-or-two-PR scope; collectively they define
the slice this doc commits us to ship.

### 4.1 RDM-blocking: license, accessRights, embargo

The RDM review scored Shepard at **FAIR 6/12 today**. The single
biggest gap is `dcterms:license` — every competitor (Kadi4Mat,
openBIS, FAIRDOM-SEEK, NOMAD, SciCat) ships it; Shepard does not.
Funder review (DFG, EU Horizon Europe, Clean Aviation JU) reads this
as R1.1 non-compliance and rejects on first read.

**Required (this doc commits to land):**

- `dcterms:license` (`xsd:anyURI`, `minCount 1`) on `:Collection`
  and `:DataObject`.
- `dcat:accessRights` enum `OPEN | RESTRICTED | CLOSED | EMBARGOED`
  on both.
- `dcterms:available` (embargo-until date, `xsd:date`) on both,
  mandatory iff `accessRights = EMBARGOED`.

One Flyway migration + property additions to the core shape +
form-renderer pick-up automatic. Closes FAIR R1.1 + A1.2 in one
slice and takes Shepard from 6/12 → ~9/12 on the FAIR scorecard.

The same migration adds `dcterms:rightsHolder` (optional) and a
controlled-vocabulary `prov:hadRole` enum for contributor roles
(creator / curator / verifier / approver) — needed for DataCite
`creator.nameIdentifier` round-trip.

### 4.2 PID strategy (canonical citable identifier)

The RDM review surfaced three competing PID minting paths (KIP
handle, Invenio DOI, Databus URI). The trio left this ambiguous;
this doc resolves:

- **Default internal PID**: ePIC Handle (free, no commitment,
  DLR-aligned via `aidocs/66` KIP path). Bound in
  `mffd-context.jsonld` as the canonical IRI.
- **External citation PID**: DOI minted by `shepard-plugin-invenio`
  **only at PUBLISHED status handoff**, only on explicit researcher
  opt-in.
- **Federation handle**: Databus URI, emitted by
  `shepard-plugin-databus` independently.

All three resolve to the same Shepard locator. The Handle is the
default; the DOI is opt-in. This is recorded on the entity as
multiple `dcterms:identifier` values; only one is the "primary
citation" (which is the Handle until/unless DOI is minted).

### 4.3 Canonical export-shape mapping table

The RDM review surfaced the drift risk: three plugins (Unhide,
Invenio, Databus) each define their own `Collection.<field> →
schema.org / DataCite / DCAT / m4i` mapping. Without a single
source of truth, drift between the four publication targets is
inevitable.

**Required.** Sibling doc `aidocs/semantics/99-export-shape-mapping.md`
+ a machine-readable JSON file at
`aidocs/semantics/contexts/export-shape-mapping.json`. Every export
plugin reads the JSON at test time and asserts emitted JSON-LD
matches it. The doc enumerates each Shepard field, its label, and
its mapped term in each of: schema.org, DataCite, DCAT, m4i, PROV-O,
RO-Crate.

### 4.4 Embedding storage (AI-blocking)

The AI Opportunities review surfaced the storage shape as the
blocker for AI1d (similarity search). **Decision (this doc):**

- `EmbeddingReference` is a new payload-kind sibling to
  `TimeseriesReference` / `FileReference` (already standard pattern;
  fits the `PayloadKind` SPI from `aidocs/47 §2`).
- Backing store: pgvector (Shepard already runs Postgres).
- v1: one vector per DataObject. v2: per-chunk.
- Channel identity = the DataObject's appId-derived IRI from §1.3
  (no 5-tuple smell).

This unblocks AI1d, AI1f (NL search reranking), and any
retrieval-augmented LLM workflow.

### 4.5 Hybrid attribute bucket (Reluctant Senior gate)

The Reluctant Senior review identified the **conversion-killer**:
the trio implies every attribute must resolve to a SHACL property
path. A senior researcher with 40 TB of legacy data and custom keys
(`material_roll_change`, `run_number`, `track_number`) cannot
migrate; the platform is locked.

**Required.** Every `:DataObject` carries two attribute buckets:

- **Typed** — properties that resolve to a SHACL property path. Drive
  shape-validated forms, views, agent contracts.
- **Literal** — freetext key-value pairs that bypass shape validation.
  Indexed for search; visible in advanced mode; never required.

Mapping from literal → typed is **an option, not a force**. The
admin can promote a literal key to a typed property by authoring a
shape; legacy data stays in the literal bucket forever if it wants
to. Per `aidocs/95 §10`'s honest "lossy long-tail" admission, this
is the designed resolution.

This single decision is what takes the Reluctant Senior from "no" to
"run it in parallel for six months."

### 4.6 Single-`shepardId` channel reads (Digital Native gate)

The Digital Native review's 5-line test still fails because
`get_channel_data` (REST + MCP) requires the 5-tuple `{measurement,
device, location, symbolicName, field}`. The fix is gated on the
TS-IDc migration tracked in `aidocs/platform/87-timeseries-appid-migration.md`
(task #58).

This doc does **not** ship the migration; it commits to the contract:
**every new endpoint / MCP tool added in the scope of this doc takes
`shepardId` as the singular identifier**. `POST /v2/shapes/render`
takes `focusShepardId`; never a 5-tuple.

### 4.7 Sampling-params provenance (AI / EASA gate)

Per §1.4 — additive `X-AI-*` headers extend TPL9b. Required because:

- EASA Learning Assurance evidence packs via REBAR need
  reproducibility metadata.
- FAIR4ML model-card export needs sampling parameters.
- The EU AI Act Article 50 deadline (2026-08-02) sets the
  upper-bound timeline.

### 4.8 Inspector independence + cert validity (IME/AQE gate)

Per §3.3.2 and §3.3.3 — both invariants land as SHACL-SPARQL
constraints. Without these the catalogue fails an EN 9100 §7.5.3 and
§7.1.5 lead-audit. Sister rules to the NDT-FAIL ⇒ NCR and
NCR-closure-anchored invariants in §3.3.

### 4.9 Namespace canonicalisation (Ontologist gate)

The Ontologist review surfaced three diverged namespaces in
production (`fair2r:`, `mffd:`, `shepard:` — each with at least two
incompatible IRI bases across backend + analytics-ts plugin).

**Required.** A sibling doc `aidocs/semantics/97-canonical-iris.md`
freezes the canonical bases:

- `mffd:` → `http://semantics.dlr.de/mffd-process#`
- `shepard:` (upper) → `http://semantics.dlr.de/shepard-upper#`
- `fair2r:` → `https://noheton.org/f-ai-r/ns#`

Plus a CI lint that rejects any new `*.ttl` declaring
`example.org`-prefixed IRIs. Plugins that already published
divergent IRIs ship `owl:sameAs` bridges in a one-time migration.

### 4.10 Shape-driven prompt templates (AI ergonomic gate)

The AI review's #3 recommendation: every SHACL shape gets an optional
`shepard-ai:promptTemplate` annotation property (Jinja-style, with
shape-derived field names as variables). When an AI is asked to
auto-fill a shape, the prompt is the template; new shape = new
auto-annotation surface for free, in git, versioned with the shape.

The plugin override path (a registry in `shepard-plugin-ai`) exists
for richer prompts but is not the default. This is the move that
turns "ontology drives the UI" into "ontology drives the AI surface".

---

## 5. Forks the doc still needs to resolve

These are the open decisions where the persona reviews disagree, or
where the lean is genuinely close to 50/50. Each follows the
`[NEEDS-CLARIFICATION]` pattern from `feedback_agent_clarify_first.md`.

```
[NEEDS-CLARIFICATION-1] Process-parameter modelling pattern

Question: Adopt §3.1 (instance + parameterType taxonomy), or stay
  with class-per-channel, or skip to SOSA observations?

Context: Ontologist Fork 2. Today's 16-classes-per-welder doesn't
  scale; instance + taxonomy is the IOF-pattern fit; SOSA is
  the right answer for the high-rate sensor side but overkill here.

Options:
  A) Instance + taxonomy (§3.1 above)
     pro: scales; QUDT round-trips; IOF-aligned
     con: bigger refactor than class-per-channel; ~1 day
  B) Keep class-per-channel
     pro: shipped
     con: 32 + 40 + 16 future channels = 88+ new OWL classes; unit
          loss; shape/ontology disagreement persists
  C) SOSA observations
     pro: W3C-standard for sensor data; future-proofs the timeseries
          migration
     con: introduces SOSA as a fifth upper anchor; foreign to current
          code; overkill for discrete per-step measurements

Lean: A. Defer SOSA to the timeseries / high-rate side where it
  genuinely buys something.
```

```
[NEEDS-CLARIFICATION-2] PID strategy

Question: Handle (KIP) as default + DOI on PUBLISHED, vs. DOI
  everywhere, vs. Databus URI as canonical.

Context: §4.2. Three minting paths today; one citation field in
  mffd-context.jsonld.

Options:
  A) Handle default; DOI opt-in on PUBLISHED via Invenio plugin
     pro: free, no external commitment, DLR-aligned
     con: two-tier PID; operator must know which is which
  B) DOI everywhere via Invenio
     pro: single citable identifier
     con: ~3€ per minted DOI; commitment-forever; inappropriate for
          DRAFT / IN_REVIEW data
  C) Databus URI as canonical
     pro: federation-native
     con: not a citation PID in DataCite/Crossref sense; doesn't
          map to "cite this dataset in a paper"

Lean: A. Matches DLR practice (KIP), zero external cost, DOI is
  opt-in at PUBLISHED handoff.
```

```
[NEEDS-CLARIFICATION-3] TemplateKind.VIEW_RECIPE enum vs dedicated
  :View subtype of :ShepardTemplate

Question: Is "view" a new enum value or a new node-type?

Context: API Scrutinizer §2. Enum is one line; subtype is a Neo4j
  label + migration. Neither blocks anything else.

Options:
  A) TemplateKind.VIEW_RECIPE enum value
     pro: 5-line change; reuses every :ShepardTemplate feature
     con: discrimination is field-based, not type-based
  B) :View extends :ShepardTemplate (Neo4j label inheritance)
     pro: type-safe in queries; cleaner OpenAPI tag
     con: migration to add the label; minor duplication

Lean: A. Field-based discrimination is fine; the cost of a new
  node-label is real and the benefit is cosmetic.
```

```
[NEEDS-CLARIFICATION-4] EmbeddingReference as new payload-kind
  sibling vs pgvector column on :DataObject directly

Question: Is the vector "of" a DataObject (one column) or
  "referenced from" a DataObject (a sibling payload kind)?

Context: §4.4. Both work; differ on (a) plugin SPI alignment,
  (b) per-chunk extension story.

Options:
  A) EmbeddingReference payload-kind, pgvector backend
     pro: fits PayloadKind SPI; plugin-first; per-chunk extension is
          natural (add chunks as more references)
     con: new SPI wiring (~1 sprint)
  B) Inline pgvector column on :DataObject
     pro: simplest possible; one column
     con: doesn't extend to per-chunk; doesn't fit the plugin SPI
          family; lineage opacity (the vector isn't an Entity in
          its own right)

Lean: A. The cost of plugin SPI is real but the architectural fit
  is correct. Per-chunk is the v2 win that locks B out.
```

```
[NEEDS-CLARIFICATION-5] NCR as DataObject vs first-class node-type
  vs Annotation

Question: How is an NCR shaped in the graph?

Context: IME/AQE Fork A. Current shipped direction is
  first-class-NCR-node-type via mini-shapes (shepard:NCR is a
  iao:DocumentPart).

Options:
  A) NCR as DataObject with templateKind = NCR_RECIPE
     pro: leverages all existing DataObject infra (REST, access
          control, audit, predecessor chain, files attachable)
     con: pollutes DataObject list views with quality records
  B) :NCR as first-class node-type (current shipped direction)
     pro: clean separation; quality workspace can hide DataObjects
     con: new Java entity + service + repo + Rest class
  C) NCR as Annotation
     pro: lightest UI
     con: annotations don't carry lifecycle / sign-off / multiple
          parents; fails EN 9100 §8.7 controlled-document requirement
          (the IME/AQE review explicitly rejects C)

Lean: A. The "pollutes DataObject views" concern is a view-filter
  problem (workspace = saved view with `templateKind ≠ NCR_RECIPE`
  filter), not a node-type problem. NCR-as-DataObject reuses the
  full lifecycle infrastructure. The shipped direction (B) is
  redundant given the trio collapse — every new "X is a first-class
  node-type" should be a TemplateKind enum value or it's the bug.
  This is the single hardest fork in this doc — see §5 closing note.
```

```
[NEEDS-CLARIFICATION-6] v2 Python SDK: Kiota-generated vs hand-curated
  shepard-py facade vs both

Question: How does a Python user load a DataObject + channels into a
  DataFrame in five lines?

Context: Digital Native §1. Kiota directory is empty; shepard-py
  facade is a 200-LOC plan in aidocs/81.

Options:
  A) Kiota-generated only
     pro: one path; auto-regenerates per release
     con: ergonomics fail the 5-line test; sync-only-by-default
  B) shepard-py facade only
     pro: 5-line test passes; ergonomic
     con: drifts from OpenAPI surface; hand-maintenance
  C) Both — Kiota for full coverage, shepard-py facade on top for the
     80% common ops
     pro: ergonomic AND complete; facade calls Kiota under the hood
     con: two artefacts; coordinated releases

Lean: C. Same pattern as kubernetes-asyncio (Kiota equivalent) vs
  kubernetes (facade). Facade is what notebooks import; Kiota is
  the long-tail safety net.
```

```
[NEEDS-CLARIFICATION-7] Disposition vocabulary granularity

Question: Keep 4-state ncrStatus, expand to 9-state inline, or split
  into status + disposition two-axis?

Context: §3.4 / IME/AQE Fork D. EN 9100 §8.7.1 distinguishes
  rework / repair / scrap / regrade / use-as-is dispositions; today's
  4-state loses this.

Options:
  A) Keep 4-state, add separate disposition predicate
     pro: minimal churn
     con: two fields for one operator decision
  B) Replace 4-state with 9-state inline
     pro: one field
     con: dropdown gets long; mixes lifecycle states with disposition
          choices
  C) Two-axis: status ∈ {OPEN, UNDER_REVIEW, DISPOSITIONED, CLOSED,
     REJECTED} + disposition ∈ {REWORK, REPAIR, SCRAP, REGRADE,
     USE_AS_IS}, mandatory iff status = DISPOSITIONED

Lean: C. Captures the lifecycle distinction and the disposition
  choice separately. Reviewer-friendly. SHACL rule binds them.
  (This is the path §3.4 adopted; flagging here so it's revisitable.)
```

```
[NEEDS-CLARIFICATION-8] Ledger anchor on every NCR closure SignOff,
  or only CRITICAL?

Question: How aggressively do we anchor sign-offs to Bloxberg /
  OpenTimestamps?

Context: §3.3.4 + IME/AQE Fork F. Anchoring is cheap; the question
  is policy uniformity vs operator surprise.

Options:
  A) Anchor every NCR closure SignOff
     pro: uniform; auditor-friendly ("we anchor every quality closure")
     con: trivial cost but operator surprise on slow paths
  B) Anchor only severity = CRITICAL
     pro: focused on highest-stakes records
     con: medium-severity NCRs lose tamper evidence
  C) Anchor severity ∈ {MAJOR, CRITICAL}
     pro: middle path
     con: arbitrary cutoff
  D) Anchor on user request only
     pro: opt-in; no surprise
     con: nobody opts in unless they remember to

Lean: A. The cost is trivial; uniform policy matches a typical
  PSAC/CMM record-retention posture.
```

```
[NEEDS-CLARIFICATION-9] Inspector qualification — typed subclass vs
  annotation vs Keycloak role

Question: How is NAS 410 Level I/II/III inspector qualification
  represented?

Context: IME/AQE Fork on inspector qualification. Today
  NDTGateShape.inspector is sh:class shepard:User — anyone can sign.

Options:
  A) shepard:QualifiedInspector subclass of shepard:User with
     shepard:certifications [{method, level, validUntil}]
     pro: SHACL-validatable; visible in audit queries
     con: adds a User subtype
  B) Annotation on User with controlled vocabulary
     pro: reuses annotation infrastructure
     con: not directly SHACL-validatable on the NDTGate
  C) External Keycloak roles "ndt-level-iii" etc.
     pro: leverages existing auth
     con: invisible in the graph for audit queries

Lean: A + C in parallel. A gives the audit reconstructability; C
  gives operational gating at JWT/auth time. B is too weak alone.
```

```
[NEEDS-CLARIFICATION-10] Provenance export shape — RO-Crate, PROV-O
  Turtle, or both?

Question: When a Collection is exported for repository handoff,
  what format(s)?

Context: RDM Fork C. Two consumer audiences (repositories vs SPARQL
  clients).

Options:
  A) RO-Crate only
     pro: repository-friendly (Zenodo, Invenio); JSON-LD manifest
     con: doesn't carry full SHACL shape; lossy for named-individuals
  B) PROV-O Turtle only
     pro: lossless for the provenance graph
     con: not a "package" format; researchers don't know what to do
  C) Both, dual export
     pro: RO-Crate is the package, PROV-O is the metadata fidelity
     con: dual maintenance burden

Lean: C. The export-shape mapping table (§4.3) keeps them consistent.
  RO-Crate IS the package; PROV-O Turtle is the metadata fidelity
  layer for MOSS + SPARQL.
```

**The single hardest fork without a confident lean** is
**[NEEDS-CLARIFICATION-5]** (NCR as DataObject vs node-type). The
trio-collapse philosophy says "every new node-type should be a
templateKind enum value or it's the bug" — which argues strongly for
A. But the shipped direction is B; reversing it costs migration work.
The lean above (A) is principled but not zero-cost; flagging
explicitly that this fork deserves a wider conversation before
implementation commits.

---

## 6. Migration / roll-out

### 6.1 Two-phase ordered

**Phase 1 — Additive (no breaking changes).**

Each item below is a small additive PR. Order is by blast-radius
(smallest first, biggest last).

1. **§4.1 license + accessRights + embargo fields** — one Flyway
   migration adds three columns to `:Collection` and `:DataObject`;
   SHACL property declarations enforce; form renderer picks up
   automatically. **#1 fundability blocker; ship first.**
2. **§4.4 EmbeddingReference payload-kind** — pgvector setup +
   `EmbeddingReference` entity + `/v2/embeddings` resource +
   payload-kind SPI registration. Unblocks AI1d.
3. **§1.4 / §4.7 X-AI-* sampling-params headers** — additive on
   TPL9b. Extends the `fair2r:AuthoringPass` SHACL shape (additive
   properties). Unblocks REBAR / EASA evidence packs.
4. **§4.10 shepard-ai:promptTemplate annotation property** — vocab
   addition to the upper ontology; no migration; opt-in per shape.
5. **§4.5 hybrid attribute bucket** — already partially present
   (`:DataObject.attributes` JSON map exists); formalises the typed
   vs literal split via a Cypher migration tagging existing keys as
   `literal` until they resolve to a SHACL property path. Reluctant
   Senior gate.
6. **§4.3 canonical export-shape mapping table** — sibling doc +
   JSON file; CI lint added to assert plugins match. Coordinates
   Unhide / Invenio / Databus / MOSS / RO-Crate exporters.
7. **§4.9 namespace canonicalisation** — `aidocs/semantics/97-canonical-iris.md`
   + CI lint rejecting `example.org` IRIs in `*.ttl` files. Plugins
   that diverged ship a one-time `owl:sameAs` migration.
8. **§1.2 POST /v2/shapes/render** — new stateless endpoint sibling
   to `/v2/shapes/validate`. Reuses Jena validator infra.

**Phase 2 — Typed-predicate + SHACL-invariant work (needs
migrations).**

Each item below ships a Cypher migration under
`backend/src/main/resources/neo4j/migrations/` per the CLAUDE.md
upgrade-path rule. Migrations are idempotent (safe to re-run) and
fail-fast (abort startup on error).

9.  **§1.3 PROV-O typing fix** — `prov:wasInformedBy` →
    `prov:wasDerivedFrom` on `DataObjectShape`. Cypher migration
    re-tags existing typed edges; idempotent re-run; ~30 lines.
10. **§3.2 shepard:reworkOf predicate** — vocab addition + Cypher
    migration that walks the seed dataset and converts the existing
    overloaded `prov:wasInformedBy` rework edges into typed
    `shepard:reworkOf` edges (uses NCR `raisedAgainst` + close-time
    heuristic as the join). Migration logs every conversion for
    operator review.
11. **§1.6 QUDT unit round-trip** — replace `sh:datatype xsd:float`
    on every quantitative property with `sh:node` →
    `QuantityValueShape`. Cypher migration on existing data is
    invasive (every weld measurement gets re-wrapped); ships a
    rollback file `V##_R__qudt_quantityvalue.cypher`.
12. **§3.1 instance + parameterType taxonomy refactor** — the
    16-class welder model collapses to instance + taxonomy.
    Cypher migration converts existing class assertions to typed
    instances. Major refactor but mechanical; ships a rollback.
13. **§3.3.1–§3.3.4 SHACL-SPARQL invariants** — the four
    invariants (NDT-FAIL ⇒ NCR, inspector ≠ operator, cert
    validity-at-time, NCR-closure-anchored). No migration; just
    SHACL constraint additions. Will surface existing data that
    violates invariants — operator runbook flags this and provides
    a remediation report (`shepard-admin invariants check`).
14. **§3.4 ncrStatus + ncrDisposition split** — two-axis vocab.
    Cypher migration maps existing 4-state values to the new pair
    (IN_PROGRESS → DISPOSITIONED with disposition = unknown;
    operator runbook documents the manual fix-up).

### 6.2 Coordination with active tasks

- **Task #58 (5-tuple → shepardId)** — §4.6 commits this doc's new
  surface to single-`shepardId` identity. Implementation gated on
  task #58's TS-IDc migration (`aidocs/platform/87`).
- **Task #120 (SHACL changeover)** — Phase 2 SHACL-SPARQL invariants
  (§3.3) land in #120's scope; this doc is the prose layer #120 was
  missing.
- **`aidocs/34-upstream-upgrade-path.md`** — every Phase 1 / Phase 2
  item that ships gets a row added; admin-visible breaking changes
  flagged `BREAKING`.
- **`aidocs/44-fork-vs-upstream-feature-matrix.md`** — each item
  flips from `📐 designed` (this doc) → `🚧 in-flight` → `✓ shipped`
  per the standard CLAUDE.md rule.

### 6.3 The trio-collapse philosophy

The `:ShepardTemplate` collapse target is the substrate — every
"new view concept" must land as a `templateKind` enum value or it
is the bug. This is the structural principle the doc enforces.

When the next design doc proposes a new top-level node-type for a
domain concept (a new container kind, a new view kind, a new
provenance-related entity), the gate question is: **can this be a
TemplateKind enum value, or a SHACL shape with a new property, or
both?** If yes, that is the shape. If no, the design doc explains
why no.

This is `feedback_ontology_first.md` operationalised — and the
direct lesson of the trio-collapse: seven proposed view-concepts,
zero survived, every one folded into an existing primitive.

---

## 7. Cross-references

**Substrate (must read first):**

- `aidocs/semantics/95-shacl-templates-and-individuals.md` —
  shapes-as-templates / views / agent-contracts; named individuals;
  F(AI)²R Part 15.
- `aidocs/semantics/96-upper-ontology-alignment.md` — BFO / IAO /
  IOF / PROV-O alignment.

**Persona-review sources (the seven inputs):**

- `aidocs/agent-findings/persona-review-ontologist.md`
- `aidocs/agent-findings/persona-review-rdm.md`
- `aidocs/agent-findings/persona-review-api-scrutinizer.md`
- `aidocs/agent-findings/persona-review-ai-opportunities.md`
- `aidocs/agent-findings/persona-review-digital-native.md`
- `aidocs/agent-findings/persona-review-reluctant-senior.md`
- `aidocs/agent-findings/persona-review-ime-aqe.md`

**Coordinating designs:**

- `aidocs/platform/87-timeseries-appid-migration.md` — 5-tuple →
  shepardId; gates §4.6.
- `aidocs/platform/47-dev-experience-and-plugin-system.md` —
  PayloadKind / FileStorage SPI; §4.4 lands here.
- `aidocs/workflows/54-templates-as-first-class-entity.md` —
  `:ShepardTemplate` infra (versioning, retire, allow-list,
  instantiation prov, import/export); §2.1 reuses everything.
- `aidocs/integrations/67-unhide-publish-plugin.md` — first
  export-shape consumer; §4.3 mapping table feeds it.
- `aidocs/integrations/77-databus-moss-federation.md` — second
  export-shape consumer; §4.3 mapping table feeds it.

**To-be-written siblings (this doc commits to writing):**

- `aidocs/semantics/97-canonical-iris.md` — namespace freeze (§4.9).
- `aidocs/semantics/99-export-shape-mapping.md` + JSON sibling at
  `aidocs/semantics/contexts/export-shape-mapping.json` — canonical
  mapping table (§4.3).
- `aidocs/semantics/contexts/mffd-context.jsonld` — the JSON-LD
  context the Ontologist review flagged as missing.

**Project memory anchors:**

- `feedback_shacl_single_source_of_truth.md` — SHACL is the
  authoritative source for domain data shape.
- `feedback_ontology_first.md` — substrate check before naming a
  new concept.
- `feedback_agent_clarify_first.md` — `[NEEDS-CLARIFICATION]`
  format used in §5.
- `feedback_agent_worktree_must_commit.md` — why this doc was
  stubbed-and-committed before enrichment.
- `feedback_appid_to_shepardid.md` — every new wire endpoint emits
  `shepardId` (§4.6).
- `feedback_basic_advanced_superset.md` — advanced mode shows
  literal-bucket attributes alongside typed (§4.5).

**Upgrade-path ledger:**

- `aidocs/34-upstream-upgrade-path.md` — row for this work to be
  added when the first Phase-1 PR lands.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — rows added /
  flipped per `feature-shipping PR` convention.

---

*End of doc 98. This replaces the lost trio with one tighter
artefact shaped by what seven persona reviews independently said
the trio should have said.*
