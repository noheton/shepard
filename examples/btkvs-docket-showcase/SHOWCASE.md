---
stage: deployed
last-stage-change: 2026-05-29
audience: BT-KVS group external + contributors + RDM
---

# BT-KVS docket showcase — C/SiC fabrication campaign

**What this is:** the third operational showcase dataset for Shepard
(after LUMEN hot-fire and MFFD AFP), modelling the **DLR BT-KVS group's
C/C and C/SiC fiber-reinforced-composite docket** tracking workflow.
One canonical docket (`I123`, a polymerised + tempered + pyrolised
(×2) + siliconised plate) decomposed into the Shepard primitives per
the BTKVS-A4 improved schema design
([`aidocs/integrations/116-btkvs-improved-schema.md`](../../aidocs/integrations/116-btkvs-improved-schema.md)).

**Why it matters:** the operator-uploaded BT-KVS package
(`Nils_Packet_fuer_Claude` — full analysis in
[`aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md`](../../aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md))
already ships a working `templates_api.py` that talks live to a
Shepard instance for **form-based docket entry** — making this the
strongest single validator of the T1 / TPL1 / TPL2 / SHACL
template-as-form design line. The seed lays the canonical data shape
so the future BTKVS-A3 (server-side decompose endpoint), BTKVS-B1
(SHACL shapes), and BTKVS-C1/C2 (`shepard-plugin-btkvs` proper) all
target a known graph topology.

