---
title: Make your Shepard data discoverable via NFDI4Ing
description: How to enable NFDI4Ing federation so your research data appears in the national engineering science discovery infrastructure
permalink: /help/register-with-nfdi4ing/
layout: default
audience: user
---

# Make your data discoverable via NFDI4Ing

NFDI4Ing is Germany's national research data infrastructure for engineering sciences.
When your Shepard instance is connected to NFDI4Ing, Collections you publish become
searchable through the Helmholtz Knowledge Graph and eventually through the
NFDI4Ing Terminology Service — making your datasets findable by researchers across
German engineering institutes without any extra effort on your part.

---

## Prerequisites

Before you can publish, your **instance administrator** must:

1. Enable the `metadata4ing` ontology bundle (the vocabulary that NFDI4Ing understands).
2. Activate the Helmholtz Unhide plugin and point it at this instance.
3. Register this instance as a data provider with Helmholtz Unhide.

Ask your admin to follow the steps in
[NFDI4Ing federation reference]({{ '/reference/nfdi4ing-federation/' | relative_url }})
before you continue here.

---

## Step 1 — Flag a Collection for publication

Open the Collection you want to publish. In the **Properties** panel (accessible from
the Collection overview page via the settings icon), toggle **Publish to Helmholtz
Knowledge Graph** to on.

Only Collections with this flag set will appear in the harvest feed.

---

## Step 2 — Mint a persistent identifier

A persistent identifier (PID) is required before a Collection can appear in Unhide.
From the Collection overview, open **Publish** and click **Mint PID**. Shepard will
register a Handle or DOI for the Collection (depending on your instance's KIP1a
configuration).

Once minted, the PID appears on the Collection page and is embedded in every harvest
entry for this Collection.

---

## Step 3 — Wait for the next harvest

Helmholtz Unhide harvests registered instances on a schedule (typically daily).
After the next harvest, your Collection's metadata — name, description, creator,
date, and provenance activities — will appear at
`unhide.helmholtz-metadaten.de` under your institute's entry.

You can verify the feed entry immediately by checking:

```
GET /v2/unhide/feed.jsonld
```

Your Collection should appear in the `@graph` array with a `dct:identifier` matching
the PID you minted in step 2.

---

## What gets published

When harvested, Unhide receives:

- Collection name, description, and creator (from your Shepard profile)
- Creation date and modification date
- The minted PID as `dct:identifier`
- Up to 5 recent provenance activities as `m4i:ProcessingStep` entries (showing what
  happened to the data — uploads, annotations, status changes)
- A `dcat:landingPage` link back to this Shepard instance

Your raw data files, timeseries channels, and annotations are **not** sent — only
the metadata shell.

---

## More details

For admin-level configuration and troubleshooting, see the
[NFDI4Ing federation reference]({{ '/reference/nfdi4ing-federation/' | relative_url }}).
