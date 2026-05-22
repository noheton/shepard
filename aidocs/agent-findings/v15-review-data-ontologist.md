# v15 review — Data & Process Ontologist lens

**Reviewer:** Data & Process Ontologist agent (CLAUDE.md Role 2)
**Scope:** does `aidocs/integrations/93-mffd-import-v15-requirements.md` fully realize the
data-forging vision (`project_dataset_forging.md`) + the per-artefact AI-provenance
requirement (`project_ai_human_collab_provenance.md`)?
**Date:** 2026-05-22

---

## Vision check: data forging snapshot chain

**Verdict: PARTIAL. v15 ships per-batch `fair2r:AuthoringPass` Activities, but
the snapshot brackets that ARE the forging-stage boundaries are entirely
absent from the locked spec.** The word "snapshot" appears zero times in
`93-mffd-import-v15-requirements.md` §§1–14. v14 (`mffd-dropbox-import.py`)
takes one post-import snapshot (line 1820) and one bootstrap t=0 snapshot
guarded by `--bootstrap` (line 1060) — neither is mentioned in v15's
requirements §14 sequencing or §13 acceptance criteria, so they're an
inheritance accident rather than a vision-aligned guarantee.

The forging vision says: **snapshot → mutate → snapshot**, repeated. v15's
import IS one forging pass — the largest one the dataset will undergo —
and its boundaries should be the canonical t=0 and t=1 snapshots for the
whole MFFD-Dropbox collection. Without them: an auditor cannot answer
"what was the collection's state just before the import started" or
"what is the canonical post-import baseline I should diff against" without
guessing.

Additionally, the snapshot entities are not typed as `prov:Entity` (today
they exist only as Shepard LPG nodes with `appId` + `name` + `capturedAt`),
so the chain `snapshot-t0 → AuthoringPass#1 → snapshot-batch1 → … → snapshot-t1`
is **not queryable as a typed PROV graph**. It exists structurally but not
semantically.

---

## Gap matrix

