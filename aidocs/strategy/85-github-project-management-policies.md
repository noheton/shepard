---
stage: deployed
last-stage-change: 2026-05-23
---

# 85 — GitHub project-management policies (traceability SSOT)

**Status.** **Policy.** Authoritative rulebook for how this fork uses
GitHub-native project-management features (Issues, Projects v2,
Milestones, Releases, Labels, PR/Issue templates) so that the
project's development is **provably traceable end-to-end**.

> **Adopted 2026-05-23** after 5-persona audit (Strategy Aligner, API
> Scrutinizer, RDM, Reluctant Senior Researcher, Digital Native
> Researcher). Six adoption questions resolved unanimously on Q1–Q4;
> Q5 (board scripted vs manual) + Q6 (PR-scope hook now vs trust+CI)
> resolved by user judgement on minority-with-evidence findings.
> Full synthesis at
> [`aidocs/agent-findings/gh-pm-adoption-synthesis-2026-05-23.md`](../agent-findings/gh-pm-adoption-synthesis-2026-05-23.md).
> Per `feedback_no_synthetic_provenance.md` in agent memory:
> **retroactive Issues require per-artefact transparency markers** —
> see §3 and §8 below.

**Audience.** Contributors, reviewers, future maintainers, the AI
(Claude). Operators consult §5 and §13.
**Companion docs.** `aidocs/00-doc-stages.md` (stage taxonomy),
`aidocs/16-dispatcher-backlog.md` (backlog SSOT),
`aidocs/34-upstream-upgrade-path.md` (admin diff stream),
`aidocs/44-fork-vs-upstream-feature-matrix.md` (per-feature matrix),
`aidocs/data/00-model-inventory.md` (live model snapshot),
`aidocs/platform/106-requirements-traceability.md` (TRACE-A design),
`aidocs/strategy/83-github-features-leverage.md` (GH-INFRA1
scaffolding), `docs/ops/cut-a-release.md`,
`docs/ops/github-projects-board-setup.md`.

---

## 1. Why this exists

**Goal.** A single git query can answer the question *"why does
feature X exist, when did it ship, who reviewed it?"* without leaving
the repository.

**The audit trail.** Every shipped change walks this chain:

```
aidocs/16 row (catalogue with stable ID)
   → optional GitHub Issue (only when §3 gate triggers)
   → branch `<ID>-<slug>`
   → PR titled `<type>(<ID>): <subject>`
   → commits with Conv-Commits scope = ID
   → merge to main
   → aidocs/34 row (admin-facing diff)
   → aidocs/data/00 delta (model snapshot)
   → release tag + auto-rendered release notes
```

If any link breaks, traceability breaks. This policy is the
discipline that keeps the chain intact.

**Non-goal.** Replicating Jira / Linear / GitLab Issues at scale.
The 360+ rows in `aidocs/16` do **not** get mirrored into Issues.
See §3.

---

## 2. The unified state machine

The vocabulary is defined exactly once in
[`aidocs/00-doc-stages.md`](../00-doc-stages.md). Eight main stages
+ two overlays:

| Stage                  | Meaning                                              |
|------------------------|------------------------------------------------------|
| `fragment`             | Raw note, mid-thought                                |
| `concept`              | Kernel of an idea                                    |
| `idea`                 | "What if we did X?" + one paragraph of mechanism     |
| `feature-defined`      | Data model, REST, frontend, plugin boundary spelled out |
| `audited-by-personas`  | Persona / agent review has run                       |
| `feedback-implemented` | Reviewer feedback incorporated                       |
| `tests-implemented`    | Test coverage shipped                                |
| `deployed`             | Live on the canonical fork instance                  |
| `upgrade-vX:vY`        | Overlay — co-exists with any main stage              |
| `decommissioned`       | Terminal — feature retired                           |

**The three-surface mapping is byte-for-byte:**

