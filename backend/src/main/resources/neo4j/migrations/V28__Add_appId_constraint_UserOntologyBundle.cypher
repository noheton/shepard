// N1c2 — uniqueness constraint on the new `:UserOntologyBundle`
// label, mirroring the V11 / V22 / V25 / V27 idiom.
//
// :UserOntologyBundle nodes carry the manifest-shape metadata
// (id, iriPrefix, canonicalUrl, sha256, byteSize, licence, …) for
// operator-uploaded ontology bundles. The actual TTL bytes live on
// disk under shepard.semantic.internal.user-bundles-dir; the node
// is the audit-trail-grade catalogue entry that the OntologySeedService
// joins to the built-in manifest on startup.
//
// Bundle-id uniqueness across the built-in + user namespace is
// enforced at the service layer in OntologyConfigService — the
// constraint here is the L2a appId uniqueness pattern.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row N1c2.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_UserOntologyBundle IF NOT EXISTS FOR (n:UserOntologyBundle) REQUIRE n.appId IS UNIQUE;
