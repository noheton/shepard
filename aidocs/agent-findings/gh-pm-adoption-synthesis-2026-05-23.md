---
stage: deployed
last-stage-change: 2026-05-23
audit-target: aidocs/strategy/85-github-project-management-policies.md
audit-round: 1
personas:
  - strategy-aligner
  - api-scrutinizer
  - rdm
  - reluctant-senior
  - digital-native
---

# Synthesis — GH-PM (policy doc 85) adoption decisions

**Date.** 2026-05-23.
**Subject.** `aidocs/strategy/85-github-project-management-policies.md` (the GH-PM1 policy).
**Inputs.** Five persona findings (paths below) + two memory-rule refinements
captured the same day:

- `aidocs/agent-findings/persona-strategy-aligner-gh-pm-2026-05-23.md`
- `aidocs/agent-findings/persona-api-scrutinizer-gh-pm-2026-05-23.md`
- `aidocs/agent-findings/persona-rdm-gh-pm-2026-05-23.md`
- `aidocs/agent-findings/persona-reluctant-senior-gh-pm-2026-05-23.md`
- `aidocs/agent-findings/persona-digital-native-gh-pm-2026-05-23.md`
- `/root/.claude/projects/-opt-shepard/memory/feedback_no_synthetic_provenance.md`
- `/root/.claude/projects/-opt-shepard/memory/feedback_persona_audit_triggers.md`

**Disposition.** Adopt §85 with the edits in §3 + the enhancement
backlog in §4. Backfill plan in
`aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`.

---

## §1 — Final decisions (6/6, with vote tally)

| Q  | Question | Vote tally (5 personas) | Final | Source links |
|----|----------|--------------------------|-------|--------------|
| Q1 | Adopt §85 as-is? | **5/5 adopt** (Strategy `A`, API `Yes-with-edits`, RDM `A`, Reluctant `A`, Digital `YES`) | **ADOPT** with §3 edits below | strategy §Q1; api §Q1; rdm §3 Q1; reluctant §Q1; digital §Q1 |
| Q2 | `v6.0.0-rc.1 = this-session bundle`? | **5/5 yes** (Strategy `edit-framing`, API `Yes`, RDM `A`, Reluctant `A`, Digital `YES`) | **ADOPT** — bundle = this-session work | strategy §Q2; api §Q2; rdm §3 Q2; reluctant §Q2; digital §Q2 |
| Q3 | Backfill via dry-run plan first? | **5/5 dry-run** (Strategy `A`, API `Yes`, RDM `A`, Reluctant `A`, Digital `YES`) | **ADOPT** — this deliverable IS the dry-run | strategy §Q3; api §Q3; rdm §3 Q3; reluctant §Q3; digital §Q3 |
| Q4 | Auto-file Issues for in-flight agent dispatches? | **5/5 NO** (Strategy `A`, API `No`, RDM `B-no-with-caveat`, Reluctant `B-manual-opt-in`, Digital `NO`) | **REJECT auto-file** — opt-in only | strategy §Q4; api §Q4; rdm §3 Q4; reluctant §Q4; digital §Q4 |
| Q5 | Projects v2 board: manual UI vs scripted? | **4/5 manual** (Strategy/API/RDM/Reluctant `manual`) vs **1/5 scripted** (Digital, with live 31-line empirical refutation) | **USER-RESOLVED → SCRIPTED** on Digital Native's empirical evidence | strategy §Q5; api §Q5; rdm §3 Q5; reluctant §Q5; **digital §Q5 (live `gh project` demo, 7.5s wall-clock, 31 Python lines)** |
| Q6 | PR-scope enforcement: hook now vs trust + CI? | **mixed** — Strategy `edit-install-before-stable`, API `Trust+CI-lint-later`, RDM `A-trust+ship-opt-in-hook`, Reluctant `B-trust+reviewer-defer-hook`, Digital `install-soft-warn-only` | **USER-RESOLVED → TRUST + CI lint-warn** (Conv-Commits scope checked by CI; warn-only until aidocs/platform/106 Option A ships then enforce) | strategy §Q6; api §Q6; rdm §3 Q6; reluctant §Q6; digital §Q6 |

