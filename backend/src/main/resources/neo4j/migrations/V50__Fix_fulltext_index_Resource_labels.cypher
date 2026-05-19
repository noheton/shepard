// V50 — Rebuild :Resource fulltext index on actual n10s property names.
//
// V44 created the index using SHORTEN-mode property names (rdfs__label,
// skos__prefLabel, skos__altLabel). However n10s is configured with
// handleVocabUris=IGNORE, which stores bare local names: label, prefLabel,
// altLabel. The old index was therefore empty and search returned no results.
//
// This migration drops the old index and creates a new one on the names
// that n10s actually uses, plus `name` and `title` for FOAF / DC / schema.org
// nodes so the search covers common label synonyms across ontologies.
//
// Idempotent (IF NOT EXISTS / IF EXISTS). Safe to re-run.
// Rollback: DROP INDEX resource_labels IF EXISTS
//           CREATE FULLTEXT INDEX resource_labels IF NOT EXISTS FOR (n:Resource) ON EACH [n.`rdfs__label`, n.`skos__prefLabel`, n.`skos__altLabel`]

DROP INDEX resource_labels IF EXISTS;
CREATE FULLTEXT INDEX resource_labels IF NOT EXISTS FOR (n:Resource) ON EACH [n.label, n.prefLabel, n.altLabel, n.name, n.title]
