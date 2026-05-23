---
title: Plugin documentation gap audit — three-page minimum across all plugins
stage: deployed
last-stage-change: 2026-05-23
audience: contributors, plugin authors, reviewers
---

# Plugin documentation gap audit — 2026-05-23

Backlog ref: **DOCS-3A2** (`aidocs/16-dispatcher-backlog.md`).
Rule audited: `CLAUDE.md §"Always: plugins ship their own documentation"` —
every `plugins/<plugin-id>/` module must ship
`docs/{reference.md, quickstart.md, install.md}`. Pure internal infrastructure
plugins may ship reference-only; feature-flag-gated plugins must at minimum
ship `install.md`.

Scope: 16 `plugins/*/` modules in-tree on `main`.

## §1 Inventory + gap table

Tick = present. ❌ = missing. Default-off means
`shepard.plugins.<id>.enabled=false` in `backend/.../application.properties`
(all bundled plugins ship opt-in).

| plugin-id | reference | quickstart | install | default | notes |
|---|:-:|:-:|:-:|---|---|
| `aas` | ✓ | ✓ | ✓ | off | All three present. **Caveat per §1.5**: hand-rolled IO + design — verify content quality. |
| `ai` | ✓ | ✓ | ✓ | off | Complete. SPI host for LLM providers. |
| `analytics-ts` | ✓ | ✓ | ✓ | off | Complete. |
| `file-s3` | ✓ | ❌ | ❌ | (gated by `shepard.file.storage=s3`) | Reference present (Plugin-style heading, 0 lines verified). Operator-facing; install matters because of sidecar (Garage) wiring. **Bundled-with-image; not a runtime-flag toggle**. |
| `git` | ✓ | ❌ | ❌ | off | Reference present (`weight: 60`). Git host adapter — install steps non-trivial (PAT, signing, CI tokens). |
| `hdf5` | ✓ | ❌ | ❌ | (in-tree, default unknown) | Reference present (Jekyll-styled). User-facing payload kind; quickstart "How do I upload an HDF5 file?" missing. |
| `importer` | ✓ | ✓ | ✓ | off | Complete. |
| `kip` | ✓ | ❌ | ❌ | off | Reference present (Plugin-style). Operator/integrator surface (`/v2/.well-known/kip/...`). Install steps trivial but still required. |
| `minter-datacite` | ✓ | ❌ | ❌ | off | Reference present (Jekyll-styled, full DOI minter doc). Install steps non-trivial (Fabrica creds, repository prefix). |
| `minter-epic` | ✓ | ✓ | ✓ | off | Complete. |
| `minter-local` | ✓ | ❌ | ❌ | off | Reference present. Install nearly trivial (flip flag); quickstart "How do I mint my first local PID?" missing. |
| `spatial` | ✓ | ❌ | ❌ | off | Reference present (PostGIS payload kind). User-facing; install non-trivial (PostGIS sidecar declaration). |
| `unhide` | ✓ | ✓ | ❌ | off | Two-of-three. Install steps live inside `reference.md` (§Install-time defaults) but operator-runbook page is missing as standalone. |
| `v1-compat` | ✓ | ✓ | ✓ | (no toggle — always on; runtime `:LegacyV1Config` controls v1 surface) | Complete. |
| `video` | ❌ | ❌ | ✓ | off | **Install-only.** Reference + quickstart both missing. User-facing payload kind — this is the largest gap. |
| `wiki-writer` | ✓ | ✓ | ✓ | off | Complete. |

**Tally:** 16 plugins, 48 expected pages, **31 present, 17 missing** across **9 plugins**.

### §1.5 AAS plugin special case (verified)

Per `aidocs/agent-findings/aas-edc-reuse-survey-2026-05-23.md`, the AAS plugin
hand-rolled IO records without a §1 library-reuse survey, raising the question
of whether its docs are equally incomplete.

**Verification result: AAS docs are present (3/3) but should be re-reviewed
for accuracy.** Page sizes: `reference.md` 155 lines, `quickstart.md` 77 lines,
`install.md` 109 lines — reasonable for a bundled IDTA-template plugin. The
flag is captured against the reference content as **AAS-DOCS-AUDIT** (separate
follow-up below) rather than counted as a missing-page gap here, because the
three-page minimum is mechanically met. Whether the content accurately reflects
the AAS4J vs. hand-rolled situation is a *different* audit.

