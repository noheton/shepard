---
layout: default
title: MFFD digital-thread tour
permalink: /help/mffd-tour/
audience: user
stage: deployed
last-stage-change: 2026-07-02
---

# MFFD digital-thread tour

_A 10-minute walk through the Multi-Functional Fuselage Demonstrator data in
shepard._

The MFFD (Multi-Functional Fuselage Demonstrator, JEC World Innovation Award
2025) upper shell was manufactured at DLR ZLP Augsburg in a chain of robotic
process steps — AFP tapelaying, stringer/bridge/spot welding, NDT
thermography. Every stop below is real process data from that campaign,
imported into shepard: ~13 500 DataObjects, hundreds of sensor channels,
welding videos, thermography frames, and the robot cell's kinematic model.

**Prereq:** sign in at <https://shepard.nuclide.systems> (any demo account,
e.g. `bob`). Every link below is clickable once signed in.

## Stop 1 — The gallery

<https://shepard.nuclide.systems/collections>

All MFFD process-step collections at a glance — names, descriptions, and
per-collection DataObject counts (8483 AFP tracks, 1031 bridge-welding
executions, 744 thermography frames, …). This is the "what's in the box"
view a new colleague sees first.

## Stop 2 — AFP tapelaying at scale

<https://shepard.nuclide.systems/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df>

The FAIR landing page of the biggest collection: 8483 track DataObjects from
the real cube3 export, with a citation card (plain text / BibTeX / RIS / CSL),
a DMP download, RO-Crate export, a Regulatory Evidence Pack, and an honest
metadata-completeness score. The sidebar tree lazily loads the full daily-log
hierarchy without breaking a sweat.

## Stop 3 — One track, full provenance

<https://shepard.nuclide.systems/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df/dataobjects/019ed617-238e-7f97-a1c6-edb93be6e9bc>

Track 9 (Run 29995): a single AFP layup run as a citable dataset — semantic
annotations linking it back to its cube3 origin, attributes, its timeseries
reference, and the provenance activity trail. Everything you see here is
addressable by one stable UUID.

## Stop 4 — The track's sensor data

<https://shepard.nuclide.systems/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df/dataobjects/019ed617-238e-7f97-a1c6-edb93be6e9bc/timeseriesereferences/019efae9-6084-71f9-8fe1-d1f820b936d6>

The TimeseriesReference scopes the shared container down to this track's
~190 channels and its exact time window. Pick channels, plot them, and jump
into the view recipes (Trace3D below) — all pre-scoped, no manual time-window
typing.

## Stop 5 — The channel inventory

<https://shepard.nuclide.systems/containers/timeseries/019ede2a-60ec-7ac1-899d-3fe4c6263cbb>

The AFP tapelaying TimeseriesContainer itself: 192 distinct channel
identities — robot TCP positions, tape temperatures, consolidation forces,
motor temperatures, cut counters. This is the raw signal richness a single
manufacturing cell produces.

## Stop 6 — Trace3D: the thermal trail

<https://shepard.nuclide.systems/shapes/render?roles=eyJ4Ijp7Im1lYXN1cmVtZW50IjoibW0iLCJkZXZpY2UiOiJSMjAiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlgiLCJmaWVsZCI6InZhbHVlIn0sInkiOnsibWVhc3VyZW1lbnQiOiJtbSIsImRldmljZSI6IlIyMCIsImxvY2F0aW9uIjoiTUZaIiwic3ltYm9saWNOYW1lIjoiWSIsImZpZWxkIjoidmFsdWUifSwieiI6eyJtZWFzdXJlbWVudCI6Im1tIiwiZGV2aWNlIjoiUjIwIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJaIiwiZmllbGQiOiJ2YWx1ZSJ9LCJ2YWx1ZSI6eyJtZWFzdXJlbWVudCI6ImNlbHNpdXMiLCJkZXZpY2UiOiJNVExIIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJUZW1wZXJhdHVyVGFwZSIsImZpZWxkIjoidmFsdWUifX0=&containerAppId=019ede2a-60ec-7ac1-899d-3fe4c6263cbb&renderer=trace-3d&startNs=1670425854562000000&endNs=1670425884562000000&colormap=inferno>

The robot TCP path (X/Y/Z) rendered in 3D and coloured by tape temperature —
30 seconds of layup as a heat trail in space. A view recipe binds channels to
axes; no plotting code, no CSV export, no notebook.

## Stop 7 — Trace3D: consolidation force