**Live verification (2026-05-29):** Collection
`019e73b4-a737-72b0-ac11-ba1064bb90fc` on
[`shepard.nuclide.systems`](https://shepard.nuclide.systems) — 12
DataObjects + 11 StructuredDataReferences landed. Re-running the seed
produces 13 `SKIP` lines, zero writes (idempotent).

**Form-template slice (BTKVS-B2, 2026-06-12):**
[`seed_form_template.py`](seed_form_template.py) registers the docket
`:general` section as a `STRUCTURED_RECIPE` template whose SHACL
`shapeGraph` is authored through the `ShaclShapeBuilder` JSON DSL
(`POST /v2/shapes/build`) — DASH editor hints, `sh:pattern
"^[A-Z][0-9]{3}$"` on the Docket-ID, and two Excel cell-mappings
(Docket-ID → `K1`, Project → `C4`). `GET /v2/templates/{appId}/form`
compiles it into the doc-125 §5.1 descriptor;
[`form_demo.py`](form_demo.py) (Streamlit) renders + submits it, and a
pattern-violating Docket-ID round-trips as an inline field error via
the structured 422 `violations[]`.

---

## 1. Source data

A single canonical v3 docket JSON, embedded verbatim into `seed.py`'s
`DOCKET_I123` constant. The original `example.json` lives at
`/tmp/nils-cc-csic-showcase/Nils_Packet_fuer_Claude/example.json`
(operator upload — never copied into the repo per
[`feedback_uploads_never_in_repo.md`](../../aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md));
the embedded copy in `seed.py` exists so the seed is self-contained
and stable across operator-upload churn.

The docket models a synthetic C/SiC plate that went through:

| # | Step | Method | Cycle | Editor |
|---|------|--------|-------|--------|
| 1 | Polymerisation | RTM | "RTM Zyklus" | Me — 2025-11-24 |
| 2 | Tempering | thermal cure | 240 °C / 2 h | Me — 2025-12-01 |
| 3 | Pyrolysis (pass 1) | inert gas | 1000 °C, burn `OP123` | Me — 2026-01-29 |
| 4 | Pyrolysis (pass 2) | — | — | (no editor stamp) |
| 5 | Siliconization | LSI | 1142 °C, burn `OS123` | Me — 2026-02-12 |

Every step carries the same `post_analysis` sub-shape (NDT methods,
sampling, density+porosity, strength_analysis, part_measurement,
damage). NOT REAL DLR/BT-KVS data — synthetic showcase only.

---

## 2. Mapping to Shepard primitives (the BTKVS-A4 refinements applied)

Per [`aidocs/integrations/116-btkvs-improved-schema.md`](../../aidocs/integrations/116-btkvs-improved-schema.md),
which itself revises the operator's original `Shepard_Database_Setup.drawio`
sketch.

### 2.1 Refinement 1 — linear `:Predecessor` chain on process steps

The original drawio sketch hung the four process-step DOs as siblings
under the root docket DO (tree shape). The seed instead writes a
**linear chain**: each step's create body carries one
`typedPredecessors` entry pointing at the previous step with
`relationshipType: "prov:wasInformedBy"` (the PROV1k default,
shipped via
[`TypedPredecessorIO`](../../backend/src/main/java/de/dlr/shepard/v2/dataobject/io/TypedPredecessorIO.java)).

```
Docket I123 ──CHILD_OF─┐
                       ├─ Structure I123        (structure JSON)
                       ├─ Polymerisation        (head of chain)
                       │   └─ Post-analysis: Polymerisation
                       ├─ Tempering         ←─[:Predecessor wasInformedBy]── Polymerisation
                       │   └─ Post-analysis: Tempering
                       ├─ Pyrolysis (pass 1) ←─[:Predecessor wasInformedBy]── Tempering
                       │   └─ Post-analysis: Pyrolysis (pass 1)
                       ├─ Pyrolysis (pass 2) ←─[:Predecessor wasInformedBy]── Pyrolysis (pass 1)
                       │   └─ Post-analysis: Pyrolysis (pass 2)
                       └─ Siliconization     ←─[:Predecessor wasInformedBy]── Pyrolysis (pass 2)
                           └─ Post-analysis: Siliconization
```

This unlocks the f(ai)²r provenance walk, the
`CollectionLineageGraph.vue` rendering, the permission walk-inherit
pattern, and the Trace3D view recipe — see
[`aidocs/integrations/116-btkvs-improved-schema.md §2.1`](../../aidocs/integrations/116-btkvs-improved-schema.md)
for the full list.

### 2.2 Refinement 2 — per-`post_analysis` DataObject

The original drawio nested `post_analysis` JSON inside each step's
`StructuredDataReference`. The seed instead creates a separate
`btkvs:post-analysis` child DO per step, carrying the
`post_analysis` JSON section as its own SDR.

This gives NDT outputs (CT volumes, X-ray PNGs, micrometer CSVs) a
natural home — they attach as peer `FileReference` rows on the
post-analysis DO, beside the JSON metadata. The seed currently
attaches only the JSON SDR (no NDT file artefacts in the synthetic
docket); a real-data run would also attach the corresponding files.

### 2.3 Refinement 3 — editor stamps → `:Activity` (DEFERRED in this seed)

The v3 docket carries `editor: {name, date}` on every step **and** on
every sub-step (`sampling.editor`, `density_porosity.editor`,
`ndt[N].editor`, …). Per
[`aidocs/integrations/116-btkvs-improved-schema.md §2.3`](../../aidocs/integrations/116-btkvs-improved-schema.md),
the right shape is one `:Activity` per editor stamp wired with PROV-O
edges (`WAS_ASSOCIATED_WITH → :User`, `GENERATED → step DO`,
`USED → input materials`).

**The seed defers this.** Each step DO carries `editor_name` +
`editor_date` as plain attributes (so the data is captured and
queryable) but does NOT mint `:Activity` rows or
`:MirroredUser` placeholders. The server-side decompose endpoint
(BTKVS-A3) will own that decomposition natively — minting Activities
from a seed script via a REST shape that doesn't exist yet would
double up the work and lock the API surface prematurely.

See the `_decode_editor` TODO in `seed.py` for the cross-reference.

---

## 3. What the seed validates for T1 / template-as-form

The operator-supplied `templates_api.py` already implements
**form-rendering driven by templates pulled live from a configured
Shepard "Process Definitions" container**. That means BT-KVS already
treats Shepard as the source-of-truth for form shapes — exactly the
T1 pattern this fork has been building toward
([`aidocs/16-dispatcher-backlog.md` T1 row](../../aidocs/16-dispatcher-backlog.md);
[`feedback_template_driven_create_all_refs.md`](../../aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md);
the SEMA-V6 audit).

This seed validates:

1. **Predecessor chain shape works for sequential manufacturing.** The
   PROV1k `prov:wasInformedBy` edge primitive is the right semantic for
   "step N built on the output of step N−1" — no new edge type needed
   for the BT-KVS use case. The `fair2r:repairs` variant is available
   for re-pyrolysis-after-NCR scenarios without further schema work.
2. **Post-analysis-as-child-DO scales.** Promoting `post_analysis` out
   of nested JSON into its own DO sets up the pattern for the NDT
   plugin family — a future `shepard-plugin-ct-porosity-analyser`
   consumes the `:FileReference` on the post-analysis DO and writes
   `:SemanticAnnotation` rows back without touching the docket-root
   DO at all.
3. **One SDC per Collection suffices.** All 11 JSON sub-sections in
   the docket ride through a single `StructuredDataContainer`
   ("BTKVS dockets — …"), addressed by per-document
   `structuredDataOids[]` from the SDR. This is the right shape for
   the BT-KVS instance (one SDC per institute-Collection).
4. **The seed's `:Predecessor` writes match the lineage-graph
   renderer.** Open the Collection in the UI: the
   `CollectionLineageGraph` shows the five step DOs as a chain (not a
   tree), which is the visual proof refinement 2.1 lands correctly.

---

## 4. What the controlled-vocab seed (BTKVS-A2) covers

`seed_vocab.py` is the A2 companion. It reads the operator's
`fiber_database` + `fabric_database` Python dicts (9 fibers, 20
fabrics) from
`/tmp/nils-cc-csic-showcase/_unpacked/laufzettel-readout-main/src/material_database.py`
(operator upload — never copied into the repo), generates an in-memory
Turtle ontology bundle, and multipart-uploads it via
`POST /v2/admin/semantic/ontologies` (the N1c2 admin endpoint).

The generated TTL declares:

- `urn:btkvs:Fiber` and `urn:btkvs:Fabric` as `owl:Class`.
- 14 `owl:DatatypeProperty` rows (one per material attribute —
  `urn:btkvs:material:density`, `urn:btkvs:material:weightPerArea`, …).
- 9 fiber individuals (`urn:btkvs:fiber:HTA`, `urn:btkvs:fiber:T800`,
  `urn:btkvs:fiber:SiC`, …) with `rdfs:label` + class membership +
  datatype-property triples for every non-null material attribute.
- 20 fabric individuals (`urn:btkvs:fabric:98140`,
  `urn:btkvs:fabric:Style_840`, `urn:btkvs:fabric:PSA-S17I16PX`, …)
  with the same shape.

The Turtle generator handles bool / int / float / str coercion to the
appropriate XSD literal type, and replaces whitespace in fabric-style
IRI suffixes (`"Style 840"` → `urn:btkvs:fabric:Style_840`).

### Status on the live instance — BLOCKED on deployment

The seed code was verified end-to-end against the
[admin upload endpoint spec](../../backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java#L355-L471).
Running against
[`shepard-api.nuclide.systems`](https://shepard-api.nuclide.systems) on
2026-05-29 returns:

```
generated Turtle bundle: 26267 bytes, 9 fibers, 20 fabrics
FAIL   status=500  body=b'{"type":"/problems/semantic.bundle.invalid-ttl",
                          "title":"Upload failed","status":500,
                          "detail":"could not write bundle to disk: /var/lib/shepard"}'
```

This is a **deployment configuration issue**, not a seed-code bug. The
backend tries to write to `<shepard.semantic.internal.user-bundles-dir>`
(default `/var/lib/shepard` per
[`SemanticAdminRest.java`](../../backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java#L362)),
which the current nuclide deployment doesn't expose as a writable
volume. A2 is **shipped but parked behind this infra knob** — the seed
will succeed unmodified once the deploy gains a writable user-bundles
dir. Tracked as a follow-up row; see §6 below.

The bundle metadata used:

```json
{
  "id": "btkvs-materials",
  "name": "BT-KVS Materials (fibers + fabrics)",
  "iriPrefix": "urn:btkvs:",
  "canonicalUrl": "urn:btkvs:",
  "license": "CC-BY-4.0"
}
```

---

## 5. Files in this showcase

| File | Role |
|------|------|
| `seed.py` | BTKVS-A1 — Collection + 12 DOs + 11 SDRs (idempotent). |
| `seed_vocab.py` | BTKVS-A2 — material controlled-vocab Turtle upload (parked behind deploy infra). |
| `SHOWCASE.md` | This narrative. |
| `README.md` | Three-line operator quick-start. |

---

## 6. Open questions (the queued follow-ups)

1. **A2 user-bundles-dir on the nuclide deploy.** The
   `shepard.semantic.internal.user-bundles-dir` knob needs a writable
   bind mount on the dev-box deploy before A2 can finish landing on
   live. Filed against the deployment, not the code.
2. **Editor → `:Activity` minting** (refinement 3) — deferred to
   BTKVS-A3 (the server-side decompose endpoint). The seed leaves
   `editor_name`/`editor_date` as attributes so the data is captured.
3. **N1c2 ontology consumption depth** — open question whether the
   N1c2 indexer ingests datatype-property triples (density,
   area_density, …) into the term-search UI or only class-membership
   triples. Either way the SPARQL surface gets the full TTL; the
   uncertainty is only on the search-affordance side. Worth a quick
   probe once A2 unblocks: search for "HTA" in the term-picker UI —
   it should match either via `rdfs:label` or via the IRI suffix.
4. **NDT files on post-analysis DOs.** Synthetic docket has no real
   file artefacts. A real-data ingestion run from a BT-KVS oven /
   CT-scanner will attach `:FileReference` rows on each
   post-analysis DO — same primitive shape, no schema work needed.
5. **Pyrolysis-pass naming.** Currently the seed uses
   `"Pyrolysis (pass 1)"` / `"Pyrolysis (pass 2)"` as the display
   name. BTKVS-A4 §5.5 recommended `name: "Pyrolysis"` + an
   `attributes.pass` structured key instead. Easy to flip in a
   follow-up if Nils prefers — the lineage-graph still renders
   correctly either way.

---

## 7. Acceptance criteria — met on 2026-05-29

| Criterion | Status |
|-----------|--------|
| Collection + 12 DOs land on live | ✓ — Collection `019e73b4-a737-72b0-ac11-ba1064bb90fc` |
| Linear `:Predecessor` chain across 5 process steps | ✓ — `typedPredecessors[{relationshipType:"prov:wasInformedBy"}]` per step |
| Per-step post-analysis child DO with JSON SDR | ✓ — 5 post-analysis DOs, 5 post-analysis SDRs |
| Idempotent — re-running produces zero writes | ✓ — second run prints 13 SKIP, 0 OK |
| `--reset` recreates from scratch | ✓ — DELETE Collection cascades, recreate succeeds |
| BTKVS-A2 controlled-vocab seed prototype | ✓ — TTL generator + multipart upload verified at code level; live upload **blocked on deploy infra** (not the seed) |
| Editor stamps → `:Activity` | DEFERRED — by design (see BTKVS-A4 §2.3, BTKVS-A3 owns this) |

Tracker rows: see
[`aidocs/16-dispatcher-backlog.md` BTKVS-A1 + BTKVS-A2](../../aidocs/16-dispatcher-backlog.md).
