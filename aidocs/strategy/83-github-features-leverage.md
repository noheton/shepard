# GitHub features — what shepard uses, deliberately skips, and why

> **Status:** Live design doc, captured 2026-05-23 alongside the
> `GH-INFRA1` scaffolding commit. Updates land in the same PR as any
> change to the set of GitHub features in use.

## Why this doc exists

This fork has been quietly underusing the GitHub platform. The repo
has been here for months: code, Pages, Actions (CI / security / CodeQL
/ build-images / e2e / clients-kiota), the `gh` CLI for tooling. What
hasn't been used: Issues, Projects, Milestones, Discussions, Releases,
Dependabot, Code Owners, Issue/PR templates, GitHub Advisories.

Some of those gaps are right — we don't want the overhead. Others are
wrong — we're leaving free leverage on the table. This doc maps the
decision per feature, names the cost vs. value trade-off, and pins the
decision so future contributors don't have to re-derive it.

Pairs with: [`.github/labels.yml`](../../.github/labels.yml),
[`.github/dependabot.yml`](../../.github/dependabot.yml),
[`.github/CODEOWNERS`](../../.github/CODEOWNERS),
[`.github/pull_request_template.md`](../../.github/pull_request_template.md),
[`.github/ISSUE_TEMPLATE/`](../../.github/ISSUE_TEMPLATE/),
[`docs/ops/cut-a-release.md`](../../docs/ops/cut-a-release.md),
[`docs/ops/github-projects-board-setup.md`](../../docs/ops/github-projects-board-setup.md).

