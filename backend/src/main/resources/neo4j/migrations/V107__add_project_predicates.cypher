// PROJ-PREDICATES-1 — Seed three urn:shepard:project* :Predicate entries into
// the Shepard internal vocabulary (SEMA-V6 controlled-vocab surface).
//
// Spec: aidocs/integrations/121 §2 + §5.
//
// Three predicates for marking a Collection as a Project and linking
// child Collections into parent Projects:
//
//   urn:shepard:project      — role marker; value "true"; 0..1 per Collection
//   urn:shepard:partOf       — child Collection appId; 0..N per Collection (non-exclusive)
//   urn:shepard:programme    — free-text programme name; 0..N per Project Collection
//
// MERGE keyed on uri — safe to re-run; ON CREATE SET fires only once.
// The vocabulary node (uri: "https://shepard.dlr.de/vocab/") is already
// seeded by V72__Add_Vocabulary_and_Predicate.cypher; we look it up to
// populate vocabularyAppId on the Predicate nodes (denormalised fast-lookup).
//
// Operator runbook: no pre-existing data to migrate.
//   Re-run: cypher-shell -u neo4j -p <password> -f V107__add_project_predicates.cypher
//   Verify:
//     MATCH (p:Predicate) WHERE p.uri STARTS WITH 'urn:shepard:project'
//       OR p.uri = 'urn:shepard:partOf'
//       OR p.uri = 'urn:shepard:programme'
//     RETURN p.uri, p.label, p.cardinality, p.expectedObjectType;
//     -- → 3 rows
//
// Rollback: V107_R__add_project_predicates.cypher
//   MATCH (p:Predicate)
//   WHERE p.uri IN ['urn:shepard:project', 'urn:shepard:partOf', 'urn:shepard:programme']
//   DETACH DELETE p;
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

// ── 1. Resolve the Shepard internal vocabulary's appId ───────────────────────
//    (already seeded by V72 with prefix "shepard"; safe to look up)

MATCH (v:Vocabulary { uri: "https://shepard.dlr.de/vocab/" })

// ── 2. urn:shepard:project — boolean role marker on a Collection ─────────────
//    Value "true" marks the Collection as a Project entity.
//    Cardinality ONE: at most one per Collection.

MERGE (p1:Predicate { uri: "urn:shepard:project" })
ON CREATE SET
  p1.appId              = randomUUID(),
  p1.label              = "project (role marker)",
  p1.vocabularyAppId    = v.appId,
  p1.expectedObjectType = "LITERAL",
  p1.cardinality        = "ONE",
  p1.required           = false,
  p1.createdAt          = timestamp(),
  p1.source             = "V107-bootstrap"

// ── 3. urn:shepard:partOf — non-exclusive parent-Project link ────────────────
//    Value: appId of a parent Collection that bears urn:shepard:project = "true".
//    Cardinality MANY: 0..N per Collection (non-exclusive bundling across Programmes).
//    SHACL cross-target constraint (ProjectShape): target must itself be a Project.

MERGE (p2:Predicate { uri: "urn:shepard:partOf" })
ON CREATE SET
  p2.appId              = randomUUID(),
  p2.label              = "part of (project)",
  p2.vocabularyAppId    = v.appId,
  p2.expectedObjectType = "DATAOBJECT_APPID",
  p2.cardinality        = "MANY",
  p2.required           = false,
  p2.createdAt          = timestamp(),
  p2.source             = "V107-bootstrap"

// ── 4. urn:shepard:programme — free-text programme name ─────────────────────
//    Free-text name of the funding programme or research programme.
//    Cardinality MANY: a Project may belong to multiple programmes.

MERGE (p3:Predicate { uri: "urn:shepard:programme" })
ON CREATE SET
  p3.appId              = randomUUID(),
  p3.label              = "programme",
  p3.vocabularyAppId    = v.appId,
  p3.expectedObjectType = "LITERAL",
  p3.cardinality        = "MANY",
  p3.required           = false,
  p3.createdAt          = timestamp(),
  p3.source             = "V107-bootstrap"

// ── 5. Smoke probe — returns 3 on first run; same 3 on re-run ────────────────
MATCH (p:Predicate)
WHERE p.uri IN ['urn:shepard:project', 'urn:shepard:partOf', 'urn:shepard:programme']
RETURN count(p) AS v107_predicates_registered;
