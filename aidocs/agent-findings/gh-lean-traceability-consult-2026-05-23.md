---
audience: contributor
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# gh-lean-traceability-consult — 2026-05-23

Consult on the 3-addition lean proposal (L1 traceability bot-comment +
artifact, L2 GH Environments + Deployments, L3 `git notes` namespace).
Tested against `scripts/trace-feature.sh` and the §14 9-surface chain.

## Verdict

**ENDORSE-WITH-CHANGES.** All three additions hit real gaps, but two of
the three reduce to **already-queued backlog rows** (GH-PM2, GH-INFRA5,
GH-PM-ENH-API-4) — the proposal renames work that's already on the list.
The honest action is *execute those rows*, not add new ones. L3 ships
into a configuration void and needs explicit push-refspec scaffolding or
it's rot-bait identical to the Wiki rationale in doc 83.

## L1 traceability delta — before/after

**Claim:** PR-time `traceability-digest.json` artifact + bot-comment with
`trace-feature.sh <ID>` output.

**Before L1:** `trace-feature.sh FS1b` runs locally; surface 9 (release
tag) says `(no release tag yet — feature is on a branch / unreleased)`
for in-flight PRs; reviewers run the script manually.

**After L1:** Same nine surfaces, surfaced as a PR comment + JSON
artifact. Reviewers don't run the script; CI runs it for them.

**Honest assessment:** The artifact-emission piece is GH-PM2 line 972
verbatim (`pages.yml` already auto-publishes `docs/reference/traceability.md`
on every `main` push, builder exists, output exists). The CI-gate piece
is GH-PM-ENH-API-4 line 999 verbatim (release pipeline abort on >0 orphan
commits). The **only net-new bit** is the **PR bot-comment**, which
brushes against §3's "automation noise on every PR" — and the PR template
already enforces the per-PR checklist that names the row ID, so a comment
that re-derives it is redundant noise. **Drop the comment; keep the
artifact; execute GH-PM2 + GH-PM-ENH-API-4 as written.**

## L2 traceability delta — before/after

**Claim:** GitHub Environments for `nuclide`/`dlr-staging`/`dlr-prod` +
`deploy.yml` emitting Deployments API events.

**Before L2:** `trace-feature.sh FS1i` shows commits, files, release tag
(if cut) — but **silent on which environment the merge landed on, when,
or whether it passed smoke**. Deploys happen via `make redeploy` in tmux
on the dev box; the chain ends at the release tag.

**After L2:** Deployments API events show `FS1i deployed to nuclide
2026-05-23 14:02 by @noheton, smoke=green`. Adds a tenth implicit surface
to the chain (deploy provenance).