## §2 Per-gap analysis

Effort: **S** = ≤2h, **M** = half-day, **L** = full day.
Priority: **HIGH** if plugin is user-facing + live; **MEDIUM** if
operator-facing + opt-in; **LOW** if internal/experimental.

### `file-s3` — missing quickstart, install

- **Why missing**: Was an early plugin and the three-page rule formalised
  later. The plugin is itself bundled and selected via `shepard.file.storage=s3`
  (not a `.enabled` flag), so install instructions live inside
  `docs/reference/file-storage.md` instead.
- **quickstart.md** outline: "Switch the bundled instance from GridFS to S3
  in 5 minutes" — points at Garage compose profile + the migration runbook.
  Effort: **S**. Priority: **HIGH** (Garage is the recommended S3 backend per
  ADR-0024; researchers / new operators land here first).
- **install.md** outline: Per-backend section (AWS S3, R2, B2, Garage, MinIO,
  Ceph RGW, SeaweedFS) with the minimal `application.properties` block,
  bucket-creation steps, and the GridFS → S3 migration link. Effort: **M**.
  Priority: **HIGH**.

### `git` — missing quickstart, install

- **Why missing**: Plugin in active development; landed before the three-page rule.
- **quickstart.md** outline: "Reference a GitHub commit as a DataObject" —
  PAT creation, URL pattern, what the resolved view looks like. Effort: **S**.
  Priority: **MEDIUM** (default-off; researcher pickup is the next wave).
- **install.md** outline: Per-host (GitHub, GitLab, Gitea, Codeberg) PAT scopes,
  rate-limit guidance, optional commit-signing verification, sidecar requirements
  (none currently). Effort: **M**. Priority: **MEDIUM**.

### `hdf5` — missing quickstart, install

- **Why missing**: Plugin scaffolded against the HSDS sidecar plan; install
  steps are non-trivial and were deferred.
- **quickstart.md** outline: "Upload your first HDF5 file + browse its groups
  in the UI" — using the seed showcase as a worked example. Effort: **S**.
  Priority: **HIGH** (user-facing payload kind; demo material).
- **install.md** outline: HSDS sidecar (compose profile), the `shepard.plugins.hdf5.enabled`
  flag, dataset-size limits, the streaming-substrate config knobs. Effort: **M**.
  Priority: **HIGH**.

### `kip` — missing quickstart, install

- **Why missing**: Internal-resolver-shaped; operator install is one flag-flip.
- **quickstart.md** outline: "Resolve a PID's KIP record via curl" — short.
  Effort: **S**. Priority: **LOW** (consumer surface, not a daily-driver task).
- **install.md** outline: Single-flag enable; cross-link to PID registry +
  KIP profile. Effort: **S**. Priority: **MEDIUM**.

### `minter-datacite` — missing quickstart, install

- **Why missing**: Plugin landed with rich reference; install/quickstart
  not split out.
- **quickstart.md** outline: "Mint your first DOI against Fabrica test"
  — credential setup, the smallest PATCH, the verification GET. Effort: **S**.
  Priority: **MEDIUM** (operator landing page for the FAIR-publishing path).
- **install.md** outline: Fabrica credential flow, AES-GCM cipher key,
  prefix allocation, switching from `minter-local` to `minter-datacite`,
  rollback considerations. Effort: **M**. Priority: **MEDIUM**.

### `minter-local` — missing quickstart, install

- **Why missing**: Trivial install; existed before the rule.
- **quickstart.md** outline: "Mint a PID for a Collection" — three-line example.
  Effort: **S**. Priority: **LOW**.
- **install.md** outline: One flag-flip; default minter; PID format. Effort: **S**.
  Priority: **LOW**.

### `spatial` — missing quickstart, install

- **Why missing**: Plugin scaffolded with PostGIS sidecar dependency; install
  documentation deferred.
- **quickstart.md** outline: "Upload a GeoJSON polygon + query overlaps"
  — worked example. Effort: **S**. Priority: **MEDIUM** (user-facing payload
  kind, niche audience).
- **install.md** outline: PostGIS sidecar (compose profile), CRS defaults,
  index sizing, the `shepard.plugins.spatial.enabled` flag. Effort: **M**.
  Priority: **MEDIUM**.

