---
stage: deployed
last-stage-change: 2026-05-24
audience: RDM steward, frontend reviewer, FAIR auditor, operator
---

# RDM-005 — Metadata Completeness Score widget (live)

**Closes:** §Top-5 #5 from
[`rdm-scrutinizer-2026-05-24.md`](rdm-scrutinizer-2026-05-24.md) — surfaces
per-Collection FAIR completeness as a 0–100 score chip + per-check breakdown,
creating operator pressure to fill the license / accessRights / annotation /
ORCID gaps the prior 13 UI improvements left untouched.

**Status:** ✓ shipped (`shepard.nuclide.systems`, 2026-05-24).

---

## TL;DR

A new `MetadataCompletenessCard` lands on every Collection landing page,
slotted between the `CiteThisCard` (RDM-001) and the Semantic Annotations
section. It renders:

- A **red/amber/green score chip** keyed to a 0–100 sum of nine FAIR
  checks (red < 50, amber 50–79, green ≥ 80).
- A collapsible **per-check list** with pass/fail icon, point value, an
  inline "Why this matters" tooltip (FAIR sub-principle + DataCite section
  reference), and a tonal **action button** that scrolls to the on-page
  edit affordance (or routes to `/me` for the ORCID check).

**Live scores at deploy (alice session, 2026-05-24):**

| Collection | Score | Band | Passing | Failing |
|---|---|---|---|---|
| LUMEN `/collections/42` | **40 / 100** | red | name, description, dataObjects | license, accessRights, creatorOrcid, semanticAnnotation, labJournal, heroImage |
| MFFD-Dropbox `/collections/661923` | **40 / 100** | red | name, description, dataObjects | license, accessRights, creatorOrcid, semanticAnnotation, labJournal, heroImage |

Both demo collections sit at the same score because they share the same
six gaps. The widget makes those gaps inescapable in the UI — closing them
is now a one-click jump from the score row.

---

## Check list — what counts, what doesn't

Nine checks, summing to exactly 100 points. The weights tilt heavily toward
the FAIR R1 dimensions (license + access rights + provenance) because those
are the bottleneck for any funder-grade deposit.

| ID | Check | Points | Source field | FAIR / DataCite mapping |
|---|---|---|---|---|
| `name` | Non-empty `name` | 10 | `Collection.name` | DataCite §3 (Title) |
| `description` | `description.trim().length >= 50` | 15 | `Collection.description` | DataCite §17 (Description); Zenodo completeness threshold |
| `license` | SPDX `license` set (post-LIC1) | **20** | `Collection.license` | DataCite §16 (Rights); FAIR R1.1 — single biggest blocker |
| `accessRights` | One of `OPEN \| RESTRICTED \| CLOSED \| EMBARGOED` | 10 | `Collection.accessRights` | DataCite §16 (Rights); FAIR A1.2 |
| `creatorOrcid` | `createdBy` user's `orcid` is set | 10 | `UserApi.getUser({username: createdBy}).orcid` | DataCite §2 (Creator) |
| `semanticAnnotation` | ≥ 1 Collection-level semantic annotation | 10 | `SemanticAnnotationApi.getAllCollectionAnnotations` | FAIR I1 + I2 |
| `labJournal` | ≥ 1 lab journal entry in the Collection | 5 | `CollectionLabJournalEntriesApi.listCollectionLabJournalEntries` | FAIR R1.2 (provenance) |
| `heroImage` | `heroImageUrl` set | 5 | `Collection.heroImageUrl` | Findability / discoverability |
| `dataObjects` | `dataObjectIds.length > 0` | 15 | `Collection.dataObjectIds` | FAIR F2 — an empty Collection has nothing to harvest |

**Total: 100 points.** The unit test `total points sums to 100` will break
the build if a check is added without adjusting the others, surfacing the
ceiling change in code review.

**Reasoning behind the weight ladder:**
- License (20) is intentionally the largest single lever — the RDM Scrutinizer
  found this to be the single hardest blocker to publication. Operators get
  the biggest visible jump when they finally set it.
- DataObjects (15) and description (15) are the next-largest because both
  are "the collection has substantive content vs. it's a stub" gates.
- Name (10), accessRights (10), ORCID (10), annotations (10) are the FAIR
  pillars where each one is necessary but no one carries the deposit
  alone.
- Lab journal (5) + hero image (5) are nice-to-have polish — they signal
  quality but don't block citation.

