# Persona — Strategy Aligner & Executive Advisor on GH-PM adoption (2026-05-23)

**Lens.** I have 42 seconds in the elevator with Prof. Voggenreiter. He
asks "what changed in how you run the project this week?" The answer
must fit on a slide a Clean Aviation JU programme manager would not
push back on. KPIs, ROI, honest risk, institute-leadership readability.

**Scope.** Audit the six adoption questions in
`aidocs/strategy/85-github-project-management-policies.md` (feature-defined,
2026-05-23) plus the unified state machine in `aidocs/00-doc-stages.md`.

---

## TL;DR — aggregate vote

| Q  | Claude's lean              | My vote          | One-line reason (strategy lens)                                                       |
|----|----------------------------|------------------|----------------------------------------------------------------------------------------|
| Q1 | Adopt §85 as-is            | **A — adopt**    | The 4-gate Issue filter + SSOT-first rule is the auditable story funders want to hear |
| Q2 | Everything-this-session rc.1| **edit**        | Bundle is correct; **name it differently** — `v6.0.0-rc.1` understates the moment     |
| Q3 | Dry-run backfill plan      | **A — dry-run**  | The backfill artefact IS the strategy deliverable; don't ship blind                   |
| Q4 | No auto-Issues for agents  | **A — no**       | Auto-spam destroys the "Issues are curated" story external evaluators see             |
| Q5 | Manual board setup         | **A — manual**   | One-time cost; a hand-built board reads as intentional, not "AI generated"            |
| Q6 | Trust + CI                 | **edit**         | Trust for now, **but commit to install the hook before v6.0.0 stable** (not GA)      |

**Aggregate verdict.** §85 is funding-body-credible as written. The
*adoption choreography* matters more than the policy details — keep the
v6.0.0-rc.1 release notes short, cite §85 as the governance artefact,
and resist the urge to ship the hook before the policy has been
exercised once on a real release cut.

---

## Q1 — Adopt §85 as-is?

**Finding.** §85 is unusually well-written for a project-management
policy. The 4-gate Issue filter (§3), the unified state machine across
front-matter/aidocs/16/labels/board (§2), the §14 traceability query,
and the §13 security disclosure split are each individually
defensible to an external evaluator. The companion-doc graph
(`00 ↔ 16 ↔ 34 ↔ 44 ↔ data/00 ↔ 106`) is the kind of artefact a Clean
Aviation JU reviewer cites as evidence of mature governance.

**Recommendation.** **A — adopt as-is.**

**Rationale (strategy lens).** Every funding-body data-management
plan question maps cleanly onto a §85 surface: *"How do you trace a
shipped feature back to its design rationale?"* → §14. *"How do you
handle security disclosures?"* → §13. *"How do contributors
onboard?"* → §6 + §7 + §10. The doc IS the DMP-style audit answer; we
should not edit it before it has been exercised once.

**Cost of being wrong.** Low. If we adopt and a clause turns out to
hurt, we amend in v6.0.0-rc.2. The cost of NOT adopting is higher: a
funder asks "how do you govern AI-assisted development?" and we have
no policy doc to point at.

---

## Q2 — `v6.0.0-rc.1` = everything-this-session?

**Finding.** The proposed bundle is enormous:
post-MFFD-import + Garage S3 (FS1b) + v15.2 smart warmup (IMPORT-W1) +
WAAPI + Bibliography + Origin myth + DOC-STAGE + GH-PM + v1-compat
Phase 1. This is roughly **nine** thematically distinct shipping
units — substrate, infra, ingest, semantics, governance, compat —
collapsed into one milestone.

**Recommendation.** **edit — keep the bundle, change the framing.**

The bundle is correct (these all stabilised together; cutting them
apart fights physics), but `v6.0.0-rc.1` undersells it. Three concrete
edits:

1. **Subtitle the milestone.** In the GH milestone description, name
   it `v6.0.0-rc.1 — "Provable provenance + open substrate"`. Two
   anchor themes — exactly what an external reader needs to grok the
   release in 10 seconds.
2. **Pillar the release notes.** Group commits into three pillars in
   the release body: (a) **Substrate freedom** — Garage S3, ingest;
   (b) **Provable provenance** — DOC-STAGE, GH-PM, Bibliography,
   Origin myth; (c) **Compat path** — v1-compat Phase 1. Not by
   `type:` or `area:` label — those are too granular for the strategy
   read.
