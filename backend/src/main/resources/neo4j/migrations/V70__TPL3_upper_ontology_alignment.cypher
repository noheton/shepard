// TPL3a-lite — upper-ontology alignment registry bootstrap.
//
// Seeds a set of (:OntologyAlignment) nodes that record how each core
// Shepard concept maps onto an upper ontology (BFO 2020, IAO, PROV-O,
// IOF Core).  The mapping authority is aidocs/semantics/96-upper-ontology-alignment.md.
//
// Each node carries:
//   shepardConcept    — simple name of the Shepard Java/Neo4j type
//   upperOntologyUri  — canonical IRI of the upper-ontology class
//   relationshipType  — rdfs:subClassOf | owl:equivalentClass
//   confidence        — HIGH | MEDIUM | LOW (editorial judgement per aidocs/96)
//   source            — aidocs document that established the mapping
//   appId             — UUID (seeded once; randomUUID() on CREATE)
//   createdAt         — epoch-millis timestamp (set once on CREATE)
//
// Safe to re-run: every MERGE is keyed on (shepardConcept, upperOntologyUri)
// via the constraint created below.  ON CREATE SET blocks fire only when the
// node does not exist yet.
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.
//
// Operator runbook: see aidocs/semantics/97-tpl3-upper-ontology-bootstrap.md.

// ── Uniqueness constraint ────────────────────────────────────────────────────
// Single-property constraint on appId (Community Edition compatible).
// Idempotency relies on MERGE keyed on (shepardConcept, upperOntologyUri) —
// no composite NODE KEY needed (that requires Enterprise Edition).

CREATE CONSTRAINT OntologyAlignment_appId_unique IF NOT EXISTS
FOR (n:OntologyAlignment)
REQUIRE n.appId IS UNIQUE;

// ── Alignment rows ───────────────────────────────────────────────────────────

// Resource → BFO:Continuant
// Every persistent Shepard artefact (Collection, DataObject, Container…) is a
// continuant in the BFO sense — an entity that persists through time.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Resource",
  upperOntologyUri: "http://purl.obolibrary.org/obo/BFO_0000002"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Collection → IAO:DataSet (IAO_0000100)
// A Collection is an information artefact that aggregates data items with a
// shared provenance context — the IAO DataSet axiom.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Collection",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000100"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// DataObject → IAO:DataItem (IAO_0000027)
// A DataObject is a data item that is part of (or referenced by) a Collection.
MERGE (n:OntologyAlignment {
  shepardConcept:   "DataObject",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000027"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// SemanticAnnotation → IAO:Annotation (IAO_0000136 = is about)
// Annotations are information artefacts that are about some entity; the IAO
// annotation axiom captures this is-about provenance.
MERGE (n:OntologyAlignment {
  shepardConcept:   "SemanticAnnotation",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000136"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// FileBundle → IAO:Document (IAO_0000310)
// A FileBundle is a document — an information artefact with a canonical
// binary form (file payload).
MERGE (n:OntologyAlignment {
  shepardConcept:   "FileBundle",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000310"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Container → BFO:GenericallyDependentContinuant (BFO_0000031)
// Containers (timeseries, structured data, file containers) are generically
// dependent continuants — they depend on some bearer for their existence.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Container",
  upperOntologyUri: "http://purl.obolibrary.org/obo/BFO_0000031"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Activity → PROV-O:Activity
// Process steps, imports, and annotations emit provenance via PROV-O activities.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Activity",
  upperOntologyUri: "http://www.w3.org/ns/prov#Activity"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Activity → BFO:Occurrent (via PROV-O:Activity rdfs:subClassOf BFO:Occurrent)
// Records the transitive BFO anchor so graph reasoners can traverse directly.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Activity",
  upperOntologyUri: "http://purl.obolibrary.org/obo/BFO_0000003"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "MEDIUM",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Agent → PROV-O:Agent
// Human users, automated importers, and plugin workers are PROV-O agents.
MERGE (n:OntologyAlignment {
  shepardConcept:   "Agent",
  upperOntologyUri: "http://www.w3.org/ns/prov#Agent"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "HIGH",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// User → IOF Core:Person
// Shepard users are persons in the IOF Core / BFO sense (specifically
// iof-core:Person which itself aligns to BFO:MaterialEntity).
MERGE (n:OntologyAlignment {
  shepardConcept:   "User",
  upperOntologyUri: "https://www.industrialontologies.org/ontology/core/Core/Person"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "MEDIUM",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Shape → IAO:Plan (IAO_0000104)
// Shapes (formerly templates) are information artefacts that prescribe how a
// DataObject should be structured — the IAO Plan (a directive information entity).
MERGE (n:OntologyAlignment {
  shepardConcept:   "Shape",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000104"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "rdfs:subClassOf",
  n.confidence      = "MEDIUM",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";

// Template → Shape (owl:equivalentClass alias kept for migration-compat)
MERGE (n:OntologyAlignment {
  shepardConcept:   "Template",
  upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000104"
})
ON CREATE SET
  n.appId           = randomUUID(),
  n.createdAt       = timestamp(),
  n.relationshipType = "owl:equivalentClass",
  n.confidence      = "MEDIUM",
  n.source          = "aidocs/semantics/96-upper-ontology-alignment.md";