### `unhide` — missing install

- **Why missing**: Install content lives inside `reference.md §Install-time defaults`;
  a standalone install-as-runbook page was never extracted.
- **install.md** outline: Five-defaults table extracted from reference, the
  one-flag enable, the `shepard-admin unhide` CLI flow, harvester-side
  registration checklist. Effort: **S**. Priority: **MEDIUM** (operator
  runbook value; reference content is fine but bracketed by other material).

### `video` — missing reference, quickstart

- **Why missing**: Plugin scaffolded with `install.md` first (good install
  doc, 200 lines); reference + quickstart deferred.
- **reference.md** outline: `VideoStreamReference` + `VideoAnnotation` entity
  reference, REST endpoints (upload, presigned URL, annotate, list), `ffprobe`
  metadata fields, `VideoAnnotation` time-segment shape, retention policy
  pointer. Effort: **M**. Priority: **HIGH** (user-facing payload kind).
- **quickstart.md** outline: "Upload a video + annotate a time segment"
  — worked example using the showcase. Effort: **S**. Priority: **HIGH**.

## §3 Backlog rows to add to aidocs/16

Add the following under `DOCS-3A`. ID schema: `PLUGIN-DOC-<plugin-id>-<page>`.
All status `queued` per the §2 priority. They pair with `DOCS-3A8`
(plugin-docs backfill batch) — DOCS-3A8 is the umbrella ticket; these are
the per-page tracking rows.

