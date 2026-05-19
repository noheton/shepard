// V51 — Extend :Resource fulltext index to cover SKOS hiddenLabel, OBO synonyms,
//        schema.org alternateName, and SKOS notation (for code-based search).
//
// Properties added (all bare IGNORE-mode local names as stored by n10s):
//   hiddenLabel      — skos:hiddenLabel (abbreviations + spelling variants for search)
//   notation         — skos:notation   (classification codes, e.g. NASA Thesaurus numeric IDs)
//   hasExactSynonym  — oboInOwl:hasExactSynonym (Gene Ontology, CHEBI, UBERON, MeSH)
//   hasRelatedSynonym— oboInOwl:hasRelatedSynonym
//   hasBroadSynonym  — oboInOwl:hasBroadSynonym
//   hasNarrowSynonym — oboInOwl:hasNarrowSynonym
//   alternateName    — schema:alternateName (schema.org, Wikidata)
//
// V50 created the index on bare IGNORE-mode label names (label, prefLabel, altLabel, name, title).
// V51 extends it to cover synonym properties used across scientific / biomedical / aerospace
// ontologies so that the NASA Thesaurus, OBO-family, and schema.org imports are all searchable.
//
// Idempotent (DROP + CREATE IF NOT EXISTS). Safe to re-run.
// Rollback: DROP INDEX resource_labels IF EXISTS
//           CREATE FULLTEXT INDEX resource_labels IF NOT EXISTS
//             FOR (n:Resource) ON EACH [n.label, n.prefLabel, n.altLabel, n.name, n.title]

DROP INDEX resource_labels IF EXISTS;
CREATE FULLTEXT INDEX resource_labels IF NOT EXISTS FOR (n:Resource) ON EACH [
  n.label, n.prefLabel, n.altLabel, n.name, n.title,
  n.hiddenLabel, n.notation,
  n.hasExactSynonym, n.hasRelatedSynonym, n.hasBroadSynonym, n.hasNarrowSynonym,
  n.alternateName
]
