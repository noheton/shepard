// A5c-annotation — seeds the HDF5 Vocabulary node used by the SHACL shape
// and the AnnotationDialog autocomplete surface.
//
// Adds one :Vocabulary row for the "hdf" prefix namespace so that
//   - the admin semantic-terms UI lists "HDF5 Vocabulary" as a selectable source
//   - the autocomplete widget resolves "hdf:hasDatasetPath" when a user annotates
//     an HdfReference
//
// MERGE key: (uri) — the stable natural key.  appId is generated once on CREATE.
//
// Safe to re-run: ON CREATE SET fires only on the first execution;
// subsequent runs are no-ops (the MERGE finds the existing node).
//
// Rollback:
//   MATCH (v:Vocabulary { uri: "https://shepard.dlr.de/ontology/hdf#" }) DETACH DELETE v;
//
// Operator runbook: no pre-existing data to migrate.
// Aborts startup on error per MigrationsRunner's fail-fast posture.

MERGE (v:Vocabulary { uri: "https://shepard.dlr.de/ontology/hdf#" })
ON CREATE SET
  v.appId       = randomUUID(),
  v.label       = "HDF5 Vocabulary",
  v.prefix      = "hdf",
  v.description = "Shepard plugin vocabulary for HDF5 dataset references: datasetPath, containerAppId, and related predicates for annotating HdfReference nodes.",
  v.enabled     = true,
  v.createdAt   = timestamp(),
  v.source      = "V87-bootstrap";
