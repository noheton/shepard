---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# UI annoyances — running log

Companion to `api-annoyances.md`. Friction encountered while using the
shepard frontend — Playwright runs, manual click-throughs, viewport
testing. Append-only, date-grouped.

Each entry follows:

- **page + action** (e.g. `/collections/<id> → click File ref row`)
- **viewport** (e.g. 4K / 1920×1080 / 1440×900)
- **expected:** one sentence
- **got:** one sentence (link to screenshot if Playwright captured one)
- **workaround:** how the human got past it
- **fix:** the change that would make this not friction

This is the standing input to the UX-auditor agent role and to backlog
rows under `aidocs/16` UX section. See
`feedback_capture_api_ui_annoyances.md` in agent memory for the
discipline + the rule that Playwright validation **must** happen at the
user's actual viewport (4K), not 1440/1920 alone.

---

## How to populate this log

Use Playwright (or manual click-through at 4K when applicable). The
moment something feels like friction — a click that does nothing, a
spinner that hangs, a label that doesn't mean what it appears to mean —
**stop, take a screenshot, write the entry**.

Capture cadence: real-time. Not at session end.

---

## 2026-05-22

*(Empty — the WAAPI / landing-page work this session was code-only; no
live click-through happened on the frontend yet. The next Playwright run
that touches the landing page will populate this section.)*

---

## 2026-05-29

Captured during the UX walk dispatch — see
`aidocs/agent-findings/ux-walk-2026-05-29.md` for the full report and
`e2e/screenshots/ux-walk-2026-05-29/` for evidence. Spec:
`e2e/tests/ux-walk-2026-05-29.spec.ts`.

- **`/collections` @ 4K → table renders 3 rows + ~1500 px whitespace** —
  no skeleton, no empty-state CTA in the void, no per-row dense view.
  At flo's actual 3840×2160 viewport the page utilises ~30% of vertical
  real estate. UX-PATTERN-D (count badges) addressed counts; this
  table-overflow void is a sibling concern. (Step 2, file
  `02-collections-list-4k.png`.)
