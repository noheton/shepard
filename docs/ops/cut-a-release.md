---
audience: admin
---

# Cutting a release

> **First post-GH-PM-adoption release** is `v6.0.0-rc.1` — the Milestone bundles the work shipped 2026-05-01 to 2026-05-23. See backfill plan at [`aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`](../../aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md) for the synthetic-Issue audit trail.

Releases are **operator-triggered** in this fork. There is no
release-on-push automation — release timing is a judgement call (post-MFFD
landing, post-substrate-split, etc.). The `release-notes.yml` workflow only
fires once a `v*.*.*` tag exists; the operator creates the tag.

## Prerequisites

- `gh` CLI authenticated against `github.com/noheton/shepard`.
- `main` is green: CI, CodeQL, security, build-images, pages.
- `aidocs/34-upstream-upgrade-path.md` ledger reflects everything since
  the last release (this is the cut-the-release readiness check).
- `aidocs/44-fork-vs-upstream-feature-matrix.md` is consistent with what
  actually shipped (no `🚧 in-flight` rows for completed features).
- The release version follows the upstream-track + fork-suffix convention:
  e.g. `v6.0.0-rc.1` for the post-MFFD candidate; `v6.0.0` once the rc
  has soaked.
- `CITATION.cff` `version` field bumped to match (gated on agent #169 landing).

## Steps

```bash
# 1. From a clean `main`, create + push the annotated tag.
git switch main
git pull --ff-only
git tag -a v6.0.0-rc.1 -m "v6.0.0-rc.1 — post-MFFD candidate"
git push origin v6.0.0-rc.1
```

The push of the tag triggers `.github/workflows/release-notes.yml`, which:

1. Walks commits between the previous `v*` tag and the new one.
2. Groups them by Conventional Commits scope using `.github/release.yml`
   categorisation rules.
3. Creates the GitHub Release with the rendered notes body attached.
4. Marks the release as `prerelease: true` automatically when the tag
   contains `-rc`, `-beta`, or `-alpha`.

```bash
# 2. (optional) Edit the auto-generated release notes after the workflow
#    finishes — the rendered body is a starting point, not final copy.
gh release edit v6.0.0-rc.1 --notes-file docs/ops/release-notes-v6.0.0-rc.1.md
```

## Manual fallback

If the workflow fails (rare — most often a Conventional-Commits scope
parsing edge case), the operator can re-render locally:

```bash
gh release create v6.0.0-rc.1 \
  --title "v6.0.0-rc.1 — post-MFFD candidate" \
  --notes-from-tag \
  --target main \
  --prerelease
```

## After the release

- Update `aidocs/34-upstream-upgrade-path.md`: add a ledger row stating
  what an operator upgrading from `<previous-tag>` to `<this-tag>` must
  do (config changes, migrations, breaking flips).
- Update `aidocs/44-fork-vs-upstream-feature-matrix.md`: flip relevant
  rows from `🚧 in-flight` to `✓ shipped` with the new release tag.
- If the release introduces breaking changes, set the GitHub Release
  as the latest release only after the `-rc.*` cycle ends with `vX.Y.Z`
  (no suffix).

## Why not release-on-push?

Solo-dev + AI workflow makes "every merge to main = release" too noisy.
Most merges are mid-arc work (e.g. an `aidocs/16` slice that's part of a
larger feature). Releases mark **operator-relevant** moments: a state
worth pinning an image to, deploying to nuclide / DLR boxes, citing in a
paper. The operator is the right authority to pick those moments.