| # | Gap | Why it matters | Concrete fix | Script-only or backend? |
|---|---|---|---|---|
| **G1** | No pre-import snapshot (`bootstrap-t0@<coll>`) in v15's normal flow | Cannot reconstruct the "before" state. v14's `--bootstrap` path is the only place this exists; v15 §14 doesn't invoke it. | At step `[3. YOU — cube]` insert: `POST /v2/collections/{appId}/snapshots {name:"v15-pre-import-t0", description:"…"}` BEFORE the producer thread enqueues the first task. Capture the snapshot appId — emit it in the bootstrap PROV fragment as `prov:wasInformedBy` source for batch #1. | **Script-only.** Endpoint exists (`CollectionSnapshotRest:52`). |
| **G2** | No per-DO AI-mode annotation. v15 emits **batch-level** Activities with `prov:generated do:X, do:Y, …` — but each DO doesn't carry `provenance:mode "ai"` back-reference in the SHACL graph | EU AI Act Art-50 + project_ai_human_collab_provenance memory require **per-artefact** visibility. A facet/badge cannot be rendered if the flag is computed per-batch instead of attached per-entity. Also violates the no-parentless-claim invariant where the AI-typed Claim itself must declare `fair2r:modeOfProduction`. | Per DO in the batch fragment add: `do:<appId> fair2r:modeOfProduction "ai" ; fair2r:wasGeneratedByAi agent:claude-opus-4-7 ; fair2r:wasAcceptedAs "auto-applied" .` (file moves are pure agent work, no human review per DO.) | **Script-only.** Predicates exist in f(ai)²r vendor ontology. |
| **G3** | Mid-import snapshots absent | A failed import 4000 DOs in cannot be "inspected at last-good checkpoint"; the forging vision's per-stage addressability collapses to "all-or-nothing". | Every N=500 DOs (or per natural boundary: PlyGroup-batch complete, Frame-batch complete), emit `POST /v2/collections/{appId}/snapshots {name:"v15-import-batch-<n>", description:"<sequence>"}`. Reference snapshot appId in the batch Activity's `prov:generated` list. | **Script-only.** |
| **G4** | Post-import snapshot is inherited from v14 muscle memory, not in v15 §13/§14 | If a script refactor accidentally drops the line, no acceptance criterion catches it. The t=1 snapshot is the canonical "as-imported" baseline cited by every downstream forging pass (wiki-extract, ODIX, AI reorg). | Add to §13 acceptance: `✓ Two collection snapshots exist: name="v15-pre-import-t0" capturedAt < import_start AND name="v15-post-import-t1" capturedAt > import_end`. Add to §14 step [3]: explicit `create_snapshot(...)` call after `ensure_standalones`. | **Script-only + spec.** |
| **G5** | Snapshots not typed as `prov:Entity` in SHACL graph | The "snapshot chain IS the provenance" claim is structural, not semantic. SPARQL cannot follow `?snap prov:wasGeneratedBy ?activity` because snapshots aren't in the SHACL graph at all. | After each snapshot creation, emit to `/v2/semantic/{repoAppId}/import`: `snap:<appId> a prov:Entity, shepard:CollectionSnapshot ; prov:wasGeneratedBy act:<sister-activity> ; prov:specializationOf coll:<collAppId> ; prov:atTime "<iso>" ; rdfs:label "<name>" .` And a sister `act:snap-<uuid7> a fair2r:AuthoringPass ; prov:generated snap:<appId> .` | **Script-only.** No backend change — the semantic repo already accepts arbitrary IRIs. **Long-term backend:** wire `SnapshotService.create` to emit this automatically (PROV1a extension). |
| **G6** | No-parentless-claim invariant violated for per-DO claims | `aidocs/95 §14e`: every `fair2r:Claim` MUST have `prov:wasGeneratedBy → Activity`. v15's design types DOs as `prov:Entity` (cheaper, no `claimText`) — fine for file movers — BUT the moment any DO carries an AI-inferred attribute (e.g. inferred frame number, classified track-segment), it becomes a Claim and the invariant kicks in. v15 silently mixes both kinds without a discriminator rule. | Add explicit rule to §10: "DO is `fair2r:Claim` iff v15 set any attribute the source didn't carry (inferred). Otherwise `prov:Entity`. Mixed batches: emit two `prov:generated` lists keyed by type." | **Script-only + design clarity.** |
| **G7** | Cross-DO lineage edges (predecessorIds) live in Neo4j only | The MFFD DAG is the production digital thread. v15 sets `predecessorIds[]` in the v1 wire (Bug I fix), which writes Neo4j `:PREDECESSOR_OF` edges — but no `prov:wasDerivedFrom` / `mffd:hasInputProcessStep` triple is emitted to the SHACL graph. ODIX, SHACL queries, RO-Crate exports all miss the lineage. | Per DO with `predecessorIds`, emit: `do:<appId> prov:wasDerivedFrom do:<predAppId>, do:<predAppId>, … ; mffd:directlyFollows do:<predAppId> .` Bundle these in the same batch Turtle fragment. | **Script-only.** |
| **G8** | Acceptance ladder `wasAcceptedAs` value not declared per DO | `project_ai_human_collab_provenance.md` defines 4 levels (auto-applied / unchecked / as-is / human-edited). v15's import is pure `auto-applied` (no human-per-DO gate) — but without explicit declaration, the badge UI cannot distinguish "AI-imported, no review" from "AI-suggested, human-accepted". | Same as G2 above — declare `fair2r:wasAcceptedAs "auto-applied"` on every v15-generated DO. | **Script-only.** |
| **G9** | Snapshot creation itself has no provenance Activity | Snapshots are write actions performed by Claude-opus-4-7 on behalf of fkrebs. Per the conversation-is-prov principle, each snapshot creation deserves its own `fair2r:AuthoringPass` Activity. Without it, the snapshot chain has Entities but no Activities between them — half the PROV graph. | Wrap each `create_snapshot()` in a `fair2r:AuthoringPass`. The Activity's `prov:generated` is the snapshot entity (G5). Tiny — 4 triples per snapshot. | **Script-only.** |
| **G10** | No `aidocs/106-dataset-forging.md` cross-reference in v15 spec | v15 is the **first large forging pass** that will define how Shepard imports establish forging stages going forward. The spec should ground its snapshot behaviour in the forging doctrine, not invent it ad-hoc. | Add §3.5 "Dataset forging stage boundaries" citing `project_dataset_forging.md` + planned `aidocs/106`. State explicitly: pre-snapshot + post-snapshot + named mid-snapshots are MANDATORY per forging doctrine. | **Spec only.** |

