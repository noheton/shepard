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
