---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# v15 import review — RDM / FAIR Data Steward lens

**Reviewer.** Claude (RDM/FAIR role per CLAUDE.md Role 5) on behalf of fkrebs@nucli.de.
**Scope.** `aidocs/integrations/93-mffd-import-v15-requirements.md` + the
PROV-O fragment design at `aidocs/agent-findings/data-ontologist-prov-o-v15.md`,
against the data-forging vision (`project_dataset_forging.md`), F(AI)²R
modeOfProduction requirement (`project_ai_human_collab_provenance.md`), and
the no-parentless-claim principle (`project_provenance_principle.md`).
**Verdict.** v15 ships a strong PROV-O backbone but **silently skips
three FAIR-load-bearing pieces**: (1) forging-stage snapshots, (2) the
three-mode AI-vs-human-vs-collaborative tag, (3) license/accessRights/PID
hydration on the dest. Importing 8,383 DOs without these is producing a
**provenance-rich but publication-incomplete** dataset.

## FAIR scorecard against v15 output

| Dimension | What v15 produces | What's missing | Where to close |
|---|---|---|---|
| **F1 — globally unique ID** | dest `appId` (UUID v7) on every DO/container | The `uri` HTTPS PID (`aidocs/platform/91`) is designed but not active on the dest instance — bare UUIDs go on the wire | Confirm `shepard.instance.base-url` set on shepard.nuclide.systems **before** v15 runs; otherwise the PROV-O turtle's `do:<appId>` IRIs are not dereferenceable post-import |
| **F2/F3 — rich, indexed metadata** | `attributes` Map (free-text key→string, lossy long-tail), `predecessorIds[]` lineage, batched semanticAnnotation turtle | Domain attribute keys (`propellant`, `bench`, `test_engineer`, `process_step`, etc.) stay as free-text — TPL4 dual-write into typed `fair2r:Claim` triples is **explicitly deferred** (§12). The MFFD import will need a re-forging pass to lift them. | Add a §10b "vocab lift" step: ship a v15-local `attribute → predicate` mapping table for the 8–12 known MFFD keys, emit one `fair2r:Claim` triple per key per DO in the same batched turtle (no extra round-trips). The long-tail stays in `attributes` for TPL4 to handle later. |
| **F4 — registered in resource** | none | Helmholtz Databus / Unhide harvest feed not in scope for v15 | Out-of-scope is correct; **but** record in §13 acceptance that the import is "harvest-ready" only when (a) license set, (b) accessRights set, (c) PID URI hydrated. v15 currently meets none of these. |
| **A1 — retrievable by ID** | `GET /shepard/api/...` works with JWT | Auth-required everywhere (per `aidocs/platform/91 §10`) — fine for now, but the imported dataset has no `publicationState` so no path to A1.2 (unauthenticated retrieval of published artefacts) exists | Not a v15 blocker — but flag in acceptance: "all imported DOs default to `null` publicationState — they're internal-only until LIC1b ships" |
| **A2 — metadata accessible even when data is not** | yes (Neo4j metadata is always queryable; payloads sit behind container access) | n/a | OK |
| **I1 — formal knowledge representation** | per-batch turtle to `POST /v2/semantic/{repoAppId}/import`; SHACL conformance enforced | DataObject attributes still live in Neo4j `Properties(delimiter="\|\|")` map → not in the semantic graph until TPL4. **The import IS interoperable for AI-provenance but NOT for domain attributes.** | Same fix as F2/F3 — vocab lift to triples in v15 closes this. |
| **I2 — vocabularies follow FAIR principles** | f(ai)²r (vendor-tier), PROV-O, dcterms, foaf — all standards-grade | `shepard:filesUploaded` etc. minted in `http://semantics.dlr.de/shepard-upper#` are **not yet published** as a dereferenceable namespace | Out-of-scope for v15; raise as a TPL3 follow-up — every shepard: predicate the import emits must resolve on the public web before external publish. |
| **I3 — qualified references** | full PROV-O `prov:wasAssociatedWith`, `prov:qualifiedAssociation`, `prov:hadRole`, `prov:hadPlan` | none — this is the strongest part of v15 | (keep) |
| **R1 — clear usage license** | **none.** `license` field on `AbstractDataObject` exists (FAIR-1, shipped) but v15 sets it to `null` on every imported DO/Collection | Add a v15 CLI flag `--default-license=<SPDX>` that hydrates `license` + `accessRights` on the dest Collection AND on every DO it creates. Default to `null` is the bug — operator must opt-in to a default. For MFFD-Dropbox: `proprietary` + `accessRights="restricted access"` (DLR industrial IP per `project_mffd_synthetic_vs_real.md`). |
| **R1.1 — license is machine-readable** | n/a (license is null) | (same fix) | emit `dcterms:license` on the Collection IRI in the same batched turtle |
| **R1.2 — detailed provenance** | per-batch `fair2r:AuthoringPass` with executor/operator roles, counts, sourceInstance, targetCollection | snapshot anchors missing — see next section | Critical fix below |
| **R1.3 — community standards** | PROV-O ✓ DCAT/DataCite ✗ schema.org/Dataset ✗ | A DataCite-shaped export is the natural follow-on (shepard-plugin-publisher / Invenio per `aidocs/integrations/72`), not v15 | Out-of-scope OK; note that v15 lays the IRI foundation that the publisher plugin will consume. |

