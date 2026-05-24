---
layout: default
title: "Maturity stages — alpha · beta · stable"
description: "Definitions for the maturity:alpha / maturity:beta / maturity:stable GitHub PR labels used in the shepard fork. Default expectation during the preview phase: BETA."
audience: contributor + reviewer + operator
stage: deployed
last-stage-change: 2026-05-24
---

# Maturity stages — alpha · beta · stable

Every PR that ships a user-visible change in the shepard fork carries
exactly **one** of three maturity labels: `maturity:alpha`,
`maturity:beta`, or `maturity:stable`. The label tells reviewers and
operators what to expect from the merged change — what works, what's
provisional, what needs follow-on.

## TL;DR

| Label | Means | Suitable for |
|---|---|---|
| **`maturity:alpha`** | Compileable. Smallest case works. Tests absent or skeletal. Docs absent or stub. | **Brave operators only.** Use on `main` if the reviewer explicitly opts in — typically when a sketch is more valuable than an empty space. |
| **`maturity:beta`** *(default during preview)* | Tested on the happy path. Docs partial. Edge cases acknowledged but not all addressed. **Default expected outcome for the hourly low-risk sweep + most preview-phase merges.** | Most preview-phase merges. Operators see the feature working; reviewer files a follow-on row for the remaining gaps. |
| **`maturity:stable`** | Tested. Docs complete (the three-audience set per `feedback_three_audience_docs.md` where applicable). Edge cases handled. Scope-bounded. | Production-ready. The reviewer can close the originating backlog row entirely. |

**Preview-phase default**: `maturity:beta`. shepard is in the v6
preview window; the realistic acceptance bar for an hourly automated PR
or a small human PR is "works + tested + honest about what's missing"
— that's beta. Stable is the aspirational target; alpha is the
exception case.

## Definitions

### `maturity:alpha`

**The PR ships a sketch.** The minimum case works; the implementation
is recognisable; the wire shape (REST / UI affordance / backend
behaviour) is what the design doc commits to. But:

- Tests are **absent or skeletal** (a single happy-path Vitest + no
  edge cases; or just the compile-passes check)
- Documentation is **absent or a one-line stub** (the in-app `/help`
  page is missing or says only "coming in v0.x")
- Edge cases are **not addressed**, neither in code nor in test
- The change is a **proof of concept** — a reviewer can verify that
  the design is implementable; an operator should NOT lean on the
  feature in production
- The follow-on work is real (typically L-sized) and named in the
  PR's "Known gaps" section

**Acceptance criteria** (a PR labelled alpha MUST satisfy ALL of):
- Compiles cleanly (`mvn -B compile` for backend touches; `npm run
  build` for frontend; `python3 scripts/regenerate-doc-stage-index.py`
  for aidocs touches)
- The smallest case demonstrated in either a unit test or a worked
  example in the PR body
- A "Known gaps" section in the PR body listing what's NOT done +
  filed backlog row IDs for each gap
- An honest empty state on any UI surface (per `feedback_basic_advanced_superset.md`)

**Example shape**: an MCP tool that responds to one canonical request
shape but no edge cases yet; a placeholder admin page with a working
REST data dump but no edit affordances; a Cypher migration that
moves the easy 90% of rows + leaves the gnarly 10% for a follow-on.

**Reviewer guidance**: merge only if "the sketch on main is better
than the empty space." Otherwise request iteration to beta.

### `maturity:beta`

**The PR ships a working feature, honestly framed.** The happy path
is tested + observably correct; the wire shape is final; the
documentation is at least the one user-facing page that lets a
researcher discover and use the feature. But:

- Edge cases are **partially handled** — the obvious ones are tested,
  the corner cases are documented as known limitations
- Documentation is the **reference page + a partial task page** — not
  the full three-audience set (admin runbook + reference + quickstart
  per `feedback_three_audience_docs.md`)
- The scope is **slightly narrower than the row implied** — the
  reviewer can read the PR + the row and see the delta
- Operators can **rely on the feature for normal workflows**; the
  reviewer should expect a small follow-on row for the docs + edge
  cases

**Acceptance criteria** (a PR labelled beta MUST satisfy ALL of):
- Compiles + passes the existing test suites (Vitest + JUnit + e2e if
  the change touches user-visible UI)
- Happy-path tests cover the row's defect type — if the row says "fix
  N+1 fan-out," there's a test asserting the request count
- The user-facing reference page is updated (`docs/reference/<thing>.md`)
  OR a new reference page is created
- A "Follow-on" section in the PR body lists the gaps + sized + filed
  as backlog rows
- Honest empty states + error states on every UI surface the PR
  introduces