**Aggregate concurrence:** 4/6 unanimous (Q1–Q4). Q5 + Q6 are
**minority-with-evidence** patterns — the lone-but-empirical voice
(Digital Native on Q5; the load-bearing minority dissent on Q6 split
between hook-now/lint-now/trust) won via user judgement. This pattern
itself is case-study material — recorded in §5 below.

---

## §2 — Auto-adopt items (Q1–Q4)

These four decisions ship in this commit, no further user signal needed:

1. **Q1 → ADOPT** `aidocs/strategy/85` as authoritative. Front-matter
   flipped `stage: deployed`, `last-stage-change: 2026-05-23`.
2. **Q2 → `v6.0.0-rc.1` = this-session bundle.** Bundle composition
   (post-MFFD-import v15.2 + Garage S3 FS1b/FS1d/FS1i + IMPORT-W1/W2/W3
   smart warmup + WAAPI + BIB-1 bibliography + ORIGIN-1 origin myth +
   DOC-STAGE-1 + GH-PM1 + V1COMPAT.0 Phase 1). Cited in milestone body
   per `aidocs/strategy/85 §4`. Per Strategy §Q2 overlay: subtitled
   *"Provable provenance + open substrate"* in the milestone body;
   release notes structured as three pillars (substrate freedom /
   provable provenance / compat path) rather than by `type:`/`area:`.
3. **Q3 → Dry-run plan first.** `aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`
   is the dry-run artefact. No `gh issue create` fires until operator
   approves the plan.
4. **Q4 → No auto-Issues for in-flight agents.** §85 §3 gate 4 stays
   *optional*. Internal agent dispatch = no Issue. (RDM §3 Q4 #1 calls
   this out as a real industrial-IP leakage risk for MFFD work.)

---

## §3 — User-resolved splits (Q5, Q6)

### Q5 — Projects v2 scripted

**Tally.** 4/5 personas voted *manual*, citing GraphQL fragility +
intentionality + one-shot-cost (Strategy §Q5 #1–#3; API §Q5; RDM §3 Q5;
Reluctant §Q5).

**Dissent with evidence.** Digital Native §Q5 [FLIP] ran the full board
bootstrap live against a real GitHub account in 31 Python lines, 7.5s
wall-clock, against `gh 2.92.0` — *empirically refuting* the GraphQL-
fragility lean. Sub-command surface (`gh project create / field-create
/ item-create / item-edit`) is complete + idempotent.

**User resolution.** **SCRIPTED.** Ship
`scripts/bootstrap-gh-project.py` as a queued backlog row
(`GH-PM-ENH-Q5-1` below). Manual fallback for "saved views" only — the
GraphQL mutation exists but `gh` doesn't expose it yet (per Digital
§Q5 caveat); operator pastes a 3-line gist into the runbook.

### Q6 — Trust + CI lint-warn

**Tally.** Mixed across all five — Strategy `install before stable` +
API `trust+CI-when-Option-A ships` + RDM `trust+ship-opt-in-hook` +
Reluctant `trust+reviewer+defer-hook` + Digital `install-soft-warn-only`.
No clean majority.

**User resolution.** **TRUST + CI lint-warn**. Concrete shape:

- Local hook ships as opt-in under `scripts/hooks/commit-msg`
  (warn-only, never blocks). Per Digital §Q6 8-line pattern; per
  Reluctant §Q6 cost-of-being-wrong (hooks I `--no-verify` past).
- CI lint posts a review comment on PRs whose title scope doesn't
  match `^[A-Z][A-Za-z0-9-]*(\+[A-Z][A-Za-z0-9-]*)*$` (API §S1 pinned
  regex). Warn-only initially; enforce when
  `aidocs/platform/106 Option A` (TRACE-A index) ships.
- The hook turns *strict* (CI fails red) for v6.0.0 stable release per
  Strategy §Q6 *"hook becomes pre-condition for v6.0.0 stable, not
  rc.1."*

---

## §4 — Enhancement backlog (24 rows from 5 personas)

Every row cites at least one element per
`feedback_persona_audit_triggers.md §Addendum "Personas comment on
elements"`. Rows land under `aidocs/16` section `### GH-PM-ENH — 2026-05-23`.

### From RDM — 6 FAIR completeness items (§4 + §7)

