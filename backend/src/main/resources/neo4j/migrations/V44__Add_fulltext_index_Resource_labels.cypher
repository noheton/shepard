// V44 — Add fulltext index on :Resource label properties for N1e term autocomplete.
//
// This index accelerates GET /v2/semantic/terms/search, which performs
// autocomplete lookups over the n10s-imported RDF :Resource nodes.
//
// The endpoint falls back gracefully to a CONTAINS scan when this index
// is absent, so existing deployments that skip this migration will not
// error — they will simply experience slower queries on large ontologies.
//
// The index covers the three primary label properties stored by n10s
// under the SHORTEN / IGNORE vocab-URI mode:
//   rdfs__label      ← rdfs:label
//   skos__prefLabel  ← skos:prefLabel
//   skos__altLabel   ← skos:altLabel
//
// Idempotent (IF NOT EXISTS). Safe to re-run.
//
// Rollback: DROP INDEX resource_labels IF EXISTS
//
// Operator note: if :Resource nodes are absent (n10s not yet loaded),
// this statement is still valid — the index will be populated as n10s
// imports data. There is no data migration needed.
CREATE FULLTEXT INDEX resource_labels IF NOT EXISTS FOR (n:Resource) ON EACH [n.`rdfs__label`, n.`skos__prefLabel`, n.`skos__altLabel`]
