---
stage: feature-defined
last-stage-change: 2026-05-23
---

# MFFD v16 UI screenshots — first live look at the digital-thread tree

**Date**: 2026-05-23 22:46–22:50 CEST  
**Captured by**: Track D Playwright agent (a9e844a05b5b3fb0b chain)  
**Target**: live MFFD-Dropbox collection 661923 at `https://shepard.nuclide.systems/collections/661923`  
**Ingest state at capture**: v16.3 active; tree being populated in real time

## Screenshots (in capture order)

| file | viewport | what it shows |
|---|---|---|
| `01-landing.png` | 4K | Collection landing — sidebar shows the tree starting to populate |
| `02-tree-expanded.png` | 4K | Sidebar tree expanded; multi-level visible (Tapelaying step → children → leaves) |
| `03-dataobject-detail.png` | 4K | Click TR239 — sidebar highlight works; **main detail panel renders BLANK** |
| `04-deep-child.png` | 4K | Click TR243 — same blank-panel symptom |
| `05-do-detail-4k-direct.png` | 4K | Direct URL navigation to a DO — still blank at 4K |
| `06-landing-1920.png` | 1920 | Landing renders correctly at 1920px |
| `07-do-detail-1920.png` | 1920 | DO detail at 1920 — same blank-panel issue |
| `08-tapelaying-expanded.png` | 1920 | Tapelaying step DO with children visible in sidebar |

## Key finding

**BUG #139 confirmed** ("DataObject detail page renders blank at 4K — and possibly other viewports"). Reproduces both at 4K (3840×2160) AND at 1920×1080 — so it's **not viewport-bound**. The sidebar tree renders fine; the click handler highlights the DO correctly; but the main detail panel stays empty.

The agent ran out of investigation turns before identifying the exact failure mode (network 404? React render error? Vuetify state stuck?). The bug existed in the task list as pending; this confirms it reproduces on the v16.x freshly-imported tree, not just on synthetic LUMEN data.

## Positive signals

- Sidebar tree populates **structurally** — parent/child relationships from v16 PRESERVE-HIERARCHY are landing correctly, the navigation reflects the real cube3 source tree shape
- Multi-page pagination via the v16.2 fix is working — Tapelaying step has multiple children at multiple depths
- Collection landing page works fine at all viewports

## Next steps

- BUG #139 needs a real fix — when an operator clicks an imported MFFD DO, they get nothing in the detail panel. The whole point of the import is rendered useless until this is fixed.
- A follow-up Playwright agent should: open browser devtools, capture the network tab + console errors when clicking a DO, identify whether it's an XHR 404 (likely: v5 list-visibility bug surfacing in the per-kind ref loads), a Vue render error, or something else.
- Pair with `aidocs/agent-findings/ux-auditor.md` if it exists (`docs/help/help` / ID navigation tracks).

## Files

8 PNG screenshots in this folder. Total ~1.1 MB. Committed per `feedback_uploads_never_in_repo.md` exception — these are project-artifact evidence (synthetic dest data, not personal uploads).