**Why "Contributor" is absent:** the spec listed it as "skip if missing".
Today's Collection wire shape has no `contributors[]` field; `createdBy`
is a bare username (one author). The check would always be a no-op until
a richer author surface ships (queued under the `RDM-002` follow-up). The
weight that would have gone to Contributor (5) was redistributed: the
description boundary moved from 10 → 15, and dataObjects from 10 → 15, to
keep the ceiling at 100.

---

## Architecture

Pure-frontend v0 per the spec — no backend changes.

```
frontend/
├── utils/metadataCompleteness.ts          # Pure scorer (no I/O)
├── components/context/collection/
│   └── MetadataCompletenessCard.vue       # Widget — fetches + renders
└── tests/unit/metadataCompleteness.test.ts # 36 Vitest cases
pages/collections/[collectionId]/index.vue # +1 line + 5 anchor ids
e2e/tests/rdm-005-metadata-completeness.spec.ts # 5 Playwright tests
```

**Separation of concerns** mirrors the `CiteThisCard` / `formatCitation`
pattern (RDM-001, 2026-05-24): the scoring math sits in
`frontend/utils/metadataCompleteness.ts`, pure and deterministic; the
component wires the three best-effort fetches (annotations, lab journal,
creator user lookup) and renders.

**Deep-link wiring** uses CSS-anchor `scrollIntoView({behavior: 'smooth'})`
+ a 1.6s primary-glow pulse (the same affordance LIC1's inline editors
use). Anchor ids planted on the parent page:

- `#metadata-description-section` — the description block
- `#metadata-license-edit` — the LIC1 chip strip (rendered as a
  placeholder when both fields are unset, so the scroll target always
  exists)
- `#metadata-annotations-section` — the Semantic Annotations heading
- `#metadata-labjournal-section` — inside the Lab Journal expansion
  panel
- `#metadata-heroimage-edit` — the hero banner (rendered as a
  placeholder when unset)
- `#metadata-dataobjects-section` — the Data Objects panel heading

The ORCID check is the only off-page action — it `router.push("/me")` so
the user lands on their profile page (RDM-002 surface, shipped 2026-05-24).
Name has no action — there's no scenario where a Collection lands with an
empty name (the create dialog forbids it), but the check is kept for
defensive completeness.

---

## Tests

### Vitest (36 cases, all green)

- Invariants (4): total = 100, exactly 9 checks, unique ids, full = 100/green
- Per-check pass/fail × edge cases (23 cases — name whitespace, description
  exactly-at-threshold, license null vs empty string, accessRights null,
  ORCID null vs empty, annotation count null (loading), lab journal count
  null vs 0, hero image null, dataObjectIds empty list)
- Band classification (4): error < 50, warning [50, 80), success ≥ 80, +
  boundary at exactly 80
- Deep-link wiring (3): action labels non-empty, on-page anchors present
  for description/license/accessRights/annotations/labJournal/heroImage/
  dataObjects, name/creatorOrcid carry `deepLink === null`

```
✓ tests/unit/metadataCompleteness.test.ts (36 tests) 11ms
Test Files  1 passed (1)
     Tests  36 passed (36)
```

### Playwright (5 cases, all green on live)

```
✓ widget visible on /collections/42 (LUMEN) with score chip (8.2s)
✓ expanding 'Show checks' renders all 9 check rows (8.3s)
✓ widget also renders on /collections/661923 (MFFD-Dropbox) (12.8s)
✓ license deep-link scrolls to #metadata-license-edit when license check fails (13.2s)
✓ score chip color matches the band thresholds (8.6s)

  5 passed (51.9s)
```

The full smoke suite (`make redeploy-frontend`) is green: **25/25**.

---

## Behaviour details worth flagging

### Auth-settle race (and the fix)

The widget hit a real race on first mount: `useShepardApi(...)` builds its
API client from `useAuth().data?.accessToken`, which is `null` on a fresh
navigation. Our `onMounted` fetches therefore went out unauthenticated
once, returned 401, and the `catch` settled the count at `0` — so the
widget perpetually showed "no annotations". Fixed by adding a `watch`
on the access token: the fetches re-fire automatically the moment the
session settles.

`SemanticAnnotationList.vue` doesn't hit this because it's mounted later
in the page (inside an existing render scope where auth is already
settled). Our widget mounts above it — different timing window.

### LUMEN's "40 / 100" score is honest

The Scrutinizer reported "≥10 semantic-annotation chips" on TR-001. Those
are **DataObject-level** annotations, not Collection-level. The
Collection 42 entity itself has zero annotations — so the widget's
`semanticAnnotation` check fails. That's not a bug; it's the widget
correctly distinguishing "this Collection is itself annotated" from
"this Collection's children carry annotations".

