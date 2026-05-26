---
layout: default
title: Showcase
description: Ten-stop tour of shepard's feature surface against a synthetic LUMEN-inspired hot-fire campaign.
audience: visitor
---
# Showcase tour — LUMEN-inspired hot-fire campaign

> **Disclaimer.** Everything below is **synthetic**. The Collection is loosely
> inspired by the public description of DLR's
> [LUMEN](https://www.dlr.de/en/ra/research-transfer/projects/project-archive/liquid-upper-stage-demonstrator-engine-lumen)
> demonstrator at Lampoldshausen, but contains no real DLR or LUMEN
> measurements. Numerical values are deterministic outputs of
> `examples/lumen-showcase/data/generate.py` (`numpy.random.default_rng(2024)`).

This page is a ten-stop tour of shepard's feature surface against the
seeded `LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)`
Collection. The seed lives at
[`examples/lumen-showcase/`](../examples/lumen-showcase/) and each stop
links to the matching Python snippet a reader can paste into a REPL or
Jupyter cell.

Screenshot slots use placeholder anchors that a Playwright job can fill
in later — the same convention used by the rest of the docs site (the
generation pipeline is not part of this PR; the placeholders are
markers, not assets).

---

## 1. Browse the campaign

Open the seeded Collection. The tree view shows seven test runs as
siblings (`TR-001` … `TR-007`), the post-anomaly investigation
DataObject nested under `TR-004`, and one TimeseriesReference,
FileReference and StructuredDataReference per fired run. Read the
Collection's `description` attribute for the synthetic-data
disclaimer.

<figure class="screenshot-placeholder" data-target="stop-01-collection-tree">
  <figcaption>Screenshot: stop 01 — Collection tree with the seven test runs and the analysis sub-tree — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.**

```python
from shepard_client import ApiClient, Configuration, SearchApi, CollectionSearchBody, CollectionSearchParams
client = ApiClient(Configuration(host=HOST, api_key={"apikey": APIKEY}))
res = SearchApi(client).search_collections(CollectionSearchBody(searchParams=CollectionSearchParams(
    query='{"property":"name","value":"LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)","operator":"eq"}'
)))
coll = res.results[0]
```

## 2. Open a clean run

Click `TR-001`. Its TimeseriesReference (`tr-001-sensors`) bundles ten
sensor channels. Render `pc_chamber` and `vib_fuel_pump` together. Both
sit cleanly inside their nominal envelopes (chamber pressure ~90 bar
at steady_state; fuel-pump vibration ≤ 4 g rms throughout). Use this
plot as the "this is what nominal looks like" reference.

<figure class="screenshot-placeholder" data-target="stop-02-tr001-clean">
  <figcaption>Screenshot: stop 02 — TR-001 sensor view with vib_fuel_pump in nominal envelope — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.** See cell 2 of `notebooks/anomaly-analysis.ipynb`.

## 3. Spot the anomaly

Open `TR-004`. Render `vib_fuel_pump`. A 0.5-second sustained event at
mid ramp_up (t ≈ 8 s) climbs to ~12 g rms — three times the envelope —
and then settles back. The rest of the burn looks healthy, which is
the canonical bearing-precursor signature: the pump completed
steady_state and the operator only flagged it after reviewing the
trace.

<figure class="screenshot-placeholder" data-target="stop-03-tr004-spike">
  <figcaption>Screenshot: stop 03 — TR-004 vib_fuel_pump showing the 12 g rms ramp-up spike at t=8s — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.** Re-use cell 2 of the notebook with `data_object_id =
TR-004.id`.

## 4. Read the debrief

`TR-004`'s lab-journal entry — written immediately after shutdown —
captures the operator's hypothesis verbatim: *"Vibration spike on
fuel-turbopump observed during ramp_up at t=8s, sustained ~0.5s
peaking 12 g rms. Engine completed steady_state nominally; suspect
bearing precursor. Recommending teardown."* The journal entry is the
narrative companion to the timeseries trace.

<figure class="screenshot-placeholder" data-target="stop-04-tr004-journal">
  <figcaption>Screenshot: stop 04 — TR-004 lab-journal debrief with operator hypothesis — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.**

```python
from shepard_client import LabJournalEntryApi
for e in LabJournalEntryApi(client).get_lab_journals_by_collection(data_object_id=tr4.id):
    print(e.journal_content)
```

## 5. Follow the investigation

The `Anomaly Investigation — TR-004 Fuel Turbopump` DataObject is
parented to TR-004 and listed as a predecessor of TR-006. That single
link makes the campaign narrative legible without R3 lineage:
TR-004 → investigation → bearing replaced → TR-006 re-test → TR-007
confirmation. The investigation's own lab-journal entry records the
finding (*"Thrust bearing inner race shows incipient spalling.
Replaced. Re-balance verified. Cleared for re-test."*) and the
`severity=HIGH` attribute makes it discoverable via search.

<figure class="screenshot-placeholder" data-target="stop-05-investigation-graph">
  <figcaption>Screenshot: stop 05 — Investigation DataObject linked TR-004 → analysis → TR-006 — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.**

```python
from shepard_client import DataObjectApi
do_api = DataObjectApi(client)
all_dos = do_api.get_all_data_objects(coll.id)
investigation = next(d for d in all_dos if d.name.startswith("Anomaly Investigation"))
tr6 = next(d for d in all_dos if d.name == "TR-006")
assert investigation.id in tr6.predecessor_ids
```

## 6. Annotations

Every fired-run TimeseriesReference is tagged with the seven
phase-of-burn IRIs (`precool` / `ignition` / `ramp_up` /
`steady_state` / `throttle` / `shutdown` / `purge`) and TR-004 carries
an extra `dlr:vibration-anomaly` annotation. The IRIs are
project-local placeholders under
`https://shepard.dlr.de/showcase/lumen-inspired#` so they're easy to
swap for a real ontology later.

<figure class="screenshot-placeholder" data-target="stop-06-annotations">
  <figcaption>Screenshot: stop 06 — Phase-of-burn annotation overlay on TR-004 timeseries — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.**

```python
from shepard_client import SemanticAnnotationApi
SemanticAnnotationApi(client).get_data_object_annotations(coll.id, tr4.id)
```

## 7. Search

Search the Collection for entities with `severity=HIGH`. Exactly one
hit comes back: the investigation DataObject. Search is also the
fastest way to locate a specific test engineer's runs (`test_engineer`
attribute) or the runs that fired vs the hold day (`is_fired`).

<figure class="screenshot-placeholder" data-target="stop-07-search-severity">
  <figcaption>Screenshot: stop 07 — Search results for severity=HIGH — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.**

```python
from shepard_client import SearchApi
SearchApi(client).search_data_objects(...)  # body: attribute name=severity, value=HIGH, operator=eq
```

## 8. Permissions

The seed creates the Collection PUBLIC so the showcase is explorable
out-of-the-box. Three logical principal roles map onto the seed:

| role           | intent                                       |
| -------------- | -------------------------------------------- |
| campaign_lead  | owner — creates / edits everywhere           |
| analyst        | writer — edits the analysis sub-tree only    |
| reviewer       | reader — read-only across the whole campaign |

The seed creates two API keys (`campaign_lead_key` and
`reviewer_key`); group-based RBAC for `analyst` is left as the
operator's responsibility because user-group setup is admin-only.
Once wired, the analyst's key fails on `PUT /collections/.../TR-004`
but succeeds on `PUT /collections/.../investigation`. The
`reviewer_key`'s name carries an intended `validUntil` (90 days from
seed time) — see seed.py for the L5 placeholder.