3. **Don't promise rc.2 timing in the rc.1 notes.** §85 §4 already
   says "no calendar." Keep that discipline visible.

**Rationale (strategy lens).** A release named "rc.1" with nine
unrelated bullets reads as drift. The same release with two named
themes reads as deliberate. Same code, different funder-narrative
landing.

**Cost of being wrong.** Medium. If the bundle is too big to be
stable, rc.1 ships with regressions and the narrative collapses. **The
honest risk:** the `post-MFFD-import` work and `v1-compat Phase 1` are
on different reliability curves — MFFD has been exercised live;
v1-compat has not. Consider holding v1-compat for rc.2 if rc.1 slips.

---

## Q3 — Backfill dry-run first?

**Finding.** Adopting §85 means classifying every existing aidocs/16
row + every recent PR + every existing branch against the new state
machine. Without a dry-run, the first 24 hours of adoption look like
chaos to anyone watching the repo (label churn, status flips, Issue
spam).

**Recommendation.** **A — dry-run first, no immediate Issue creation.**

The dry-run artefact should be a single markdown file
(`aidocs/strategy/86-gh-pm-backfill-plan.md` or similar) listing:
- Every aidocs/16 row → proposed `stage:` label
- Every recent merged PR (last 30) → proposed scope-correction if any
- Every existing branch → proposed rename or "leave"
- The 0–5 rows that meet the 4-gate test for actual Issue creation

**Rationale (strategy lens).** The backfill plan IS a deliverable.
It's the audit artefact that proves the policy was applied with
intent, not retroactively rationalised. When a reviewer asks "show me
how you adopted the policy," handing them the dry-run + the diff that
followed it is a stronger answer than handing them the policy alone.

**Cost of being wrong.** High if we skip the dry-run. Issue spam +
label churn on a public repo damages external perception during
precisely the moment we want it to look governed.

---

## Q4 — Auto-file Issues for in-flight agent dispatches?

**Finding.** §3 gate 4 explicitly *permits* an Issue at agent dispatch
("optional Issue opens at dispatch"). Claude's lean is to default-off
unless the user signals. The auto-file alternative would create an
Issue every time an agent is dispatched to a worktree.

**Recommendation.** **A — no auto-file. User-signal only.**

**Rationale (strategy lens).** Two reasons this matters more
strategically than it looks:

1. **AI-assisted-development optics.** Auto-spamming Issues from
   agent runs makes the repo look "AI-driven" in the cargo-cult
   sense. Curated Issues — one per genuinely external-facing piece
   of work — read as "AI is a tool; humans curate." That distinction
   matters for EU AI Act Art. 50 transparency claims and for the
   f(ai)²r provenance story we're already telling.
2. **Issue-count is a noisy KPI.** Funding bodies sometimes ask
   "how active is the project?" and read Issue counts as a proxy.
   Inflating Issue counts via agent-dispatch auto-fire is the
   exact kind of gaming we should avoid being even suspected of.

**Cost of being wrong.** Low if we default off and turn it on later.
Medium if we default on and have to turn it off — the spam is already
on the public timeline.

---

## Q5 — Projects v2 board: manual UI vs scripted?

**Finding.** §9 + the GH-INFRA2 runbook
(`docs/ops/github-projects-board-setup.md`) define the board shape:
five columns, five custom fields, three saved views. Manual setup is
~30 minutes; a `gh api` script could automate it.

**Recommendation.** **A — manual setup.**

**Rationale (strategy lens).** Three reasons this is the right call
this time:

1. **One-shot cost.** The board gets configured once. Scripting a
   one-shot is yak-shaving when a manual run is 30 minutes.
2. **Intentionality reads better.** A manually-curated board with the
   three saved views named and pinned reads as "someone thought about
   this." A scripted board with auto-generated names reads as
   boilerplate. Same end state; different impression.
3. **Reduces "AI everywhere" footprint.** The board is the public
   face of the project on GitHub. Hand-shaping it once is a small
   anti-cargo-cult signal.

**Cost of being wrong.** Trivial. If the manual board needs a
rebuild, we either redo it manually (30 min) or script it then. No
sunk cost.

---

## Q6 — PR-scope enforcement: install hook now vs trust + CI?

**Finding.** GH-PM3 is the hook that would reject any PR title without
a Conv-Commits scope matching an `aidocs/16` ID. Claude's lean is
trust + CI (let people land PRs that breach, fix in review). The
alternative is install-now (auto-reject at PR creation time).

