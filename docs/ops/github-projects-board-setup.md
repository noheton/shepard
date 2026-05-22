# Setting up the GitHub Projects v2 board

GitHub Projects v2 is the Kanban surface for **issues actively being
worked on** — not for tracking the full backlog. The full backlog SSOT
is [`aidocs/16-dispatcher-backlog.md`](../../aidocs/16-dispatcher-backlog.md)
and stays that way. Mirroring 100+ rows into Issues would mean
double-bookkeeping with predictable rot.

## What goes on the board

A GitHub Issue (and therefore a board card) should exist for:

1. **Bug reports** — anything filed via the bug-report template.
2. **External feature requests** — anything filed via the
   feature-request template (which the maintainer hasn't yet copied
   into aidocs/16).
3. **Items external contributors could realistically claim** — labelled
   `good first issue` or `help wanted`. These need an issue (not just
   an aidocs/16 row) because external contributors don't watch aidocs.
4. **The currently-active aidocs/16 slices** — at most a handful at a
   time. When the maintainer picks up `VIS-T1`, that row gets a
   matching GitHub Issue with the aidocs/16 ID in the title; the issue
   is closed when the PR merges. The aidocs/16 row remains the SSOT;
   the issue is a temporary tracking shadow.

Items in `aidocs/16` that are queued / blocked / not yet picked up do
NOT get GitHub Issues. They live in aidocs/16 only.

This avoids the 100-row double-bookkeeping trap.

## One-time board setup (via the GitHub web UI)

GitHub's Projects v2 API is still maturing and brittle to script — the
GraphQL surface changes shape, and `gh project` doesn't cover all
fields cleanly. The board is set up **manually, once**, then maintained
by the auto-add-to-project workflow rule.

### Step 1 — Create the project

1. Navigate to https://github.com/noheton?tab=projects (the user's
   projects, not the repo's) → "New project".
2. Template: "Board" (Kanban).
3. Name: `shepard backlog (active)`.
4. Description:
   > Active work surface for github.com/noheton/shepard. The full
   > backlog SSOT is `aidocs/16-dispatcher-backlog.md`; this board
   > tracks issues currently in flight.
5. Visibility: Public (so external contributors can see what's in
   flight before filing a duplicate issue).

### Step 2 — Configure columns

Replace the default columns with:

| Column         | Filter (status field)        |
| -------------- | ---------------------------- |
| Backlog        | `status:queued`              |
| In Progress    | `status:in-progress`         |
| In Review      | `status:in-review`           |
| Blocked        | `status:blocked`             |
| Done           | `status:done` (auto-archive) |

Use the `Status` built-in single-select field for this; rename its
options to match the labels above.

### Step 3 — Add custom fields

| Field name | Type             | Source                         |
| ---------- | ---------------- | ------------------------------ |
| Severity   | Single-select    | `severity:*` labels            |
| Area       | Single-select    | `area:*` labels (multi-select) |
| Effort     | Single-select    | `effort:S/M/L/XL` labels       |
| Stage      | Single-select    | `stage:*` labels               |
| Aidocs ID  | Text             | aidocs/16 row ID, e.g. `VIS-T1`|

For the single-selects backed by labels, use the GitHub built-in
"Labels" facet for filtering rather than manually copying values into
each field — the board view supports grouping by labels directly.

### Step 4 — Connect the repo

Project settings → Manage access → Add repository:
`noheton/shepard`. Grant write access so contributors can move cards.

### Step 5 — Configure auto-add rules

Workflows tab → "Auto-add to project":

- Trigger: new issue opened in `noheton/shepard`
- Action: add to project, set Status to `Backlog`

This means every newly filed issue lands on the board automatically.

### Step 6 — Saved views

Click "New view" and create at least these:

| View name        | Filter                                                  | Group by  |
| ---------------- | ------------------------------------------------------- | --------- |
| Active sprint    | `Status:"In Progress"`                                  | Area      |
| Open bugs        | `label:type:bug AND -Status:"Done"`                     | Severity  |
| External-facing  | `label:"good first issue", label:"help wanted"`         | Effort    |
| Plugin work      | `label:area:plugins AND -Status:"Done"`                 | Stage     |
| Security         | `label:area:security OR label:security`                 | Severity  |

## Ongoing rhythm

- When picking up an aidocs/16 slice: open an Issue titled
  `<aidocs-id>: <slice description>`, set the `Aidocs ID` field to the
  row ID, set Status to `In Progress`, link to the aidocs/16 line in
  the issue body. Close the issue when the implementing PR merges.
- When triaging a new external issue: leave it in `Backlog` until you
  decide. If you adopt it, copy a row into aidocs/16 and link both
  directions.
- The board is **not** the place to scan for future work — that's
  aidocs/16. The board is the in-flight + external-facing surface.

## Why not script this?

The original brief considered scripting the board creation via the
Projects v2 GraphQL API. Two reasons not to:

1. **API churn** — the v2 API has had three breaking-change waves in
   2024–2025; scripts rot fast.
2. **One-time work** — board creation is a single operator action. The
   marginal cost of automating it exceeds the marginal cost of doing
   it once with this runbook.

The maintenance workflows that DO get automated:

- Label sync: `.github/workflows/sync-labels.yml`
- Auto-add to project: GitHub's native rule (configured in step 5)
- Release notes: `.github/workflows/release-notes.yml`

That's the right level of automation for the solo + AI development
phase. As the team grows, revisit.
