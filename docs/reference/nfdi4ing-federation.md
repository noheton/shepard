---
audience: admin
layout: default
title: NFDI4Ing federation
description: Operator reference for connecting a Shepard instance to NFDI4Ing discovery infrastructure via the metadata4ing ontology and Helmholtz Unhide harvester
permalink: /reference/nfdi4ing-federation/
---

# NFDI4Ing federation reference

**Feature ID:** M4I-e  
**Design doc:** `aidocs/semantics/94-metadata4ing-integration-design.md §4.5`  
**API surface:** `/v2/admin/semantic/ontologies/*`, `/v2/unhide/feed.jsonld`

---

## What NFDI4Ing is and why it matters

**NFDI4Ing** (National Research Data Infrastructure for Engineering Sciences) is a
DFG-funded consortium that builds shared data infrastructure for German engineering
research. Its central vocabulary is **metadata4ing (m4i)** — an OWL ontology
(version 1.4.0, CC BY 4.0) that describes the generation of research data within
scientific activities. Canonical IRI: `http://w3id.org/nfdi4ing/metadata4ing/`.

Adopting m4i earns Shepard alignment with the **NFDI4Ing Terminology Service**
(`https://terminology.nfdi4ing.de/ts/`), the Helmholtz Unhide Knowledge Graph, and
downstream consumers such as NFDI-MatWerk, DataPLANT, and the NFDI4Ing Archetype
Doris for HPMC (High-Performance Measurement and Computation) workflows.

Shepard already ships the m4i 1.4.0 bundle as an optional ontology preseed (bundle id
`metadata4ing`). When the bundle is enabled, every provenance activity exposed via
`GET /v2/provenance/activities` can be content-negotiated to an m4i-flavoured JSON-LD
shape using `Accept: application/ld+json; profile=metadata4ing`. This is the hook that
makes Shepard data discoverable via NFDI4Ing infrastructure.

---

## Prerequisites

Before configuring federation, confirm the following are in place:

1. **Shepard is running** and the admin endpoint is reachable:
   `GET /v2/admin/semantic/ontologies` returns 200.
2. **Keycloak instance-admin role** is configured and your API key carries it (required
   for admin-tier endpoints).
3. **Unhide plugin active** (`shepard-plugin-unhide` in the plugins list):
   `GET /v2/admin/plugins` — look for `id: "unhide"`, `state: "ENABLED"`.
4. At least one Collection has been flagged `publishToHelmholtzKG: true` via
   `PATCH /v2/collections/{appId}/properties` and has a minted PID (KIP1a).

---

## Step 1 — Verify the m4i bundle is loaded

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://shepard.example.com/v2/admin/semantic/ontologies
```

Look for an entry with `"id": "metadata4ing"` and `"enabled": true` in the response.
If the bundle is present but disabled, enable it:

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  https://shepard.example.com/v2/admin/semantic/ontologies/metadata4ing \
  -d '{"enabled": true}'
```

If the bundle is absent (fresh install without the default preseed), trigger a re-seed:

```bash
# Via the admin CLI:
shepard-admin semantic ontologies seed

# Or via REST (seeds all bundles from the manifest):
curl -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://shepard.example.com/v2/admin/semantic/ontologies/seed
```

Verify the canonical m4i terms are queryable after seeding:

```sparql
PREFIX m4i: <http://w3id.org/nfdi4ing/metadata4ing/>
SELECT ?class WHERE { ?class a owl:Class }
LIMIT 10
```

via `GET /v2/semantic/sparql?query=...` — should return m4i classes including
`m4i:ProcessingStep`, `m4i:Method`, `m4i:Tool`.

---

## Step 2 — Configure the Unhide harvester endpoint

The NFDI4Ing discovery path runs through the **Helmholtz Unhide Knowledge Graph**
(`unhide.helmholtz-metadaten.de`). Unhide harvests Shepard's feed endpoint
(`GET /v2/unhide/feed.jsonld`) on a schedule. The feed body is **schema.org +
metadata4ing JSON-LD** — the union of discoverability metadata and m4i provenance
traces that Unhide's inward-mappings understand natively.

Configure the Unhide plugin to point at your instance:

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  https://shepard.example.com/v2/admin/unhide/config \
  -d '{
    "enabled": true,
    "feedBaseUrl": "https://shepard.example.com",
    "contactEmail": "rdm@your-institute.dlr.de"
  }'
```

Verify the feed is reachable and returns m4i-shaped entries:

```bash
curl -H "Accept: application/ld+json" \
  https://shepard.example.com/v2/unhide/feed.jsonld
```

The response `@graph` array should contain entries with `m4i:hasProcessingStep`
arrays referencing provenance activities.

To register your instance with Unhide, contact the Helmholtz Metadata Collaboration
(HMC) via `https://helmholtz-metadaten.de/en/inf-services/unhide` and provide:

- Your feed endpoint URL (`https://shepard.example.com/v2/unhide/feed.jsonld`)
- Your institute's Helmholtz centre identifier
- A contact email for harvest errors

---

## Step 3 — Verify the m4i provenance shape

Once the bundle is enabled, test that provenance content-negotiation returns canonical
m4i triples:

```bash
curl -H "Authorization: Bearer $USER_TOKEN" \
  -H "Accept: application/ld+json; profile=metadata4ing" \
  https://shepard.example.com/v2/provenance/activities?size=5
```

The response should include `@type: "m4i:ProcessingStep"` entries with the canonical
predicate `m4i:realizesMethod`. If you see Shepard-local predicates like
`m4i:hasMethod`, the m4i deepening slice M4I-b has not yet shipped — the bundle is
functional for vocabulary lookup but the renderer still emits the pre-M4I-b shape.
Check `aidocs/semantics/94 §3.2` for the status of M4I-b.

---

## What's next (roadmap, not shipped)

The following are **not yet available** and require additional design work:

- **Automatic Terminology Service sync** — today operators register the m4i bundle
  manually via the admin surface; automated discovery registration with
  `terminology.nfdi4ing.de` is not wired.
- **NFDI4Ing Archetype Doris registration** — HPMC workflows (LUMEN hot-fire, MFFD
  AFP layup) can be ingested via the Doris crawler once the m4i HPMC sub-ontology
  bundle (M4I-f) is preseeded. Doris registration is a manual step per-institute.
- **Full canonical m4i 1.4.0 vocabulary** — the shipped bundle is a curated subset
  (~7 classes vs ~150 in the full vocabulary). The N1c2 admin refresh endpoint
  (`POST /v2/admin/semantic/ontologies/metadata4ing/refresh`) can pull the full
  canonical TTL; this is an opt-in operator action.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `GET /v2/admin/semantic/ontologies` returns 403 | API key lacks `instance-admin` role | Rotate to an admin key (`shepard-admin api-keys list`) |
| `metadata4ing` bundle absent from ontology list | ontology preseed did not run or was skipped | Run `shepard-admin semantic ontologies seed` |
| Feed returns empty `@graph` | No Collections flagged for publication | `PATCH /v2/collections/{appId}/properties` with `{"publishToHelmholtzKG": true}` |
| m4i SPARQL query returns 0 results | Bundle disabled or n10s import failed | Check `GET /v2/admin/semantic/ontologies/metadata4ing`; re-enable and reseed |

---

## Cross-references

- `aidocs/semantics/94-metadata4ing-integration-design.md §4.5` — M4I-e design
- `aidocs/integrations/67-unhide-publish-plugin.md §4` — Unhide feed body shape
- `docs/reference/semantic-repositories.md` — full ontology admin guide
- `docs/help/register-with-nfdi4ing.md` — casual-user task page
