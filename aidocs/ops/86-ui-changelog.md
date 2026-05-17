# 86 — UI Change Log

**Purpose.** Living changelog for user-visible frontend changes in this fork.
Every PR that ships a UI change adds one row.  Follows the same discipline
as `aidocs/34` (admin-facing upgrade path) and `aidocs/44` (feature matrix)
but focused on the researcher/user experience layer.

**Audience.** Contributors, UX reviewers.
**Relates to.** `aidocs/ops/85-ui-overhaul-design.md`, `aidocs/34`, `aidocs/44`.

---

## 1. Convention

| Column | Content |
|---|---|
| Date | Merge date (YYYY-MM-DD) |
| ID | Backlog ID from `aidocs/16` or `aidocs/ops/85` |
| Area | Component / page affected |
| Change | One sentence describing what the user sees differently |
| Screenshot | `screenshots/<id>-<slug>.png` relative path, or `—` if not captured |

Screenshots live in `e2e/screenshots/` and are committed with the PR or added
by the CI Playwright run (see `aidocs/ops/85 §5`).

---

## 2. Changelog

| Date | ID | Area | Change | Screenshot |
|---|---|---|---|---|
| 2026-05-17 | CC1a | Collection sidebar | Collapsible "Containers" section added at the bottom of the sidebar with a "Browse containers" button linking to `/containers` | — |
| 2026-05-17 | CC1b | Container detail pages (File / Timeseries / Structured Data) | "Referenced by" expansion panel added at the bottom of each container detail page; placeholder body pending a list-by-container API endpoint | — |
| 2026-05-17 | CC1c | Upload files dialog | Info banner explains the Collection/Container relationship on every upload; default file-container name pre-filled as `"<Collection name> — file store"` when "Create new file container" is toggled on | — |
| 2026-05-17 | UI3a | DataObject detail page | "Video References" expansion panel added: native `<video>` player per `VideoStreamReference` against `/v2/.../video-stream-references/{appId}/download`; ffprobe metadata chips (duration, resolution, video codec, audio codec, fps, file size, estimated bitrate); per-reference download button | — |
| 2026-05-17 | UI2a | Admin page (`/admin`) | Templates pane added: `v-data-table` listing all templates with kind/tag/retired filters; "New template" button + create/edit dialog (POST / PATCH copy-on-write versioning); retire action with confirmation; kind chip + status chip per row | — |
| 2026-05-17 | QW4 | DataObject detail — Git References pane | `v-alert` (info, tonal) shown when user has no git credentials; includes "Go to profile" link to `/user#git-credentials` | — |
| 2026-05-17 | QW5 | DataObject / Collection detail — Publish button | `v-tooltip` on hover explains persistent identifier (DOI/PID); `prepend-icon` changed to `mdi-information-outline` | — |
| 2026-05-17 | QW1 | Header bar | "Advanced Search" nav button replaced with a 300px type-ahead `v-autocomplete`; debounced 250ms, queries collections by name, navigates to `/collections/{id}` on selection; "Advanced search →" footer link preserved in dropdown | — |
| 2026-05-17 | QW2 | Collection sidebar | Text filter at top of sidebar; client-side filtering of data objects by name | — |
| 2026-05-17 | QW3 | User profile (`/user`) | JupyterHub section added to ProfilePane: `v-text-field` bound to `useJupyterPreference`, Save button; persists `editor.preferredJupyter` preference via `PATCH /v2/users/me/preferences` | — |
| 2026-05-17 | QW6 | Admin page (`/admin`) | "Instance Health" pane added: heap bar, uptime chip, HTTP request totals + mean latency chips, permissions cache hit ratio; reads `GET /v2/admin/metrics-summary`; silently hidden for non-admins | — |
| 2026-05-17 | UI1a | Collection detail page | "Snapshots" expansion panel added (owner/manager only): create snapshot (name + description), list with date/by/entryCount, delete with confirmation, diff two snapshots with colour-coded added/removed/changed/unchanged sections | — |
| 2026-05-17 | UI8 | Collection detail page | "Download as RO-Crate" button (`mdi-package-down`, tonal, secondary) triggers authenticated blob download via `GET /shepard/api/collections/{id}/export` | — |
| 2026-05-17 | J1c | DataObject detail page | Added "Jupyter Notebooks" expansion panel — lists all `.ipynb` file references with download link and "Open in JupyterHub" button; first-time URL setup via inline cog | — |
| 2026-05-17 | U1c2 | Collection sidebar header | Owner / Editor / Reader role chip + amber Admin chip now displayed below the collection name for the current user | — |
| 2026-05-17 | J1c / U1d | User preferences | `editor.preferredJupyter` preference key introduced; stored via `PATCH /v2/users/me/preferences`; accessible through the Notebooks panel cog | — |
| 2026-05-07 | KIP1e | DataObject detail page | "Publish" button + modal added; post-publish snackbar shows PID + resolver URL with copy-to-clipboard | — |
| 2026-05-07 | PROV1d | Collection detail page | Activity sparkline card shows per-collection activity heatmap | — |
| 2026-05-07 | G1a-ui | DataObject detail page | Git References pane: create/edit/delete git references; credential-aware repo URL autocomplete | — |
| 2026-05-07 | G1-cred-ui | User profile (`/user`) | Git Credentials pane: manage per-user repo credentials (host + username + encrypted PAT) | — |
| 2026-05-07 | A3b-ui | Admin page (`/admin`) | Feature toggles pane with per-toggle enable/disable switches (instance-admin only) | — |
| 2026-05-07 | U1-profile-ui | User profile (`/user`) | ProfilePane: ORCID iD edit + displayName override | — |

---

## 3. Pending UI work (from aidocs/ops/85)

Items in the design doc (`aidocs/ops/85`) that are not yet shipped:

| ID | Summary | Gate |
|---|---|---|
| UI4a | PayloadVersion history panel | PV1a in-flight |
| UI5a | Drag-and-drop tree reparenting | design in aidocs/58 §2 |
| UI7 | Graph view | design needed |
| UI8 | Inline attach-to-data-object from container | design needed |
| UI10 | @-mention in lab journal | design in aidocs/58 §4 |
| UI11 | Unified publish/Unhide status panel | UH1 + KIP1 shipped |
