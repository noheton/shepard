---
stage: audited-by-personas
last-stage-change: 2026-05-24
---

# Reluctant Senior Researcher — re-walk on live, 2026-05-24

**Persona.** 28 years at DLR. 40 TB on shared NFS. 600-row Excel master
sheet. Track 1 Run 22192 is at `/mffd2024/track1/run22192/`. I find any
of my files in under 30 seconds with `cd` and tab-completion. My
attribute keys are literal strings — `material_roll_change=true`,
`run_number=22192`, `propellant_batch=LH2-2024-06-01`. My collaborators
know what they mean because I told them.

**Subject.** A live walk of `https://shepard.nuclide.systems` as it
sits on 2026-05-24, after ~22 UI shipments today. Compare against my
prior verdict in `persona-review-reluctant-senior.md` (2026-05-22,
source-only on `aidocs/semantics/95`).

**Evidence.** Eight Playwright screenshots committed to
`e2e/screenshots/persona-reluctant-2026-05-24/01-08-*.png` plus three
follow-up shots `09-11-*.png`. Logged-in as `alice/alice-demo`.

---

## How I read this round

Last time I read design docs. This time I logged in and clicked.
That's a different thing. The docs sold me a vision; the product
sells me the actual day. So what I'm grading now is whether the
day moved closer to "I might sit down with this" or further away.

The headline: **today's polish helped. It did not change my answer.**
A few things crossed the bar from "I can see why someone would
want this" to "this is actually useful to me." A few things still
make me close the tab and `cd` back to my NFS share.

---

## What I tried

Sequence below, with the screenshot the persona references.

| Step | What I did | Screenshot | Verdict |
|------|------------|------------|---------|
| 1 | Sign in via Keycloak, land on home | `01-home.png` | OK. "Good afternoon, Alice Researcher." Friendly. |
| 2 | Click *Collections* | `02-collections-list.png` | **Conversion killer #1**, see below. |
| 3 | Open collection 42 (LUMEN) directly | `03-collection-42-landing.png` | The good page. Cite card + Completeness widget land. |
| 4 | Click into TR-001 | `09-tr001-detail.png` | Best moment of the walk. |
| 5 | Header search "TR-04" | `05-header-search.png` | Best **delta** vs last round. |
| 6 | `/me` landing + Profile | `06-me-landing.png` + `07-me-profile.png` | Clean section grid. Trustworthy. |
| 7 | `/admin` as alice (no role) | `08-admin-as-alice.png` | Honest restricted view. Good. |
| 8 | Try Dataset Lineage panel on coll 42 | `11-lineage.png` | Empty. Sells the feature short. |

---

## CK1 — The collections list is **a wall of test fixtures**

This is the single biggest deterrent of the walk and it is fixable
in an afternoon.

I click "Collections" expecting to see the showcase datasets the
product wants me to engage with. What I get is page 1 of 4, twenty
rows of:

```
lic1-e2e-coll-1779624939355   —   0   24 May 2026   Alice Researcher
lic1-e2e-do-1779619239337     —   1   24 May 2026   Alice Researcher
lic1-e2e-coll-1779619220282   —   0   24 May 2026   Alice Researcher
...
```

The LUMEN campaign collection — the one your `/help` text and your
home page hero card both push me towards — **is not on page 1 of the
collections list.** It's buried somewhere on page 4 sorted by recency
because today's e2e test run pushed it down.

The home page (`01-home.png`) handles this correctly — three Recent
Collection cards at the top show LUMEN, Home Environment, Xchange
ahead of the fixture noise. But the `/collections` index does not
apply the same hygiene.

What my Excel master sheet does not do: drown my real work in
machine-generated junk.

**Persona fix, ranked:**

1. **Hide collections with the `e2e-test`/`fixture` tag from the
   default list view.** Add a "show test fixtures" toggle in the
   filter bar. Default off. (The `lic1-e2e-` name prefix is a free
   discriminator.)