<figure class="screenshot-placeholder" data-target="stop-08-permissions-matrix">
  <figcaption>Screenshot: stop 08 — Permissions matrix for the analyst principal — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

## 9. Versioning

The seed best-effort creates three Collection versions: `v0` (campaign
in progress), `v1` (campaign complete; anomaly open), `v2`
(post-anomaly addendum). Switch the version selector from `v1` to
`v2` and the investigation's `closed_at` attribute appears (set
deterministically when the seed is run a second time with
`--close-anomaly`); the TR-006 lab-journal entry "Bearing replacement
confirmed effective" appears too. Versioning is feature-toggled in
the backend; on stacks where the toggle is off the seed logs `SKIP`
and this stop is omitted.

<figure class="screenshot-placeholder" data-target="stop-09-versions-diff">
  <figcaption>Screenshot: stop 09 — Collection version v1 vs v2 with the journal addendum — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

## 10. Selective export

The companion notebook builds a **proposed R2c-shape**
`ExportSelection` body that picks the TR-004 + investigation sub-tree
only, includes annotations and lab-journal references, but redacts
`LAB_JOURNAL_CONTENT` so the bundle can be shared with an external
reviewer without leaking the operator's debrief. The current
`/collections/{id}/export` endpoint is GET-only, so the notebook
falls back gracefully and prints the selection record next to the
RO-Crate manifest. When R2c lands, the notebook needs no changes.