- **`/collections/{appId}` → "Not found" + raw error toast in user-facing
  text** — toast surfaces the literal backend message "ID ERROR -
  Collection with id 19 is null or deleted". Even if the data lookup
  fails, the message ought to be friendly ("This collection isn't
  available — it may have been deleted or you may not have access")
  rather than exposing the substrate-internal "id 19" detail. (Step 3,
  file `03-microsections-landing-4k.png`.) Also fires from
  `fetchTreeviewItem` (step 4) and `fetching collection` (step 9).
- **Collections list "Access" column shows `—` for two of three rows**
  (Microsections + MFFD) where LUMEN shows `Open`. The dash is
  ambiguous — closed? unknown? not-yet-resolved? A "Closed" / "Shared"
  / "Open" / "Restricted" vocabulary would carry more meaning. (Step 2.)
- **No Trace3D / "Visualize in 3D" affordance on the TS container page**
  even though the container has 86 channels with spatial role
  annotations. The affordance lives downstream (per `aidocs/platform/98`)
  but a container-level CTA "Open 3D view of this container" would
  shorten the path for a researcher. (Step 6.)
- **The `fetchTreeviewItem` error blocks the DataObject page
  indefinitely** — perpetual `v-progress-circular` with no degraded
  shell. A graceful-degradation pattern (render what loaded, log/dismiss
  what didn't, show a "Some data couldn't load — retry" banner) would
  let the user at least see the DO name / attributes / Jupyter notebooks
  panes when only the breadcrumb-resolver fails. (Step 4.)

---

## 2026-06-12

Captured during the shapes-for-displays UX audit — full report at
`aidocs/agent-findings/ux-shapes-displays-and-journeys-2026-06-12.md`,
evidence in `aidocs/agent-findings/screenshots/ux-2026-06-12/`. Walk
scripts: `e2e/scripts/ux-walk-2026-06-12.mjs` + `ux-walk2-2026-06-12.mjs`.
Auth: flo/flo-demo, 3840×2160.

- **TS reference detail page never loads — either id shape.** appId route →
  perpetual spinner with NO error at all; numeric route (what the refs table
  actually pushes) → spinner + "Error while listDataObjects:" toast with an
  EMPTY message body. The chart, CSV export and "Visualize in 3D" are all
  behind this page. (`08*.png`)
- **Trace3D render always fails with "Channel 'x' returned 0 points. Check
  the container ID and time window."** — the real cause is the page fetching
  `/v2/timeseries-containers/{numericId}/channels` against an appId-keyed
  endpoint (404). The error blames the user for a backend id-shape
  mismatch. (`17-shapes-render-from-dialog-4k.png`)
- **"Render view" Tools-menu item appears only where it cannot work.**
  Hidden on all LUMEN DOs (no attached template), shown on `coupon-valid`
  whose template is a DATAOBJECT_RECIPE → raw `422 : {"error":"render not
  yet supported for templateKind=DATAOBJECT_RECIPE…"}` dumped into the
  alert. (`07/18-…png`)
- **"Create template for this DataObject" / "…for this Collection" → hard
  404.** Routes to `/admin/templates`, which doesn't exist (templates are an
  `/admin` fragment). (`13-admin-templates-route-4k.png`)
- **Reference-name links are `href="#"`** — middle-click opens nothing,
  copy-link copies `#`, and the JS push uses numeric ids.
- **Console TypeError `t?.toLocaleDateString is not a function` twice per
  collection/DO page load** — a date prop somewhere receives a string.
- **Guaranteed 404s on every authed page**: `/v2/users/{id}/avatar` +
  `/v2/data-objects/{appId}/publications`. Console + network panel are
  permanently red; real failures drown.
- **TS container page fires `GET /v2/timeseries-containers//channels`**
  (empty appId) → 405, when reached via numeric route.
- **Tools tile mis-describes the render playground** as "Render URDF / mesh /
  spatial-shape previews" — its actual job is projecting VIEW_RECIPEs onto
  DataObjects. (`12-tools-landing-4k.png`)
- **Template autocomplete options render full-paragraph descriptions** — at
  4K each option is a 5-line wall of text; scanning 5 options means reading
  ~600 words. (`19-template-autocomplete-open-4k.png`)
- **"TS container ID (numeric)" text field on the render page** asks the
  user to transcribe a Neo4j-internal id from another page — banned shape
  (UI pulls from references), and the only way to find it is URL-reading.
- **`PlaceholderImplStatus` footers with backlog rows + design-doc paths**
  render for regular users on `/shapes/render`. Power-user/debug info in
  the default layer.

## 1920×1080 pass — 2026-06-13 (UI-1920 audit)

- **Single-column tool pages stretch to ~1800px at 1920** (and full-bleed at
  4K). `/semantic/sparql` and `/tools/form-preview` use a bare `<v-container>`
  whose xl/xxl breakpoint max-width is ~1800px — query text, prose subtitles
  and single appId pickers run edge-to-edge at >120 chars/line. Fixed in-PR
  with a `max-width: 1200px` cap (UI-1920-SPARQL-WIDTH / UI-1920-FORM-PREVIEW-WIDTH).
- **Inconsistent width strategy across tool pages.** `/shapes/render` + `/tools`
  cap at `2400px` (4K-tuned, `LAYOUT-4K-CENTERED-EMPTY-001`); sparql/form-preview
  had no cap at all. No single shared "readable tool page" width token.
- **`/me` profile: large dead left gutter at 1920.** The 2-column flex (Profile
  nav + content card) starts the content card at x≈675 with ~480px empty space
  on the far left; the layout was proportioned for a wider canvas. Filed
  UI-1920-ME-GUTTER.
- **Full-bleed list rows on Home.** "Shared with me" rows put the collection
  name at x≈30 and "View →" at x≈1860 — ~1830px of horizontal eye-travel per
  row at 1920. Filed UI-1920-HOME-WIDEROWS.
- **TS-container chart legend clips the 3rd 5-tuple label** (`rpm_fuel_pu…`) and
  packs 3 very long labels in one row with a 1/4 pager. Width-bound, not
  1920-specific, but acute at 1920. Filed UI-1920-TS-LEGEND.
- **TR-004 DataObject detail hangs on a perpetual spinner at 1920** (main pane
  never renders; CONTENTS sidebar shows only "Publications"). Pre-existing
  plumbing bug (appId/numeric-id seam, matches prior 4K audit C1) — blocked
  auditing the DO detail layout + the new ActionMenuButton at 1920.
