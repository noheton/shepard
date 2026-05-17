---
layout: default
title: Monitor Collection activity
permalink: /help/monitor-collection-activity/
---

# See what's been happening in a Collection

Every Collection page in shepard surfaces an **Activity** panel —
the at-a-glance answer to "what's been going on in here lately?"

## Where it lives

Open any Collection (`/collections/{id}` in the UI). Scroll past
the **Description**, **Semantic Annotations**, **Attributes**, and
**Lab Journal** panels. The **Activity** expansion panel sits at
the bottom of the page; expand it to render the dashboard.

You only see the panel if you have **Read** permission on the
Collection. The backend enforces this server-side — a `403` is
returned to anyone who tries to hit the underlying
`GET /v2/provenance/stats?scope=collection&id=…` endpoint without
the role.

## What it shows

Three panels stacked vertically:

1. **Header strip.** Total activities + distinct-contributor count
   over the chosen window, plus a small horizontal stacked bar
   showing the action-kind histogram (POST / PUT / PATCH / DELETE
   coloured green / blue / grey / red). Hover any slice for the
   exact count.
2. **Sparkline.** A bar chart of bucketed activity counts. shepard
   automatically picks **daily** buckets for windows ≤ 90 days,
   **weekly** buckets for longer windows. Hover any bar for the
   bucket's date and count.
3. **Cumulative overlay.** An orange line layered on top of the
   sparkline showing the *running total* of activities — useful
   for the "is uptake growing or flat?" question.

## Picking a time range

The dropdown in the panel header offers four presets:

| Preset | Bucket width | When to use |
|---|---|---|
| **Last 7 days** | daily | sprint review, post-incident "who changed what" |
| **Last 30 days** *(default)* | daily | monthly review |
| **Last 90 days** | daily | quarter snapshot |
| **Last year** | weekly | annual contribution shape, dormancy check |

Changing the preset re-fetches the stats and re-renders the
panels in place. No page reload.

## "No recorded activity"

If the window shows zero rows, shepard prints a hint:

> *"No recorded activity in the selected window. Older activity
> may have been pruned — shepard keeps provenance rows for 2 years
> by default."*

The retention window is operator-configurable via
`shepard.provenance.retention-days` (negative = keep-forever).
A truly silent Collection (no recent writes, no recent reads if
read-capture is opted-in) is also a possibility — confirm by
checking `updatedAt` on the Collection itself.

## What's *not* in the dashboard

- **Read activity** — by default, shepard does not log `GET`
  requests as `:Activity` rows. Operators with compliance needs
  can flip `shepard.provenance.capture-reads=true`. Without it,
  the dashboard only reflects mutations (POST / PUT / PATCH /
  DELETE).
- **Request bodies / headers** — for GDPR and audit reasons,
  `:Activity` rows never echo user payload or auth headers. The
  on-row `summary` is server-generated from the method + target
  metadata.
- **Permission diffs.** A permission change generates one
  `actionKind=UPDATE / targetKind=Permissions` row — but the
  *who-gained-what* diff lives in F3's tamper-evident security
  audit trail (`aidocs/24 §3.7`), not in the casual-user
  dashboard.

## Programmatic access

The dashboard is a thin Vue wrapper over the same JSON endpoint
your scripts can hit directly:

```
GET /v2/provenance/stats?scope=collection&id={collectionAppId}&since={epochMs}&until={epochMs}
```

Append `Accept: application/prov+json` to the per-activity
endpoint for a [W3C PROV-JSON](https://www.w3.org/Submission/prov-json/)
serialisation suitable for RO-Crate exports, OpenLineage
adapters, and federated-provenance catalogues:

```
GET /v2/provenance/activities?targetAppId={collectionAppId}
Accept: application/prov+json
```

For **JSON-LD** export — feeds directly into Apache Jena, RDF4J,
or any other SPARQL store — switch the Accept header. Plain
PROV-O JSON-LD:

```
GET /v2/provenance/activities?targetAppId={collectionAppId}
Accept: application/ld+json
```

For the **NFDI4Ing metadata4ing** flavour (engineering-research
subtypes on top of PROV-O — `m4i:ProcessingStep`,
`m4i:InvestigatedObject`, `m4i:Person`):

```
GET /v2/provenance/activities?targetAppId={collectionAppId}
Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"
```

`profile=metadata4ing` works as a short form. See the full
[Provenance reference](../reference/provenance.md) for the wire
shapes and field mappings.

See [aidocs/55](https://gitlab.com/dlr-shepard/shepard/-/blob/main/aidocs/55-provenance-and-activity-overhaul.md)
for the full design + [aidocs/64](https://gitlab.com/dlr-shepard/shepard/-/blob/main/aidocs/64-provenance-architecture.md)
for the m4i mapping rules.