| ID | Slice | Element cited | Size | Severity |
|---|---|---|---|---|
| GH-PM-ENH-RDM-1 | Extend `scripts/trace-feature.sh` to distinguish "empty because exempt" vs "empty because rule was broken" — surface 6 (aidocs/34) + 7 (data/00) emit `(legitimately empty — chore/docs-only)` when the row Notes column flags doc-only/chore | `persona-rdm-gh-pm-2026-05-23.md §5`; `scripts/trace-feature.sh`; `aidocs/strategy/85 §14` | S | minor |
| GH-PM-ENH-RDM-2 | Add `aidocs/data/00-model-inventory.md` to the §5 pre-flight release-block checklist (mirror the existing aidocs/34 check) | `persona-rdm-gh-pm-2026-05-23.md §4 gap 2`; `aidocs/strategy/85 §5 pre-flight item 1`; `aidocs/data/00-model-inventory.md` | S | minor |
| GH-PM-ENH-RDM-3 | Per-row `Personas:` column on aidocs/16 rows linking persona findings that audited the row — closes "who reviewed this?" surface | `persona-rdm-gh-pm-2026-05-23.md §4 gap 3 + §7 #4`; `aidocs/16-dispatcher-backlog.md` | M | minor |
| GH-PM-ENH-RDM-4 | Extend §12 release-notes step 4 to cite persona findings + bibliography keys that motivated the work (for Nature-grade publication evidence) | `persona-rdm-gh-pm-2026-05-23.md §4 gap 4`; `aidocs/strategy/85 §12 step 4`; `docs/bibliography.md`; `docs/_data/references.bib` | S | minor |
| GH-PM-ENH-RDM-5 | Add `commit:` field to CITATION.cff on every release tag — GitHub "Cite this repository" UI then renders commit-pinned citation | `persona-rdm-gh-pm-2026-05-23.md §4 gap 5`; `CITATION.cff`; `docs/ops/cut-a-release.md` | S | minor |
| GH-PM-ENH-RDM-6 | RO-Crate manifest at release-tag boundary for any release that touches a user-visible payload kind — Horizon Europe DMP-grade evidence | `persona-rdm-gh-pm-2026-05-23.md §4 gap 6`; `aidocs/agent-findings/research-data-manager.md` (prior); `aidocs/strategy/85 §5 pre-flight` | M | nice-to-have |

(RDM §7 item 7 "license/embargo/funder/grantId fields on Collection +
DataObject" is **out of scope** for the GH-PM enhancement backlog — the
RDM persona itself flags it as a separate data-model concern; tracked
under existing aidocs/16 RDM rows, not duplicated here.)

### From API Scrutinizer — 14 "should → MUST" contract tightenings (§"Contracts to tighten" table)

