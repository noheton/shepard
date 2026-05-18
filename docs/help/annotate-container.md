---
title: Annotate a container with semantic tags
description: How to attach ontology terms to a timeseries / file / structured-data container so its purpose and provenance are searchable
permalink: /help/annotate-container/
layout: default
---

# Annotate a container with semantic tags

shepard lets you attach **semantic annotations** — ontology terms with
both a property and a value — to any container. The annotations are
visible on the container detail page and queryable through search.

This is the right place to tag *the container itself*:

- "This is the LUMEN-3 telemetry stream."
- "Data was acquired with a Brüel & Kjær LAN-XI front-end."
- "Belongs to the rocket-engine hot-fire test campaign Q3 2024."

For tagging an individual *channel inside* a timeseries container,
use the per-row annotation chips on the channel preview. For tagging
a *reference* (the link between a data object and a container), use
the chip-row on each row of the Data References panel.

## The flow

1. Open the container detail page (**Containers → Timeseries / Files /
   Structured Data**, then click the container name).
2. Find the **Semantic Annotations** expansion panel — it's open by
   default on the timeseries container, collapsed on the others.
3. Click the **+** button in the panel's title bar.
4. The Add Annotation dialog opens with two fields:
   - **Property** — what *kind* of relationship this is (e.g. "is
     about", "has instrument", "belongs to").
   - **Value** — the thing being asserted (e.g. "LUMEN-3", "B&K
     LAN-XI front-end").
5. Start typing in either field. shepard's term autocomplete suggests
   matches from the loaded ontologies (PROV-O, Dublin Core,
   schema.org, QUDT, metadata4ing, the shepard-experiment domain
   ontology, …). You can also paste a raw IRI if you have one.
6. Pick a property and a value, click **Add**. The annotation appears
   as a chip in the panel.

## Removing an annotation

If you have Write permission on the container, hover over an
annotation chip — an `x` appears. Click it to remove.

## Why annotate containers?

The most common discovery flow in research workflows is:

1. *"Which raw datasets came from the LUMEN-3 campaign?"*
2. *"Of those, which collections have a derived lab-journal entry?"*
3. *"Of those, which channels carry pressure (and not just
   temperature)?"*

The first question is answered by **container annotations** — you
tag the timeseries container once with "campaign: LUMEN-3", and
every search that finds that tag surfaces the container. Without
container annotations you'd have to tag every individual
TimeseriesReference, which is N times more work.

Container annotations live in the same n10s knowledge graph as the
existing collection / data-object / reference annotations, so they
participate in the same SPARQL and term-search surfaces.

## API equivalent

External tools can also attach annotations:

```bash
# List existing annotations on a timeseries container
curl -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/timeseries-containers/42/annotations

# Add a new one
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "propertyIri": "http://purl.obolibrary.org/obo/IAO_0000136",
    "propertyName": "is about",
    "propertyRepositoryId": 1,
    "valueIri": "http://example.dlr.de/lumen3#campaign",
    "valueName": "LUMEN-3 campaign",
    "valueRepositoryId": 1
  }' \
  https://shepard.example.dlr.de/v2/timeseries-containers/42/annotations
```

See [container annotations (reference)](/reference/container-annotations/)
for the full wire shape and the equivalent endpoints for File and
Structured-Data containers.