## Snapshot-chain dereferenceability

This is the single biggest gap.

The data-forging vision states: **"each pass is bracketed by snapshots. The
chain of snapshots IS the provenance."** Snapshot infrastructure is shipped
in `de.dlr.shepard.context.snapshot.*` (Snapshot, SnapshotEntry,
SnapshotService, SnapshotPinnedReadRest). v15 makes **zero mention** of
snapshots in its 14 sections.

What v15 should do, per the forging discipline:

| Step | When | Why |
|---|---|---|
| **S0 — pre-import snapshot** | Before the first POST, after Collection is created but empty | The "t=0 forging-stage" anchor. Without this, the import's "what existed before me" is unknowable. |
| **S1 — post-import snapshot** | After the last batch acceptance | The "as-imported" forge stage. The MFFD demo narrative ("the AFP run dataset went through 5 forging stages from raw to publishable") starts here. |
| **Activity link** | The `fair2r:AuthoringPass` activity emits `prov:used <S0-iri>` + `prov:generated <S1-iri>` | Closes the loop — the snapshot pair IS the "what changed" query, machine-queryable in SPARQL |

`feedback_mutate_after_snapshot.md` makes this an explicit operational rule
("Don't mutate dest mid-ingestion — snapshot as-imported state first"). v15
silently violates it: there's no S0, and Garage-activation / log re-upload
mutate the dest before the as-imported state is captured.

**Minimum fix:** add §3b to v15 — pre-flight snapshot creation (idempotent;
named `bootstrap-t0@MFFD-Dropbox-<sourceInstance>`), and §13 acceptance row
"✓ Two named snapshots (`bootstrap-t0` and `as-imported-t1`) on dest, linked
in the final batch's AuthoringPass via prov:used / prov:generated."

## AI-annotation completeness for FAIR-for-AI

Per `project_ai_human_collab_provenance.md` (user directive 2026-05-22): every
artefact must carry a **three-mode** tag — 🧑 human / 🤖 AI / 🤝 collaborative
— **visible** in UI and **machine-readable** on the wire via
`fair2r:modeOfProduction` on the Activity + a derived `_provenanceMode`
field in v2 responses.

What v15's PROV-O fragment (`data-ontologist-prov-o-v15.md`) emits today:

```turtle
do:0192a14b-... a shepard:DataObject , fair2r:Claim ;
    prov:wasGeneratedBy  act:0192a14b-... ;
    prov:wasAttributedTo agent:claude-opus-4-7 , usr:fkrebs-at-nucli-de ;
    fair2r:verificationState verif:unverified ;
    ...
```

What it doesn't emit:

