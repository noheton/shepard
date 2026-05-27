---
title: NFDI4Ing federation (reference)
description: Operator reference for connecting a Shepard instance to the NFDI4Ing infrastructure — Terminology Service, Helmholtz Unhide, and Archetype Doris
permalink: /reference/nfdi4ing-federation/
layout: default
audience: operator
---
# NFDI4Ing federation

Shepard speaks **Metadata4Ing (m4i)** — NFDI4Ing's engineering provenance
vocabulary — out of the box (shipped via ONT1b / M4I-a). This page walks an
operator through the steps to make that compliance visible to the NFDI4Ing
federation infrastructure.

**Time to complete:** ≤ 30 minutes for steps 1–3. Step 4 (Archetype Doris)
is handled by your institute's data steward out of band.

---

## 1. Verify the m4i bundle is active

The `metadata4ing` bundle must be **enabled** in the internal semantic
repository. It is on by default; confirm with:

```sh
shepard-admin semantic ontologies list \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

You should see a row like:

```
ID               REQUIRED  ENABLED  SOURCE
metadata4ing     no        yes      builtin
```

If `ENABLED` shows `no`, re-enable it:

```sh
shepard-admin semantic ontologies enable metadata4ing \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

The bundle carries ~20 representative m4i 1.4.0 classes. To pull the full
~150-term canonical OWL from NFDI4Ing:

```sh
shepard-admin semantic refresh-ontologies --bundles=metadata4ing \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

This calls `n10s.rdf.import.fetch` against
`https://w3id.org/nfdi4ing/metadata4ing/1.4.0/` and replaces the stub
in-place. Requires outbound network from the Shepard host.

---

## 2. Self-register with the NFDI4Ing Terminology Service (operator action)

