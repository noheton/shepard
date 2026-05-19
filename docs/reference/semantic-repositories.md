---
layout: default
title: Semantic repositories (reference)
permalink: /reference/semantic-repositories/
---

# Semantic repositories

A **`SemanticRepository`** is a registered source of ontology terms.
Annotations on shepard entities (DataObject, Collection, Reference,
…) cite an IRI; the connected repository is what shepard asks when
the UI needs to resolve that IRI to a human-readable label.

A shepard instance can register **as many repositories as it
wants** — each annotation chooses which one provided the term. The
upstream surface ships three external connector types; this fork
adds a fourth, *internal*, type that runs inside the same Neo4j
shepard already uses.

## Connector types

| Type | Where it lives | When to pick it |
|---|---|---|
| **`SPARQL`** | User-configured external SPARQL endpoint (Stardog, GraphDB, Jena Fuseki, AllegroGraph). | You already operate a triple store, OR you need full SPARQL 1.1 (federation, advanced aggregations, reasoning). |
| **`JSKOS`** | External JSKOS service. | You have an existing JSKOS-hosted vocabulary (BARTOC, GBV's coli-conc, …). |
| **`SKOSMOS`** | External SKOSMOS service. | You want a SKOS-shaped controlled vocabulary with the FINTO-style read API. |
| **`INTERNAL`** *(this fork, N1a)* | Neosemantics (`n10s`) plugin running inside shepard's existing Neo4j. | You want a casual-deployment story — no extra service to run, back up, or auth. Great for ontology *references* that an analyst's annotations point at. Falls short for users who need a full reasoner or multi-GB ontology storage; those users should pick `SPARQL` and point at a real triple store. |

For typical research-data workflows, **`INTERNAL` is the new
default for casual users.** It needs zero infrastructure beyond
what shepard already requires, and it's the connector that the
pre-seeded common ontologies (see table below) land into.

## Bundled ontologies

shepard ships fourteen common ontologies under
`backend/src/main/resources/ontologies/`, each pinned by SHA-256 in
`ontologies-manifest.json`. The `OntologySeedService` loads them into
the `INTERNAL` repository on startup. To skip a subset, set
`shepard.semantic.internal.preseed-ontologies.skip-bundles=<csv>`.

Most bundles are **classpath-bundled Turtle stubs** — minimal
representative triples, enough for the annotation picker on a fresh
install. The **NASA Thesaurus** is the exception: on startup the seed
service calls `n10s.rdf.import.fetch` against the canonical SKOS/RDF-XML
URL and imports the full ~18,400-concept vocabulary directly. If the
network is unavailable the bundled stub (120 propulsion concepts) is
used as a fallback.

| Ontology | IRI prefix | Licence | What it gives you |
|---|---|---|---|
| **W3C PROV-O** | `http://www.w3.org/ns/prov#` | W3C Document License | Provenance vocabulary — Entity / Activity / Agent + `wasGeneratedBy` / `wasDerivedFrom` / `used`. Pairs with shepard's lineage design (`aidocs/30`). |
| **DCMI Metadata Terms** (Dublin Core) | `http://purl.org/dc/terms/` | CC BY 4.0 | Common metadata properties — creator, title, created, license, modified, … |
| **schema.org core** | `https://schema.org/` | CC BY-SA 3.0 | Web-style metadata vocabulary; what RO-Crate exports already use, so pre-seeding makes those terms resolvable. |
| **FOAF** | `http://xmlns.com/foaf/0.1/` | CC BY 1.0 | Person / Agent / Organization for author-style annotations. |
| **QUDT 2.1 Units** | `http://qudt.org/vocab/unit/` | CC BY 4.0 | Units of measure for scientific values. |
| **OM-2** (Ontology of Units of Measure) | `http://www.ontology-of-units-of-measure.org/resource/om-2/` | CC BY 4.0 | Alternative units ontology; QUDT is the default but OM-2 ships for cross-compat. |
| **W3C Time Ontology** | `http://www.w3.org/2006/time#` | W3C Document License | Time interval / duration vocabulary; annotate timeseries spans and lab-journal events. |
| **OGC GeoSPARQL** | `http://www.opengis.net/ont/geosparql#` | OGC Open Data Licence | Spatial-data references — Feature / Geometry / `hasGeometry` / `asWKT`. |
| **OBO Relation Ontology (RO)** | `http://purl.obolibrary.org/obo/RO_` | CC0 1.0 | Cross-cutting relations — `part_of` / `has_part` / `derives_from` / `participates_in` / `has_input` / `has_output` / `precedes`-family. Widely used in life-sciences research-data work. |
| **Metadata4Ing (NFDI4Ing) 1.4.0** | `http://w3id.org/nfdi4ing/metadata4ing/` | CC BY 4.0 | NFDI4Ing engineering-research extension of PROV-O — `m4i:ProcessingStep` (subtype of `prov:Activity`), `m4i:Method`, `m4i:Tool`, `m4i:InvestigatedObject` (subtype of `prov:Entity`), `m4i:NumericalVariable` + QUDT-unit hookup, `m4i:Person` / `m4i:Organization` subtypes of the `prov:` equivalents. |
| **SIMAT** (stub) | *(instance-specific)* | *(instance-specific)* | Aerospace / structures domain vocabulary for the SIMAT research context. |
| **Lumen-inspired** (stub) | *(instance-specific)* | *(instance-specific)* | Experiment-level concepts used in the LUMEN demonstration seed. |
| **NASA Thesaurus (2024)** | `https://sti.nasa.gov/docs/thesaurus/thesaurus-SKOS.xml#` | Public domain (NASA STI) | ~18,400 aerospace / engineering concepts with `skos:prefLabel` + `skos:altLabel`. Fetched as full SKOS/RDF-XML on first startup; 120-concept propulsion stub used offline. Searchable immediately via the annotation picker. |
| **Shepard Experiment Ontology** | `https://shepard.dlr.de/ontologies/experiment#` | CC BY 4.0 | 7 SKOS concept schemes (ExperimentPhase / MeasurementRole / QualityFlag / DefectType / InspectionMethod / ManufacturingProcess / SensorRole) for engineering experimental-data annotation. `ExperimentPhase` subclasses `prov:Activity`. |

For the eight standard bundles (PROV-O through metadata4ing), operators
who want the full canonical vocabularies run
`shepard-admin semantic refresh-ontologies` (N1c) — see
[admin CLI reference](/reference/admin-cli/#shepard-admin-semantic-refresh-ontologies).
The backend endpoint is `POST /v2/admin/semantic/refresh-ontologies`
(instance-admin gated).

## Admin-configurable preseed (N1c2)

Two bundles are **required** — `prov-o` (PROV-O audit-trail interop)
and `obo-relations` (cross-cutting Relation-Ontology relations PROV-
extending ontologies cite). Required bundles are always seeded;
attempts to disable them at runtime return `409` with RFC 7807
`semantic.bundle.required`.

Every other bundle is **operator-controllable at runtime**, and an
operator can add their own TTL ontology without rebuilding shepard:

- **`GET /v2/admin/semantic/ontologies`** — list every bundle
  (built-in + operator-uploaded) with `{id, source, required,
  enabled, iriPrefix, canonicalUrl, license, sha256, byteSize}`.
- **`POST /v2/admin/semantic/ontologies/{id}/enable`** — flip a
  bundle on (removes from runtime `disabledBundles` set).
- **`POST /v2/admin/semantic/ontologies/{id}/disable`** — flip a
  bundle off. `409 semantic.bundle.required` for `prov-o` /
  `obo-relations`.
- **`POST /v2/admin/semantic/ontologies`** (multipart) — upload a
  custom `.ttl`. Multipart parts: `file=<bytes>` + `metadata=<JSON
  with id, iriPrefix, license, optionally name and canonicalUrl>`.
  Server SHA-256s the bytes, refuses duplicates, refuses >10 MB,
  refuses non-Turtle, persists a `:UserOntologyBundle` row, writes
  the bytes to `<shepard.semantic.internal.user-bundles-dir>/<id>.ttl`,
  and the bundle joins the seed loop on the next restart. For an
  immediate import, run `shepard-admin semantic refresh-ontologies
  --bundles=<id>`.
- **`DELETE /v2/admin/semantic/ontologies/{id}`** — remove an
  operator-uploaded bundle. `409 semantic.bundle.builtin-not-removable`
  for built-ins (those ship in the JAR and update via release
  upgrades).

All five endpoints are `@RolesAllowed("instance-admin")` on the
`/v2/` development surface. The CLI parity is documented under
[admin CLI reference — `semantic ontologies` subgroup](/reference/admin-cli/#shepard-admin-semantic-ontologies).

### Precedence rules

The seed pass at startup applies these rules in order (per
`aidocs/65 §2.6`):

1. **`required: true` always seeds.** Even if the operator named
   the bundle in `disabledBundles` or in the deploy-time
   `skip-bundles` CSV.
2. **Master toggle off and no required bundles** → no-op exit.
3. **Otherwise**: a bundle is skipped if its id appears in the
   runtime `:SemanticConfig.disabledBundles` set OR in the
   deploy-time `shepard.semantic.internal.preseed-ontologies.skip-bundles`
   CSV.

The deploy-time keys
(`shepard.semantic.internal.preseed-ontologies.{enabled,skip-bundles}`)
keep working — they're the **install defaults** that seed the
`:SemanticConfig` singleton on first start. Runtime mutations via
the admin REST / CLI win forever after (per CLAUDE.md "Always:
surface operator knobs"). The new deploy-time-only key
`shepard.semantic.internal.user-bundles-dir` (default
`/var/lib/shepard/ontologies/`) is the filesystem directory the
upload endpoint writes TTLs to; mount it persistently in container
deployments.

### Custom-bundle example

```bash
# Upload a lab-specific TTL extension of metadata4ing
shepard-admin semantic ontologies upload \
  --file=lab-vocab.ttl \
  --id=acme-lab-vocab \
  --iri-prefix=https://acme.example.org/vocab/ \
  --license="CC BY 4.0"

# Confirm it joined the catalogue
shepard-admin semantic ontologies list

# Re-import canonical-URL-pinned bundles AND your new upload
shepard-admin semantic refresh-ontologies --bundles=acme-lab-vocab

# Disable a built-in you don't need
shepard-admin semantic ontologies disable qudt
```

Attempting to disable a required bundle:

```bash
$ shepard-admin semantic ontologies disable prov-o
error: 409 ... — Bundle 'prov-o' is required and cannot be disabled.
```

## `INTERNAL` — neosemantics inside shepard's Neo4j

The `INTERNAL` type is backed by the
[neosemantics (`n10s`)](https://github.com/neo4j-labs/neosemantics)
Neo4j plugin. n10s imports RDF (Turtle / RDF-XML / JSON-LD /
N-Triples) into the same Neo4j database that shepard runs against
— each imported resource becomes a node with the `:Resource`
label and `rdfs__label[@lang]` properties.

shepard's domain entities (Collection, DataObject, …) never carry
the `:Resource` label, so the two graphs co-exist without
collision. n10s-managed nodes are read by the connector via
straight Cypher (`MATCH (r:Resource {uri: $iri}) RETURN
properties(r)`), without needing a separate SPARQL endpoint to
also be deployed.

### How shepard talks to it

- **`getTerm(iri)`** — runs a single Cypher MATCH for the
  `:Resource` node and returns its `rdfs__label` properties as a
  language-keyed map.
- **`healthCheck()`** — checks whether the `n10s.*` procedure
  namespace is registered on the connected Neo4j (i.e. the plugin
  is installed and allowlisted).

A `SemanticRepository` of type `INTERNAL` ignores its `endpoint`
field; the connector always routes through the in-process Neo4j
session.

### What if my Neo4j doesn't have n10s?

The connector **gracefully degrades**. If the plugin is absent
(e.g. you upgraded shepard but didn't update your
`docker-compose.yml`):

- `healthCheck()` returns `false`.
- `getTerm()` returns the empty map.
- shepard's startup log carries one clear `WARN` line explaining
  the situation and telling you exactly how to install the plugin
  (set `NEO4J_PLUGINS=["n10s"]` and add `n10s.*` to the
  procedures-allowlist).
- Every other connector type (`SPARQL`, `JSKOS`, `SKOSMOS`)
  continues to work unchanged.

This is intentional: the goal is "casual users get the internal
repo for free, power users keep their external setup as before."
No upgrade ever fails because the new plugin isn't there.

### Installing the plugin

The shipped `infrastructure/docker-compose.yml` does this for
you — every new deployment gets the plugin auto-fetched on first
Neo4j start. If you maintain your own Neo4j or modified the
compose file:

1. **Add the plugin.** Set `NEO4J_PLUGINS=["n10s"]` in the Neo4j
   container env, OR drop a current `neosemantics-<version>.jar`
   into `/plugins`.
2. **Allowlist its procedures.** Add `n10s.*` to both:
   - `NEO4J_dbms_security_procedures_unrestricted`
   - `NEO4J_dbms_security_procedures_allowlist`
3. **Restart Neo4j.** (`docker compose restart neo4j`)
4. **Restart shepard.** Watch for the startup log line
   `N10sBootstrapHook: n10s INTERNAL semantic repository ready.` —
   that's the signal `INTERNAL` repositories are healthy.

The bootstrap hook calls `n10s.graphconfig.init(...)` with
shepard-default values; an operator can override the
`handleVocabUris` mode (one of `IGNORE` / `SHORTEN` / `MAP` /
`KEEP`) via `shepard.semantic.internal.handle-vocab-uris`. The
hook is idempotent — safe to re-run on every startup.

### Trade-offs

The internal-repo path is best for:

- Pre-seeded common ontologies (PROV-O, QUDT, RO, metadata4ing, …)
  that almost every research deployment references.
- Up to a few hundred MB of total ontology data.
- Read-mostly workloads.

Pick an external `SPARQL` repository instead if you need:

- Full SPARQL 1.1 (federation, complex aggregations).
- Real RDFS / OWL reasoning.
- Multi-GB ontology storage.

shepard supports **registering multiple repositories of mixed
types** — an instance can hold an `INTERNAL` repo for common
ontologies *and* a `SPARQL` repo for the lab's domain-specific
controlled vocabulary at the same time.

## Picking a connector

When in doubt:

1. **First-time install / casual research** → `INTERNAL` (the
   default if you have no existing triple store).
2. **Lab already runs Stardog / Fuseki / GraphDB** →
   `SPARQL` pointing at it. Stay external; you don't need to
   migrate the data.
3. **Existing JSKOS / SKOSMOS catalogue** → keep using `JSKOS`
   or `SKOSMOS` as appropriate; nothing has changed for those.

You can change a repository's `type` later by creating a new one
and migrating annotations — annotation IRIs themselves never
change, so the swap is a config flip, not a data migration.

## Term autocomplete (N1e)

`GET /v2/semantic/terms/search?q=…&limit=20` searches the terms
loaded into the INTERNAL n10s repository and returns up to `limit`
(max 50) matching suggestions.

```
GET /v2/semantic/terms/search?q=turbopump&limit=5
Authorization: Bearer <token>

200 OK
[
  {
    "uri": "https://sti.nasa.gov/docs/thesaurus/thesaurus-SKOS.xml#186132",
    "label": "turbopumps",
    "description": null
  },
  {
    "uri": "https://sti.nasa.gov/docs/thesaurus/thesaurus-SKOS.xml#62509",
    "label": "liquid propellant rocket engines",
    "description": null
  }
]
```

**Properties searched.** The endpoint matches against a wide set of
standard label and synonym properties:

- `rdfs:label` / `skos:prefLabel` / `skos:altLabel` / `schema:name` / `dct:title`
- `skos:hiddenLabel` (abbreviations and alternative spellings)
- `skos:notation` (classification codes, e.g. NASA Thesaurus numeric IDs)
- OBO synonym properties: `oboInOwl:hasExactSynonym`, `hasRelatedSynonym`, `hasBroadSynonym`, `hasNarrowSynonym`
- `schema:alternateName` (schema.org and Wikidata-style alternates)
- `rdfs:comment` / `skos:definition` / `skos:scopeNote` (description fields)
- The URI itself

The query must be at least 2 characters.

**Performance.** When the `resource_labels` fulltext index exists
(created by `V50` / `V51` on startup), the endpoint uses Neo4j
fulltext search — relevance-ranked and fast even on the full
365,000-node NASA Thesaurus graph. The index covers all the properties
listed above. On databases that haven't run those migrations yet the
endpoint falls back to a CONTAINS scan automatically.

**Graceful degradation.** If no ontology data is loaded the endpoint
returns `200` with an empty array — it never errors. The
Semantic Repositories configuration page shows a warning banner when
an INTERNAL repository is present but no searchable labels are found.

**Auth.** Any authenticated shepard user. No per-entity permission
check — matching the SPARQL proxy posture.

The annotation picker in the UI (`AddAnnotationDialog.vue`) uses
this endpoint automatically with a 300 ms debounce. Users who know
the exact IRI can still type it directly into the field without
selecting from suggestions.

## See also

- [Design: internal semantic repository via neosemantics](https://github.com/noheton/shepard/blob/main/aidocs/48-internal-semantic-repository-via-neosemantics.md)
- [Admin guide — Neo4j section](/admin/)
- [Upstream `SemanticRepository` REST docs](https://gitlab.com/dlr-shepard/shepard) (the byte-frozen API surface)
