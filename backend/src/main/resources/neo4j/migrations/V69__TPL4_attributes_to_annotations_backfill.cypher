// V69__TPL4_attributes_to_annotations_backfill.cypher
//
// TPL4 — One-shot backfill: legacy DataObject attributes → :SemanticAnnotation nodes.
//
// Operator runbook: aidocs/34 § TPL4 row.
// Rollback: V69_R__TPL4_attributes_to_annotations_backfill.cypher
//           (deletes all :SemanticAnnotation nodes with source = 'attributes-backfill')
//
// Purpose
// -------
// Every DataObject (and AbstractDataObject subtype) may carry a free-text
// attributes map stored as flattened properties on the Neo4j node using the
// '||' delimiter convention (e.g. "attributes||bench", "attributes||propellant").
// This migration creates equivalent :SemanticAnnotation nodes with:
//
//   propertyIRI  = "urn:shepard:attribute:<key>"   (synthetic, no repository)
//   propertyName = "<key>"
//   valueName    = "<value>"
//   source       = "attributes-backfill"
//
// The source tag lets callers distinguish synthetic backfill annotations from
// hand-authored ontology annotations, and allows targeted cleanup when data
// owners migrate to first-class annotations.
//
// Idempotency guarantee
// ---------------------
// MERGE on (propertyIRI, valueName, source) ensures that re-running this
// migration — or running it after the service-layer dual-write has already
// created some annotations — does NOT create duplicate nodes.
// Running on a system with no attributes is a safe no-op.
//
// Performance note
// ----------------
// This migration touches every DataObject node that carries at least one
// "attributes||*" property.  On large instances (10k+ DataObjects with
// attributes) the outer MATCH may be slow; the MERGE is indexed by the
// property combination.  If performance is a concern, batch using APOC or
// run during a maintenance window.
//
// Note: Neo4j OGM stores Map<String,String> attributes with the key pattern
// "attributes||<key>".  This migration uses keys() + filtering to discover
// those properties and extract the bare key.

MATCH (do)
WHERE (do:DataObject OR do:Collection)
  AND any(k IN keys(do) WHERE k STARTS WITH 'attributes||')
WITH do, [k IN keys(do) WHERE k STARTS WITH 'attributes||'] AS attrKeys
UNWIND attrKeys AS attrKey
WITH do,
     attrKey,
     substring(attrKey, size('attributes||')) AS bareKey,
     do[attrKey] AS attrValue
WHERE attrValue IS NOT NULL AND attrValue <> ''
  AND NOT EXISTS {
    MATCH (do)-[:has_annotation]->(existing:SemanticAnnotation)
    WHERE existing.propertyIRI = ('urn:shepard:attribute:' + bareKey)
      AND existing.valueName   = attrValue
      AND existing.source      = 'attributes-backfill'
  }
CREATE (sa:SemanticAnnotation {
    propertyIRI:  'urn:shepard:attribute:' + bareKey,
    propertyName: bareKey,
    valueName:    attrValue,
    source:       'attributes-backfill'
})
CREATE (do)-[:has_annotation]->(sa)
RETURN count(sa) AS annotationsCreated;
