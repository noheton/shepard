---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Persona Debate Round — Reluctant Senior vs. Digital Native

**Moderator**: Structural debate between two independently-authored persona findings.
**Date**: 2026-05-21.
**Source files**: `persona-reluctant-senior.md` + `persona-digital-native.md`.

---

## Round 1: The Excel Master Sheet vs. the Shepard Graph

### Senior's Opening

"My 600-row Excel sheet is faster to search than any provenance graph. I open
it, hit Ctrl+F, type 'LOX/LH2', and I see every test run that used that propellant
in 0.5 seconds. In Shepard, my attribute `propellant = LOX/LH2` sits in the
`Attributes` dict on each DataObject — but the search layer on the collection page
doesn't let me filter by attribute key-value. The searchable layer is the semantic
annotation picker, which requires an ontology term IRI. I don't have a propellant
ontology. So Shepard's cross-collection attribute search is both worse AND requires
admin setup I can't do myself. Excel wins, straight up."

### Native's Response: AGREE AND ESCALATE

"Worse from the API angle too. The only cross-attribute query path today is
`POST /shepard/api/search` — a v1 endpoint with an undocumented JSON DSL on the
frozen surface. Friction score: 3/5. There is no v2 endpoint:
`GET /v2/search?attributes.propellant=LOX/LH2` does not exist. Kadi4Mat ships
this as their bread-and-butter 'extra metadata' filter with a clean query string.
A researcher writing a data-curation script has to know the shape of an internal
search DSL that isn't documented in any OpenAPI spec. Senior's Excel column filter
is literally better developer experience than our 'searchable metadata layer.'"

### Senior's Response: DOUBLE DOWN

"Exactly. And it exposes the deeper structural problem: the Attributes panel and
the semantic Annotations panel look like parallel paths to the same goal — tagging
my data — but they are not equivalent. Attributes are stored as a property map on
the Neo4j node. Annotations go through a SPARQL-backed ontology resolver. The
attribute search doesn't filter by Attributes. The semantic search only works if
your terms are in a loaded ontology. So there are two annotation systems, neither
gives you the Excel-column-filter experience, and as a new user there is no
signpost that tells you which system to use for which purpose. My Excel sheet has
one column for propellant. Shepard has two places to put it and neither is
fully searchable."

### Design Insight — Round 1

**Add `GET /v2/search/data-objects?attributes.<key>=<value>` (or
`POST /v2/data-objects/filter` with a typed filter body) that queries the
plain `attributes` map without requiring ontology terms.**

This is not a semantic-search feature — it is a literal key-value index query
on the existing Attributes dict that is already stored on every DataObject node.
The endpoint should accept multiple filter params and support `AND` composition.
Kadi4Mat's `GET /api/records/?extras_fields[propellant]=LOX/LH2` pattern is the
reference implementation. Effort: 3 days backend + 1 day frontend filter UI.
Until this ships, the two-annotation-system confusion is a retention-killer for
every researcher who reaches for their spreadsheet first.

---

## Round 2: The `referenceIds` Naming Mess

### Native's Opening

"I spent 45 minutes debugging a 404. `GET /v2/collections/{cId}/data-objects/{doId}`
returns a `referenceIds` field containing integers: `[331, 335, 337, 1077]`. The
field is called `referenceIds`. I called `GET /v2/data-objects/331`. 404. Because
`referenceIds` contains `DataObjectReference` join-record node IDs — internal
Neo4j OGM artefacts — not DataObject IDs, not container IDs, not anything navigable
via the v2 API. The MCP design doc (`aidocs/30`) called this out as a known bug.
It is not fixed. This is not a minor naming nit: every new API caller who tries
to navigate from a DataObject to its containers hits this wall. The field name is
actively lying about its contents."

### Senior's Response: AGREE AND ESCALATE