| Predicate | Why required | v15 fix |
|---|---|---|
| `fair2r:modeOfProduction "AI"` on the Activity | The wire field `_provenanceMode` is derived from this — without it the frontend badge defaults to "human" which is **wrong** for every v15-imported DO | One-line addition to the turtle template |
| `fair2r:wasAcceptedAs "auto-applied"` on the Activity | Distinguishes "agent ran without human gate" from human-curated — v15 IS auto-applied (Claude executed without per-batch human accept) | One-line addition |
| `fair2r:wasGeneratedByAi <aiAgent>` derived property on every Claim | Lets SHACL enforce the no-parentless-claim invariant *for the AI mode specifically*; lets SPARQL filter "all auto-applied AI imports this week" | Two lines in the per-entity stanza |

The EU AI Act Art. 50 deadline (2026-08-02) is **74 days from today**
(2026-05-22) per the F(AI)²R review-status table. The MFFD import is the
first large-scale AI-driven write into Shepard and will be the reference
example. Shipping it without modeOfProduction tags makes the dataset
publishable but **not Art-50-compliant** until a forging pass re-tags every
DO retroactively. Cheaper to tag at import time.

**No-parentless-claim invariant.** The v15 PROV-O fragment satisfies this
for entities typed `fair2r:Claim` (every Claim has `wasGeneratedBy → Activity`).
**But the fragment notes (lines 158–162) that DataObjects v15 "merely moves"
stay as `prov:Entity`, not `fair2r:Claim`** — these are the bulk of the 8,383
DOs. They DO carry `wasGeneratedBy` but won't be picked up by Claim-shape
SHACL. Fine for invariant compliance; surfacing for awareness when someone
later asks "why does the AI-provenance facet not show all 8,383?"

## Recommendations ordered by FAIR-impact

1. **R1 license / accessRights — add `--default-license` flag (1 line + 1 hydration call).**
   Without this, the imported dataset is **R1-unpublishable**. Today's
   shipped `license`/`accessRights` fields on `AbstractDataObject` (FAIR-1)
   sit empty across 8,383 DOs and one Collection. Operator-supplied default
   `--default-license=proprietary --default-access-rights="restricted access"`
   for the DLR MFFD case writes both at create-time. Single largest FAIR delta.

2. **R1.2 forging snapshots — bracket the import (S0 before, S1 after).**
   The dataset-forging vision is a CLAUDE.md-load-bearing principle and v15
   silently skips the discipline. Two snapshot calls (+ two IRIs in the
   AuthoringPass turtle) close the loop. Without this, every downstream
   "what did v15 actually change?" question must be answered by inference
   against git+state-file, not by graph query.

3. **F2/I1 vocab lift — emit `fair2r:Claim` triples for the top-12 MFFD attributes.**
   Defer TPL4 dual-write (§12) is **fine** for the long tail, but the dozen
   known MFFD keys (propellant, bench, test_engineer, process_step,
   material_batch, etc.) cost ~30 lines to lift inline. The alternative is a
   second forging pass that re-walks 8,383 DOs and re-emits annotations.
   Cheap now, expensive later.

4. **F(AI)²R modeOfProduction — three triples per batch + one per entity.**
   Closes the EU AI Act Art-50 gap (deadline 2026-08-02). One Activity
   triple (`fair2r:modeOfProduction "AI"`), one Activity acceptance triple
   (`fair2r:wasAcceptedAs "auto-applied"`), and a derived `_provenanceMode`
   on the Wire (which the existing UI badge code can already consume per
   the memory). Total impl: ~6 lines in the v15 turtle template.

5. **F1 PID hydration — verify `shepard.instance.base-url` set before import.**
   Add a v15 pre-flight assertion: `GET /v2/admin/instance-config` returns
   a non-null base-url. If unset, exit with the same runbook-style message
   as the Garage check. Without this, the post-import PROV-O turtle's
   `do:<appId>` IRIs collapse to bare UUIDs that no third party can resolve.

---

**Out-of-scope but worth noting:** the publisher path (Invenio per
`aidocs/integrations/72`; Unhide per `aidocs/integrations/67`) is the
natural consumer of a v15-forged dataset. If v15 ships recommendations 1–4
above, the publisher plugin work becomes mostly mapping; if v15 ships
without them, the publisher plugin will need a "re-forge before publish"
pass that retroactively tags 8,383 DOs. Pay now or pay later.