**Honest assessment:** L2 = GH-INFRA5 line 955. The phrasing differs
("Environments + Deployments API events" vs "Environments with
required-reviewer deploy gates"), but the substance is the same. The
**Deployments-API-event emission** is the implementation detail that
makes GH-INFRA5 actually trace-able vs. just a permission gate — worth
calling out in the GH-INFRA5 row, but doesn't justify a new ID.
**Promote GH-INFRA5 priority + amend its row to name the Deployments API
emission as the trace-surface integration.**

## L3 traceability delta — before/after

**Claim:** `refs/notes/trace` namespace to retro-attach aidocs/16 IDs to
scope-less commits.

**Before L3:** Pre-§7 commits without `(<ID>)` scope are invisible to
`trace-feature.sh` surface 4 (commits) — they exist in `git log` but
nothing maps them to a feature row. The traceability index shows 169 IDs
"with commits" out of 392 catalogued — the gap is partially these
unscoped historical commits.

**After L3:** `git notes show <hash>` emits the retro-attached ID.

**Honest assessment:** Two problems make L3 rot-bait.
1. **`git notes` don't push or fetch by default.** No `.git/config`
   `remote.origin.fetch '+refs/notes/*:refs/notes/*'` anywhere in the
   repo (verified). Without explicit push-refspec config in
   `CONTRIBUTING.md` + a shipped `make push-notes` target, the notes
   live on one machine and die when the dev box rotates. This is
   identical to doc 83's Wiki-skip rationale: "a third location would
   rot."
2. **`trace-feature.sh` doesn't read `refs/notes/trace` today.** L3
   requires a `--notes` enhancement to surface 4 of the script, otherwise
   the retro-attachment is invisible to the canonical trace tool.

**Either ship L3 with both (a) push-refspec config + CI verification +
`make push-notes` AND (b) `trace-feature.sh` notes-reading, or skip it.**
Halfway is the rot-trap.

## Highest-value gap not in proposal

**#1 — Post-deploy smoke result ↔ feature ID linkage.** `make redeploy`
chains through `wait-for-health + smoke` (per worklog 2026-05-22). The
smoke result is **not** linked to the feature ID that triggered the
deploy. Closing it: emit a `smoke-result-<ID>-<hash>.json` artifact from
`scripts/smoke-test.sh` and ingest into the post-L1 traceability
artifact. Cost: ~30 solo-dev min/week. Closes the "feature shipped but
did smoke pass?" surface for the §5 release pre-flight.

**#2 — Runtime telemetry → feature ID.** Per
`feedback_shepard_measures_itself.md`, OBS-MFFD1 ships self-observability
metrics inside Shepard's own TS substrate. Today, metrics carry no
feature-ID label. Closing it: every new metric carries a
`shepard.feature_id` tag matching the aidocs/16 row that introduced it.
Cost: ~10 solo-dev min/feature (set the tag once when shipping the
metric). Closes the "feature shipped but is it being used?" surface.

**#3 — External-consumer-of-API tracking.** `/v2/...` endpoint additions
land per §6 PR discipline, but no surface records which clients
(generated kiota client versions, MCP tool list, plugin SDK) reflect
the change. Closing it: emit `client-coverage.json` from
`clients-kiota.yml` listing endpoint→generated-method coverage per
release. Cost: one workflow edit, ~20 solo-dev min one-time. Closes
"did the kiota client catch up to FS1c?" — the question the Digital
Native persona keeps asking.

## Duplicates / rot-risks identified

| Concern | Status |
|---|---|
| L1 emit-artifact → **dup** of GH-PM2 (line 972) | drop, execute GH-PM2 |
| L1 release-pipeline gate → **dup** of GH-PM-ENH-API-4 (line 999) | drop, execute GH-PM-ENH-API-4 |
| L1 PR bot-comment → **noise risk**, redundant with PR template | drop |
| L2 → **dup** of GH-INFRA5 (line 955) with naming nuance | amend GH-INFRA5 to name Deployments API events; promote priority |
| L3 → **rot risk** (notes don't push by default; no `trace-feature.sh` reader) | ship complete or skip |

## Recommended action

1. **Mark this consult as input to GH-INFRA5 + GH-PM2 + GH-PM-ENH-API-4
   rows** in `aidocs/16` — promote GH-INFRA5 priority + amend the row's
   Notes column to name "Deployments API events" as the integration
   surface.
2. **Drop L1 as a new ID.** L1 reduces to executing the two queued rows.
   The PR bot-comment piece is noise; the PR template already enforces
   the ID-in-title contract.
3. **Either land L3 complete or skip it.** Complete = push-refspec
   config + `make push-notes` target + `trace-feature.sh --notes` flag,
   all shipped in one PR. Without all three, it rots.
4. **File three new aidocs/16 rows for the gaps surfaced above:**
   - `GH-PM-ENH-SMOKE-1` — smoke-result artifact per feature ID.
   - `GH-PM-ENH-OBS-1` — `shepard.feature_id` tag on all
     OBS-MFFD1-family metrics.
   - `GH-PM-ENH-CLIENT-1` — kiota client coverage diff per release.

The lean proposal got the **diagnostic** right (traceability needs
deploy + retro-scope + automation surfaces) but the **prescription** is
mostly already in the backlog. Discipline > new IDs.