2. Default sort = "starred" or "owned by me with > 1 DO" before falling
   back to recency.
3. A `--gc` cron that retires e2e fixture collections older than 24h
   would also do it, and it's the right shape long-term — test runs
   shouldn't accumulate visible state.

(See aidocs/16 backlog hint: this is the same shape as
CRIT-WORKTREE-DOCKER-CACHE — fixture residue masquerading as user data.)

---

## CK2 — The persistent red error toast on collection landing

`03-collection-42-landing.png` shows a red error banner overlapping
the page:

> Error while AddCollectionLabAJournalEntries:
> An internal server error occurred. Reference:
> 3b94b5dc-65ff-44e2-a06e-49eaab6f2eb5

It does not block content — but it sits across the page like a
flickering warning light. As an operator I read this as **"the
system is not OK right now."** Even if it's a Lab Journal sub-feature
that's broken (not the dataset itself), I have to know enough about
the architecture to discount it.

What my folder tree does not do: tell me at midnight that something
internal is broken when I just wanted to find a run.

**Persona fix:**
- If a sub-feature panel fails to load, the toast should name what
  failed in **researcher language** ("Lab journal entries are
  unavailable right now") and offer a retry link.
- A reference UUID with zero context is forensic shrapnel — fine
  for a logger, useless for a user.
- Don't render the toast over the page content. The page worked.
  The lab-journal entries didn't. Differentiate.

This is the same gap the "honest empty state" PR (`b3dfe98d`) just
closed for provenance — apply the same discipline to error toasts.

---

## CK3 — Dataset Lineage panel is empty for a Collection that obviously has lineage

`11-lineage.png` — I click *Dataset Lineage* on the LUMEN collection.
It says **"No datasets in this section."**

But the LUMEN demo *has* lineage. TR-004 (the anomaly) → investigation
sub-tree → TR-005 hold/repair → TR-006 re-test. That's the entire
point of the demo. If the Collection-level lineage panel doesn't
surface it, the persona's killer-demo moment never fires.

I suspect "Dataset Lineage" at the Collection level means something
different from "predecessor/successor chain among the DOs in this
Collection" — but I shouldn't have to learn the distinction. If
this panel exists on the Collection page and it's empty for **the
demo dataset built to show provenance**, the feature is selling
itself short.

**Persona fix:**
- Either: collapse the empty Lineage panel by default with a hint
  ("This collection's lineage view is for **dataset-level** lineage
  links — for DO-level provenance, see TR-004 → Predecessor of TR-005")
- Or: aggregate DO-level provenance into the Collection lineage view.
- Or: drop the panel from the Collection level entirely until it has
  something to show on the flagship dataset.

(Same family as the `b3dfe98d` provenance-empty-state fix. Pattern:
empty states that lie.)

---

## CK4 — Annotation dialog is still a leap

The Semantic Annotations row on TR-001 (`09-tr001-detail.png`) is
full of chips: "Campaign Run", "Nominal Run", "Test Outcome", "Test
Outcome", "Nominal Run", "Campaign Run"… repeated. I don't know
what the duplicates mean. I don't know if one is a tag and another
is a triple. I don't know which were typed by the seed script and
which are clickable to edit.

The chip list works as eye-candy. It does not work as a thing I
would maintain.

What I want — same as last round, restated against the live UI:

- **Three columns: key | value | source.** Plain typography, alphabetised by
  key, sortable. Clear visual distinction between literal user
  attributes (`propellant_batch=LH2-…`) and ontology-driven semantic
  annotations (`f4i:hasCampaignRun`).
- An "Add annotation" button that defaults to *literal key/value*
  with an opt-in "this is a typed semantic annotation" advanced
  toggle.
- The chip pill view is the *summary*, not the *editor*. Editor =
  the table.

The Attributes accordion (also on TR-001) already does this for
literal attributes — count badge `(10)` is visible and the new today's
shipment count-badge pattern works. The **Semantic Annotations**
panel needs the same Attributes-style accordion + table editor.

---

## CK5 — 40 TB migration story still missing

No change from last round. I see no "Import a folder tree as a
Collection" button anywhere in the UI. The Create new collection
button gives me an empty Collection — exactly what I cannot use
when I already have 40 TB of structured data.

The `shepard-plugin-importer` design exists in memory (per
`project_importer_plugin.md`) but the user-facing entry point does
not. Until I can point Shepard at `/mffd2024/track1/` and watch it
build the Collection from my directory layout + sidecar YAML, this
project asks me to re-key 40 TB by hand.

---

## What today actually moved

Three deltas from `persona-review-reluctant-senior.md` (2026-05-22) that
genuinely shifted my read:

### Δ1. Cite this dataset card (`03-collection-42-landing.png`)

> Inex Heton (2026). LUMEN-Inspired Hotfire Test Campaign — Q3 2024
> (synthetic) by Damian Holman. Shepard Research Data Platform.
> https://shepard.nuclide.systems/collections/42. Accessed 2026-05-24.

A copy-paste-ready citation block with a one-click Copy button and a
"BibTeX | RIS | XML/JSON" tab row. This is the thing that gets me to
say "OK, this could appear in my paper's bibliography." It costs me
zero clicks to acquire — the page renders it for me.

**This is the first feature I would actively show a colleague.**
Last round I didn't have a positive moment to point at. Today I do.

(`RDM-001`, commit `6d85abd9`.)

### Δ2. Metadata Completeness widget (`03-collection-42-landing.png`)

A bar that says "80/100" with a "Show details" link. I haven't expanded
it but the principle is right: **tell me what's missing before the
funder/auditor does.** If the expanded panel says "Add a license,
add an ORCID, add a DOI" with one-click fixes, this is the FAIR
on-ramp that's been missing from every RDM tool I've used. If it
says "Add `dcterms:creator`, conform to `mffd:CampaignShape` §4" it
fails the persona test from CK4.

I gave it the benefit of the doubt; I'd need to expand it before
final judgement.

(`RDM-005`, commit `a8bd5e01`.)

### Δ3. Header search dropdown actually finds my anomaly (`05-header-search.png`)

I type "TR-04" in the header search and get **"Anomaly investigation —
TR-004 Fuel Turbopump"** as the top result, with "Advanced search for
TR-04" as the second option. This is the moment my muscle memory
finally gets a payoff.

Before today I had to navigate to /collections, find the right one,
expand the DataObjects panel, type in the in-page search… four-five
clicks. Now it's one keystroke from anywhere.

**This is the first interaction where Shepard's speed beat my
`cd /mffd2024/...; ls TR-004*` baseline.** That matters.

### Δ4. `/me` profile grid is honest

`06-me-landing.png` shows a clean five-card grid: Profile / API Keys /
MCP / Subscriptions / Git Credentials. Each card has a one-sentence
description. **No mystery icons, no ontology terms.** This is the
quality of UI I'd expect from a commercial product.

`07-me-profile.png` is the same screen (no follow-through to the
profile page itself in my walk — the click target wasn't the right
selector), but the landing alone gets a thumbs up.

### Δ5. `/admin` restricted view is friendly

`08-admin-as-alice.png` — "Administration is restricted. Required
role: instance-admin. This section is only available to instance
administrators. If you need access, ask an instance admin to grant you
the instance-admin role." Plus two buttons: Go home / Sign in as
another user.

That's a 401 done right. Not a stack trace. Not a redirect loop.
The text tells me what to do next. **Trust-building.**

---

## What still hurts

Listed by severity for adoption.

1. **CK1 collections-list fixture noise** — 5-min fix, deal-breaker
   if not addressed. Today this means the first navigation a curious
   colleague would do shows them what looks like a broken database.
2. **CK2 red error toast on landing** — 30-min fix, screams "broken
   system" at the user.
3. **CK3 empty lineage panel** — the demo dataset's killer feature
   silently doesn't fire at the Collection level.
4. **CK4 annotation dialog ergonomics** — the chips view is for
   the summary; editing requires the Attributes-style table.
5. **CK5 40 TB migration** — until `shepard-plugin-importer` ships
   user-facing, this is research-data-management in name only for
   anyone with legacy data.

---

## The one killer demo moment

Same as last round: pull up Run 22192 (or for the live demo, TR-004),
show the **provenance graph** at the DataObject level, with the
investigation sub-tree branching, repair entry, re-test all visible
in one screen with two clicks from the top, plus a one-line "this
took 2 hours to reconstruct from the folder tree last month."

Today's walk: I got *close*. Header search → TR-004 → detail page
loads with the count-badged accordions, semantic annotations chips,
data references grouped by type. If I now had a **Provenance** tab
with the lineage graph on the DataObject page rendering TR-004 → TR-005
→ TR-006 visually, that's the demo.

The provenance accordion is on the DO page (visible in
`09-tr001-detail.png`, scrolled below the fold) — I didn't click it
on this walk; that's a follow-up. The Collection-level Lineage panel
is empty (CK3); I assume the DataObject-level one is the real article.

(If the DO Provenance accordion *also* shows "No datasets in this
section" for TR-004 on a synthetic dataset designed to demonstrate
provenance, the same fix as CK3 applies, and the gap is severe.)

---

## Minimum feature set I'd need before migrating anything real

Carried over from prior round, refined:

1. **Folder-walk importer with sidecar YAML, no shape required**
   (CK5). Until I can point Shepard at `/mffd2024/track1/run22192/`
   and get a Collection back, I cannot start.
2. **Hybrid attribute bucket — literal + typed** (CK4 + prior round
   `[NEEDS-CLARIFICATION-1]`). My `material_roll_change` stays a
   literal string attribute on the DataObject; the shape engine
   ignores it; the search index finds it.
3. **Table view as the default landing page for a Collection**
   (prior round §4). The Data Objects table already exists on
   the Collection landing (`03-…`); it just needs to be the
   *first* thing I see (above the Description and the Cite card),
   not the third.
4. **Collections list filter that hides fixture residue by default**
   (CK1).
5. **A "Restore my collection from snapshot" feature** so I know
   the snapshot taken yesterday is *actually* recoverable, not just
   "stored somewhere."

---

## Honest verdict — would I adopt after this session?

**Not yet. But the gap shrank.**

Last round I said *"I would not adopt after one session. Would I
run it in parallel for six months? Maybe."* That answer holds.

What today added: **a positive demo moment** (header search +
TR-004 hit) and a **professional polish floor** (the Cite card,
the Completeness widget, the friendly /admin restricted view, the
/me grid). The product no longer feels like a prototype to a
returning visitor. It feels like a beta of a real RDM tool.

What today did **not** add: a story for my 40 TB. An annotation
editor I could maintain. A collections-list view that doesn't drown
me in test fixtures. A live demo of the provenance graph on TR-004.

**If, on next round, the importer plugin ships + the collections
list hides fixtures + the lineage panel shows something for TR-004
→ TR-006, I would clear an afternoon.** That's three changes. Two
of them are afternoon work. The third is the strategic one.

---

## Recommendations to the Shepard team, in order

1. **Today** — hide e2e fixture collections from the default
   `/collections` view (CK1). A `WHERE name NOT LIKE 'lic1-e2e-%'`
   default filter or, better, a `:Tag {key:'test-fixture'}` exclusion.
   Cost: under an hour. Value: every new visitor stops thinking
   the database is broken.
2. **This week** — fix the red error toast that hovers on
   `/collections/42` (CK2). Either the Lab-Journal sub-feature
   stops 500-ing, or the panel renders a graceful inline empty
   state with a per-panel "details" link. No more page-wide toasts
   for sub-feature failures.
3. **This week** — populate the Collection-level Dataset Lineage
   panel for collection 42 with the TR-004 → TR-005 → TR-006 chain
   (CK3). Or, if the Collection-level lineage is meant for something
   else, collapse the panel by default with an explanatory hint
   pointing me to the DataObject page.
4. **This sprint** — expand the Metadata Completeness widget's
   "Show details" to a single-screen checklist with one-click fix
   buttons. Verify the labels are in researcher-language (license,
   ORCID, DOI), not ontology-speak.
5. **This sprint** — add a "Provenance" tab to the DataObject page
   that renders a one-screen graph of predecessor + successor at
   depth ≥ 2. This is the killer-demo screen. Make it the home
   tab on TR-004.
6. **Next milestone** — `shepard-plugin-importer` user-facing UI
   (CK5). Without this the persona never crosses from "interesting"
   to "I'll move my data."
7. **Next milestone** — Annotations-as-table editor on the DataObject
   page (CK4), Attributes-style. Chips stay as the summary.

---

## Delta from prior round — what shifted with today's 22 shipments

| Item | Prior round (2026-05-22) | This round (2026-05-24) | Δ |
|------|--------------------------|--------------------------|---|
| Citation story | Implied, not surfaced. | Live "Cite this dataset" card with copy button on Collection landing. | **+ Adoption-positive.** |
| Metadata completeness | Concept, no surface. | Live widget with score (80/100) on Collection landing. | **+ Adoption-positive.** |
| Header search | Source-only review; not exercised live. | Live dropdown; typing "TR-04" finds anomaly investigation. | **+ The single biggest UX win.** |
| `/me` profile UX | Not in scope. | Clean 5-card section grid with descriptions. | **+ Trust-building polish.** |
| `/admin` for non-admin | Not in scope. | Friendly restricted view, naming the missing role + action. | **+ Trust-building.** |
| Collection landing toast/error | Not in scope. | Persistent red 500 toast over content. | **− NEW conversion killer.** |
| Collections list curation | Not in scope. | List dominated by `lic1-e2e-*` fixtures. | **− NEW conversion killer.** |
| Annotation editor (chips) | Flagged as IRI-heavy. | Chips work, no editor surface. | **= Same gap, same severity.** |
| Dataset Lineage (Collection level) | Not exercised. | Empty on the demo dataset. | **− Negative — feature lies about itself.** |
| 40 TB migration path | Missing. | Still missing. | **= No change.** |
| Provenance graph as killer demo | Asked for. | Not exercised on DO page; Collection-level panel empty. | **? Pending DO-page walk.** |

**Net read.** Today's polish moved the floor up — the product now
**looks** like something I would adopt. Two new conversion killers
appeared (the fixture noise and the red toast) that did not exist on
my prior list, but both are cheap fixes. The strategic gaps —
importer, annotations editor, killer demo screen — are unchanged.

If I had to score the round: **+2 polish, −2 hygiene, 0 strategy.**

— The persona, 28 years in, slightly more curious than last time.
Still has the NFS share open in another tab.

---

## Note on evidence

- Screenshots: `e2e/screenshots/persona-reluctant-2026-05-24/01..11-*.png`,
  committed.
- Spec files: `e2e/tests/persona-reluctant-senior-walk.spec.ts` +
  `e2e/tests/persona-rs-tr001.spec.ts`, committed.
- Source baseline: `aidocs/agent-findings/persona-review-reluctant-senior.md`
  (2026-05-22, source-only).
- Today's shipment list: `git log --since=2026-05-23 --oneline` —
  19 commits, 22 features (per task brief).
- Auth helper used: `e2e/tests/helpers/auth.ts` (`loginAs` tolerant variant).

Three follow-ups for the next persona round:

1. Click into TR-004 specifically + try the DataObject-level Provenance
   accordion to confirm/deny CK3 at the DO level.
2. Expand the Metadata Completeness widget "Show details" and screenshot
   the language used (researcher vs ontology).
3. Try `shepard-plugin-importer` if/when its UI ships — the persona's
   one strategic blocker.
