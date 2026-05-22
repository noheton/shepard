// V61 — Register the v15 MFFD-import provenance predicates + role
// individuals as n10s :Resource nodes so SemanticAnnotation creation
// against these IRIs resolves cleanly (no 404 from the IRI lookup
// path in InternalSemanticConnector).
//
// Scope: 8 new shepard: predicates emitted by the v15 batch
// AuthoringPass writeback (see aidocs/integrations/93 §10 +
// aidocs/agent-findings/data-ontologist-prov-o-v15.md §"New shepard:
// predicates proposed") plus 2 named-individual prov:Role declarations
// (shepard:role-executor, shepard:role-operator) referenced from
// `prov:qualifiedAssociation [prov:hadRole ...]` on every batch.
//
// All MERGE-based and additive — re-running the migration is a no-op.
// Companion rollback: V61_R__rollback.cypher.
//
// Operator runbook:
//   - Re-run with cypher-shell -f V61__v15_prov_predicates.cypher
//   - No restart required; reads through InternalSemanticConnector
//     pick up the new resources on next query.
//   - Verify with:
//       MATCH (r:Resource) WHERE r.uri STARTS WITH 'http://semantics.dlr.de/shepard-upper#'
//       RETURN r.uri, r.rdfs__label ORDER BY r.uri;
//
// Why MERGE not n10s.rdf.import.inline:
//   The MigrationsRunner (post-A1e) doesn't have an n10s session bound
//   at startup ordering — Cypher MERGE on :Resource is the n10s-stable
//   shape (same nodes the importer produces, just minted by hand).
//   Avoids race with N10sBootstrapHook.

// --- 8 shepard: predicates --------------------------------------------------
// Property metadata mirrors the data-ontologist doc's domain/range
// table; rdfs__label is what InternalSemanticConnector.getTerm()
// surfaces back to the SemanticAnnotation IO layer.

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#targetCollection'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'target collection',
    p.`rdfs__comment@en` = 'Destination Collection scoped by an import AuthoringPass.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#filesUploaded'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'files uploaded',
    p.`rdfs__comment@en` = 'Count of FileContainer payload uploads completed by an AuthoringPass.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#timeseriesImported'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'timeseries imported',
    p.`rdfs__comment@en` = 'Count of TimeseriesReferences materialised by an AuthoringPass.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#structuredPayloads'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'structured payloads imported',
    p.`rdfs__comment@en` = 'Count of StructuredDataReference payloads imported by an AuthoringPass.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#batchSequence'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'batch sequence number',
    p.`rdfs__comment@en` = 'Monotonic per-(script,source) batch index; gaps indicate missed batches.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#throughputBytesPerSec'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'throughput (B/s)',
    p.`rdfs__comment@en` = 'Observed payload throughput in bytes per second during an AuthoringPass.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#retryCount'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'retry count',
    p.`rdfs__comment@en` = 'HTTP retries observed by an AuthoringPass; non-zero = backpressure.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

MERGE (p:Resource {uri: 'http://semantics.dlr.de/shepard-upper#sourceInstance'})
  ON CREATE SET
    p:Property,
    p.`rdfs__label@en` = 'source instance identifier',
    p.`rdfs__comment@en` = 'Cross-instance partition key for SPARQL UNIONs over migrated provenance.',
    p.shepard__addedBy = 'V61__v15_prov_predicates',
    p.shepard__addedAt = timestamp();

// --- 2 prov:Role named individuals ------------------------------------------

MERGE (r:Resource {uri: 'http://semantics.dlr.de/shepard-upper#role-executor'})
  ON CREATE SET
    r:NamedIndividual,
    r.`rdfs__label@en` = 'executor',
    r.`rdfs__comment@en` = 'AI agent that executed the import; delegated tool, not responsible party.',
    r.shepard__addedBy = 'V61__v15_prov_predicates',
    r.shepard__addedAt = timestamp();

MERGE (r:Resource {uri: 'http://semantics.dlr.de/shepard-upper#role-operator'})
  ON CREATE SET
    r:NamedIndividual,
    r.`rdfs__label@en` = 'operator (responsible party)',
    r.`rdfs__comment@en` = 'Human researcher who supervised the import; responsible party per EU AI Act Art-50.',
    r.shepard__addedBy = 'V61__v15_prov_predicates',
    r.shepard__addedAt = timestamp();

// --- Smoke probe: count the nodes we just touched ---------------------------
// Returns 10 on first run; same 10 on re-run (idempotent).
MATCH (r:Resource)
WHERE r.uri STARTS WITH 'http://semantics.dlr.de/shepard-upper#'
  AND r.shepard__addedBy = 'V61__v15_prov_predicates'
RETURN count(r) AS v61_predicates_registered;
