---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 73 — Personal Landing Page

**Status.** Design — initial draft 2026-05-19.
**Audience.** Frontend contributors, product.
**Relates to.** `aidocs/ops/85-ui-overhaul-design.md` (UX overhaul),
`aidocs/ops/87-collection-container-duality.md` (model),
`aidocs/42-vision.md` (researcher-facing vision),
`aidocs/16-dispatcher-backlog.md` (backlog item #43).

---

## 1. Goal

Replace the bare collections list (`/collections`) as the default
post-login destination for researchers. The new landing page is a
**personalised digest** — a single screen that answers "what happened
since I was last here?" without requiring any navigation.

The current page shows a `v-data-table` of collections the user can
access, sorted by creation date. It answers "what exists?" It does not
answer "what changed?", "who contributed?", or "is there new data in
my watched channels?"

---

## 2. User Stories

| ID | Story |
|----|-------|
| LP1 | As a researcher I want to see at a glance which of my collections changed recently, who contributed, and whether new data arrived. |
| LP2 | As a new user I want a greeting that confirms I'm logged in and tells me how to get started. |
| LP3 | As a collaborator I want to see collections shared with me separately from my own, so I can quickly find work I'm contributing to. |
| LP4 | As a power user I want quick-action buttons ("New collection", "Browse all") without scrolling. |
| LP5 | As a researcher with watched timeseries I want a sparkline thumbnail on the collection card so I can spot anomalies before drilling in. |

---

## 3. Data Sources

All data is available from existing endpoints. No new backend endpoint
is required for v1.

| UI element | Endpoint | Notes |
|---|---|---|
| Collection list | `POST /shepard/api/search/collections` | Filter by `createdBy` or membership. Sort by `updatedAt` desc. |
| DataObject count | `GET /shepard/api/collections/{id}/dataObjects` | Count returned in response; no additional call needed. |
| Contributor avatars | `createdBy` / `updatedBy` fields on Collection and DataObject | Avatar URL derived from `/v2/users/{appId}/avatar`. |
| Activity sparkline | `GET /v2/provenance/stats?scope=collection&id={appId}&since=7d` | Returns bucketed activity counts already used in `ActivitySparklineCard.vue`. |
| Timeseries sparkline | `TimeseriesContainerApi.getTimeseries()` | Reuse `useFetchChannelPreview.ts` + `ChannelPreviewChart.vue`. |
| "Shared with me" | `POST /shepard/api/search/collections` | Same endpoint; filter for collections where user has `READ` or `WRITE` but is not `createdBy`. |

The provenance stats endpoint (PROV1c) already powers the per-collection
activity sparkline panel on the collection detail page. The landing page
reuses the same composable (`useFetchProvenanceStats`) at a smaller
card size.

---

## 4. Proposed Layout

```
┌────────────────────────────────────────────────────────────────────┐
│  [Avatar] Good morning, Alice.   ●  12 activities in the last 7d   │
│                                  [ New collection ]  [ Browse all ] │
└────────────────────────────────────────────────────────────────────┘

My collections  (sorted by last change)
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ LUMEN Run 7  │ │  HDF Samples │ │ Rocket Test  │ │  + 4 more …  │
│              │ │              │ │              │ │              │
│  [sparkline] │ │  [sparkline] │ │  [sparkline] │ │              │
│              │ │              │ │              │ │              │
│ 23 objects   │ │  8 objects   │ │ 41 objects   │ │              │
│ [av][av][av] │ │   [av][av]   │ │  [av]        │ │              │
│ 3h ago       │ │ 2d ago       │ │ 5d ago       │ │              │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

Shared with me
┌──────────────────────────────────────────────────────────────────┐
│  [icon] Plasma Dataset  │ Bob  │  14 objects  │  Updated 1d ago  │
│  [icon] Turbine Log     │ Carol │  7 objects   │  Updated 3d ago  │
└──────────────────────────────────────────────────────────────────┘
```

### 4.1 Greeting card

- User avatar (from `/v2/users/me/avatar`, existing endpoint).
- Time-of-day greeting + effective display name (`effectiveDisplayName`,
  already in `UserIO`).
- 7-day activity count from `useFetchProvenanceStats` (one API call).
- Quick-action buttons: "New collection" (opens existing creation
  dialog) and "Browse all" (navigates to `/collections`).

### 4.2 "My collections" digest

A horizontally scrollable row of cards (max 6 visible, "+N more" card
at the end). Each card:

- **Collection name** (truncated at 2 lines).
- **Sparkline** — activity bars from PROV1c stats, 7 buckets / 7 days.
  If the collection has a watched timeseries channel, substitute the
  timeseries sparkline from `ChannelPreviewChart.vue` (the watch link
  is already stored by WATCH1).
- **Object count** — from the search result's DataObject list length or
  a count field if the API returns one.
- **Contributor avatars** — up to 3 `<v-avatar>` chips derived from
  `createdBy` and `updatedBy` across the collection's DataObjects
  (collected from the search result, no extra call).
- **Last change** — relative timestamp (`3h ago`, `2d ago`).

Clicking a card navigates to the collection detail page.

### 4.3 "Shared with me" section

A flat `v-list` (not cards) showing collections where the user has
read or write access but is not the owner. One row per collection:
name, owner avatar, object count, last updated. Sorted by last
updated.

This section is hidden if empty.

### 4.4 Quick actions

"New collection" and "Browse all" in the greeting card header are
sufficient for v1. A future iteration may add "Recent searches" or
pinned collections.

---

## 5. Implementation Notes

### 5.1 Routing

Add a new page at `frontend/pages/index.vue` (or redirect `/` to a
new `/home` route). The existing `/collections` page stays intact
as the "Browse all" destination.

`nuxt.config.ts` `routeRules` does not need changes; the landing page
has the same no-store cache posture as all HTML routes.

### 5.2 Component structure

```
pages/index.vue
  ├── GreetingCard.vue          (greeting + activity count + quick actions)
  ├── MyCollectionsRow.vue      (horizontal scroll, 6 cards + overflow)
  │     └── CollectionDigestCard.vue (per-collection card)
  │           └── ChannelPreviewChart.vue (reused from timeseries pane)
  └── SharedWithMeList.vue      (v-list of shared collections)
```

`CollectionDigestCard.vue` fetches its own provenance stats inline
(one `GET /v2/provenance/stats` call per card), triggering on mount.
Six cards = 6 parallel requests. This is acceptable for v1; a batched
stats endpoint can be added later if latency is a concern.

### 5.3 Reusing ChannelPreviewChart.vue

`ChannelPreviewChart.vue` (at
`frontend/components/container/timeseries/ChannelPreviewChart.vue`)
already exists and accepts `containerId` + `channel` props. It uses
`useFetchChannelPreview.ts` which calls `TimeseriesContainerApi.getTimeseries()`
with a 1-second bucket aggregation. No changes to the chart or composable
are needed — just pass the container ID and first watched channel from
the WATCH1 link.

If a collection has no watched timeseries, render the PROV1 activity
bar sparkline instead (vanilla SVG, already implemented in
`ActivitySparklineCard.vue`).

### 5.4 "Shared with me" filtering

The existing `POST /shepard/api/search/collections` endpoint returns
all collections the caller can access. Split the results client-side:

- `mine`: `collection.createdBy.appId === currentUser.appId`
- `sharedWithMe`: all others

No backend change needed.

### 5.5 Loading states

Each card should use a `v-skeleton-loader` variant while fetching its
provenance stats. The greeting card can render synchronously (user info
is available from `useAuth().data`).

---

## 6. Open Questions

| # | Question | Current thinking |
|---|----------|-----------------|
| OQ1 | Should the landing page be opt-out (default for all users) or opt-in via preferences? | Default for all; power users who prefer the table view can navigate to `/collections`. A preference toggle can be added later if there's demand. |
| OQ2 | Mobile layout — horizontal scroll for cards works on desktop; on mobile it becomes a vertical stack. | Stack cards vertically on mobile (1 column). The `v-container` grid already handles this with `cols="12" md="4"`. |
| OQ3 | How many "My collections" to show before the "+N more" card? | 4–6. Show 4 on narrow viewports, 6 on wide. |
| OQ4 | What if the user has no collections? | Show the greeting card and an empty-state CTA ("Create your first collection"). |
| OQ5 | Should the timeseries sparkline auto-refresh in live mode? | No for v1 — it's a digest, not a monitoring screen. Static snapshot on mount is fine. |

---

## 7. Implementation Sequence

1. Create `GreetingCard.vue` — avatar, name, activity count, quick actions.
2. Create `CollectionDigestCard.vue` — name, object count, contributor avatars, relative timestamp, activity sparkline.
3. Create `MyCollectionsRow.vue` — horizontal scroll wrapper with overflow card.
4. Create `SharedWithMeList.vue` — flat list, hidden when empty.
5. Assemble `pages/index.vue` (or `pages/home.vue` + redirect from `/`).
6. Wire `ChannelPreviewChart.vue` into `CollectionDigestCard.vue` when a watch link exists.
7. Add empty-state path (no collections).

This can ship as a frontend-only PR with no backend or migration
changes. The existing backend endpoints supply everything.