| ID | What | Effort | Priority | Status | Notes |
|---|---|---|---|---|---|
| `PLUGIN-DOC-file-s3-quickstart` | Write `plugins/file-s3/docs/quickstart.md` — "Switch bundled instance from GridFS to S3 in 5 minutes" with Garage profile. | S | HIGH | queued | Per §2; pair with `ADR-0024` link. |
| `PLUGIN-DOC-file-s3-install` | Write `plugins/file-s3/docs/install.md` — per-backend install table + migration link. | M | HIGH | queued | Per §2. |
| `PLUGIN-DOC-git-quickstart` | Write `plugins/git/docs/quickstart.md` — reference a GitHub commit as DataObject. | S | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-git-install` | Write `plugins/git/docs/install.md` — per-host PAT scopes + sidecar requirements. | M | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-hdf5-quickstart` | Write `plugins/hdf5/docs/quickstart.md` — first HDF5 upload using showcase. | S | HIGH | queued | Per §2. |
| `PLUGIN-DOC-hdf5-install` | Write `plugins/hdf5/docs/install.md` — HSDS sidecar + streaming substrate config. | M | HIGH | queued | Per §2. |
| `PLUGIN-DOC-kip-quickstart` | Write `plugins/kip/docs/quickstart.md` — resolve a PID's KIP record via curl. | S | LOW | queued | Per §2. |
| `PLUGIN-DOC-kip-install` | Write `plugins/kip/docs/install.md` — flag enable + cross-link to KIP profile. | S | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-minter-datacite-quickstart` | Write `plugins/minter-datacite/docs/quickstart.md` — mint first DOI against Fabrica test. | S | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-minter-datacite-install` | Write `plugins/minter-datacite/docs/install.md` — Fabrica creds + AES-GCM key + prefix allocation. | M | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-minter-local-quickstart` | Write `plugins/minter-local/docs/quickstart.md` — mint a PID for a Collection. | S | LOW | queued | Per §2. |
| `PLUGIN-DOC-minter-local-install` | Write `plugins/minter-local/docs/install.md` — single-flag enable + PID format. | S | LOW | queued | Per §2. |
| `PLUGIN-DOC-spatial-quickstart` | Write `plugins/spatial/docs/quickstart.md` — upload GeoJSON polygon + query overlaps. | S | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-spatial-install` | Write `plugins/spatial/docs/install.md` — PostGIS sidecar + CRS defaults + index sizing. | M | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-unhide-install` | Write `plugins/unhide/docs/install.md` — extract install-time defaults + CLI flow from `reference.md`. | S | MEDIUM | queued | Per §2. |
| `PLUGIN-DOC-video-reference` | Write `plugins/video/docs/reference.md` — `VideoStreamReference` + `VideoAnnotation` entity reference + REST endpoints + ffprobe metadata. | M | HIGH | queued | Per §2. **Largest gap** in the audit. |
| `PLUGIN-DOC-video-quickstart` | Write `plugins/video/docs/quickstart.md` — upload + annotate a video time segment. | S | HIGH | queued | Per §2. |
| `AAS-DOCS-AUDIT` (follow-up, not a gap row) | Re-review AAS docs against `aas-edc-reuse-survey-2026-05-23.md` findings — verify reference content matches the hand-rolled IO reality (does the reference document the AAS4J reuse path that *should* have been taken? Does it mislead operators about which IDTA templates are supported?). | M | MEDIUM | queued | Per §1.5. Not a missing-page gap — a content-accuracy follow-up. |

DOCS-3A8 (umbrella) stays. Each row above is a leaf under DOCS-3A8 and is
mergeable individually — no PR-bundling required.

## §4 Recommended fill order

Optimise for: user-facing-payload-kind first, then operator-facing-sidecar,
then internal/experimental.

1. **`video` reference + quickstart** — HIGH × HIGH. Largest gap; user-facing
   payload kind; install doc already excellent and unsupported by the missing
   two pages. Single batch.
2. **`hdf5` quickstart + install** — HIGH × HIGH. Same shape as video; HDF5 is
   the next-most-cited user-facing payload kind in the design docs.
3. **`file-s3` quickstart + install** — HIGH × HIGH. Garage is the recommended
   S3 backend (ADR-0024); operators landing here without doc is a perma-snag.
4. **`spatial` quickstart + install** — MEDIUM × MEDIUM. PostGIS payload kind;
   smaller audience but full sidecar dependency.
5. **`minter-datacite` quickstart + install** — MEDIUM × MEDIUM. FAIR-publishing
   gateway; operator runbook value is real.
6. **`git` quickstart + install** — MEDIUM × MEDIUM. Per-host PAT setup
   shouldn't live in tribal knowledge.
7. **`unhide` install** — MEDIUM × S. Easy extraction from `reference.md`.
8. **`kip` install + quickstart** — MEDIUM/LOW × S. Trivial pages; bundle them.
9. **`minter-local` install + quickstart** — LOW × LOW. Trivial; do last.

Estimate: **3 person-days** to clear all 17 missing pages (no original code
or research needed; install content largely exists in reference pages or
backend properties). Could be a single dispatched agent batch per the
"agents at discretion for well-defined backlog items" rule.

## §5 Aggregator update — `docs/reference/plugins.md`

The aggregator is a comprehensive operator runbook (715 lines) and already
mentions per-plugin docs paths for **`video`** only ("Install guide:
`plugins/video/docs/install.md`"). Two changes recommended:

1. **Add a per-plugin docs column to the §"Bundled plugins" table** with
   links to `plugins/<id>/docs/{reference,quickstart,install}.md`. Use ❌ as
   a deliberate broken-link marker for missing pages until DOCS-3A8 lands —
   makes the gap visible to operators reading the aggregator. (Alternative:
   omit the broken link and add it as each page lands. Pick one; current
   recommendation: **show the gap** — it's a stronger forcing function for
   maintenance.)
2. **Add a "Per-plugin documentation" boilerplate paragraph** under the
   "Install a plugin" section pointing operators at the three-page minimum
   as the canonical lookup pattern. Until the in-app `/help` route ships
   (DOCS-3A4 / task #46), the aggregator is the discovery surface.

Both changes are S effort, deferred to a separate PR (this audit lands
clean — DOCS-3A2 is "audit + file gaps", not "fix gaps").

## ESCALATIONS

None. No plugin should be archived. No plugin shouldn't exist. The 9 plugins
with gaps are all legitimately in-tree — they just shipped before the rule
was formalised, or shipped one page first with the other two deferred.

The `video` plugin is the clearest doc-debt accumulation (1 of 3 pages
present, the most user-facing of the missing-page plugins). **Tier-1
recommendation**: dispatch a single execution-agent batch to clear all 17
gaps under DOCS-3A8 with the `🤖 BACKFILL — created retroactively...` marker
per `feedback_no_synthetic_provenance.md`. Capture as one
`docs-backfill-plugin-2026-05-23` Shepard Collection per DOCS-3A10.
