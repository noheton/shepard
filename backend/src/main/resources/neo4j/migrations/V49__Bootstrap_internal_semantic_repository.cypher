// N1-REPO-BOOTSTRAP — Create the singleton INTERNAL SemanticRepository for n10s.
//
// Root cause: the N10sBootstrapHook initialises n10s and seeds ontologies but
// never created a :SemanticRepository node of type INTERNAL. Without this node,
// getAllSemanticRepositories() returns empty, the annotation-picker dialog can
// never be submitted (isValid stays false), and users cannot annotate anything
// from the built-in n10s store.
//
// Using MERGE ON CREATE ensures idempotency — if an admin already created one
// manually, this migration is a no-op. The endpoint field is left empty because
// InternalSemanticConnector ignores it (routes via the in-process OGM session).
//
// Runbook: aidocs/semantics/ — N1a/N1b/N1c family.
//
// To roll back manually:
//   MATCH (r:SemanticRepository {type: 'INTERNAL', name: 'Built-in Semantic Store (n10s)'}) DELETE r;
MERGE (r:SemanticRepository {type: 'INTERNAL'})
ON CREATE SET
  r:BasicEntity,
  r.appId     = randomUUID(),
  r.name      = 'Built-in Semantic Store (n10s)',
  r.endpoint  = '',
  r.deleted   = false,
  r.createdAt = timestamp()