If a future check should aggregate child-DO annotations into the
Collection score, that's an RDM-005a follow-up — but it changes the
semantic substantially (a Collection with 8500 DOs would always score
green on annotations even if its own metadata is bare).

### Lab journal 500 on LUMEN

The `listCollectionLabJournalEntries` endpoint returns a 500 for
`/collections/42` (Collection appId resolution). Filed as the
`labJournal` check failing on the live deploy — handled gracefully by
the widget's try/catch. The endpoint itself is a separate bug worth
investigating (likely related to the 1-based legacy id ↔ appId path
the LJ endpoint uses), but doesn't block this widget shipping.

---

## What I didn't ship (queued as RDM-005a)

The spec gave an explicit out: *"If the deep-link wiring per check is
too fiddly, ship the score + per-check list as v0 + queue deep-link
follow-up as RDM-005a."* I shipped the deep-link wiring as v0 because
it turned out cheap (existing anchors + `scrollIntoView`). The
follow-ups that genuinely deferred:

- **Aggregate child-DO annotations into the Collection score.** Today's
  check is strictly Collection-level. Toggling to "Collection OR child"
  is a design call (it would mask Collection-level gaps); deferred.
- **Embargo end date** as part of the `accessRights` check. Today
  "set or not" is the gate; embargo requires the field to be EMBARGOED
  AND an `embargo_until` date to be in the future. Field doesn't exist
  yet.
- **Funder grant reference** as a 10th check. DataCite §17
  (FundingReference) — required for EU-funded work, currently absent
  from the Collection wire shape.
- **A `GET /v2/collections/{appId}/completeness` server endpoint** so
  search/index/harvest pipelines can filter Collections by
  completeness without the per-row client compute. Backend lift, not
  ship-with-v0.
- **A "Show only failing" filter** on the per-check list. Today's list
  always renders all 9 rows; the spec didn't ask for filtering.

---

## Per CLAUDE.md doc-currency rules

- `aidocs/16` — RDM-005 row flipped from `queued` to ✅ shipped, this commit hash
- `aidocs/44` — new row in §7 (Semantic annotations / FAIR-surface
  frontend extensions)
- `aidocs/34` — new row documenting the new UI surface visible to operators

The widget makes no admin-visible runtime change — no new endpoint, no new
config key, no migration. Operators upgrading from upstream see the score
chip appear on every Collection landing the moment the frontend image
rolls; no migration steps.

---

## Live verification

- **LUMEN:** [https://shepard.nuclide.systems/collections/42](https://shepard.nuclide.systems/collections/42)
- **MFFD-Dropbox:** [https://shepard.nuclide.systems/collections/661923](https://shepard.nuclide.systems/collections/661923)
- **Reproduce score capture:** `npx playwright test e2e/tests/rdm-005-metadata-completeness.spec.ts`

---

## Reasoning trail

**Why these nine checks and these weights, not a different cut?**
Three constraints drove the design:

1. **The check must read off something already on the wire shape OR
   reachable in one cheap API call.** Anything requiring a new
   endpoint deferred to v1. This excluded `embargo_until` (no field),
   `fundingReference` (no field), `contributors[]` (no field).
2. **The weights must move the needle when LIC1 ships.** Pre-LIC1,
   license/accessRights were 0; post-LIC1, both populate easily and
   together add 30 points. A LUMEN admin who sets `license=CC-BY-4.0`
   + `accessRights=OPEN` immediately jumps from red to amber. That's
   the intended motivator.
3. **The thresholds (50, 80) must classify the demo collections
   informatively.** LUMEN and MFFD both at 40 (red) means "do work";
   when LUMEN closes license + access + annotation + ORCID, it lands
   at 90 (green) — the "almost-DMP-grade" feeling. Tested by
   mentally walking the scoreboard against the buildFullCollection
   fixture in the unit tests.

**Opposing-lens consideration** (per the project's "agents argue +
consult the persona board" rule): the **Reluctant Senior Researcher**
would call this widget "another gauge demanding I fill in your boxes".
That criticism stands — the widget is a stick, not a carrot. The
counterargument: the per-check tooltips show *why* each one matters
(DataCite section, FAIR principle), so the score isn't bureaucratic
self-evident, it's pedagogical. The action button is one click, not
five-step wizard. For the reluctant persona who already has 40 years
of muscle memory: they can hide the per-check list entirely (default
collapsed); the score chip stays small and ignorable. For the persona
who *cares* about FAIR (the **Research Data Manager**, the
**Digital Native Researcher**), the widget gives them the gauge they
asked for.

---

## Co-Authored-By

🤖 Generated with [Claude Code](https://claude.com/claude-code)
Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
