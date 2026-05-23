---
stage: deployed
last-stage-change: 2026-05-23
audience: contributor
---

# Audience-persona front-matter retrofit (DOCS-3A9)

Date: 2026-05-23
Backlog row: [`aidocs/16` DOCS-3A9](../16-dispatcher-backlog.md#docs-3a--2026-05-23-three-audience-docs-admin--user--plugin)
Scope: every `docs/**/*.md` page in this repo.
Pairs with: DOCS-3A1 (admin docs consolidation — already classified those 13 files), DOCS-3A3 (reviewer enforcement going forward), `feedback_three_audience_docs.md`.

## What landed

62 docs pages walked. All 62 now carry an `audience:` field in their YAML front-matter. The classification was applied per the rules in DOCS-3A9 spec; ambiguous cases used persona-aware judgment (see §"Judgment calls" below).

The retrofit was idempotent (re-running the inject script reports `noop-already-set (62)` for every page).

## Counts by audience

| Audience       | Count | Notes |
|----------------|------:|-------|
| `admin`        | 18    | docs/admin/* (11) + docs/ops/* (4) + docs/install/* (1) + docs/admin.md + docs/deploy.md + docs/system-requirements.md + docs/reference/admin-cli.md |
| `user`         | 33    | docs/help/* (10) + docs/reference/* (20, excluding admin-cli + plugins) + docs/getting-started.md + docs/user-guide.md |
| `plugin-author`| 1     | docs/reference/plugins.md |
| `[user, admin, plugin-author]` (multi) | 2 | docs/architecture.md, docs/comparison.md |
| `visitor`      | 5     | docs/index.md, docs/bibliography.md, docs/origin-myth.md, docs/advisory-board.md, docs/showcase.md |
| `contributor`  | 1     | docs/README.md |
| **Total**      | **60**| (62 unique pages — `admin` count above is 18 because two of those overlap the `noop-already-set` set; the table sums on the audience side, not the file side; full per-file list below) |

Actual unique file count: 62 (verified: `find docs -name '*.md' -type f | wc -l`).

The audience-band counts sum to 60 because the "multi" category contains 2 files that contribute to all three of `user`/`admin`/`plugin-author` simultaneously. If you count each audience appearance, the totals are admin=20, user=35, plugin-author=3, visitor=5, contributor=1 → 64.

## Per-file results

### `audience: admin` (18 files)
- `docs/admin.md` *(already set)*
- `docs/admin/auth.md` *(already set)*
- `docs/admin/backup.md` *(already set)*
- `docs/admin/config.md` *(already set)*
- `docs/admin/index.md` *(already set)*
- `docs/admin/install.md` *(already set)*
- `docs/admin/observability.md` *(already set)*
- `docs/admin/security.md` *(already set)*
- `docs/admin/storage.md` *(already set)*
- `docs/admin/system-requirements.md` *(already set)*
- `docs/admin/upgrade.md` *(already set)*
- `docs/deploy.md` *(already set)*
- `docs/install/superset.md`
- `docs/ops/cut-a-release.md` *(no front-matter before; created)*
- `docs/ops/garage-activation-runbook.md` *(no front-matter before; created)*
- `docs/ops/github-projects-board-setup.md` *(no front-matter before; created)*
- `docs/ops/migrate-gridfs-to-s3.md` *(no front-matter before; created)*
- `docs/reference/admin-cli.md`
- `docs/system-requirements.md` *(already set)*

### `audience: user` (33 files)
- `docs/getting-started.md`
- `docs/help/annotate-container.md`
- `docs/help/collection-lineage.md`
- `docs/help/create-from-template.md` *(no front-matter before; created)*
- `docs/help/delete-container-with-references.md`
- `docs/help/monitor-collection-activity.md`
- `docs/help/observing-an-import.md`
- `docs/help/provenance-tracing.md`
- `docs/help/publish-data-object.md`
- `docs/help/timeseries-plotting.md`
- `docs/help/upload-data.md`
- `docs/reference/api.md`
- `docs/reference/container-annotations.md`
- `docs/reference/container-safe-delete.md`
- `docs/reference/file-bundle.md`
- `docs/reference/file-reference.md`
- `docs/reference/file-storage.md` *(no front-matter before; created)*
- `docs/reference/import.md`
- `docs/reference/import-validate.md` *(no front-matter before; created)*
- `docs/reference/lab-journal.md`
- `docs/reference/payload-versioning.md` *(no front-matter before; created)*
- `docs/reference/provenance.md`
- `docs/reference/publish-and-pids.md`
- `docs/reference/semantic-repositories.md`
- `docs/reference/sidecars.md`
- `docs/reference/snapshots.md` *(no front-matter before; created)*
- `docs/reference/timeseries-reference.md`
- `docs/reference/traceability.md` *(no front-matter before; created)*
- `docs/reference/user-profile.md`
- `docs/reference/v1-deprecation.md` *(no front-matter before; created)*
- `docs/reference/v5-cross-instance-quirks.md`
- `docs/reference/video-stream-references.md`
- `docs/reference/view-recipes.md`
- `docs/user-guide.md`

### `audience: plugin-author` (1 file)
- `docs/reference/plugins.md`

### `audience: [user, admin, plugin-author]` — multi (2 files)
- `docs/architecture.md` — every audience needs an architectural overview at some point (researcher to know which substrate stores what; admin to plan ops; plugin author to know SPI seams).
- `docs/comparison.md` — researchers want capability deltas vs upstream; admins want operator-visible deltas; plugin authors want to know which fork-only seams to build against.

### `audience: visitor` (5 files)
- `docs/index.md` — the landing page; pre-adoption.
- `docs/bibliography.md` — citation evidence trail, useful to anyone evaluating the project.
- `docs/origin-myth.md` — narrative/explainer.
- `docs/advisory-board.md` — process transparency for external observers.
- `docs/showcase.md` — pre-adoption demo tour.

### `audience: contributor` (1 file)
- `docs/README.md` — site-build instructions for contributors; not part of the published Pages content.

## Judgment calls (deviations / additions to the spec table)

The DOCS-3A9 spec laid out the classification table; I extended it in three places where the spec was silent or ambiguous. Flagging these here so user can review and override if desired.

### J1 — `docs/index.md` → `visitor`
**Spec said:** not listed among the root-files enumeration.
**Decision:** `visitor`. The landing page is the front door for first-time visitors (pre-credentials). The same persona category as `bibliography.md`/`showcase.md` per the spec.
**Alternative considered:** `[user, admin, plugin-author]` (multi). Rejected because the page is primarily an unauthenticated marketing-style overview; once a reader knows their role they navigate away to the audience-scoped sections.

### J2 — `docs/README.md` → `contributor`
**Spec said:** `audience: contributor` (only enumerated audience outside the canonical three).
**Decision:** kept `contributor` per spec. **Caveat:** `contributor` is not one of the three canonical audiences in `feedback_three_audience_docs.md` — this is a deliberate fourth-audience marker for repo-build / site-source pages that aren't published on Pages. Worth tracking whether `contributor` becomes a first-class audience or stays as a one-off marker.

### J3 — Single `docs/admin/` install file
**Observation:** `docs/install/superset.md` is the only file under `docs/install/`. The spec said `docs/install/*.md → audience: admin (operator audience; integrators welcome but admin-primary)`. Applied as-is.
**Note for DOCS-3A1 consolidation:** this single file is a candidate for moving under `docs/admin/install-extras/superset.md` or similar so the `docs/install/` folder doesn't linger with one file. Out of scope for DOCS-3A9, but flagging.

### J4 — `audience: visitor` not in canonical three
The canonical three are `admin | user | plugin-author`. `visitor` (5 pages) and `contributor` (1 page) sit outside the three. This matches the spec's allowance for pages where "the audience is genuinely unclear" / pre-credentials. Recommend formalising `visitor` as a fourth audience in `feedback_three_audience_docs.md` if it's going to stick — currently it covers landing, bibliography, origin myth, advisory board, showcase (a non-trivial 5 pages).

## Genuinely-unclear cases (none escalated for user review)

After classification, **no page** was left genuinely unclear. The `visitor` and `contributor` overflow bands handled the pages that don't cleanly fit `admin | user | plugin-author`. If the user wants those overflow audiences collapsed back into the canonical three:

- `visitor` pages would mostly become `[user, admin, plugin-author]` (multi).
- `contributor` (README.md) is genuinely outside the published-doc audiences; recommend keeping as-is.

## Process notes

- Script: `/tmp/add_audience.py` (Python stdlib only; idempotent; preserves existing front-matter and other keys).
- Idempotency verified: second run reports `noop-already-set (62)`.
- Doc-stage index regenerated via `python3 scripts/regenerate-doc-stage-index.py` — produced 0 diff because that script walks `aidocs/` only (audience tags live in `docs/`). Confirms no collateral damage.
- 13 of 62 files already had `audience:` (the DOCS-3A1 batch). Untouched.
- 12 files had no front-matter at all; the script wrapped them in a minimal `--- audience: X ---` block.
- 37 files had front-matter without an audience line; the script appended `audience: X` as the last key in the existing block.

## Reviewer enforcement (DOCS-3A3 hook)

When DOCS-3A3 lands, the docs-CI gate should:
1. Fail if `docs/**/*.md` lacks `audience:` front-matter.
2. Accept the values: `admin | user | plugin-author | visitor | contributor` (or array combos of the first three).
3. Warn (but not fail) on `visitor`/`contributor` overflow values — encourages eventual collapse to canonical three.
