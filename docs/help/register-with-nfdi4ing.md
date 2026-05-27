---
title: Register with NFDI4Ing
description: How to connect your Shepard instance to the NFDI4Ing federation infrastructure in four steps
permalink: /help/register-with-nfdi4ing/
layout: default
audience: operator
---
# Register with NFDI4Ing

Shepard speaks the NFDI4Ing engineering vocabulary
([metadata4ing](https://w3id.org/nfdi4ing/metadata4ing/)) out of the box.
These steps make that compliance visible to the federation.

**You need:** admin access to the Shepard CLI and a public URL for your instance.

---

## Step 1 — Confirm the m4i bundle is on

```sh
shepard-admin semantic ontologies list \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

The `metadata4ing` row should show `ENABLED = yes`. If not:

```sh
shepard-admin semantic ontologies enable metadata4ing \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

To pull the full ~150-term vocabulary (instead of the bundled stub):

```sh
shepard-admin semantic refresh-ontologies --bundles=metadata4ing \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

---

## Step 2 — Enable the Helmholtz Unhide feed (Helmholtz institutes only)

Skip this step if your institute is not a Helmholtz member.

```sh
shepard-admin unhide enable \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
shepard-admin unhide set-contact-email ops@YOUR-INSTITUTE.dlr.de \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
shepard-admin unhide rotate-harvest-key \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

Register the feed URL and harvest key at:
[https://unhide.helmholtz-metadaten.de/dataprovider/register](https://unhide.helmholtz-metadaten.de/dataprovider/register)

Feed URL to register: `https://YOUR-SHEPARD/v2/unhide/feed.jsonld`

---

## Step 3 — Register with the NFDI4Ing Terminology Service

Contact `terminology@nfdi4ing.de` with:
- Your institute name and DFN/NFDI affiliation
- The IRI prefix of any custom vocabularies you have uploaded
- Your public SPARQL endpoint, if you operate a standalone triple store

Automated self-registration via the TS API is on the roadmap but not yet
shipped. This step is currently manual.

---

## Step 4 — Archetype Doris (HPMC campaigns only — forward-looking)

If you run HPMC campaigns (high-rate DAQ, AFP robot telemetry, similar),
the NFDI4Ing Archetype Doris will eventually harvest your data once the
HPMC sub-ontology bundle (M4I-f) ships. Hold off on this step until that
bundle is available. Watch the
[NFDI4Ing federation reference](/reference/nfdi4ing-federation/) for updates.

---

## What works today vs. what's coming

| Capability | Today |
|---|---|
| m4i vocabulary terms in annotation picker | ✓ |
| m4i triples in Unhide feed | ✓ |
| PROV activities in m4i JSON-LD | ✓ |
| Automated TS sync | ✗ roadmap |
| Archetype Doris integration | ✗ roadmap (after M4I-f) |

---

For the full reference including troubleshooting, see
[NFDI4Ing federation (reference)](/reference/nfdi4ing-federation/).