The [NFDI4Ing Terminology Service](https://terminology.nfdi4ing.de/ts/) is a
federated SPARQL catalogue of community vocabularies. Registering your
Shepard instance makes its custom ontology extensions (uploaded via
`shepard-admin semantic ontologies upload`) discoverable to other institutes
running TS queries.

**Current status (v6 preview):** Shepard's internal n10s SPARQL endpoint
(`GET /v2/semantic/{repoAppId}/sparql`) is not yet publicly exposed or
auto-registered with the TS. The operator performs this out of band:

1. Upload any lab-coined vocabulary extensions:

   ```sh
   shepard-admin semantic ontologies upload \
     --file lab-vocab.ttl --id acme-lab \
     --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
   ```

2. Contact the NFDI4Ing Terminology Service team at
   `terminology@nfdi4ing.de` with your canonical vocabulary IRI prefix and
   a public SPARQL endpoint if your institute operates one.

3. If your institute operates a standalone triple store alongside Shepard
   (Stardog, GraphDB, Fuseki), register that endpoint as a `SPARQL`
   [semantic repository](/reference/semantic-repositories/) in Shepard and
   point the TS at the triple store's public SPARQL URL.

**Roadmap:** Automated TS self-registration (pushing the bundle IRIs +
SPARQL endpoint via the TS registration API) is planned for the v2
semantic surface (`aidocs/semantics/98`). Do not promise this capability
to stakeholders before it ships.

---

## 3. Enable Helmholtz Unhide feed (Helmholtz-affiliated institutes)

If your institute is a Helmholtz member, Shepard's
[Unhide publish plugin](/reference/plugins/) exposes a JSON-LD feed that
Unhide's harvester reads. The feed body already carries `m4i:hasProcessingStep`
and `m4i:hasIdentifier` triples alongside `schema.org` metadata.

**3.1 Enable the feed:**

```sh
shepard-admin unhide enable \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
shepard-admin unhide set-contact-email ops@YOUR-INSTITUTE.dlr.de \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

**3.2 Generate a harvest key for Unhide:**

```sh
shepard-admin unhide rotate-harvest-key \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

Copy the printed key. You will register it with Unhide in the next step.

**3.3 Register with Helmholtz Unhide:**

1. Go to [https://unhide.helmholtz-metadaten.de/dataprovider/register](https://unhide.helmholtz-metadaten.de/dataprovider/register).
2. Enter your feed URL: `https://YOUR-SHEPARD/v2/unhide/feed.jsonld`
3. Paste the harvest key when prompted.

The harvester polls on its own schedule (typically daily). After the first
successful harvest, your Collections appear in the
[Helmholtz Knowledge Graph](https://unhide.helmholtz-metadaten.de/).

**Verify the feed is reachable:**

```sh
curl -s "https://YOUR-SHEPARD/v2/unhide/feed.jsonld?page=0&page-size=5" \
  -H "X-API-KEY: $HARVEST_KEY" | python3 -m json.tool | head -40
```

You should see a `@context` block referencing `http://schema.org/` and
`http://w3id.org/nfdi4ing/metadata4ing/` alongside your Collection metadata.

**Note for non-Helmholtz institutes:** The Unhide feed is Helmholtz-specific.
Skip this step; the m4i bundle and the SPARQL surface still apply.

---

## 4. Register with Archetype Doris (HPMC use cases — forward-looking)

[Archetype Doris](https://nfdi4ing.de/archetypes-3/doris/) is NFDI4Ing's
reference data management archetype for **High-Performance Measurement and
Computing (HPMC)** workflows. It is directly relevant to LUMEN (hot-fire
hi-rate DAQ) and MFFD AFP (multi-sensor robot telemetry).

**Current status (v6 preview):** Full Doris integration requires the HPMC
sub-ontology bundle (M4I-f, not yet shipped) and a configured HOMER crawler
endpoint. This step is **forward-looking only**.

When M4I-f ships, the registration flow will be:

1. Enable the `metadata4ing-hpmc` bundle:
   ```sh
   shepard-admin semantic ontologies enable metadata4ing-hpmc \
     --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
   ```
2. Contact the Doris team at `nfdi4ing.de/archetypes-3/doris/` to register
   your instance as an HPMC data provider.
3. Configure the HOMER crawler URL in your Shepard instance (config key
   TBD — will land in the M4I-f reference when that slice ships).

---

## 5. What is NOT included in this release

Shepard currently claims m4i-compliance along the following axes and
**only these**:

| Claim | Status |
|---|---|
| `metadata4ing` 1.4.0 OWL seeded in internal repository | ✓ shipped (ONT1b + M4I-a) |
| m4i triples in Unhide feed body | ✓ shipped (UH1b) |
| PROV-O activities rendered with `profile=metadata4ing` content-neg | ✓ shipped (PROV1h) |
| Operator runbook for federation surfaces | ✓ this doc |
| Automated TS self-registration | ✗ roadmap (`aidocs/semantics/98`) |
| HPMC sub-ontology bundle | ✗ roadmap (M4I-f) |
| m4i on v1 `/shepard/api/...` paths | ✗ by design — m4i is `/v2/` only |
| OWL DL reasoning at runtime | ✗ out of scope |

Do not represent unshipped items as current capabilities to stakeholders or
funding bodies.

---

## 6. Troubleshooting

**`metadata4ing` bundle shows `ENABLED=no` after restart**

Check `shepard.semantic.internal.preseed-ontologies.skip-bundles` in
`application.properties`. If `metadata4ing` is listed there, remove it and
restart.

**`refresh-ontologies` for `metadata4ing` fails with a network error**

The canonical URL (`https://w3id.org/nfdi4ing/metadata4ing/1.4.0/`) requires
outbound HTTP from the Shepard host. If the host is airgapped, copy
the full OWL TTL to the host and upload via:

```sh
shepard-admin semantic ontologies upload \
  --file /tmp/metadata4ing-1.4.0.ttl --id metadata4ing --replace \
  --url https://YOUR-SHEPARD --api-key $ADMIN_KEY
```

**Unhide harvester reports 401 for the feed**

The harvest key may have been rotated. Issue a new key with
`shepard-admin unhide rotate-harvest-key` and update the key in the
Unhide data provider registration.

**Unhide harvester reports 503 for the feed**

The Unhide feed is disabled. Run `shepard-admin unhide enable`.

---

## See also

- [Semantic repositories (reference)](/reference/semantic-repositories/) —
  how the internal n10s repository is configured
- [Semantic annotations (reference)](/reference/semantic-annotations/) —
  how to annotate entities with m4i terms
- [Unhide publish plugin (reference)](/reference/plugins/) —
  full Unhide feed reference including auth modes and JSON-LD shape
- [Admin CLI (reference)](/reference/admin-cli/) —
  all `shepard-admin` commands including `semantic` and `unhide` subcommands
- [Register with NFDI4Ing](/help/register-with-nfdi4ing/) —
  short task-oriented companion to this page