**Recommendation.** **edit — trust now, commit to install before
`v6.0.0` stable.**

The hook becomes a pre-condition for the v6.0.0 stable release, not
rc.1. Concrete: file the hook task in `aidocs/16` as `GH-PM3` with a
note that it gates v6.0.0 stable.

**Rationale (strategy lens).** Two competing forces:

- **Now:** the policy is fresh; reviewers have to manually enforce
  it; a hook would back them up. But a brand-new policy enforced by
  a hook on day one is the kind of thing that generates "ugh,
  process for process's sake" backlash from contributors.
- **Before stable:** by the time v6.0.0 ships, the policy will have
  been exercised through one full RC cycle. The hook then codifies
  established practice rather than imposing untested rules.

**Cost of being wrong.** If we install now and the policy needs
tweaks, the hook becomes a friction tax we can't easily reverse
without looking like we're walking back governance. If we wait and a
malformed PR slips into rc.1, it's recoverable (rewrite the commit
message; rerun the release-notes script).

---

## Strategy-specific overlays — angles Claude's lean missed

Claude's six leans are technically sound. Three angles I want
escalated before any of this lands:

### Overlay 1: The release-notes body is a strategy artefact.

§85 §12 treats release notes as auto-rendered + lightly edited. For
v6.0.0-rc.1 (and especially v6.0.0 stable) treat the release-notes
body as a **funder-readable artefact** — open with three sentences
that an external programme manager can quote. Specifically:

> "Shepard v6.0.0 introduces open-substrate storage (Garage S3),
> provable end-to-end provenance (DOC-STAGE + GH-PM governance), and
> live MFFD manufacturing data flowing from ZLP Augsburg into a
> FAIR-compliant catalogue. Backwards-compatible with upstream
> dlr-shepard 5.2.0; opt-in `/v2/` surface for fork additions."

That paragraph is what gets pasted into a Clean Aviation JU progress
report. Auto-rendered commit-list categories cannot do that work.

### Overlay 2: §85 itself is a publishable artefact.

A 550-line policy doc covering AI-agent-collaborative development +
end-to-end traceability + 4-gate Issue filter is genuinely novel as
of mid-2026 — most projects either over-process (Jira clone) or
under-process (Issues + vibes). Claude's lean treats §85 as internal
governance. Treat it as a **publication candidate**: the basis for an
RDA Working Group submission, an EOSC Symposium poster, or at
minimum a blog post citing the policy as a public artefact ("here's
how we run an AI-assisted research-data-platform project"). This is
free strategic capital we'd otherwise leave on the table.

### Overlay 3: The "AI as collaborator" story needs §85 to be visible.

The f(ai)²r integration + per-artefact 🧑/🤖/🤝 provenance badges
already tell a strong story about distinguishing human from AI work.
§85's Conv-Commits scope + Co-Authored-By trailers (Claude commits
all carry `Co-Authored-By: Claude …`) close the loop at the
commit-history level. Make this **explicit** in the v6.0.0 release
notes — one sentence: *"Every commit in this release is traceable to
an `aidocs/16` requirement row, and Claude-co-authored commits are
machine-distinguishable from human-authored commits via Co-Authored-By
trailers."* That sentence is the EU AI Act Art. 50 transparency
answer for the project's own development process. Don't bury it.

### Overlay 4 (honest risk): the policy is correct but expensive at scale.

The 4-gate Issue filter assumes we can keep external contribution
low. The moment we cross the GH-INFRA4 threshold (5+ external
contributors/month) the no-Issue default becomes a barrier — external
contributors expect to file Issues to start conversations. §85 §3
acknowledges this implicitly ("Until external velocity picks up").
The strategic action: **add a tripwire** — when monthly external PR
authors hit 3 (not 5), trigger a §85 review. Don't wait until the
filter is actively excluding people.

---

## Bottom line for the elevator

Prof. Voggenreiter asks "what changed?" The answer:

> "We codified how this project gets built. Every shipped feature now
> traces back to a single requirement ID, through code, tests,
> documentation, and the release that contained it — in under a
> minute, from one git query. AI contributions are
> machine-distinguishable from human contributions at the commit
> level. The first release that proves this works ships as v6.0.0-rc.1."

42 seconds, no slide needed. That's the deliverable.
