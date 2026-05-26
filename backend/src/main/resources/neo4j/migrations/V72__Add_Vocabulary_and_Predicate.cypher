// SEMA-V6-002 — introduces :Vocabulary and :Predicate node types.
//
// This migration:
//   1. Creates uniqueness constraints on :Vocabulary.appId, :Vocabulary.uri,
//      :Predicate.appId, and :Predicate.uri (natural keys for both types).
//   2. Seeds ten bootstrap :Vocabulary rows that correspond to the ten
//      pre-seeded ontology bundles (N1b + ONT1b). MERGE is keyed on uri
//      so this is safe to re-run; ON CREATE SET appId = randomUUID() fires
//      only on the first execution.
//
// MERGE key: (uri) — the stable natural key.  appId is generated once.
//
// Bootstrap vocabularies:
//   1. Dublin Core Terms   (dcterms)
//   2. PROV-O              (prov)
//   3. schema.org          (schema)
//   4. DataCite            (datacite)
//   5. CHAMEO              (chameo)
//   6. Material OWL        (mat)
//   7. shepard:            (shepard — internal vocabulary)
//   8. metadata4ing / m4i  (m4i)
//   9. SKOS                (skos)
//  10. GeoSPARQL           (geo)
//
// Safe to re-run: all CREATE CONSTRAINT and MERGE statements are idempotent.
// Rollback:
//   DROP CONSTRAINT Vocabulary_appId_unique IF EXISTS;
//   DROP CONSTRAINT Vocabulary_uri_unique   IF EXISTS;
//   DROP CONSTRAINT Predicate_appId_unique  IF EXISTS;
//   DROP CONSTRAINT Predicate_uri_unique    IF EXISTS;
//   MATCH (n:Vocabulary) WHERE n.source = 'V72-bootstrap' DETACH DELETE n;
//
// Operator runbook: no pre-existing data to migrate.
// Aborts startup on error per MigrationsRunner's fail-fast posture.

// ── Constraints ──────────────────────────────────────────────────────────────

CREATE CONSTRAINT Vocabulary_appId_unique IF NOT EXISTS
FOR (n:Vocabulary) REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT Vocabulary_uri_unique IF NOT EXISTS
FOR (n:Vocabulary) REQUIRE n.uri IS UNIQUE;

CREATE CONSTRAINT Predicate_appId_unique IF NOT EXISTS
FOR (n:Predicate) REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT Predicate_uri_unique IF NOT EXISTS
FOR (n:Predicate) REQUIRE n.uri IS UNIQUE;

// ── Bootstrap vocabularies ───────────────────────────────────────────────────

// 1. Dublin Core Terms
MERGE (v:Vocabulary { uri: "http://purl.org/dc/terms/" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "Dublin Core Terms",
  v.prefix      = "dcterms",
  v.description = "Provides terms for describing resources: creator, title, date, license, and more.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 2. PROV-O
MERGE (v:Vocabulary { uri: "http://www.w3.org/ns/prov#" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "PROV-O (W3C Provenance Ontology)",
  v.prefix      = "prov",
  v.description = "W3C vocabulary for recording provenance: Entity, Activity, Agent, wasGeneratedBy, wasAttributedTo, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 3. schema.org
MERGE (v:Vocabulary { uri: "https://schema.org/" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "schema.org",
  v.prefix      = "schema",
  v.description = "Web vocabulary for structured data: name, description, identifier, dateCreated, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 4. DataCite Metadata Schema
MERGE (v:Vocabulary { uri: "http://datacite.org/schema/kernel-4" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "DataCite Metadata Schema 4",
  v.prefix      = "datacite",
  v.description = "DataCite vocabulary for publishing research data records: DOI, relatedIdentifier, fundingReference, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 5. CHAMEO (Characterisation Methodology Ontology)
MERGE (v:Vocabulary { uri: "http://www.chameo-ontology.eu/" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "CHAMEO (Characterisation Methodology Ontology)",
  v.prefix      = "chameo",
  v.description = "Ontology for describing characterisation experiments, specimens, measurement techniques, and results in materials science.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 6. Material OWL
MERGE (v:Vocabulary { uri: "http://www.owl-ontologies.com/material.owl#" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "Material OWL",
  v.prefix      = "mat",
  v.description = "Domain ontology for material properties, processing conditions, and manufacturing steps.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 7. Shepard internal vocabulary
MERGE (v:Vocabulary { uri: "https://shepard.dlr.de/vocab/" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "Shepard Internal Vocabulary",
  v.prefix      = "shepard",
  v.description = "Shepard-native predicates: legacyAttribute, dataObjectStatus, processingStep, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 8. metadata4ing / m4i (NFDI4Ing)
MERGE (v:Vocabulary { uri: "http://w3id.org/nfdi4ing/metadata4ing/" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "metadata4ing (NFDI4Ing)",
  v.prefix      = "m4i",
  v.description = "NFDI4Ing metadata vocabulary for engineering research data: ProcessingStep, Tool, Method, NumericalVariable, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 9. SKOS (Simple Knowledge Organization System)
MERGE (v:Vocabulary { uri: "http://www.w3.org/2004/02/skos/core#" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "SKOS (Simple Knowledge Organization System)",
  v.prefix      = "skos",
  v.description = "W3C vocabulary for thesauri, taxonomies, and controlled vocabularies: Concept, prefLabel, altLabel, broader, narrower, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";

// 10. GeoSPARQL
MERGE (v:Vocabulary { uri: "http://www.opengis.net/ont/geosparql#" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "GeoSPARQL",
  v.prefix      = "geo",
  v.description = "OGC vocabulary for spatial data: Feature, Geometry, wktLiteral, sfContains, sfIntersects, etc.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V72-bootstrap";
