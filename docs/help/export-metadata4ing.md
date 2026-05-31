---
layout: default
title: Export a DataObject as metadata4ing JSON-LD
permalink: /help/export-metadata4ing/
audience: user
---

# Export a DataObject as metadata4ing (m4i) JSON-LD

You need an m4i-flavoured JSON-LD representation of a DataObject —
for an NFDI4Ing-aware tool, a SPARQL store, a SHACL validator, or
a downstream pipeline that ingests engineering-research provenance.

## What you need

- The Collection's `appId` (UUID v7) — visible in the URL of any
  Collection page (`/collections/<appId>`).
- The DataObject's `appId` — visible on the DataObject detail page
  in the breadcrumb header.
- An API key with at least Read on the parent Collection. Get one
  from your profile → API tokens, or ask your instance admin.

## How

```bash
curl -H 'Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"' \
     -H "X-API-KEY: $SHEPARD_API_KEY" \
     "https://shepard.example.dlr.de/v2/collections/<collection-appid>/data-objects/<do-appid>" \
   | jq .
```

Or the short profile form:

```bash
curl -H 'Accept: application/ld+json; profile=metadata4ing' \
     -H "X-API-KEY: $SHEPARD_API_KEY" \
     "https://shepard.example.dlr.de/v2/collections/<collection-appid>/data-objects/<do-appid>" \
   | jq .
```

You will get back a JSON-LD document looking like this (truncated):

```json
{
  "@context": {
    "m4i": "http://w3id.org/nfdi4ing/metadata4ing#",
    "obo": "http://purl.obolibrary.org/obo/",
    "prov": "http://www.w3.org/ns/prov#",
    "dcterms": "http://purl.org/dc/terms/",
    "schema": "http://schema.org/",
    "qudt": "http://qudt.org/schema/qudt/",
    "shepard": "https://noheton.github.io/shepard/prov#",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  },
  "@id": "shepard:dataobject/019e6ffc-89a4-76b5-8dbb-15888646a904",
  "@type": ["m4i:InvestigatedObject", "prov:Entity"],
  "dcterms:identifier": "019e6ffc-89a4-76b5-8dbb-15888646a904",
  "dcterms:title": "TR-004",
  "schema:dateCreated": {
    "@type": "xsd:dateTime",
    "@value": "2026-05-15T10:14:00Z"
  },
  "prov:wasGeneratedBy": { "@id": "shepard:activity/abc-123" },
  "m4i:realizesMethod": { "@id": "shepard:method/CREATE" },
  "m4i:hasNumericalVariable": [
    {
      "@type": "m4i:NumericalVariable",
      "rdfs:label": "vibration_peak",
      "m4i:hasValue": { "@type": "xsd:double", "@value": "12.3" },
      "qudt:unit": { "@id": "http://qudt.org/vocab/unit/G" }
    }
  ]
}
```

## Then what?

- **Load into a SPARQL store.** Apache Jena, RDF4J, GraphDB,
  Stardog, Allegrograph — all parse the embedded `@context` without
  network round-trip.
- **Validate against the shape contract.** The acceptance script
  `examples/mffd-showcase/scripts/validate_m4i_shape.py` walks every
  DataObject in a Collection and reports per-DO pass/fail against
  `backend/src/main/resources/shapes/m4i-dataobject-shape.ttl`.
- **Feed into an NFDI4Ing Terminology Service** — see the operator
  runbook at [Register with NFDI4Ing](/help/register-with-nfdi4ing/).

## When something goes wrong

- **`406 dataobject.unsupported-profile`** — the `profile=` parameter
  on your `Accept` header is not recognised. Use either the canonical
  URL `https://w3id.org/nfdi4ing/metadata4ing/` or the short
  `metadata4ing`; trailing slashes are normalised.
- **`401`** — your API key is missing or rejected. Re-issue from the
  profile page.
- **`404`** — the Collection or DataObject doesn't exist or has been
  soft-deleted. Check the appId in the URL.
- **Plain JSON instead of JSON-LD** — your `Accept` header is being
  rewritten by a proxy. Test with `curl -v` to confirm what reaches
  the server, then re-test through a direct path or fix the proxy.

## See also

- [DataObjects reference](/reference/data-objects/) — full m4i
  predicate table.
- [NFDI4Ing federation runbook](/reference/nfdi4ing-federation/) —
  operator steps for federation registration.
- [Provenance tracing](/help/provenance-tracing/) — the related
  `Accept: application/ld+json; profile=metadata4ing` surface on
  `/v2/provenance/*`.