<figure class="screenshot-placeholder" data-target="stop-10-export-selection">
  <figcaption>Screenshot: stop 10 — Selective export ExportSelection JSON body and resulting manifest — placeholder. Replace with Playwright capture once R5 lands.</figcaption>
</figure>

**Try it.** Cells 7-8 of `notebooks/anomaly-analysis.ipynb`.

---

## Bonus stops — fork-only features

The seed also exercises capabilities that don't exist in upstream
shepard 5.2.0. They're not numbered into the ten-stop tour because
they're additive, but new visitors should know about them.

### Container annotations

Open `lumen-inspired-sensors` (the timeseries container behind the
TR-001…TR-007 references). The detail page shows a **Semantic
Annotations** panel for the container itself — distinct from the
per-channel annotations and the per-reference annotations. Use it
to tag the whole container with "campaign = LUMEN-3", "instrument =
B&K LAN-XI front-end", or any other ontology term. See
[container annotations (reference)](/reference/container-annotations/).

### Server-enforced safe-delete

Click the trash icon on the same container. The confirm dialog
shows a yellow warning naming the data objects that reference the
container (the in-UI client-side check). Behind the scenes the
frontend calls `DELETE /v2/timeseries-containers/{id}?force=true`;
external clients hitting that endpoint without `?force=true` get
a `409 Conflict` with `{referenceCount, sampleDataObjectAppIds}`.
See [container safe-delete (reference)](/reference/container-safe-delete/).

### Anomaly detection in the UI

Open TR-004's timeseries reference (`tr-004-sensors`). The
**Anomalies & intervals** section near the bottom of the page has a
**Run anomaly detection** button. One click invokes the AI1b
rolling-median MAD detector on the server; the detected intervals
appear inline as `aiGenerated=true` annotations with confidence
scores.

### SQL over HTTP

The Git References panel on TR-006 points at
[`examples/lumen-showcase/notebooks/sql-channel-summary.py`](../examples/lumen-showcase/notebooks/sql-channel-summary.py).
The script demonstrates the P10 curated-SQL endpoint
`POST /v2/sql/timeseries` end-to-end (per-channel min / max / mean /
stddev over the whole campaign in one request).

### Instance identity (ROR)

Open **About → Organization** in the app bar's overflow menu. The
demo instance is preseeded with DLR's ROR id (`04bwf3e34`); the page
fetches live details from ror.org and shows the organisation name,
location, established date, and primary website. An admin sets the
ROR id once via `PATCH /v2/admin/instance/ror` and every authenticated
user can read it through `GET /v2/instance/identity`.

### TS-IDc — single-key channel addressing (AFP thermal trail demo)

A second demo Collection — **"TS-IDc demo — AFP TCP thermal trail"**
— shows the new channel-identity endpoint in action. Open it, click the
DataObject, and open its TimeseriesContainer. The v2 channel listing at
`GET /v2/timeseries-containers/{id}/channels` returns each channel with
a stable UUID (`shepardId`) alongside the legacy 5-tuple. The data
endpoint then accepts that UUID directly:

```
GET /v2/timeseries-containers/{id}/channels/{shepardId}/data
    ?start=…&end=…&downsample=lttb&max_points=200
```

LTTB (Largest-Triangle-Three-Buckets) downsampling is opt-in via
`?downsample=lttb` — the server clips the raw trace to `max_points`
visually-faithful samples, typically reducing 1000-point payloads to
50 points for chart rendering without losing the peak/trough shape.
The seed script at `examples/ts-id-demo/seed.py` seeds 4 channels
(AFP tool-centre-point XYZ + pyrometer head temperature, 25 Hz for
40 s) and exercises both endpoints end-to-end with `--demo`.

### Personal landing page

Sign in and click the Shepard logo to reach the personal home page.
The digest shows collections you can access sorted by last-changed,
each with a live sparkline of its data-object count over time. The
"Recently changed" panel and the "Watched" panel use the same endpoint
(`GET /v2/users/me/collections`) and update automatically.

### Collection hero image + contributor badges

Open any Collection. If a hero image was uploaded (`PATCH /v2/collections/{id}/hero`),
it fills the top banner. Avatar badges for every contributor who has
written to the Collection appear beside the title. Click any badge to
reach that contributor's profile. The completeness score widget (below)
lives in the same header row.

### Metadata completeness score

The Collection landing page carries a **Completeness** badge
(RDM-005). It reflects how many of the recommended FAIR metadata
fields (`name`, `description`, `license`, `accessRights`, contact
information) are populated. Hover the badge for a per-field
breakdown and a direct link to the missing fields. Target green
(≥ 80 %) before publishing a Collection via the Unhide/Databus
plugin.

### License and access rights

Every Collection and DataObject has `license` and `accessRights` fields
(LIC1). Set them via the detail-page edit form or via the API:
`PATCH /v2/collections/{appId}` / `PATCH /v2/data-objects/{appId}`.
The metadata completeness score counts both fields; the planned
export plugin will embed them into DataCite and schema.org
records automatically.

### Payload version history

Open any FileContainer, StructuredDataContainer, or
TimeseriesContainer and click **History** in the tab bar. The
version viewer (PV1a/PV1b) shows every payload snapshot with who
uploaded it, when, and the payload size delta. You can download
any earlier version directly. The diff viewer for structured data
(JSON payloads) shows field-level changes inline.

### "Cite this dataset" card

Click **Cite** on a Collection landing page to open the citation card
(RDM-001). It generates a formatted APA/BibTeX/DataCite citation block
from the Collection's metadata. The DataCite record is the basis for the
Helmholtz Databus publish flow; if the Collection has no DOI yet, the card
shows the `appId` UUID as the provisional identifier. Copy-to-clipboard
works with one click.

### View recipes — 3D visualization playground

Navigate to `/shapes/render` (or follow the **View Recipes** link in the
admin sidebar). The VIEW_RECIPE playground accepts a JSON template that
maps timeseries channel IDs to spatial axes and a colour map. Paste in:

```json
{
  "kind": "VIEW_RECIPE",
  "axes": {"x": "tcp_x-shepardId", "y": "tcp_y-shepardId", "z": "tcp_z-shepardId"},
  "value": "head_temp-shepardId",
  "colormap": "inferno"
}
```

and hit **Render** to see the AFP TCP thermal trail as a colour-mapped
3D helical path (Trace3D, powered by Three.js). The channel IDs above
are the `shepardId` values from the TS-IDc demo Collection. This is the
first V_RECIPE implementation and the acceptance test for the M1
milestone (TS-IDc + shapes/render + Trace3D).

---

## Where to next

- The seed and its disclaimer:
  [`examples/lumen-showcase/README.md`](../examples/lumen-showcase/README.md).
- The deeper-analysis hook (anomaly walkthrough, MAD detector, export
  body):
  [`examples/lumen-showcase/notebooks/anomaly-analysis.ipynb`](../examples/lumen-showcase/notebooks/anomaly-analysis.ipynb).
- The deterministic generator:
  [`examples/lumen-showcase/data/generate.py`](../examples/lumen-showcase/data/generate.py).
