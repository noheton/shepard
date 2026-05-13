# Bundled ontology licensing

This directory ships nine ontologies as **shepard-bundled Turtle
files** for the N1b "pre-seeded common ontologies" feature (see
`aidocs/48 §4`) plus the ONT1a follow-up that adds the OBO Relation
Ontology. All nine are openly licensed for redistribution.

| File | Ontology | Licence | Licence URL |
|---|---|---|---|
| `prov-o.ttl`        | W3C PROV-O                       | W3C Document License  | <https://www.w3.org/Consortium/Legal/2015/doc-license> |
| `dublin-core.ttl`   | DCMI Metadata Terms              | CC BY 4.0             | <https://creativecommons.org/licenses/by/4.0/> |
| `schema-org.ttl`    | schema.org core                   | CC BY-SA 3.0          | <https://creativecommons.org/licenses/by-sa/3.0/> |
| `foaf.ttl`          | FOAF                              | CC BY 1.0             | <http://creativecommons.org/licenses/by/1.0/> |
| `qudt.ttl`          | QUDT Units of Measure 2.1         | CC BY 4.0             | <https://creativecommons.org/licenses/by/4.0/> |
| `om-2.ttl`          | OM-2 Ontology of Units of Measure | CC BY 4.0             | <https://creativecommons.org/licenses/by/4.0/> |
| `time.ttl`          | W3C Time Ontology                 | W3C Document License  | <https://www.w3.org/Consortium/Legal/2015/doc-license> |
| `geosparql.ttl`     | OGC GeoSPARQL                     | OGC Open Data Licence | <https://www.ogc.org/legal/> |
| `obo-relations.ttl` | OBO Relation Ontology (RO)        | CC0 1.0               | <https://creativecommons.org/publicdomain/zero/1.0/> |

None of these licences fall in the banned families
(GPL / AGPL / SSPL) tracked by `.github/dependency-review-config.yml`.

The shepard project copyright and licence on this directory's
metadata + `ontologies-manifest.json` is **EPL-2.0** (matches the
rest of the backend); the bundled Turtle files carry the upstream
licence above and are redistributed unchanged in IRI shape.

## Refresh path

The bundled files are minimum-viable stubs carrying each ontology's
canonical IRI prefix plus a handful of representative classes /
properties — enough for the casual annotation flow on a fresh
install. To bundle the full vocabularies:

1. Download the canonical TTL from the `canonicalUrl` recorded in
   `ontologies-manifest.json` for that ontology.
2. Replace the corresponding `*.ttl` file.
3. Recompute SHA-256 and file size; update the manifest entry.
4. Update `retrievedAt`.

The forthcoming `shepard-admin semantic refresh-ontologies` CLI
(N1c) will automate steps 1-4 in one command.
