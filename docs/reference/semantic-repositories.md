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
pre-seeded common ontologies (PROV-O, QUDT, schema.org, Dublin
Core, FOAF, OM-2, W3C Time, GeoSPARQL — coming in N1b) will land
into.

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

- Pre-seeded common ontologies (PROV-O, QUDT, …) that almost
  every research deployment references.
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

## See also

- [Design: internal semantic repository via neosemantics](https://github.com/noheton/shepard/blob/main/aidocs/48-internal-semantic-repository-via-neosemantics.md)
- [Admin guide — Neo4j section](/admin/)
- [Upstream `SemanticRepository` REST docs](https://gitlab.com/dlr-shepard/shepard) (the byte-frozen API surface)