**Example shape**: the SEMA-V6-005 3-click annotation dialog ships
with the basic + advanced mode flow working + Vitest happy-path +
docs/help/annotating-data.md draft — but per-vocabulary keyboard
shortcuts deferred to a v1 follow-on; that's beta.

**Reviewer guidance**: merge by default. File the follow-on row if
not already present.

### `maturity:stable`

**The PR ships a feature that is production-ready.** The change is
scope-complete; the test set covers happy + edge + error paths; the
three-audience docs are landed; the standing rules are honoured. The
reviewer can close the originating backlog row entirely on merge.

**Acceptance criteria** (a PR labelled stable MUST satisfy ALL of):
- All beta criteria PLUS:
- Edge cases identified during design are tested OR explicitly
  documented as out-of-scope-by-design
- Three-audience docs landed where applicable (admin runbook +
  reference page + quickstart per `feedback_three_audience_docs.md`)
- The originating backlog row is updated to `✅ shipped` with the
  PR's commit hash + ship date
- No "Follow-on" section in the PR body, OR if there is one, the
  follow-ons are explicit "v1+" stretch goals, not gap-coverage
- Per `feedback_done_criteria.md`: backend + frontend + tests + docs;
  all four present where the row's surface touches them

**Example shape**: the today's-LIC1 PR (license + accessRights on
Collection + DataObject) — backend + frontend + JSON serialisation
tests + V57 migration + admin docs + the typed enum on the OpenAPI
schema; nothing deferred. That's stable.

**Reviewer guidance**: merge + close the backlog row. The feature is
done.

## How the label is applied

**By the hourly low-risk sweep routine** (per `aidocs/16` row for the
routine; the QE phase of the routine reads the rubric below + applies
the label automatically on draft → ready-for-review transition).

**By a human reviewer** for human-authored PRs — the reviewer reads
the PR diff + applies the appropriate label before merging. If
unsure, default to `maturity:beta` and file the follow-on row.

**By any contributor** at PR-open time — self-tag as a request; the
reviewer confirms or downgrades before merging.

## How a feature evolves through the labels

A feature typically lives through all three:

1. **Iteration 1** — a v0 ships at `maturity:alpha` because the
   sketch is more valuable than the empty space. Operator-facing
   surfaces are clearly marked "Preview" so users set expectations.
2. **Iteration 2** — a follow-on PR addresses the alpha's "Known
   gaps" section, lands the happy-path tests + the reference page,
   bumps the label to `maturity:beta`. The original feature row is
   updated to `🚧 in-flight → ✓ shipped (beta)`.
3. **Iteration 3+** — subsequent PRs close edge-case gaps + land the
   full three-audience docs; the label graduates to `maturity:stable`.
   The originating row is closed.

Not every feature reaches stable. Some live at beta indefinitely if
the gap-closing follow-ons aren't justified by usage; that's an
explicit operator decision documented in `aidocs/44-fork-vs-upstream-feature-matrix.md`.

## How operators read the labels

| Operator question | Answer |
|---|---|
| "Is feature X production-ready?" | `maturity:stable` → yes. `maturity:beta` → yes for normal workflows; check the follow-on for what's not handled. `maturity:alpha` → no; treat as preview only. |
| "Should I depend on the wire shape?" | All three: yes; the wire shape is final by alpha. The label tells you about test/docs maturity, not API stability. |
| "Will this break in a future shepard?" | Per `aidocs/34-upstream-upgrade-path.md` rules: no. The maturity label is orthogonal to upgrade-safety. |
| "Is there documentation?" | `stable` → yes, three-audience. `beta` → yes, partial. `alpha` → minimal or absent; the PR body is the documentation until the follow-on. |

## Cross-references

- `CLAUDE.md` — the doctrine library; especially `feedback_done_criteria.md`,
  `feedback_three_audience_docs.md`, `feedback_basic_advanced_superset.md`
- `aidocs/16-dispatcher-backlog.md` — backlog rows reference these
  maturity tags in their status field
- `aidocs/34-upstream-upgrade-path.md` — wire-stability rules
  (separate from maturity)
- `aidocs/strategy/85-github-project-management-policies.md` — how PRs
  + Issues + labels integrate into the project-management surface
- `docs/contributing/` — other contributor-facing pages (PR templates,
  release notes shape, etc.)

## Provenance

- Filed by: hourly low-risk backlog sweep routine bootstrap, 2026-05-24
- Author intent (user, 2026-05-24): *"if shipping decide on Alpha
  Beta stable versioning to indicate current completion."* During the
  preview phase, **beta is the default expected outcome.**
- Labels created on the GitHub repository as `maturity:alpha`,
  `maturity:beta`, `maturity:stable` with descriptions pointing back
  at this page.