"And this is exactly why I don't trust a system that calls itself 'graph-native'
but exposes raw internal node IDs as the navigation primitives. In my folder
structure, `ls TR-004/` shows me `sensors/ report.pdf config.yaml`. The names
tell me what they are and I can open them. Shepard's equivalent is `referenceIds:
[331, 335, 337, 1077]` — four meaningless integers. There is no semantic in
that output. An experienced developer hits a 404 and spends 45 minutes debugging.
An engineer who trusts the API surface and isn't debugging will silently write
broken code that calls the wrong endpoint. That's not a graph — that's a leaking
internal ORM model."

### Native's Response: DOUBLE DOWN

"Three separate shapes the response could take that would all be better:

Shape A (minimal): drop `referenceIds` entirely; add `containerCount: 4`.
Shape B (usable): replace with `containers: [{kind:'TIMESERIES', appId:'...'},
{kind:'FILE_BUNDLE', appId:'...'}]` — navigable via `/v2/timeseries-containers/{appId}`.
Shape C (full): same as B plus `containerReferenceAppId` for the join-record itself
if anyone needs it.

The design doc (`aidocs/30`, the MCP gap list) recommends Shape B and calls it
MCP-1a. It's designed. It's not shipped. Every day it stays unshipped, a new
researcher hits a 404 on their first API exploration."

### Design Insight — Round 2

**In the `DataObjectIO` response record, replace `referenceIds: List<Long>` with
`containers: List<ContainerSummaryIO>` where each entry carries `{kind, appId,
name}` — all three navigable fields.**

File to change: the IO record class in
`backend/src/main/java/de/dlr/shepard/v2/` that backs the DataObject GET response.
The Neo4j query already has access to the related container nodes; this is a
projection change, not a schema change. **Also rename the legacy field to
`_legacyReferenceIds` or drop it** — keeping it with its current name while
adding the correct field creates two surfaces with overlapping but different
semantics. The MCP design doc (aidocs/30, MCP-1a) specifies this fix; treat it
as a blocking bug, not a future feature.

---

## Round 3: The `v-if="advancedMode"` Bug

### Senior's Opening

"I arrived at a collection, looked in the sidebar. I could see DataObjects. I
could see the collection description. I could not see anything about containers —
timeseries or files. I assumed the collection had no sensor data. I was wrong.
`CollectionSidebar.vue` wraps the entire Containers section in
`<template v-if=\"advancedMode\">`. Basic mode is the default. I have no indication
that 'Advanced mode' exists or that toggling it reveals the core data I came here
to find. This is not a design decision — it is a violation of the system's own
documented rule ('advanced is a strict superset of basic, never hides what basic
shows'). It actively hides data that a first-time user needs. I'd never find my
sensor data without a colleague telling me to toggle the switch."

### Native's Response: AGREE — AND SIGNAL QUALITY CONCERN

"I'd fix it in a PR within 20 minutes — `CollectionSidebar.vue`, remove the
`v-if=\"advancedMode\"` wrapper from the Containers section. One line deletion.
But the fact that this bug exists and made it to main signals something about
the development process. The 'advanced = strict superset' rule is in `CLAUDE.md`.
It's in the memory notes. It's documented. And it was still violated. If a rule
that simple and that explicit can be broken without a test catching it, what
other assumed-invariants are silently broken? I'd want a Vitest test that
mounts `CollectionSidebar.vue` in both modes and asserts that every element
visible in basic mode is also visible in advanced mode — as a regression guard,
not as a one-time fix."

### Senior's Response: CONCEDE ON FIX, HOLD ON TRUST

"Conceded — one line fix, I can see that. But your regression-test point is the
real one. The reason I trust my NFS setup is that it has no bugs that hide my
data. My files don't disappear based on a toggle. The moment a system has a mode
where my sensor data is invisible by default, I am carrying mental overhead —
'did I forget to toggle something?' — on every visit. You can fix this line. You
cannot fix the fact that it shipped. That's the trust tax."

### Design Insight — Round 3

**Two-part fix — ship together, not separately.**

Part 1 (immediate bug fix): In `frontend/components/context/sidebar/CollectionSidebar.vue`,
remove the `<template v-if="advancedMode">` wrapper around the Containers section.
Containers are navigational primitives, not advanced features.

Part 2 (regression guard): Add a Vitest component test for `CollectionSidebar.vue`
that mounts it in basic mode and in advanced mode, collects all rendered `v-list-item`
or equivalent elements, and asserts `basicModeItems ⊆ advancedModeItems`. If any
element present in basic mode is absent in advanced mode, or any element present in
advanced mode was not already in basic mode (for the core structural items), the
test fails. This test should be the standing enforcement mechanism for the strict-superset
invariant — not CLAUDE.md prose, not code review memory.

---

## Round 4: The 5-Line Python Problem

### Native's Opening

"Loading TR-004 channels into a DataFrame: not possible in 5 lines today.
The minimum working path uses raw `requests` + the P10 SQL endpoint:

```python
import requests, pandas as pd
r = requests.post(
    'https://shepard.nuclide.systems/v2/sql/timeseries',
    headers={'Authorization': f'Bearer {token}'},
    json={'sql': "SELECT time, value FROM \"lumen-inspired-sensors\" WHERE measurement='vib_fuel_pump' AND field='g_rms' ORDER BY time"},
)
df = pd.DataFrame(r.json()['rows'])
```

That's 7 lines — and only if I already know the container name
(`'lumen-inspired-sensors'`), the measurement name, the field name, and have
a token in scope. Discovery of those values requires v1 client calls with
numeric IDs or prior knowledge from the seed script. The designed `shepard-py`
SDK — `client.load_timeseries_reference(collection='lumen-dataset', data_object='TR-004', reference='Measurements')` — would make this 3 lines. It doesn't exist.
The Kiota v2 Python client directory is a `.gitkeep`. The `shepard-client` on
PyPI targets the frozen v1 surface with numeric IDs. The gap between 'designed'
and 'installable' is killing adoption velocity."

### Senior's Response: AGREE ON PROBLEM, DISAGREE ON PRIORITY

"I'm glad you can write Python. My analysis pipeline is MATLAB and a Fortran
post-processor from 1997. Your 7-line problem is my 'CSV export doesn't exist'
problem. I go to the timeseries container page and I see a CSV upload button and
no download button. You need a Python SDK. I need a browser button. We both need
the same underlying feature: a way to get my channel data out of Shepard and
into my analysis tool without knowing the SQL schema. The specific shape differs —
you want `shepard-py`, I want a download button — but the root cause is identical:
there is no v2 endpoint for timeseries data reads at all. The P10 SQL surface is
the only path, and it's opaque to both of us unless we know the schema."

### Native's Response: CONCEDE — SHARED ROOT CAUSE

"Correct, and that's the clean framing: there is no `GET /v2/timeseries-containers/{appId}/data`
endpoint. The P10 SQL surface (`POST /v2/sql/timeseries`) fills the gap for people
who know SQL and know their schema, but it is not a first-class timeseries data
read API. The senior researcher's CSV download button and my `shepard-py`
`load_timeseries_reference()` are both downstream of the same missing endpoint.
Build the endpoint first; the SDK wrapper and the browser button are both 1-day
add-ons once the endpoint exists."

### Design Insight — Round 4

**Ship `GET /v2/timeseries-containers/{appId}/export` with `?format=csv|parquet|json&from=&to=&channels=` before the Python SDK and before the download button — because both depend on it.**

The endpoint should:
- Accept an optional `channels` comma-list of `timeseriesAppId` values to subset
  (or return all channels if omitted).
- Accept `from` / `to` as ISO 8601 or epoch-ns.
- Return `Content-Disposition: attachment; filename="{containerName}.csv"` for CSV,
  enabling the browser button to be a plain anchor link.
- The P10 SQL endpoint can back this if the `timeseriesAppId` → container-name mapping
  is resolved server-side — no new TimescaleDB query logic needed.

Then wire two deliverables in parallel:
1. Frontend download button in `frontend/pages/containers/timeseries/[containerId]/index.vue`.
2. `shepard-py` `load_timeseries_reference()` as a thin wrapper over the same endpoint.

Both can be built by different people simultaneously once the endpoint exists.

---

## Round 5: The Killer Feature Neither Has

### Moderator frames the question

Both personas have listed what blocks them. This round asks: what ONE feature,
if it shipped tomorrow, would make both reach for Shepard as their primary system?
The constraint: the feature must be genuinely named in both findings, not
synthesised from hints.

### Senior's Candidate

"The moment that would stop me shrugging: TR-004 already has a red band across
t=7.8–9.2s labeled 'turbopump vibration anomaly — MAD spike, 4.8σ — AI generated.'
I click the annotation, see the linked investigation DataObject, click 'Create
snapshot,' and get a DOI-resolvable, immutable, timestamped record of the entire
campaign state. Those two things — automatic anomaly detection that found something
I would have found myself eventually, plus a one-button citable record — my NFS
and Excel setup provably cannot do. That is the killer. It has to actually work on
the deployed instance, not silently skip when AI1b returns 404."

### Native's Candidate

"My daily driver condition: `import shepard; df = shepard.client().load_timeseries_reference(collection='lumen-dataset', data_object='TR-004', reference='Hot-fire sensors')` returns a DataFrame. That's the test. Until that line works, Shepard is a storage backend I query awkwardly. The anomaly annotations as a column — `df['anomaly_flag']` — would make it an analysis platform."

### The Converging Point

Both personas' candidates share the same three primitives:

1. Channel data out of Shepard in 5 lines — the Native's `load_timeseries_reference()`, the Senior's "CSV I can open in Python."
2. Anomaly intervals already annotated and attached — the Senior's "red band on TR-004," the Native's `df['anomaly_flag']`.
3. A citable, immutable snapshot — the Senior's "one-button DOI," the Native's "citable snapshot ID as a DataFrame metadata field."

### The One Feature (Agreed)

**A `shepard-py` call that returns a DataFrame pre-joined with AI-detected
anomaly intervals, against a named snapshot.**

```python
import shepard

snap = shepard.client().snapshot('lumen-dataset', 'campaign-q3-2024-v1')
df = snap.load_timeseries('TR-004', 'Hot-fire sensors', channels=['vib_fuel_pump'])
# df.columns: ['time', 'g_rms', 'anomaly_label', 'anomaly_confidence']
print(snap.cite())  # "shepard:inst:lumen-dataset:snap:v1 — DOI 10.5072/..."
```

Senior gets the red band and the citation without touching Python — the UI shows
the same thing. Native gets the 5-line load with anomaly columns — no separate
annotation join needed. Both get a citable, reproducible record they can put in
a paper or a quality audit trail.

**Why this is the right answer:** It is the intersection of the primitives that
already exist in design or in partial ship state — AI1b (anomaly detector, backend
shipped), V2 snapshots (shipped), TS-IDc (appId per channel, designed), `shepard-py`
(designed). None of these require new architecture. The gap is assembly and a thin
SDK wrapper. The Senior doesn't need to write Python; the UI does the same thing.
The Native doesn't need the UI; the SDK does the same thing. One feature, two
expressions, shared primitives.

---

## Synthesis Table: 10 Highest-Priority Fixes

| # | Feature / Fix | Who it serves | Effort | User value (1–5) |
|---|---|---|---|---|
| 1 | `GET /v2/timeseries-containers/{appId}/export?format=csv&channels=` — the missing timeseries data-read endpoint that unblocks the download button AND the Python SDK | Both | M | 5 |
| 2 | `CollectionSidebar.vue`: remove `v-if="advancedMode"` from Containers section + add Vitest strict-superset regression test | Both | S | 5 |
| 3 | Replace `referenceIds: List<Long>` in DataObject IO response with `containers: [{kind, appId, name}]` — fix the 404-on-navigation bug | Both | S | 5 |
| 4 | `GET /v2/search/data-objects?attributes.<key>=<value>` — plain attribute filter without ontology requirement | Both | M | 5 |
| 5 | `shepard-py` minimal SDK: `load_timeseries_reference(collection, data_object, reference)` → DataFrame via P10 SQL under the hood | Native + power users | M | 4 |
| 6 | Run `make generate-python`, commit Kiota v2 client output — removes the `.gitkeep` embarrassment from the first-impression | Native | S | 4 |
| 7 | TS-IDa + TS-IDb: mint `appId` on existing Timeseries nodes and expose in v2 responses — prerequisite for `shepard-py` using appId-addressed reads | Both (unblocks 5 and the OPC UA wiring) | M | 4 |
| 8 | Pre-seed TR-004 anomaly annotations as static data in the LUMEN seed script, gated on AI1b deploying them at runtime but always showing something — the killer demo must work on a partial-feature instance | Senior (and every demo audience) | S | 4 |
| 9 | `POST /v2/import/jobs` — wire the import plan executor so the 24h commitId is actually consumed; closes the "import validate works but execute is a stub" gap | Native + any bulk-ingest user | M | 4 |
| 10 | "Who can see this?" summary on DataObject page — one-line ACL render ("Visible to: DLR-ZLP, viewer-group-3") without clicking into the permissions panel | Senior | S | 3 |

**Effort key**: S = under 2 days; M = 1 sprint (2 weeks); L = multiple sprints.

---

## The One Feature

**A `shepard-py` `snapshot.load_timeseries()` call that returns a DataFrame
pre-joined with AI-detected anomaly intervals, backed by a citable, immutable
snapshot.**

Senior expression: the UI already shows the red band and the DOI chip — same
primitives, no Python required.
Native expression: `import shepard; df = snap.load_timeseries('TR-004', 'Hot-fire sensors')` returns `time, g_rms, anomaly_label, anomaly_confidence` columns.

Component primitives already on `main` or in final design: AI1b (backend shipped),
V2 snapshots (shipped), TS-IDc (designed/queued), `shepard-py` (designed).
Gap is assembly + SDK wrapper, not new architecture.

---

## Red Flags for the Team

These are findings both personas independently surfaced and flagged as alarming.
The risk of normalization is highest here — they are in the code, they are visible
every day, and they have apparently not been treated as blocking.

**1. There is no v2 endpoint for timeseries data reads.**

Senior finding: "no download button on the timeseries container page."
Native finding: "There is no v2 endpoint for reading timeseries data at all"
(persona-digital-native.md, line 151). Friction score 4/5 for both load paths.
The P10 SQL endpoint exists but requires prior knowledge of the container name and
schema. This is the platform's primary payload kind. A platform that makes its
primary payload kind inaccessible without schema knowledge is not production-ready
for its stated audience.

**2. `referenceIds` returns internal Neo4j OGM node IDs that cannot be used to
navigate the API.**

Senior finding: "meaningless integers" with no semantic content.
Native finding: spent 45 minutes debugging 404s (persona-digital-native.md, lines
94-98); confirmed in `aidocs/30` as a known documented bug. This is not a
hypothetical API friction issue — it is a confirmed 404 bug on the first navigation
attempt any new API caller makes. It is documented in a design doc. It has not
been fixed. Every new researcher, every MCP agent, every LLM trying to use the
API hits this wall and either debugs or gives up.

**3. The strict-superset invariant ("advanced mode shows everything basic shows,
plus more") is in CLAUDE.md, in the memory notes, and was still violated in
`CollectionSidebar.vue`.**

Senior finding: containers invisible in basic mode (persona-reluctant-senior.md,
line 35). Native finding: "signals quality — if a rule this simple can be broken
without a test catching it, what other assumed-invariants are silently broken?"
The invariant violation is a one-line fix. The absence of a mechanical enforcement
test is the real risk. There is nothing stopping the same violation from happening
again in any other component that conditionally renders on `advancedMode`.

**4. The killer demo degrades silently when AI1b is not deployed.**

Senior finding (persona-reluctant-senior.md, lines 66 and 113): the seed script
calls `best_effort_anomaly_detection()` but silently skips if the AI1b endpoint
returns 404 or 501. A demo Shepard instance without AI1b active shows TR-004 with
no anomaly annotations — the most compelling first impression is absent with no
warning. The native persona did not catch this independently, which means the
degraded state looks normal to a developer who hasn't read the seed script carefully.
This is a team blind spot, not just a missing feature: the demo setup does not fail
loudly when a critical feature is absent; it succeeds quietly and shows nothing.

**5. The Python v2 client directory is a `.gitkeep`.**

Native finding (persona-digital-native.md, lines 149-151): `clients-v2/python-kiota/shepard_v2/`
contains only a `.gitkeep`. The vision doc (`aidocs/42`) says "pip install shepard-client"
is entry point #2. The `make generate-python` command exists but has never been run
and committed. Any researcher who follows the vision doc's instructions for the Python
entry point hits `ModuleNotFoundError` as their first interaction with the v2 surface.
This is a documentation promise that fails on the first command.
