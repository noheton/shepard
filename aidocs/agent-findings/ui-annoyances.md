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