Backlog: `GH-INFRA*` rows in [`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md).

## The decision rule

> Default is "don't add a new GitHub feature unless the solo-dev cost
> is near zero AND the external-contributor / security-posture value is
> high." `aidocs/16` stays SSOT — never duplicate it into Issues
> wholesale.

Captured in memory at `feedback_github_features.md`.

## Per-feature decision matrix

Symbols: ✅ adopt (post-this-PR), 🟡 partial / niche use, ❌ skip,
🔵 already in use pre-this-PR.

| Feature                  | Decision | Why                                                                                                                                                       |
| ------------------------ | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Code hosting             | 🔵        | Primary mirror of upstream `gitlab.com/dlr-shepard/shepard`. Non-negotiable.                                                                              |
| Actions (CI/CD)          | 🔵        | 7 workflows live: `ci`, `codeql`, `security`, `pages`, `perf-smoke`, `integration-tests`, `clients-kiota`. Hits the six security gates from CLAUDE.md.    |
| Pages                    | 🔵        | Hosts `docs/`. Generated via `pages.yml`.                                                                                                                 |
| Issues                   | ✅        | Newly adopted: bug reports, feature requests from external contributors, docs improvements. **NOT** the backlog SSOT — that stays aidocs/16.              |
| Issue templates          | ✅        | Five templates (bug / feature / docs / security-redirect / disabled-blank). Mirror CLAUDE.md "Always:" sections in feature template plugin-first heuristic. |
| PR template              | ✅        | Mirrors every CLAUDE.md "Always:" rule. Acts as a forcing function on aidocs/34, aidocs/42, aidocs/44, plugin docs, coverage, security gates.             |
| CODEOWNERS               | ✅        | `* @noheton` for now. Auto-requests review on every PR. Belts-and-braces.                                                                                 |
| Dependabot               | ✅        | Weekly Monday scans against backend Maven, frontend npm, Python client + scripts, GitHub Actions versions, 16 plugin pom.xmls. Minor + patch grouped.    |
| Releases (manual)        | ✅        | `.github/release.yml` categorises PR titles by Conventional-Commits scope; `release-notes.yml` workflow renders the body on tag push. Operator-triggered. |
| Projects v2 (board)      | ✅        | Manual setup per `docs/ops/github-projects-board-setup.md`. Board surfaces **active** issues only; aidocs/16 stays SSOT.                                  |
| Labels (synced)          | ✅        | Canonical set in `labels.yml`; synced by `sync-labels.yml`. Five axes: severity, area, type, status, stage, effort.                                       |
| Security Advisories      | 🟡        | Use the GitHub Security Advisories surface when a CVE materialises. Disclosure path: email `fkrebs@nucli.de` (private), then draft advisory on the repo. |
| Dependabot security alerts | ✅      | Implicit-on for repos using Dependabot version updates. No extra config needed.                                                                            |
| Code scanning (CodeQL)   | 🔵        | Already wired via `.github/workflows/codeql.yml` with `security-extended` query set. One of the six CLAUDE.md security gates.                             |
| Secret scanning          | 🔵        | gitleaks runs in `security.yml`. GitHub's native secret scanning is also implicit-on for public repos.                                                    |
| Discussions              | ❌        | Skipped: Mattermost (HZDR) is the upstream conversation channel; introducing a second forum splits attention. Revisit if external community grows.        |
| Wiki                     | ❌        | Skipped: `aidocs/` + `docs/` cover the design + user-facing docs surface respectively. A third location would rot.                                        |
| Sponsors                 | ❌        | Skipped: funding flows via DLR institutional channels, not individual sponsorship. Not appropriate for the project.                                       |
| Codespaces               | ❌        | Skipped: `docker compose --profile dev` + the documented local-dev path covers the equivalent need. Codespaces would add a vendor-specific dev surface. |
| Milestones               | ❌        | Skipped: release tags + the aidocs/44 status column already track time-bound feature scope. Milestones would duplicate aidocs/16 sizing.                  |
| Environments             | 🟡        | The fork has two live environments (nuclide.systems + DLR boxes). Could be modelled as GitHub Environments for deploy gates; deferred to GH-INFRA5.       |
| Releases (auto-on-push)  | ❌        | Explicitly skipped — releases are operator-triggered judgement calls. See `docs/ops/cut-a-release.md` for the rationale.                                  |
| GitHub Apps / Marketplace | ❌      | Skipped unless concrete need surfaces. Each App is a new external trust + permission scope.                                                               |

## What gets adopted in `GH-INFRA1` (this PR)

| File | Purpose |
| --- | --- |
| `.github/release.yml`                       | Categorise PR titles into release-notes sections via Conventional Commits scopes. |
| `.github/workflows/release-notes.yml`       | On `v*.*.*` tag push, render release body via `softprops/action-gh-release@v2`. |
| `.github/ISSUE_TEMPLATE/config.yml`         | Disable blank issues; route to aidocs/16 + upstream Mattermost. |
| `.github/ISSUE_TEMPLATE/bug-report.yml`     | Structured bug report form. Labels: `type:bug` + `status:queued`. |
| `.github/ISSUE_TEMPLATE/feature-request.yml`| Structured feature-request form with plugin-first heuristic dropdown. Labels: `type:feature` + `status:queued`. |
| `.github/ISSUE_TEMPLATE/docs-improvement.yml`| Structured docs-issue form. Labels: `type:docs` + `status:queued`. |
| `.github/ISSUE_TEMPLATE/security-finding.yml`| Stub form that redirects to private disclosure (`fkrebs@nucli.de`). |
| `.github/pull_request_template.md`          | PR checklist mirroring every CLAUDE.md "Always:" section. |
| `.github/CODEOWNERS`                        | `* @noheton` default. |
| `.github/dependabot.yml`                    | Weekly Monday scans across backend / frontend / clients / scripts / actions / 16 plugins. |
| `.github/labels.yml`                        | Canonical label set across severity / area / type / status / stage / effort. |
| `.github/workflows/sync-labels.yml`         | Sync labels on `main` push when `labels.yml` changes. |
| `docs/ops/cut-a-release.md`                 | Operator runbook for cutting a release. |
| `docs/ops/github-projects-board-setup.md`   | Operator runbook for the (manually-created) Projects v2 board. |
| `aidocs/strategy/83-github-features-leverage.md` | This survey. |

## What's deferred to later `GH-INFRA*` slices

| ID         | Slice                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------ |
| GH-INFRA2  | Manually set up the Projects v2 board per `docs/ops/github-projects-board-setup.md`.       |
| GH-INFRA3  | Cut `v6.0.0-rc.1` once the MFFD real-data import lands.                                    |
| GH-INFRA4  | aidocs/16 ↔ GitHub Issues sync tooling. **Gated on external-contribution velocity.** Don't build until 5+ external contributors are filing issues per month — the duplicate-bookkeeping cost only pays back when there's real bilateral flow. |
| GH-INFRA5  | GitHub Environments for nuclide.systems + DLR boxes; deploy-gate via Environments approval. |

## Cross-references

- `feedback_github_features.md` — memory rule pinning the decision.
- `aidocs/16-dispatcher-backlog.md` § `GH-INFRA — 2026-05-23` —
  backlog row.
- `aidocs/34-upstream-upgrade-path.md` — pairs with releases (a
  release tag is a checkpoint where an operator should consult the
  ledger).
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — a release is the
  natural moment to reconcile `🚧 in-flight` → `✓ shipped` rows.
- `CONTRIBUTING.md` § "Filing issues" + § "PR discipline" — points
  external contributors here.
- `CLAUDE.md` §§ "Always: maintain the upstream upgrade path",
  "Always: keep the security gates green", "Always: keep test
  coverage at the recommended floor" — the rules the PR template
  enforces.

## Honest assessment

The fork has been carrying real engineering rigour internally
(aidocs/16, the six security gates, the four "Always:" docs) without
projecting that rigour to anyone outside the repo. From an external
contributor's perspective, the project looks lightly maintained
because the surface gives no signal of the work going on. The
templates + labels + Dependabot + release notes flip that signal.
The Project board is the smallest possible expression of "this is
what's in flight" that doesn't double-book aidocs/16.

None of these adoptions add solo-dev overhead. Every one of them adds
external-contributor and security-posture value. That's the test from
`feedback_github_features.md`, and these passes it.
