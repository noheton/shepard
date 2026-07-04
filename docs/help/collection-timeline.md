# See a campaign-wide process-chain timeline

When you have hundreds — or thousands — of DataObjects under one
Collection, scrolling through them one by one is slow and the Lineage
graph collapses past a few hundred nodes. The **Timeline** view shows
your whole campaign at a glance: when each process step happened, how
many tracks ran per day, and where quality issues clustered.

## When to use it

- "When did NCR-Open spikes happen during the AFP layup phase?"
- "How many bridge-welding tracks did we run per week last year?"
- "Did the re-test loop after the August anomaly recover by September?"
- Quick scan of your campaign cadence before a milestone review.

## How to open it

1. Open the Collection landing page.
2. Scroll down to the expansion panels (below the "Data Objects"
   section).
3. Click **Timeline**. (The first open fetches the data — a few
   seconds for an MFFD-scale Collection.)

## What you see

- One **horizontal swimlane per process-type**. Lanes come from the
  `urn:shepard:mffd:process-type` annotation on your DataObjects.
- Stacked colour-coded bars:
  - **Green** = DataObjects with normal lifecycle status.
  - **Amber** = NCR_OPEN or CONCESSION_PENDING.
  - **Red** = REJECTED.
- An x-axis of day buckets (or week / month buckets when you toggle
  the bin size). The earliest day in your campaign is on the left.

DataObjects without a process-type annotation collect into a single
**Unclassified** lane so the chart still has something to show — handy
for early-stage Collections (LUMEN, home-showcase) before you've
annotated everything.

## What you can do

- **Hover** a bar → tooltip "AFP Layup, 2024-04-15 — 34 DOs (1 NCR)".
- **Click** a bar → jumps to the Data Objects list for that day and
  process-type (the list filter pass-through is rolling out as a
  follow-up; for now you land on the unfiltered list).
- **Day / Week / Month** toggle → switches the bin window. If your
  campaign spans more than two years, the server may coarsen further
  (you'll see a note like "server coarsened to 7-day bins").
- **Reload** → re-fetches the data (e.g. after you've added new
  DataObjects).

## Notes

- The Timeline reads the DataObject's `createdAt` to place it in a
  bin. Backfilled data uses the original timestamp; new data uses the
  upload time.
- The view caches for 5 minutes — a recent re-open uses the cached
  response unless you click **Reload**.
- If you don't see lanes for the process steps you care about, your
  DataObjects probably don't carry the `urn:shepard:mffd:process-type`
  annotation yet. Add it via the AnnotationDialog or the
  `shepard-admin annotations add` CLI.

## See also

- [Collection lineage](collection-lineage.md) — graph view that
  complements the Timeline (good for small Collections / detailed
  edge-by-edge inspection).
- [Cross-track view](cross-track-view.md) — small-multiples chart
  that pairs nicely with the Timeline once you've picked a lane.
- `docs/reference/collections.md §Timeline` — the API and wire shape
  if you want to build your own view on top.
