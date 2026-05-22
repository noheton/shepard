---
stage: deployed
last-stage-change: 2026-05-23
---

# 82 — Basic vs Advanced Mode Feature Matrix

**Status:** live reference (update whenever a gate is added or removed)
**Audience:** frontend contributors, UX reviewers

---

## Governing rule

Advanced mode is a **strict superset** of basic mode.
`v-if="!advancedMode"` (hiding things from advanced users) is always wrong.
`v-if="advancedMode"` (showing extra things in advanced) is the only valid gate direction.

The toggle lives in `frontend/composables/context/useAdvancedMode.ts`.

---

## What the toggle does NOT gate

These are research-relevant facts or data panels. Their visibility is determined
by whether data exists and whether the user has permission — not by mode.

| Feature | Rationale |
|---|---|
| Description field | Always-visible |
| Semantic annotations | Always-visible |
| Lab journal | Always-visible |
| Attributes / key-value pairs | Always-visible |
| Data references (files, timeseries, video, structured, git) | Always-visible |
| Snapshots panel | Visible when snapshots exist (data-driven) |
| Publishing panel | Visible when publish is configured (data-driven) |
| Provenance graph | Always-visible (data about the object) |
| Collection lineage | Always-visible |
| Anomaly / interval annotations | Always-visible |
| Channel Overview chart on timeseries reference page | Always-visible |
| Per-channel annotation chips in timeseries reference table | Always-visible |
| Video annotation timeline + list | Always-visible |

---

## What the toggle DOES gate (current, as of 2026-05-20)

| Feature | File | Gate | Notes |
|---|---|---|---|
| Internal Neo4j numeric IDs | `TitleAndMetadataDisplay.vue` | `v-if="advancedMode"` | Dev/debug; researchers don't need them |

---

## Planned advanced-only additions (not yet shipped)

These are designed but not yet implemented. When shipped, add them to the table above.

| Feature | Planned gate | Notes |
|---|---|---|
| Containers tab on DataObject page | `v-if="advancedMode"` | Basic mode hides containers; shows inline content instead (task #51) |
| "Unsafe delete" on containers | `v-if="advancedMode"` | Only admins/power users need hard-delete |
| Raw container settings (appId, stats, advanced config) | `v-if="advancedMode"` | Container-level metadata; not researcher-facing |
| Create-container button | `v-if="advancedMode"` | Basic mode uses "Add data" shortcut instead |

---

## Page-by-page breakdown

### Home (`/`)

| Element | Basic | Advanced |
|---|---|---|
| Personal digest (recent collections) | ✓ | ✓ |
| New collection button | ✓ | ✓ |
| Closed-collection filter toggle | ✓ | ✓ |
| Pending-cleanup badge | ✓ | ✓ |

### Collections list (`/collections`)

| Element | Basic | Advanced |
|---|---|---|
| Collection cards | ✓ | ✓ |
| Search field | ✓ | ✓ |
| Create new collection | ✓ | ✓ |

### Collection page (`/collections/[id]`)

| Element | Basic | Advanced |
|---|---|---|
| Description | ✓ | ✓ |
| Semantic annotations | ✓ | ✓ |
| DataObjects list | ✓ | ✓ |
| Snapshots panel | ✓ | ✓ |
| Publishing panel | ✓ | ✓ |
| Collection lineage graph | ✓ | ✓ |
| Internal numeric ID in title bar | — | ✓ (advancedMode gate) |

### DataObject page (`/collections/[id]/dataobjects/[id]`)

| Element | Basic | Advanced |
|---|---|---|
| Description | ✓ | ✓ |
| Semantic annotations | ✓ | ✓ |
| Attributes | ✓ | ✓ |
| Lab journal | ✓ | ✓ |
| Data references (files, timeseries, video, structured, git) | ✓ | ✓ |
| Provenance graph | ✓ | ✓ |
| Containers tab | — | Planned ✓ (task #51) |
| Internal numeric ID | — | ✓ (advancedMode gate) |

### Timeseries reference page (`/…/timeseriesreferences/[id]`)

| Element | Basic | Advanced |
|---|---|---|
| Channel Overview chart (locked to reference bounds) | ✓ | ✓ |
| Channel table with metrics (count/min/max/mean) | ✓ | ✓ |
| Per-channel annotation chips | ✓ | ✓ |
| Channel comparison chart (checkbox-gated) | ✓ | ✓ |
| Anomalies & intervals section | ✓ | ✓ |
| Run anomaly detection button | ✓ (if editor) | ✓ (if editor) |

### Timeseries container page (`/containers/timeseries/[id]`)

| Element | Basic | Advanced |
|---|---|---|
| Channel list + charts | ✓ | ✓ |
| Live mode toggle | ✓ | ✓ |
| Channel anomaly annotations | ✓ | ✓ |
| Container-level settings / stats | Planned — | Planned ✓ |

---

## How to update this doc

When adding any `v-if="advancedMode"` gate to the frontend:

1. Add a row to the "What the toggle DOES gate" table above.
2. Update the page-by-page breakdown for the affected page.
3. If moving a feature from "planned" to "shipped", update the status and date.

When removing a gate (reverting to always-visible), delete the row from the
gate table and mark the page-by-page cell as `✓` for both modes.

---

## Related

- `aidocs/ux/78-containerless-basic-mode.md` — design intent + future containerless UX
- `frontend/composables/context/useAdvancedMode.ts` — the toggle implementation
- Memory: `feedback_basic_advanced_superset.md` — superset rule