---

## Per-DO AI annotation — what's missing

What v15 currently plans (per `data-ontologist-prov-o-v15.md` lines 64–115):

```turtle
# Batch-level Activity references the DO list:
act:0192a14b-3c00-7a44-9f12-c0ffeebabe11
    a fair2r:AuthoringPass ;
    prov:generated do:0192a14b-3c01-7a44-9f12-c0ffee000001 ,
                   do:0192a14b-3c01-7a44-9f12-c0ffee000002 .

# Only ONE DO in the spec carries the back-reference + verification state:
do:0192a14b-3c01-7a44-9f12-c0ffee000001
    a shepard:DataObject , fair2r:Claim ;
    prov:wasGeneratedBy act:0192a14b-3c00-7a44-9f12-c0ffeebabe11 ;
    fair2r:verificationState verif:unverified ;
    fair2r:claimText "Imported from nuclide-edge dropbox; …" .
```

What v15 **should also emit** for EVERY generated DO in the batch (closes G2/G6/G7/G8):

```turtle
do:<appId>
    a shepard:DataObject ;
    # PROV chain (no-parentless invariant — applies even when not typed as Claim)
    prov:wasGeneratedBy act:<batchActivityIri> ;
    prov:wasAttributedTo agent:claude-opus-4-7 , usr:fkrebs-at-nucli-de ;

    # f(ai)²r per-artefact mode (G2/G8)
    fair2r:modeOfProduction "ai" ;
    fair2r:wasGeneratedByAi agent:claude-opus-4-7 ;
    fair2r:wasAcceptedAs "auto-applied" ;
    fair2r:verificationState verif:unverified ;

    # Forging-stage entry point (G5 sister edge)
    prov:wasInformedBy snap:<preImportT0AppId> ;

    # Cross-DO lineage in SHACL graph (G7)
    prov:wasDerivedFrom do:<predAppId> ;          # one per predecessorId
    mffd:directlyFollows do:<predAppId> ;

    # When inferred attributes exist, ALSO add (G6 discriminator):
    a fair2r:Claim ;
    fair2r:claimText "<the inferred attribute payload, e.g. 'inferred Frame=12 from filename pattern'>" .
```

Snapshots themselves (closes G5/G9):

```turtle
snap:<appId>
    a prov:Entity , shepard:CollectionSnapshot ;
    rdfs:label "v15-pre-import-t0" ;
    prov:specializationOf coll:<mffdDropboxAppId> ;
    prov:atTime "2026-05-22T20:00:00Z"^^xsd:dateTime ;
    prov:wasGeneratedBy act:snap-<uuid7> .

act:snap-<uuid7>
    a fair2r:AuthoringPass ;
    rdfs:label "v15 pre-import t0 snapshot" ;
    prov:wasAssociatedWith agent:claude-opus-4-7 , usr:fkrebs-at-nucli-de ;
    prov:used src:01923f7e-import-mffd-v15 ;
    prov:generated snap:<appId> ;
    shepard:targetCollection coll:<mffdDropboxAppId> .
```

---

## Snapshot chain — what's missing

What v15 §14 sequences today (informal — paraphrased from the spec):

