---
stage: feature-defined
last-stage-change: 2026-05-23
---

# GH lean-extension cost consult — 2026-05-23

Cost-skeptic review of the L1/L2/L3 lean proposal. Rule: solo-dev cost
near-zero **AND** value high (per `feedback_github_features.md`).

## Verdict

**TOO-LEAN-ADD, with one substitution.** L1 endorses cleanly. L3 endorses
as zero-cost. **L2 fails the bar** as currently scoped — Environments + a
new `deploy.yml` doesn't pay back for a 1.x-deploys-per-week solo. Drop
L2 for now; add two cheaper traceability wins (annotated checkpoint tags
+ Conv-Commits scope pre-commit hook) that close more of the trace-gap
than L2 would.

## L1 cost analysis — `traceability.yml` PR digest

- **Setup:** ~2 h. One workflow, calls `scripts/trace-feature.sh` against
  the scope parsed from the PR title (Conv-Commits regex), uploads
  `traceability-digest.json` artifact + posts a `${{ github.token }}`
  comment. Re-uses existing script — no new logic.
- **Recurring maintenance:** ~5 min/month, only when `trace-feature.sh`
  output shape changes (rare; the script is the SSOT).
- **When it breaks:** soft. PR comment fails to render → reviewer falls
  back to running the script locally. Doesn't block merge unless someone
  wires `needs:` against it (don't).
- **Estimate confidence:** medium-high. The script exists and is stable.

**Verdict: PASS.** Solo-cost ~zero, value high (the §5 release pre-flight
gets a per-PR preview; reviewer no longer needs to run nine greps).

## L2 cost analysis — Environments + `deploy.yml`

- **Setup:** ~6–10 h honestly. Three Environments to create, deploy
  workflow to write, secret-routing per env (nuclide vs dlr-staging vs
  dlr-prod live on different networks — dlr-prod is unreachable from
  GitHub Actions per `feedback_host_boundary.md`, so the workflow can
  at best record the deploy event, not execute it). Plus the inevitable
  iteration when the Deployments API event shape doesn't match what
  downstream readers expect.
- **Recurring maintenance:** 10–20 min/week in steady state. Every time
  the deploy script on the box changes, the workflow gets out of step.
- **When it breaks:** **blocking**, depending on shape. If the workflow
  is the deploy path, a broken `deploy.yml` means no deploys. If it's a
  parallel record-keeping shim (preferred), it's soft — but then it
  duplicates `make redeploy` for marginal trace value.
- **Estimate confidence:** medium. The 6–10 h floor assumes deploys stay
  on-box (per host-boundary memory); if Flo wanted GitHub-driven deploys
  to nuclide that's a different, larger project.

**Verdict: FAIL the bar as scoped.** The deploy cadence (~1×/week of
visible features, per `feedback_deploy_after_visible_features.md`)
doesn't justify the recurring cost. The traceability value — knowing
which commit is on which env — is real but is already cheaply available
via `git describe` on the box + the existing `make redeploy` log. Defer
to a later GH-INFRA slice when there's bilateral team flow demanding it.

## L3 cost analysis — `git notes refs/notes/trace`

- **Setup:** ~30 min. Pick the namespace, document the convention in
  `aidocs/strategy/85` §5 or §14, add a `git config --add
  notes.displayRef refs/notes/trace` snippet to the contributor
  onboarding.
- **Recurring maintenance:** ~zero. Notes only get added when an old
  scope-less commit needs retro-attachment, which is rare going forward
  (the pre-commit hook below makes it rarer still).
- **When it breaks:** **trace-data-loss, soft**. `git notes` don't push
  by default (`git push origin refs/notes/*` is the manual step). If Flo
  forgets, the notes don't survive a clone. Mitigation: add the explicit
  push refspec to local `.git/config` or a make target (`make
  push-trace-notes`).
- **Estimate confidence:** high.

**Verdict: PASS as zero-cost utility.** Value is modest but real for the
predecessor-history commits + early MFFD-arc commits that pre-date the
Conv-Commits-scope discipline.

## Operator failure modes per L#

| L# | Failure mode | Severity |
|----|---|---|
| L1 | PR comment posts but artifact upload fails (storage quota) | soft — script still runnable locally |
| L1 | `trace-feature.sh` output schema changes, JSON parsing breaks | soft — bot comment falls back to plain text |
| L2 | Env approval blocks deploy when Flo is unavailable | **blocking** unless explicitly bypass-able |
| L2 | dlr-prod env unreachable from Actions → workflow misreports state | trace-data-loss, possibly misleading |
| L3 | Notes namespace not pushed → notes invisible to fresh clones | trace-data-loss |
| L3 | Two collaborators edit same commit's notes → merge conflict | soft — `git notes merge` exists |

## Cheaper alternatives identified

1. **Annotated checkpoint tags.** `git tag -a checkpoint/<name> -m "..."`
   for each substantive milestone (MFFD ingest complete, v15.10 ship,
   audit-pass moments). Cost: ~30 sec/tag. Gain: every checkpoint is a
   first-class git object, sorts in `git log --graph`, survives clone,
   no infra. **Endorse.** Add to `aidocs/strategy/85` §5 ("when you
   close a §5 pre-flight checklist, tag a checkpoint").

2. **Pre-commit hook nudging Conv-Commits scope.** ~1 h to write a
   `.git/hooks/commit-msg` (and a `scripts/install-hooks.sh` to land it).
   Refuses (or warns) on commits without `<feat|fix|docs|...>(<ID>):`
   shape, where `<ID>` matches the aidocs/16 catalogue. Cost: near-zero
   recurring; the hook is local-machine only (no CI cost). Gain:
   structural fix for the scope-less-commit problem that L3 papers over
   retroactively. **Endorse strongly.** Pairs with L3: hook prevents new
   scope-less commits; L3 fixes old ones.

3. **Repository custom properties (GitHub 2024 feature).** Cost: ~5 min
   one-time via Settings UI; tags the repo with `instance: nuclide,
   parent-upstream: dlr-shepard/shepard, license: AGPL-3.0` etc.
   Discoverable via `gh repo list --json customProperties`. Gain:
   modest — only useful when DLR has multiple repos. **Defer.** No
   compelling use case today; revisit if more shepard-related repos
   land under `noheton/`.

4. **Augment `trace-feature.sh` with fuzzy scope-less commit match.** ~2
   h to add a fallback grep over commit messages + file-path overlap for
   commits where Conv-Commits scope is missing. Gain: closes the same
   gap L3 closes, without needing the operator to remember to push
   notes. **Endorse as backstop.** Complements rather than replaces L3
   (notes record human judgement; fuzzy match is a heuristic).

## Recommended LEAN-er proposal

Replace the 3-item lean with this 4-item lean:

- **L1 (keep):** `traceability.yml` PR-digest workflow.
- **L2 (drop):** GitHub Environments. Defer until external-team flow
  justifies it. Note in `aidocs/strategy/83` matrix.
- **L3 (keep):** `git notes refs/notes/trace` + a `make
  push-trace-notes` target so notes always travel.
- **L4 (add):** annotated checkpoint tags at §5 pre-flight close.
- **L5 (add):** local `commit-msg` hook enforcing Conv-Commits scope
  against the aidocs/16 catalogue.
- **L6 (add, lower priority):** fuzzy-match fallback in
  `trace-feature.sh` for scope-less historic commits.

Net solo-dev recurring cost: still near-zero. Net trace-gap closure:
larger than the original 3-item proposal, because the structural
prevention (L5) does more work than the recording infra (L2) would have.

(796 words)
