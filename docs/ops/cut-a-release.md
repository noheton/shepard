---
audience: admin
redirect_to: /admin/runbooks/10-cut-a-release/
---

# Cutting a release

> **This page has moved.**
>
> The canonical release runbook is now at
> **[docs/admin/runbooks/10-cut-a-release.md](../admin/runbooks/10-cut-a-release.md)**
> (part of the `ADMIN-RUNBOOKS-LIBRARY` shipped 2026-05-26).
>
> The new runbook incorporates everything below and adds: SHA-256 digest
> verification, SBOM attachment steps, `scripts/trace-feature.sh` pre-flight,
> and the full `aidocs/strategy/85 §5` release checklist.

---

## Historical notes (preserved for reference)

> **First post-GH-PM-adoption release** is `v6.0.0-rc.1` — the Milestone bundles the work shipped 2026-05-01 to 2026-05-23. See backfill plan at [`aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`](../../aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md) for the synthetic-Issue audit trail.

Releases are **operator-triggered** in this fork. There is no
release-on-push automation — release timing is a judgement call (post-MFFD
landing, post-substrate-split, etc.). The `release-notes.yml` workflow only
fires once a `v*.*.*` tag exists; the operator creates the tag.

The push of the tag triggers `.github/workflows/release-notes.yml`, which
walks commits between the previous `v*` tag and the new one, groups them by
Conventional Commits scope using `.github/release.yml` categorisation rules,
creates the GitHub Release, and marks it `prerelease: true` automatically when
the tag contains `-rc`, `-beta`, or `-alpha`.

**For the full current procedure, see
[docs/admin/runbooks/10-cut-a-release.md](../admin/runbooks/10-cut-a-release.md).**
