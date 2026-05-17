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
| 2026-05-17 | QW2 | Collection sidebar | Text filter at top of sidebar; client-side filtering of data objects by name | — |
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
| QW1 | Global search bar in HeaderBar | unblocked |
| QW3 | JupyterHub URL in user profile pane | unblocked |
| QW4 | Git credentials shortcut in GitReferencesPane | unblocked |
| QW5 | Publish deep-link tooltip | unblocked |
| QW6 | Admin metrics card | unblocked |
| UI1a | Snapshots UI (create / list / delete / diff) | V2b–V2e shipped |
| UI2a | Templates browser + instantiation | T1a–T1f shipped |
| UI3a | Video reference inline viewer | VID1a shipped |
| UI4a | PayloadVersion history panel | PV1a in-flight |
| UI5a | Drag-and-drop tree reparenting | design in aidocs/58 §2 |
| UI6a | RO-Crate export download button | G1c shipped |
| UI7 | Graph view | design needed |
| UI8 | Inline attach-to-data-object from container | design needed |
| UI9 | Snapshot diff viewer | V2e shipped |
| UI10 | @-mention in lab journal | design in aidocs/58 §4 |
| UI11 | Unified publish/Unhide status panel | UH1 + KIP1 shipped |