| ID | Slice | Element cited | Size | Severity |
|---|---|---|---|---|
| GH-PM-ENH-API-1 | §2 mapping table → CI MUST fail on `.github/labels.yml` ↔ `aidocs/00-doc-stages.md` regex-diff drift | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 1`; `aidocs/strategy/85 §2`; `.github/labels.yml`; `aidocs/00-doc-stages.md` | S | minor |
| GH-PM-ENH-API-2 | §3 gate #4 → tighten to MUST NOT auto-file for purely-internal agent work (verifiable via `gh issue list --author <bot>`) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 2 + §Q4`; `aidocs/strategy/85 §3 gate 4` | S | minor |
| GH-PM-ENH-API-3 | §4 → CI MUST reject milestone moves until source milestone closed (`gh api` query in release-prep workflow) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 3`; `aidocs/strategy/85 §4` | S | minor |
| GH-PM-ENH-API-4 | §5 #4 → release pipeline MUST abort when `build-traceability-index.py` reports >0 orphan commits (exit code gating) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 4`; `aidocs/strategy/85 §5 pre-flight item 4`; `aidocs/platform/106-requirements-traceability.md` | M | minor |
| GH-PM-ENH-API-5 | §6 PR-template checkbox-presence CI lint — reviewers MUST + CI MUST | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 5`; `aidocs/strategy/85 §6`; `.github/pull_request_template.md` | S | minor |
| GH-PM-ENH-API-6 | §7 Pin scope grammar `^[A-Z][A-Za-z0-9-]*(\+[A-Z][A-Za-z0-9-]*)*$` as MUST. Drop the `fix(#148):` example or clarify `#N` semantics | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 6+7 + §S1.4`; `aidocs/strategy/85 §7`; `scripts/trace-feature.sh` regex | S | minor |
| GH-PM-ENH-API-7 | §8 Issue-close webhook MUST verify `aidocs/16` Status == done; reject otherwise (deferred until external velocity) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 8`; `aidocs/strategy/85 §8` | M | nice-to-have |
| GH-PM-ENH-API-8 | §9 board setting MUST disallow manual card additions; auto-add only (GH Project field config) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 9 + §Q5 edit`; `aidocs/strategy/85 §9` | S | minor |
| GH-PM-ENH-API-9 | §10 PR-template enforces one-label-per-axis via dropdown `validation: required` | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 10 + §S2`; `aidocs/strategy/85 §10`; `.github/ISSUE_TEMPLATE/*.yml` | S | minor |
| GH-PM-ENH-API-10 | §13 issue-template `security-finding.yml` MUST carry `private: true`; public template MUST NOT accept security keywords | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 12`; `aidocs/strategy/85 §13`; `.github/ISSUE_TEMPLATE/security-finding.yml` | S | major |
| GH-PM-ENH-API-11 | §14 `trace-feature.sh` MUST return in <5s wall (current p50: 0.28s) — timing assertion as test fixture | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 13 + §S3`; `aidocs/strategy/85 §14`; `scripts/trace-feature.sh` | S | minor |
| GH-PM-ENH-API-12 | §15 anti-pattern #4 → CI MUST reject PR titles whose subject doesn't match the §7 regex (warn-only initially per user Q6 resolution) | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"Contracts" row 14 + §Q6`; `aidocs/strategy/85 §15 #4` | S | minor |
| GH-PM-ENH-API-13 | Add `trace-feature.sh` surfaces 10–13 — `aidocs/44`, open PRs `gh pr list --search`, `plugins/*/docs/*.md`, `docs/reference/*.md` | `persona-api-scrutinizer-gh-pm-2026-05-23.md §S3 + §"Top 3 changes" #3`; `scripts/trace-feature.sh`; `aidocs/strategy/85 §16 companion docs list` | S | minor |
| GH-PM-ENH-API-14 | Promote `aidocs/platform/106` from `stage: idea` to `stage: feature-defined` with `build-traceability-index.py` JSON schema pinned (version, last_validated_at, orphan_commits[], unresolved_ids[]) BEFORE implementation | `persona-api-scrutinizer-gh-pm-2026-05-23.md §"The one endpoint that needs a design doc"`; `aidocs/platform/106-requirements-traceability.md §3 Option A` | M | major |

### From Strategy Aligner — 2 strategic overlays (§"Overlays")

| ID | Slice | Element cited | Size | Severity |
|---|---|---|---|---|
| GH-PM-ENH-STRAT-1 | v6.0.0-rc.1 release-notes body opens with a **funder-readable three-sentence summary** (substrate / provenance / MFFD live) — Clean Aviation JU programme-manager-quotable | `persona-strategy-aligner-gh-pm-2026-05-23.md §Overlay 1`; `aidocs/strategy/85 §12 step 1` | S | minor |
| GH-PM-ENH-STRAT-2 | Tripwire — when monthly external PR authors hit **3** (not the GH-INFRA4 trigger of 5), trigger a §85 review of the 4-gate Issues filter | `persona-strategy-aligner-gh-pm-2026-05-23.md §Overlay 4`; `aidocs/strategy/85 §3 "Until external velocity picks up"`; `aidocs/strategy/83-github-features-leverage.md GH-INFRA4` | M | minor |

### From Digital Native — 2 terminal-friendly automation rows (§"What I'd ship in one sprint")

| ID | Slice | Element cited | Size | Severity |
|---|---|---|---|---|
| GH-PM-ENH-Q5-1 | `scripts/bootstrap-gh-project.py` — 31-line idempotent board bootstrapper (verified live 7.5s wall-clock against `gh 2.92.0`); 3-line saved-views setup gist | `persona-digital-native-gh-pm-2026-05-23.md §Q5 [FLIP]`; `aidocs/strategy/85 §9`; `docs/ops/github-projects-board-setup.md` | S | major |
| GH-PM-ENH-Q3-1 | `scripts/trace-all-shipped.py` — 8-line Python wrapper calling `trace-feature.sh` in a loop over every `done` aidocs/16 row, emits `aidocs/agent-findings/traceability-audit-<date>.md` | `persona-digital-native-gh-pm-2026-05-23.md §Q3 + §"What I'd ship"`; `scripts/trace-feature.sh`; `aidocs/16-dispatcher-backlog.md` | S | minor |

**Total: 6 + 14 + 2 + 2 = 24 rows.** Each element-anchored.

---

## §5 — The synthetic-backfill refinement (load-bearing case-study material)

This is the second case-study insight from this round, alongside the
minority-with-evidence pattern in §1 Q5/Q6.

**Two-step refinement, recorded honestly:**

**Beat 1 — AI proposes mass backfill.** When the GH-PM5 row was first
drafted, the proposal was a straightforward "walk every aidocs/16 row,
file an Issue per gate 1/2/3/4 trigger, group into Milestones."
Mechanically defensible against §85 §3 — but the proposal treated
backfilled Issues as wire-shape-identical to real-time Issues. The
AI did not flag the provenance implication.

**Beat 2 — Human flags forgery.** User reaction (2026-05-23):
*"nah . backfill is synthetic anyway"*. The flag was correct: a
backfilled Issue with no disclosure looks identical to a real-time-filed
Issue. A future reader scanning the Issues tab cannot tell the
difference. That's not "filling in audit history" — that's
**fabricating** distributed contemporaneous review. The F(AI)²R /
honest-provenance discipline (see `project_provenance_principle.md` in
agent memory) forbids exactly this shape.

**Beat 3 — Joint refinement to "ALLOWED with disclosure".** User
follow-up (same day): *"no. thats what i meant by transparency hint,
each intercatiuion sttest ist partt of a synsthetic backfill"*. The
refinement preserved the structural value of backfill (audit-trail
completeness) while adding the discipline that distinguishes it from
forgery: **per-artefact transparency markers, in the artefact's own
body, machine-readable, durable**. Captured in
`feedback_no_synthetic_provenance.md` and now binding on GH-PM5.

**Why this matters for the case study:**

- Neither extreme — *no backfill at all* (loses audit-trail
  completeness) nor *free backfill* (silent forgery) — would have been
  right.
- The fix is not "AI was wrong" — it's "AI's first cut + human's first
  flag + AI's second cut" produces a position neither would have
  reached alone. The collaboration shape itself is the lesson.
- The discriminator between honest backfill and forgery is
  **disclosure in the artefact itself**. A changelog note is
  insufficient; the marker must travel with the Issue body, the
  Milestone description, the PROV-O row, the persona finding's
  front-matter. Anything else can be lost / silently overlooked.

**Operational binding:** GH-PM5 (and this synthesis) MANDATE that every
backfilled artefact carries the marker shape specified in
`feedback_no_synthetic_provenance.md`. The backfill plan deliverable
(`gh-pm-backfill-plan-2026-05-23.md`) enforces this in its proposed
Issue templates — each proposed Issue body opens with the
`🤖 **BACKFILL** — …` line, each proposed Milestone description opens
with the `🤖 BACKFILL milestone — …` line. No exceptions.

**Anti-pattern enshrined:** §85 §15 gains a new entry #7 — *"Filing
backfilled Issues without a BACKFILL disclosure marker — silent
forgery of the audit trail."*

---

## §6 — Companion file pointers

- Persona findings (5): see §"Inputs" above — paths verbatim.
- Backfill plan (dry-run): `aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`
- Policy doc (now `stage: deployed`): `aidocs/strategy/85-github-project-management-policies.md`
- Adoption ledger row (admin-facing): `aidocs/34-upstream-upgrade-path.md` — `GH-PM-ADOPT`
- Enhancement backlog section: `aidocs/16-dispatcher-backlog.md` — `### GH-PM-ENH — 2026-05-23`
- Memory rules consulted: `feedback_no_synthetic_provenance.md`, `feedback_persona_audit_triggers.md`, `feedback_continuous_doc_maintenance.md`, `feedback_agent_worktree_must_commit.md`
- Case-study highlights: this synthesis §1 (minority-with-evidence) and §5 (synthetic-backfill refinement) both feed `project_collab_highlights.md` next sweep