| Stage                  | aidocs front-matter      | aidocs/16 Status   | GH label                       | Projects v2 column |
|------------------------|--------------------------|--------------------|--------------------------------|--------------------|
| `fragment`             | `stage: fragment`        | (rarely listed)    | `stage:fragment`               | Backlog            |
| `concept`              | `stage: concept`         | `concept`          | `stage:concept`                | Backlog            |
| `idea`                 | `stage: idea`            | `idea`             | `stage:idea`                   | Backlog            |
| `feature-defined`      | `stage: feature-defined` | `queued`           | `stage:feature-defined`        | Backlog            |
| `audited-by-personas`  | `stage: audited-by-personas` | `queued`       | `stage:audited-by-personas`    | Backlog            |
| `feedback-implemented` | `stage: feedback-implemented` | `in-progress` | `stage:feedback-implemented`   | In Progress        |
| `tests-implemented`    | `stage: tests-implemented` | `in-progress`    | `stage:tests-implemented`      | In Review          |
| `deployed`             | `stage: deployed`        | `done` / `shipped` | `stage:deployed`               | Done               |
| `decommissioned`       | `stage: decommissioned`  | (archive)          | `stage:decommissioned`         | Done (archived)    |

**No translation cost.** A label-rename in `.github/labels.yml`
breaks the contract.

---

## 3. aidocs/16 vs GitHub Issues — the 4-gate rule

**Default: no Issue.** `aidocs/16` is the SSOT. The 360+ existing
rows do NOT get back-filled into Issues.

**File an Issue ONLY when at least one of these four gates is true:**

1. **External-contributor-visible work** — someone outside DLR could
   pick it up (so it needs to be public-by-default + threaded).
2. **Security disclosure** — file privately via the security-finding
   template (`.github/ISSUE_TEMPLATE/security-finding.yml`). Never
   as a public Issue.
3. **Bug with clear repro** — a customer-facing or reviewer-facing
   defect that benefits from public threaded discussion + a
   `Closes #N` linkage from the fix PR.
4. **In-flight agent execution** — when an agent is dispatched
   against a row, the matching Issue (if filed) acts as the public
   in-flight ledger. Closes automatically when the agent merges its
   PR.

**Sync direction.** One-way: aidocs/16 → Issue. Never the reverse.

**Practical workflow.**

```
✓ New row in aidocs/16 → no Issue.
✓ Row picked up by an internal contributor → no Issue.
✓ Row picked up by an agent → optional Issue opens at dispatch.
✓ External contributor pings #shepard about row XYZ → Issue opens.
✓ CVE found in dependency-check → Issue opens (security template).
✗ Backlog grooming sprint → DON'T mass-file Issues.
```

**Until external velocity picks up** (5+ external contributors / month,
per GH-INFRA4 gate), expect Issues to remain rare — single digits
per quarter.

**Retroactive backfill — ALLOWED with transparency markers, NOT auto-fire.**
Backfilling historical Issues (catching up the public ledger after a
period of aidocs/16-only operation) is permitted as a **deliberate
operator gesture** under GH-PM5, never as an automatic §3 gate-fire.
The 4-gate filter still selects which rows backfill applies to — most
rows are internal refactors / chores / docs and SKIP. Every backfilled
artefact MUST carry a transparency disclosure as the first element of
its primary surface (Issue body, Milestone description, persona
finding, PROV-O `:Activity` row) per `feedback_no_synthetic_provenance.md`.
See §8 for the Issue-body marker template; §15 anti-pattern #7 for the
forbidden form. A backfilled Issue that conceals its retroactivity is
forgery of the audit trail.

---

## 4. Milestones

**Per release pillar, not per sprint.** A milestone names a
coherent feature set, not a calendar window.

**Naming convention.** `v<major>.<minor>.<patch>[-rc.N]` — match the
release tag exactly.

**Current pipeline.**

| Milestone           | Bundles                                              | aidocs/34 lookahead             | Target          |
|---------------------|------------------------------------------------------|---------------------------------|-----------------|
| `v6.0.0-rc.1`       | Post-MFFD-import + Garage S3 + smart warmup          | FS1b + IMPORT-W1 + AAS-1        | After MFFD lands |
| `v6.0.0-rc.2`       | Post-substrate-split + SHACL-1                       | SHACL-1 + (substrate-split rows)| After rc.1 stable |
| `v6.0.0`            | Stable after RC validation                           | Cumulative + decommission v1    | After rc.2 +14d |

**Milestone rules.**
- Each milestone lists the **aidocs/16 row IDs** it bundles in its
  description. The release-notes workflow groups commits by scope.
- A row may belong to **at most one** milestone at a time. Don't
  pre-assign queued rows to future milestones; assign at the
  RC-cut moment.
- A milestone closes when its release tag is cut + `aidocs/34`
  carries every bundled row.

---

## 5. Releases

