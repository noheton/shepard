// PROJ-PREDICATES-1 — add the three Project predicates to the controlled vocabulary.
//
// Three new urn:shepard:* predicates are minted so the admin vocabulary picker
// surfaces them and SHACL has a stable schema to validate against:
//
//   urn:shepard:project    — boolean role marker. A :Collection carrying this
//                             with valueName = "true" IS a Project.
//                             Cardinality on the subject Collection: 0..1.
//
//   urn:shepard:partOf     — non-exclusive parent pointer. A :Collection carrying
//                             this names another Collection's appId (UUID v7) as
//                             a Project it belongs to. A Collection may carry
//                             multiple partOf annotations.
//                             Storage convention: the parent appId is stored as
//                             the *literal* valueName (xsd:string, no IRI form),
//                             so the XOR invariant in SemanticAnnotationV2Rest
//                             (objectLiteral XOR objectIri) is respected. The
//                             SHACL gate (PROJ-SEMA-WRITE-GATE-1) enforces that
//                             the referenced Collection itself carries
//                             urn:shepard:project = "true".
//                             Cardinality: 0..N per Collection.
//
//   urn:shepard:programme  — free-text funder / DLR-internal programme line on
//                             a Project Collection. Cardinality: 0..N. Only
//                             valid on a Collection that also carries
//                             urn:shepard:project = "true".
//
// Design doc:
//   aidocs/integrations/121-project-and-subcollections.md §2
//
// Idempotent — MERGE creates each :Predicate on first run; re-runs no-op.
//
// Rollback: V107_R__add_project_predicates.cypher detaches and deletes the
// three predicate nodes (their use by existing annotations stays — the
// annotations carry the IRI as a literal string and don't link to the
// :Predicate node).

// ── Project role marker ───────────────────────────────────────────────────────
MERGE (p:Predicate {iri: 'urn:shepard:project'})
ON CREATE SET
  p:BasicEntity,
  p.appId        = randomUUID(),
  p.label        = 'project',
  p.description  = 'Role marker on a Collection that designates it as a Project — a bundle of non-exclusive child Collections joined via urn:shepard:partOf. Value must be the literal "true".',
  p.deleted      = false,
  p.createdAt    = timestamp(),
  p.source       = 'V107-project-predicates';

// ── Non-exclusive parent pointer ──────────────────────────────────────────────
MERGE (p:Predicate {iri: 'urn:shepard:partOf'})
ON CREATE SET
  p:BasicEntity,
  p.appId        = randomUUID(),
  p.label        = 'partOf',
  p.description  = 'Non-exclusive parent pointer. Value is the appId (UUID v7) of a Project Collection, stored as a literal string. A Collection may carry multiple partOf annotations to be a member of multiple Projects.',
  p.deleted      = false,
  p.createdAt    = timestamp(),
  p.source       = 'V107-project-predicates';

// ── Free-text programme name on a Project ─────────────────────────────────────
MERGE (p:Predicate {iri: 'urn:shepard:programme'})
ON CREATE SET
  p:BasicEntity,
  p.appId        = randomUUID(),
  p.label        = 'programme',
  p.description  = 'Free-text funder / DLR-internal programme line (e.g. "Clean Aviation JU", "DLR Project Line 4"). Only valid on a Collection that also carries urn:shepard:project = "true". A Project may declare multiple programmes.',
  p.deleted      = false,
  p.createdAt    = timestamp(),
  p.source       = 'V107-project-predicates';
