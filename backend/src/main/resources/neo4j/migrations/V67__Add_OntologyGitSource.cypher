// TPL5 — Add uniqueness constraint for :OntologyGitSource nodes.
//
// :OntologyGitSource represents a Git repository that Shepard polls for
// ontology files (.ttl / .owl / .rdf). Each node carries an appId (UUID v7)
// minted at creation by GenericDAO#createOrUpdate.
//
// The constraint mirrors the pattern established for all HasAppId entities
// in this codebase (V11, V27, V28, V58, V59, V66, …).
//
// Idempotency: IF NOT EXISTS makes this safe to re-run.
//
// Operator runbook:
//   - New sources are created via POST /v2/admin/semantic/git-sources
//   - Manual ingest: POST /v2/admin/semantic/git-sources/{appId}/ingest
//   - Nightly scheduler: set shepard.tpl5.git-ingest.enabled=true
//
// To roll back (drops all :OntologyGitSource nodes AND the constraint):
//   DROP CONSTRAINT OntologyGitSource_appId_unique IF EXISTS;
//   MATCH (n:OntologyGitSource) DELETE n;
CREATE CONSTRAINT OntologyGitSource_appId_unique IF NOT EXISTS
  FOR (n:OntologyGitSource)
  REQUIRE n.appId IS UNIQUE;
