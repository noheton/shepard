---
stage: deployed
last-stage-change: 2026-05-28
purpose: First comprehensive pass of the Documentation Aligner role — fight doc sprawl, fix dead cross-references, bump stale "planned" notes for shipped features.
---

# Doc Alignment — 2026-05-28

**Branch:** `doc-align-2026-05-28`
**Worktree:** `/tmp/shepard-doc-align` (off `main` at `b2d40b525`)
**Dispatcher:** `feedback_continuous_doc_maintenance.md` + `feedback_ssot_per_concept.md` (standing role; backlog row `DOC-ALIGN-STANDING`).
**Time budget:** ~3 hours; stopped after high-leverage SURE finds were applied. Lower-leverage UNSURE items are surfaced below for the operator to decide.

---

## Summary

- **34 files edited** across three commit batches.
- **32 dead aidocs/* cross-references fixed** (target file moved into a subdirectory but inbound links still pointed at the old root-level path).
- **4 status-board entries flipped** from "designed/queued" to "shipped" for features that landed in the last two weeks (AI1b, SPATIAL-V6-001, FS1b, M4I-a/b/f).
- **1 new backlog row** filed: `DOC-ALIGN-STANDING` in `aidocs/16-dispatcher-backlog.md` (records this pass + sets a next-pass trigger).
- **0 files deleted.** The redundant-SSOT scan surfaced candidates but none had zero inbound references; deletion stayed conservative.
- **0 `stage:` frontmatter bumps.** Several candidates surfaced; none were obvious enough to execute unilaterally — surfaced as UNSURE.

---

## What I changed

### Commit 1 — `docs(align): dead-link-fix — repoint moved aidocs paths to current subdirs`

A rename map (`/tmp/rename-map.txt`) mapped 26 old root-level `aidocs/<NN>-<slug>.md` paths to their current subdirectory locations and was applied across live docs (excluding `aidocs/agent-findings/` and `aidocs/archive/` per dispatcher scope). 32 files modified.

Representative mappings applied:

| Old path | New canonical path |
|---|---|
| `aidocs/47-dev-experience-and-plugin-system.md` | `aidocs/platform/47-dev-experience-and-plugin-system.md` |
| `aidocs/63-architecture-decision-log.md` | `aidocs/platform/63-architecture-decision-log.md` |
| `aidocs/64-provenance-architecture.md` | `aidocs/workflows/64-provenance-architecture.md` |
| `aidocs/55-provenance-and-activity-overhaul.md` | `aidocs/workflows/55-provenance-and-activity-overhaul.md` |
| `aidocs/55-provenance-design.md` | `aidocs/workflows/55-provenance-and-activity-overhaul.md` (folded SSOTs) |
| `aidocs/30-provenance-and-lineage.md` | `aidocs/workflows/30-provenance-and-lineage-design.md` |
| `aidocs/31-rocrate-export.md` | `aidocs/workflows/31-rocrate-export-optimisation.md` |
| `aidocs/35-hdf5-hsds-implementation-design.md` | `aidocs/data/35-hdf5-hsds-implementation-design.md` |
| `aidocs/37-lab-journal-and-jupyter-design.md` | `aidocs/data/37-lab-journal-and-jupyter-design.md` |
| `aidocs/45-gridfs-to-s3-evaluation.md` | `aidocs/data/45-gridfs-to-s3-evaluation.md` |
| `aidocs/48-internal-semantic-repository-design.md` | `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md` (folded SSOTs) |
| `aidocs/48-internal-semantic-repository-via-neosemantics.md` | `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md` |
| `aidocs/53-file-reference-rename-video-content.md` | `aidocs/data/53-file-reference-rename-video-content.md` |
| `aidocs/56-v2-api-simplification-output-profiles-mcp.md` | `aidocs/platform/56-v2-api-simplification-output-profiles-mcp.md` |
| `aidocs/57-openapi-client-generator-evaluation.md` | `aidocs/ops/57-openapi-client-generator-evaluation.md` |
| `aidocs/65-admin-configurable-ontology-preseed.md` | `aidocs/semantics/65-admin-configurable-ontology-preseed.md` |
| `aidocs/65-unhide-publish-plugin.md` | `aidocs/integrations/67-unhide-publish-plugin.md` |
| `aidocs/66-hmc-kip-integration.md` | `aidocs/integrations/66-hmc-kip-integration.md` |
| `aidocs/64-hmc-kip-integration.md` | `aidocs/integrations/66-hmc-kip-integration.md` |
| `aidocs/78-cad-annotator.md` | `aidocs/data/78-cad-geometry-annotator.md` |
| `aidocs/113-urdf-viewer.md` | `aidocs/integrations/113-urdf-viewer.md` |
| `aidocs/22-admin-cli.md` | `aidocs/ops/22-admin-cli-draft.md` |
| `aidocs/24-permission-system-review.md` | `aidocs/platform/24-permission-system-review.md` |
| `aidocs/27-convenience-clients-design.md` | `aidocs/ops/27-convenience-clients-design.md` |
| `aidocs/01-repo-overview.md` | `aidocs/archive/01-repo-overview.md` |
| `aidocs/62-novacrate-evaluation.md` | `aidocs/data/r4-novacrate-evaluation.md` |

Also includes one `README.md` edit (line 28 `aidocs/63-architecture-decision-log.md` → `aidocs/platform/63-architecture-decision-log.md`) and regenerated `aidocs/01-doc-stage-index.md`.

### Commit 2 — `docs(align): shipped-ref-fix — mark AI1b + SPATIAL-V6-001 + FS1b + M4I-a/b/f shipped in live status docs`

Four surgical edits to status-board docs whose entries lagged the actual ship state:

1. **`aidocs/roadmap.md` line 135** — AI1b mid-term row marked `~~shipped~~` (matches the existing VID1a / T1a pattern on lines 112 / 171). AI1b shipped (`AnomalyDetectionService` + `AI1b-UI` button + `DetectAnomaliesDialog.vue`); AI1b-fix auth-gate correction also shipped.

2. **`aidocs/40-ecosystem.md` line 141** — `shepard-plugin-spatial 📐 designed` → `shepard-plugin-spatiotemporal ✓ shipped (2026-05-27, SPATIAL-V6-001)`; pointer updated to `aidocs/data/90-spatial-as-temporal-sweep.md` + `plugins/spatiotemporal/docs/reference.md`.

3. **`aidocs/40-ecosystem.md` line 145** — `shepard-plugin-file-s3 📐 designed` → `✓ shipped (ef82feb3)`; pointer updated to `plugins/file-s3/docs/reference.md`.

4. **`aidocs/44-fork-vs-upstream-feature-matrix.md` line 216** — m4i row flipped from "📐 designed (six slices queued)" to "**M4I-a + M4I-b + M4I-f shipped** (2026-05-26 / -27); M4I-c/d/e queued"; status column flipped to "✓ ↑ M4I-a/b/f shipped ; 🚧 in-flight for M4I-c/d/e".

Plus `docs/reference/plugins.md` lines 41–45: "future plugins follow the same shape" list refreshed from the obsolete `shepard-plugin-spatial-postgis`-style examples to current shipped plugin names (`spatiotemporal`, `file-s3`, `git`, `ai`, `importer`, …) since 11 of the 14 plugins enumerated there are now live in `plugins/*`.

### Commit 3 — `docs(align): file DOC-ALIGN-STANDING backlog row + doc-alignment report`

New row `DOC-ALIGN-STANDING` filed in `aidocs/16-dispatcher-backlog.md §DOC-STAGE` group. Next-pass trigger captured: "when more than 50 dead-link incidence accrue or when an aidocs/16 ID is shipped but still appears as `📐 designed` in three or more live status docs". Report committed at this path.

---

## What I propose (UNSURE / ESCALATE)

### UNSURE — operator-decision-required

1. **`aidocs/data/90-spatial-as-temporal-sweep.md` content drift.** This was the precursor design doc for SPATIAL-V6-001. Three sections still read as live design ("Lean: **keep `shepard-plugin-spatial`** … keeps the name carries continuity"; `D10` decision-table row still says "retains name `shepard-plugin-spatial`"). The team picked rename. Recommendation: small banner at top + flip `D10` row → "rename adopted as `shepard-plugin-spatiotemporal` per SPATIAL-V6-001 (2026-05-27)". I did not execute this because the doc carries `stage: feature-defined` and is the canonical design-rationale SSOT; a content edit deeper than a banner is a substantive rewrite. **Operator call.**

2. **`aidocs/platform/68-plugin-vs-core-overview.md`.** Whole-doc drift: ~12 plugins listed as "designed" that have shipped (`shepard-plugin-video`, `shepard-plugin-unhide`, `shepard-plugin-file-s3`, `shepard-plugin-hdf-hsds`/now `-hdf5`, `shepard-plugin-minter-*`, `shepard-plugin-aas`, `shepard-plugin-git`, `shepard-plugin-spatial`/now `-spatiotemporal`). The doc serves as the "plugin-by-interface vs plugin-by-extension" mental model, not a live status board, so the entries are pedagogical. But the volume of out-of-date "designed" labels is now misleading. **Recommendation:** add a "as-of" date header + single sentence pointing readers at `aidocs/40-ecosystem.md` for current status. **Did not execute** — needs the author's call on the level of in-doc status discipline they want.

3. **`aidocs/semantics/43-ai-opportunities.md` §8 phasing table** still describes AI1b as `M | None`, with the endpoint URL `POST /v2/timeseries/{appId}/detect-anomalies` and language "rolling-median + isolation-forest. Pure-Python". Shipped endpoint is `POST /v2/timeseries-references/{refAppId}/detect-anomalies` (pure-Java, no isolation forest). The doc is a survey/catalogue with `stage: idea` — its job is to capture rationale at the moment of the survey, not track shipped state. **Recommendation:** add a single "Shipped as of 2026-05-28: AI1b, AI1c, AI1r" line under §8 rather than editing the table. **Did not execute** — operator's call on whether design-rationale docs should track ship status at all.

4. **Stage frontmatter drift candidates.** None are unambiguous enough to flip without human review:
   - `aidocs/data/35-hdf5-hsds-implementation-design.md` — `stage: feature-defined`; A5a/b/d shipped per the matrix, but the doc is the implementation design, not a status row. Could go `deployed`.
   - `aidocs/semantics/43-ai-opportunities.md` — `stage: idea`; AI1b/c/r shipped. Could go `concept` → `feature-defined`. Likely the right move but the §8 phasing is heavily stale (see #3); flipping the stage without addressing the content drift gives a false-confidence signal.
   - `aidocs/integrations/66-hmc-kip-integration.md` — `stage: feature-defined`; KIP1h shipped per the matrix.
   - `aidocs/integrations/113-urdf-viewer.md` — `stage: feature-defined`; status unknown — backlog row search returned nothing.

5. **`aidocs/16-dispatcher-backlog.md` redundancy spot-check.** I spot-checked for duplicate row IDs but found none egregious. `REF-EDIT-TPL-3` and `REF-EDIT-TPL-6` look like clean follow-up rows after their parents shipped, not duplicates. `IMPORT-W1/W2/W3` are sub-rows of the same warmup feature and intentionally separate. **No merge proposed.**

### ESCALATE — do not execute without operator approval

1. **The dispatcher's "shipped-but-still-planned" priority #1 surface is bigger than this pass.** I focused on the 3 highest-traffic docs (`roadmap.md`, `40-ecosystem.md`, `44-fork-vs-upstream-feature-matrix.md`, `docs/reference/plugins.md`). A deeper sweep — every doc that lists "shepard-plugin-X" with a status pill, every `aidocs/16` row whose sister docs need a status flip — is a 1–2 day pass. **Recommendation: dispatch a focused agent on "plugin-status alignment" alone**, scoped to ecosystem.md + plugin-vs-core overview + traceability + the aidocs/16 backlog scan, with permission to flip status pills systematically.

2. **`aidocs/data/90-spatial-as-temporal-sweep.md` § "naming bikeshed" rewrite.** Sections 7 and the D10 decision-table row argue *against* rename to `-spatiotemporal`. The team picked rename. Surgical rewrite would put a "ADOPTED" marker on the rejected D10 row and update the §7 bikeshed paragraph. Not done because the doc is the canonical design rationale — editing its argument structure rewrites history. **Operator call: surgical update vs. leave-as-historical-record?**

3. **`aidocs/01-doc-stage-index.md` regeneration timing.** Currently regenerated as part of dead-link-fix commit because the pre-commit hook required it. Working as designed; no change recommended.

---

## SSOT registry — canonical picks for ambiguous topics

| Topic | Canonical SSOT | Notes |
|---|---|---|
| Provenance design (overall) | `aidocs/workflows/55-provenance-and-activity-overhaul.md` | `aidocs/55-provenance-design.md` references now redirect here |
| Internal semantic repository | `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md` | `aidocs/48-internal-semantic-repository-design.md` references now redirect here |
| HMC KIP integration | `aidocs/integrations/66-hmc-kip-integration.md` | `aidocs/64-hmc-kip-integration.md` (older-numbered duplicate) was already gone; references redirected |
| Spatial/spatiotemporal plugin | `aidocs/data/90-spatial-as-temporal-sweep.md` (SSOT design) + `plugins/spatiotemporal/docs/reference.md` (current reference) | `plugins/spatial/docs/reference.md` is a redirect stub (`stage: decommissioned`) — keep as-is |
| AI opportunities catalogue | `aidocs/semantics/43-ai-opportunities.md` | Single SSOT; no duplicate found |
| Metadata4Ing integration | `aidocs/semantics/94-metadata4ing-integration-design.md` | Single SSOT |

No deletions performed in this pass. All folded-SSOT pointers go through the existing redirect surface (the old paths simply weren't files; the references were pointing at non-existent paths and got rewritten to point at the canonical doc).

---

## Doc-stage transitions

**None executed.** Candidates surfaced under UNSURE #4 above.

---

## End-of-run stats

- **Files edited:** 34 (32 dead-link fixes + 1 README + 1 stage-index regeneration + 4 status-board edits)
- **Files proposed for deletion:** 0 (no candidate had zero inbound + clear redundancy + no SSOT role)
- **UNSURE items surfaced:** 5
- **ESCALATE items surfaced:** 3
- **Backlog row filed:** `DOC-ALIGN-STANDING` (under `§DOC-STAGE` group in `aidocs/16-dispatcher-backlog.md`)

---

## Persona consultation (per `feedback_agents_argue_and_consult.md`)

Per the dispatcher rule, before substantial changes I named the persona who would push back:

- **Plugin Designer:** Would push back on the spatial → spatiotemporal flip in `40-ecosystem.md` only if the rename is *partial* (some installs still ship `shepard-plugin-spatial`). Defence: the `plugins/spatial/docs/reference.md` redirect stub (`stage: decommissioned`, 2026-05-27) confirms full retirement; the Maven module still exists for backwards naming continuity but the live name is `spatiotemporal`. The `40-ecosystem.md` row tracks the *current* plugin, not historical names.

- **API Scrutinizer:** Would push back on the AI1b roadmap-row strikethrough if the endpoint shape on main differs from the row description. Defence: backlog row 149 + `aidocs/34` AI1b row describe the shipped endpoint precisely; the roadmap row was less specific. The `~~shipped~~` marker matches the doc's own established convention (VID1a, T1a) for "this row is now historical".

- **RDM Steward:** Would push back on dead-link fixes if any redirect lost FAIR/auditability metadata. Defence: every rename target is a real file at the new path with the same content; the metadata travels with the file, not the inbound reference.

No persona objections were strong enough to block the SURE edits.