<https://shepard.nuclide.systems/shapes/render?roles=eyJ4Ijp7Im1lYXN1cmVtZW50IjoibW0iLCJkZXZpY2UiOiJSMjAiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlgiLCJmaWVsZCI6InZhbHVlIn0sInkiOnsibWVhc3VyZW1lbnQiOiJtbSIsImRldmljZSI6IlIyMCIsImxvY2F0aW9uIjoiTUZaIiwic3ltYm9saWNOYW1lIjoiWSIsImZpZWxkIjoidmFsdWUifSwieiI6eyJtZWFzdXJlbWVudCI6Im1tIiwiZGV2aWNlIjoiUjIwIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJaIiwiZmllbGQiOiJ2YWx1ZSJ9LCJ2YWx1ZSI6eyJtZWFzdXJlbWVudCI6Im5ld3RvbiIsImRldmljZSI6Ik1UTEgiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlRhcGVGb3JjZV9UYXBlQWN0Rm9yY2VbMV0iLCJmaWVsZCI6InZhbHVlIn19&containerAppId=019ede2a-60ec-7ac1-899d-3fe4c6263cbb&renderer=trace-3d&startNs=1674117039068000000&endNs=1674117099068000000&colormap=inferno>

Same idea, different question: the TCP path coloured by tape consolidation
force during a Track-9 layup window. The force applied to each ply, visible
along the actual path it was applied on — the kind of view a process engineer
sketches on a whiteboard, live from the recorded data.

## Stop 8 — NDT thermography heatmap

<https://shepard.nuclide.systems/shapes/render?renderer=thermography&fileReferenceAppId=019ed593-0db3-7185-b863-fcc75379d412>

An OTvis thermography frame from the NDT step, streamed straight from the
uploaded instrument file and rendered as an interactive amplitude/phase
heatmap. The file *is* the reference — no conversion step, no exported PNG.

## Stop 9 — Spot welding, machine-readable

<https://shepard.nuclide.systems/collections/019ed455-67f7-7725-bf2d-7cd1b67aca9f/dataobjects/019ed586-87df-7710-a94c-c5fd60108d20>

A spot-welding scope project (.svdx). shepard's file-format plugin parsed the
instrument file on upload and materialised 213 `urn:shepard:svdx:*` semantic
annotations on the file reference — welding parameters as queryable facts,
not bytes in a blob.

## Stop 10 — The welding video

<https://shepard.nuclide.systems/collections/019edb10-c107-7473-ae28-ffc592aba860/dataobjects/019f129e-c45e-703a-97de-079cb19d1052/videostreamreferences/019f129f-aa8b-70a6-b908-45e9918d7531>

Stringer welding on camera: a 4K HEVC upload (1.04 GB) that plays in the
browser because shepard transcoded an h.264 proxy on the side. Scrub the
timeline — the metal surface and weld head are the same seconds the sensor
channels recorded.

## Stop 11 — The robot, animated

<https://shepard.nuclide.systems/scene-graphs/play/019f16d8-2fc7-76e8-b066-f713e2fb713d>

The KUKA KR210 R2700/2 from the MFFD AFP cell as a live kinematic model —
the URDF file reference plus six joint telemetry channels, bound by a
mapping-recipe template. Press play and the KUKA-orange arm re-runs the
recorded trajectory. (Index of all recipes:
<https://shepard.nuclide.systems/scene-graphs>.)

## Stop 12 — The vocabularies underneath

<https://shepard.nuclide.systems/semantic/vocabularies>

The controlled vocabularies powering every annotation you saw: CHAMEO,
DataCite, Dublin Core, PROV-O, Material OWL, metadata4ing, schema.org, SKOS
and more — seeded into the internal semantic store so annotation keys are
terms, not typos.

## Stop 13 — Find it again

<https://shepard.nuclide.systems/search?q=tapelaying>

Search across the instance brings you back to any of it — the entry point
your colleague uses next week when they only remember the word "tapelaying".

## Where to go deeper

- **In-app help:** <https://shepard.nuclide.systems/help> — task pages for
  uploading, annotating, plotting, SPARQL, templates, and more.
- [Timeseries plotting]({{ '/help/timeseries-plotting/' | relative_url }}),
  [Render a view]({{ '/help/render-a-view/' | relative_url }}),
  [Query with SPARQL]({{ '/help/query-with-sparql/' | relative_url }}),
  [Upload and annotate video]({{ '/help/upload-and-annotate-video/' | relative_url }}),
  [Importing from DLR cube3]({{ '/help/importing-from-dlr-cube3/' | relative_url }})
- **Reference docs:** the per-primitive pages under
  [Reference]({{ '/reference/' | relative_url }}) cover every payload kind and
  endpoint used on this tour.