**Cut on major coherent-feature-set boundary** — not on calendar.

**Pre-flight checklist.** Block the release if any check fails:

1. Every shipped row in the milestone has its matching
   `aidocs/34` row landed (admin sees what changed).
2. `aidocs/data/00-model-inventory.md` updated with the model
   delta (if the release touched the substrate model).
3. `python3 scripts/regenerate-doc-stage-index.py --check` clean
   (no `UNTAGGED` docs).
4. `python3 scripts/build-traceability-index.py` runs clean — the
   emitted `docs/reference/traceability.md` has zero orphan commits
   (scopes that don't map to any aidocs/16 ID).
5. Security gates green on the merge commit (SpotBugs + CodeQL +
   Dependency-Check + Trivy + gitleaks + dependency-review — the
   six gates from CLAUDE.md "Always: keep the security gates green").
6. SBOM (CycloneDX) attached via `anchore/sbom-action` per
   `build-images.yml`.

**Runbook.** [`docs/ops/cut-a-release.md`](../../docs/ops/cut-a-release.md)
— shipped via GH-INFRA1. Don't deviate; if the runbook is wrong, fix
the runbook first.

**Release notes.** Auto-rendered from Conventional Commits scopes per
`.github/release.yml`. The operator reviews the draft, edits if
needed, publishes. The release body MUST cite the aidocs/16 row IDs
the release bundles + the matching aidocs/34 rows.

---

## 6. PR discipline

**Every PR title carries a Conventional Commits scope** that matches
an aidocs/16 row ID:

```
feat(FS1b): add S3 file storage plugin
fix(KIP1d): reject NaN in keepalive interval setter
docs(GH-PM1): policy doc + trace-feature helper
refactor(SWEEP-12): consolidate v2 IO base class
```

If no row exists yet, the PR introduces it (in the same PR, in the
`aidocs/16-dispatcher-backlog.md` diff). **There is no PR without a
row.**

**The PR body uses [`.github/pull_request_template.md`]** (shipped
via GH-INFRA1), which mirrors every CLAUDE.md "Always:" rule as a
checkbox. Reviewers MUST reject PRs missing:

- The matching `aidocs/34` row when the change is admin-visible
  (config, schema, endpoint, default, dependency, behaviour).
- The `aidocs/44` row flip when the change ships a feature.
- The `aidocs/data/00` delta when the change touches the model.
- The `docs/reference/*` page when the change is user-visible (per
  CLAUDE.md "Always: keep user-facing docs in step").
- The `aidocs/42` vision update when the change is user-visible at
  the payload-kind / top-level-concept level.
- The plugin self-docs (`plugins/<id>/docs/{reference,quickstart,install}.md`)
  when the change touches a plugin surface.
- The migration script + idempotency note when the change is
  data-mutating.

Cite the specific CLAUDE.md "Always:" section in the review comment.

---

## 7. Branch + commit convention

**Branch name format:**

```
<aidocs-id>-<short-slug>

Examples:
  FS1b-s3-storage
  IMPORT-W1-smart-warmup
  GH-PM1-policy-doc
  SWEEP-12-v2-io-consolidation
```

Worktree branches dispatched by agents use the auto-generated names
per `feedback_agent_worktree_must_commit.md` — the policy is
relaxed there, but the commit-scope rule still holds.

**Every commit message follows Conventional Commits** with scope =
the aidocs/16 row ID:

```
<type>(<ID>): <subject line, <72 chars>

<body, wrapped at 72>

Co-Authored-By: …
```

Valid types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`,
`perf`, `style`, `build`, `ci`.

**The traceability index reads the scope verbatim.** A typo
(`feat(fs1b)` vs `feat(FS1b)`) makes TRACE-A miss the commit. Case
is significant. The IDs are uppercase as authored in `aidocs/16`.

---

## 8. Issue lifecycle (when an Issue exists)

The Issue is **secondary**. The aidocs/16 row leads; the Issue
follows.

```
┌─────────────────────────────────────────────────────────────┐
│  aidocs/16 row Status flips → Issue label updates           │
│                                                             │
│  aidocs/16: queued      ─→ Issue: status:queued             │
│  aidocs/16: in-progress ─→ Issue: status:in-progress        │
│  aidocs/16: done        ─→ Issue auto-closed by merging PR  │
│                            (via `Closes #N` in PR body)     │
└─────────────────────────────────────────────────────────────┘
```

### 8.1 Transparency marker for retroactive Issues (MANDATORY)

When a backfilled Issue is filed under GH-PM5 (or any other retroactive
backfill operation), the Issue body MUST open with the following
marker before any other content:

```
🤖 **BACKFILL** — created retroactively YYYY-MM-DD as part of <operation>.
Original work: commit <hash>, date <YYYY-MM-DD>. Audit trail: <aidocs/16
row ID>; persona findings: <paths if applicable>.
```

If the row's status is already `done`, the body MUST also include a
closing line at the end:

```
Closed by <commit-hash> on <YYYY-MM-DD>.
```

The same shape applies to backfilled **Milestones** — the milestone
description MUST open with:

```
🤖 BACKFILL milestone — bundles work shipped between <date-range>.
Post-hoc milestone, not real-time planning.
```

The marker is the discriminator between honest backfill and forgery
(see §15 anti-pattern #7). Per `feedback_no_synthetic_provenance.md`
in agent memory.

**Never:**
- Close an Issue without first flipping the aidocs/16 row to `done`
  (orphan close — the SSOT lies).
- Edit Issue labels by hand to mean something different from the
  aidocs/16 status (state-machine drift).
- Reopen an Issue without first flipping aidocs/16 to `queued` or
  `in-progress`.

**Auto-close via PR.** When a PR's body contains `Closes #N`, GitHub
closes Issue N at merge. This is the canonical close path.

---

## 9. Projects v2 board

**One board.** Operator-setup per
[`docs/ops/github-projects-board-setup.md`](../../docs/ops/github-projects-board-setup.md)
(GH-INFRA2).

**Columns.** Backlog / In Progress / In Review / Blocked / Done.
Mapping per §2.

**Custom fields.** `Severity` / `Area` / `Effort` / `Stage` /
`Aidocs ID`. Populated from labels on auto-add.

**Three saved views.**

1. **Active sprint** — `status:in-progress`. The "what is being
   worked on right now" view.
2. **External-pickup** — `status:queued AND has-label:area:* AND
   no:assignee`. The "good first issue" surface for external
   contributors.
3. **Recently shipped** — `status:done` last 30 days, grouped by
   milestone. The "what was the last release about" view.

**Rules.**
- The board is **populated automatically** from Issues at creation
  via the auto-add-to-project rule. Never hand-edited.
- The board is **NOT** a substitute for `aidocs/16`. If a row has no
  Issue, it has no board card. That's correct: the row lives in the
  SSOT, full stop.
- A board card without a matching aidocs/16 row is a bug. Fix the
  Issue first (close as `invalid` with a pointer to the missing
  row), then add the row.

---

## 10. Labels axis matrix

**Source.** [`.github/labels.yml`](../../.github/labels.yml) — 50+
labels, synced to GitHub by `.github/workflows/sync-labels.yml`.

**Axes.**

| Axis        | Values                                                      | Mirrors                       |
|-------------|-------------------------------------------------------------|-------------------------------|
| `severity:` | `critical` / `major` / `minor` / `nice-to-have`             | aidocs/16 Severity (SWEEP rows) |
| `area:`     | `backend` / `frontend` / `plugins` / `infra` / `docs` / `semantics` / `mcp` / `vis` / `ai` | aidocs subfolder topology |
| `type:`     | `bug` / `feature` / `docs` / `refactor` / `chore`           | Conv-Commits type             |
| `status:`   | `queued` / `in-progress` / `blocked` / `done`               | aidocs/16 Status              |
| `stage:`    | (the 8 main stages + decommissioned)                        | `aidocs/00-doc-stages.md`     |
| `effort:`   | `S` / `M` / `L` / `XL`                                      | aidocs/16 Size                |

**Rule.** Every Issue carries **at least one label per axis**.
Defaults applied on file:

```yaml
severity: minor
status: queued
stage: feature-defined
type: feature
area: (inferred from path or asked)
effort: M
```

A label-axis gap is a triage failure. Reviewers fix it before
moving the card to In Progress.

---

## 11. Dependabot

**Cadence.** Weekly Monday scans. Source:
[`.github/dependabot.yml`](../../.github/dependabot.yml) (shipped via
GH-INFRA1).

**Ecosystems.** Maven (`backend/`), npm (`frontend/`),
GitHub Actions (`.github/workflows/`), Docker
(`backend/Dockerfile.patched` + `frontend/Dockerfile`),
pip (`scripts/`).

**Review discipline.**
- Each Dependabot PR runs the standard CI matrix (security gates +
  test suite).
- Reviewer gates on: green CI + zero new `CVSS≥7` from
  Dependency-Check.
- Conv-Commits scope: use the package name. Example:
  `chore(deps): bump org.neo4j:neo4j-ogm-core 4.0.6 → 4.0.7`.

Dependabot PRs do **not** require an aidocs/16 row — they're the
one exception, captured under `chore(deps)` in release notes.

---

## 12. Release notes

**Generation.** Auto-rendered from Conventional Commits scopes per
[`.github/release.yml`](../../.github/release.yml) — categories
mirror the `type:` and `area:` label axes.

**Workflow.** [`.github/workflows/release-notes.yml`](../../.github/workflows/release-notes.yml)
opens a draft release on tag push.

**Operator review.** Edit the draft to:
1. Cite the **aidocs/16 row IDs** the release bundles (top of body).
2. Cite the matching **aidocs/34 rows** (in the body).
3. Highlight any **breaking changes** with an explicit operator
   migration note (per CLAUDE.md "Always: maintain the upstream
   upgrade path").
4. Link the **SBOM artefact**.

Then publish. The draft is never auto-published.

---

## 13. Security disclosures

**Private channel.** [`SECURITY.md`](../../SECURITY.md) at repo root
+ the security-finding template at
[`.github/ISSUE_TEMPLATE/security-finding.yml`](../../.github/ISSUE_TEMPLATE/security-finding.yml).

**Rules.**
- **Never** file a public Issue for a security finding.
- The reporter follows `SECURITY.md` (private contact +
  coordinated-disclosure window).
- Internal triage opens a **private** GitHub Security Advisory
  (`gh secret-advisories create`) — that's where the work tracks.
- The fix lands as a normal PR (Conv-Commits `fix(<ID>)`), but the
  Security Advisory provides the public CVE-grade record at
  disclosure time.
- The aidocs/16 row is filed AFTER the advisory is published — the
  row may carry only the advisory ID + a sanitised summary while
  the embargo holds.

If `SECURITY.md` doesn't exist at the repo root, **create it now**
pointing at `security@nuclide.systems` (or the project-defined
contact) + a 90-day coordinated-disclosure default.

---

## 14. The traceability query

**The whole policy is in service of this one query being
answerable in under a minute:**

> *For feature X — what aidocs/16 row, what design doc, what persona
> findings, what branch, what commits, what PR, what reviewer, what
> release, what aidocs/34 row, what aidocs/data/00 delta?*

The query — runnable as `scripts/trace-feature.sh <ID>`:

```bash
#!/usr/bin/env bash
# Resolve a feature ID across every traceability surface.

ID="$1"

# 1. aidocs/16 backlog row
grep -A 2 "^| ${ID} " aidocs/16-dispatcher-backlog.md

# 2. design doc(s) — search aidocs/ for the ID outside the backlog
grep -rn "${ID}" aidocs/ --include='*.md' | grep -v '16-dispatcher-backlog.md'

# 3. persona findings
grep -rn "${ID}" aidocs/agent-findings/ 2>/dev/null

# 4. commits scoped to the ID
git log --grep="^[a-z]\+(${ID}): " --oneline

# 5. files touched
git log --grep="^[a-z]\+(${ID}): " --name-only --pretty=format:""

# 6. aidocs/34 admin-facing row
grep "${ID}" aidocs/34-upstream-upgrade-path.md

# 7. aidocs/data/00 model delta
grep "${ID}" aidocs/data/00-model-inventory.md

# 8. GitHub Issue (if any)
gh issue list --search "${ID} in:title" --state all 2>/dev/null

# 9. Release that contains the merge commit
COMMIT=$(git log --grep="^[a-z]\+(${ID}): " --format=%H | tail -1)
[ -n "${COMMIT}" ] && git tag --contains "${COMMIT}" 2>/dev/null
```

Wired as [`scripts/trace-feature.sh`](../../scripts/trace-feature.sh)
— shipped with this policy. Try it:

```bash
$ scripts/trace-feature.sh FS1b
```

If any one of the nine surfaces returns nothing for a shipped
feature, **traceability is broken**. Fix the missing surface before
shipping anything else.

---

## 15. Anti-patterns

Things NOT to do:

1. **File an Issue for every backlog row** — the 360-row sync trap.
   aidocs/16 is the SSOT; Issues are the narrow 4-gate subset.
2. **Use Projects v2 as a substitute for aidocs/16** — the
   SSOT-drift trap. The board reflects Issues; Issues reflect the
   4-gate subset; aidocs/16 is the catalogue.
3. **Cut a release without the aidocs/34 row landing first** — the
   "what shipped?" gap. Admins read aidocs/34; if it's empty, the
   feature is invisible to operators.
4. **PR title without Conventional Commits scope** — traceability
   break. TRACE-A misses the commit; release-notes auto-render
   drops it into the "uncategorised" bucket.
5. **Issue close without the matching aidocs/16 row flipping** —
   orphan close. The SSOT lies; the next contributor sees `queued`
   for work that shipped weeks ago.
6. **Stage label drift between doc front-matter and Issue label** —
   the unified state machine breaks. If `aidocs/00` taxonomy
   changes, `.github/labels.yml` changes in the same commit.
7. **Filing backfilled Issues without a BACKFILL disclosure marker —
   silent forgery of the audit trail.** Retroactive Issues without
   the §8.1 marker look identical to real-time-filed Issues; a future
   reader cannot tell the difference. That's not "filling in audit
   history" — it's **fabricating** distributed contemporaneous review.
   Per `feedback_no_synthetic_provenance.md`. The marker shape in §8.1
   is non-negotiable.
8. **Mass-renaming labels in the GitHub web UI** — `.github/labels.yml`
   is the SSOT. The sync workflow re-applies the canonical set on
   the next push to `main`.
9. **Cherry-picking commits across the release boundary without
   updating aidocs/34** — back-porting a fix into a published
   release line requires a fresh aidocs/34 row (the fix is, by
   definition, admin-visible).

---

## 16. Cross-references

**Companion docs:**
- [`aidocs/00-doc-stages.md`](../00-doc-stages.md) — stage taxonomy
- [`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md) — backlog SSOT
- [`aidocs/34-upstream-upgrade-path.md`](../34-upstream-upgrade-path.md) — admin diff stream
- [`aidocs/44-fork-vs-upstream-feature-matrix.md`](../44-fork-vs-upstream-feature-matrix.md) — per-feature matrix
- [`aidocs/data/00-model-inventory.md`](../data/00-model-inventory.md) — model snapshot
- [`aidocs/platform/106-requirements-traceability.md`](../platform/106-requirements-traceability.md) — TRACE-A design
- [`aidocs/strategy/83-github-features-leverage.md`](83-github-features-leverage.md) — GH-INFRA1 scaffolding
- [`docs/ops/cut-a-release.md`](../../docs/ops/cut-a-release.md) — release runbook
- [`docs/ops/github-projects-board-setup.md`](../../docs/ops/github-projects-board-setup.md) — board setup runbook

**Memory rules** (in `/root/.claude/projects/-opt-shepard/memory/`):
- `feedback_unified_work_item_states.md` — doc stages = backlog statuses = GH labels
- `feedback_github_features.md` — solo-dev cost vs external-value heuristic
- `feedback_backlog_consult_context.md` — read aidocs/16 + 34 + 44 + data/00 + 00-doc-stages before substantive work
- `feedback_continuous_doc_maintenance.md` — every learning + every shipped change updates affected docs in the same turn
- `feedback_github_pm_policies.md` — this policy's standing rules (4-gate Issues filter, one-way sync, SSOT-first)

**CLAUDE.md sections** that reviewers cite when rejecting PRs:
- "Always: maintain the upstream upgrade path" → aidocs/34 row
- "Always: keep `aidocs/42-vision.md` current" → vision update
- "Always: keep `aidocs/44-fork-vs-upstream-feature-matrix.md` current" → matrix flip
- "Always: keep test coverage at the recommended floor" → tests in same PR
- "Always: keep the security gates green" → SAST/SCA gates
- "Always: keep user-facing docs in step with shipped features" → docs/reference/* page
- "Always: think plugin-first for new features" → plugin shape check
- "Always: surface operator knobs in the admin config" → `:*Config` + admin REST
- "Always: plugins ship their own documentation" → plugins/<id>/docs/*
- "Always: file Issues + cut releases per `aidocs/strategy/85`" → this doc
