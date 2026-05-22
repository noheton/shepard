---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# GH Pages docs refresh — pre-push survey + fix log

**Snapshot.** 2026-05-22.
**Scope.** `/opt/shepard/docs/**`.
**Trigger.** Drift accumulated since c03fbd96 (Garage runbook). Recent
shipped features (v15 MFFD import, PM1f sidecars SPI, TPL2a view
recipes, FS1b/c/d/e/f/g Garage-live, V1COMPAT.0, AI1a / WW1, MCP-2)
have not landed on the Pages site.

## Phase 1 — survey

### Stale-but-low-cost fixes

| File | Issue | Fix |
|---|---|---|
| `_config.yml` | `snapshot_date: 2026-05-07` — stale | Bump to `2026-05-22` |
| `index.md` "At a glance" | Says "4 Persistence stores"; Garage adds a fifth substrate | Update count + add S3 row |
| `index.md` "What's new" | Stops short — no mention of S3/Garage, v15 import, sidecars SPI, view recipes, AI/wiki-writer, MCP-2, V1COMPAT.0 | Add fresh bullets + cross-links to new reference pages |
| `architecture.md` Persistence table | Missing S3-compatible row (Garage) | Add row + ADR-0024 citation |
| `architecture.md` block diagram | No Garage / no plugin SPI hint | Add an S3-storage node, optional |
| `deploy.md` | Doesn't mention Garage or the new GHCR override pattern | Add a short Garage-activation pointer |
| `getting-started.md` | Doesn't mention `/help` or Garage | Optional touch — keep narrow |

### Missing reference pages (new, must create)

1. **`reference/import.md`** — the v15 MFFD import script (`examples/mffd-showcase/scripts/mffd-import-v15.py`). Covers: bootstrap mode, source mode, local mode; env vars; the agentic 4-phase workflow (bootstrap → fetch → warmup → full import); presigned-URL flow; provenance writeback; ETA attribute; log-as-proof re-upload; JWT pause / SIGCONT resume; exit codes; cross-link to the Garage runbook + GridFS→S3 migration runbook.
2. **`reference/sidecars.md`** — PM1f `PluginManifest.sidecars()` SPI extension. Covers: `SidecarSpec` record, `PortSpec`, `VolumeSpec`, `HealthcheckSpec`; templating placeholders (`{{generate:hex:N}}`, `{{sidecar.host}}`, `{{from:postInit.N.field}}`); `SidecarsAssembler` deterministic render; worked example for `file-s3` → Garage; pointer to `aidocs/47 §2.6`.
3. **`reference/view-recipes.md`** — TPL2a `TemplateKind.VIEW_RECIPE` + `PROCESS_RECIPE`. Covers: the meta-shape at `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`; the contract (`ChannelBindingShape` with `role`, `channelSelector`, `qudt:unit`, `required`); renderer hint enum; pointer to the planned `POST /v2/shapes/render` endpoint (#157) and Trace3D (#142).

### Existing pages — targeted refresh

- `reference/file-storage.md` — already comprehensive but predates the live Garage activation. Cross-link to `ops/garage-activation-runbook.md` and `ops/migrate-gridfs-to-s3.md` from the top. FS1e1/e2/f endpoints already present — verified.
- `reference/plugins.md` — needs a one-paragraph mention of PM1f `sidecars()` + link to the new sidecars page. Already covers PM1a–PM1e well.

### Dead-link / wrong-endpoint sweep

`grep -rn "POST /v2/file-containers\|POST /v2/timeseries-containers"` finds no incorrect create-via-`/v2/` references in the docs site. The `/v2/file-containers/{id}/...` paths that ARE used (upload-url, files, etc.) are all correct (they're operations on an existing container, not creates).

`MinIO` mentions are all properly contextualized — flagged as community-edition-archived in `ops/migrate-gridfs-to-s3.md` (matching project memory `project_storage_s3_garage.md`). No edit needed.

### Out of scope / deferred

- `docs/README.md` — internal Jekyll dev-site docs, not user-facing.
  Skipping per advisor.
- `docs/help/*.md` — task pages — not touched in this refresh. The
  task said "task pages when the feature has a casual expression" —
  v15 import, sidecars SPI, view recipes are operator/contributor
  features, not casual-user task surfaces.
- `docs/admin.md` and `docs/user-guide.md` — not surveyed in detail;
  no critical drift identified by the targeted greps. Deferred.
- `docs/comparison.md`, `docs/system-requirements.md` — out of scope
  for this refresh.

## Phase 2 — fix log

Filled in as edits land.

### Commit 1 — new reference pages (import, sidecars, view-recipes)

- Created `docs/reference/import.md`. Covers v15 MFFD-import script
  including bootstrap/source/local modes, env vars, the 4-phase
  agentic workflow, presigned-URL flow, JWT pause/resume, exit
  codes, cross-link to Garage runbook and GridFS→S3 migration.
- Created `docs/reference/sidecars.md`. Documents PM1f
  `PluginManifest.sidecars()` with `SidecarSpec`, templating
  placeholders, the Garage worked example, and the cross-link to
  `aidocs/47 §2.6`.
- Created `docs/reference/view-recipes.md`. Documents TPL2a
  `VIEW_RECIPE` + `PROCESS_RECIPE` template kinds with the
  meta-shape at `view-recipe-meta.shacl.ttl`, the channel-binding
  contract, renderer enum, and the path to Trace3D (#142) +
  `POST /v2/shapes/render` (#157).

### Commit 2 — landing + architecture refresh

- Bumped `_config.yml snapshot_date` to 2026-05-22.
- Updated `index.md` "At a glance" — 5 persistence stores (S3 added);
  fresh "What's new on this fork" bullets covering S3/Garage live,
  v15 MFFD-import, sidecars SPI, view recipes / Trace3D path,
  AI plugin + wiki-writer, MCP-2 native server, V1COMPAT.0 control
  plane.
- Updated `architecture.md` — persistence table now lists the
  S3-compatible adapter (Garage default per ADR-0024) as the fifth
  store; cross-link to FS1 + sidecars + plugin SPI.

### Commit 3 — file-storage cross-links + plugins page sidecars mention

- `reference/file-storage.md` — added a top-of-page "See also"
  reference to the two new ops runbooks so an operator reaching
  this page sees the live runbooks first.
- `reference/plugins.md` — added a one-paragraph PM1f section under
  "What plugins can do" with a link to the new sidecars page.

## Summary

- **docs/ files modified:** 5 (`_config.yml`, `index.md`,
  `architecture.md`, `reference/file-storage.md`,
  `reference/plugins.md`).
- **New pages:** 3 (`reference/import.md`, `reference/sidecars.md`,
  `reference/view-recipes.md`).
- **Dead links fixed:** 0 (none found by the sweep — the docs were
  drift-by-omission, not drift-by-wrong-link). MinIO references
  remain but are properly contextualised as a community-archived
  alternative.
- **Commits:** 3.
  1. `docs: add reference pages for v15 import, sidecars SPI, view recipes`
  2. `docs: refresh landing + architecture for Garage + plugin SPI`
  3. `docs: refresh GH Pages for pre-push (Garage active + v15 + sidecars + view recipes)`
     (cross-link sweep + bundled-plugin table extension)
- **Ready for push.**