```
[start] → 4-worker pool → drains queue → ensure_standalones → [end]
                                                                  ↑
                                                              (v14 inherits a post-import snapshot here)
```

What the data-forging vision REQUIRES:

```
[start]
  │
  ▼
┌─────────────────────────────┐
│ snap:v15-pre-import-t0      │  ← G1 (forging stage entry, addressable, typed)
│ a prov:Entity, shepard:…    │
└─────────────────────────────┘
  │ wasInformedBy
  ▼
┌─────────────────────────────┐
│ act:batch-1                 │
│ a fair2r:AuthoringPass      │
│ prov:generated DOs[0..99]   │
└─────────────────────────────┘
  │ followed by
  ▼
┌─────────────────────────────┐
│ snap:v15-import-batch-1     │  ← G3 (mid-import checkpoint, optional but recommended every ~500 DOs)
└─────────────────────────────┘
  │
  ▼
… repeat 80–100 batches …
  │
  ▼
┌─────────────────────────────┐
│ act:batch-N (final)         │
└─────────────────────────────┘
  │
  ▼
┌─────────────────────────────┐
│ snap:v15-post-import-t1     │  ← G4 (forging stage exit — canonical baseline)
│ "as-imported MFFD-Dropbox"  │
└─────────────────────────────┘
  │
  ▼
[next forging pass: wiki-extract / ODIX / AI-reorg consumes t1 as input]
```

Each arrow is a typed PROV-O edge in the SHACL graph — `prov:wasInformedBy`,
`prov:wasGeneratedBy`, `prov:wasDerivedFrom`. The whole chain becomes a single
SPARQL traversal. **Today it's reconstructible only by reading wall-clock
timestamps and guessing.**

---

## Recommendations ordered by leverage

1. **G1 + G4 + G5 (one PR):** add pre-import snapshot, post-import snapshot,
   and emit both as typed `prov:Entity` to the semantic repo. Script-only.
   ~30 lines of Python + 8 new triples per snapshot. Closes the
   biggest gap: the forging vision becomes a real, queryable graph instead
   of a wall-clock-only sequence. Add the matching acceptance criterion to
   §13 so the script can't silently lose this in a refactor.

2. **G2 + G8 (one PR, two extra triples per DO):** declare
   `fair2r:modeOfProduction "ai"` + `fair2r:wasAcceptedAs "auto-applied"` on
   every generated DO. Closes the EU AI Act Art-50 per-artefact-visibility gap
   surfaced by `project_ai_human_collab_provenance.md`. Without this, no UI badge
   can be rendered downstream — the data simply isn't there. Tiny script change;
   the predicates already exist in the f(ai)²r vendor ontology vendored at TPL9a.

3. **G7 (one PR):** mirror Neo4j's `:PREDECESSOR_OF` into SHACL as
   `prov:wasDerivedFrom` + `mffd:directlyFollows`. The MFFD DAG becomes
   visible to ODIX, SHACL queries, and RO-Crate export. ~5 lines of Python
   per DO emission. **Unlocks ODIX-on-imported-data immediately** (currently
   ODIX has to read Neo4j directly, breaking the substrate-split discipline
   in `feedback_shacl_single_source_of_truth.md`).

4. **G3 + G9 (one PR):** mid-import snapshots every 500 DOs, each with its
   own creation Activity. Cheap (1 REST call + 4 triples per 500 DOs ≈ 70
   total over the full 8383-DO run). Pays back the moment a failed import
   needs forensic inspection.

5. **G6 + G10 (spec-only PR):** add the Claim-vs-Entity discriminator rule
   to §10, and add the dataset-forging citation to §3.5. Zero runtime cost,
   prevents future drift, and makes the forging doctrine load-bearing in the
   spec rather than inherited muscle memory.

**Combined effort:** ~120 lines of Python + 1 spec PR. **No backend changes
required** — every endpoint (snapshots, semantic import, semanticAnnotations,
PROV1a capture) already exists. This is purely a discipline gap in the v15
spec, not a missing capability.
