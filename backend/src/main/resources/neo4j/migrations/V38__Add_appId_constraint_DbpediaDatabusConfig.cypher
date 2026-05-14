// REF1c — uniqueness constraint on the new :DbpediaDatabusConfig
// singleton label. Mirrors the V30 (:UnhideConfig) idiom.
//
// The singleton invariant (exactly one :DbpediaDatabusConfig node ever
// exists) is held by the service-layer seed in
// DbpediaDatabusConfigService#seedIfNeeded() at startup; this constraint
// is the database-side belt-and-braces guarantee.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row REF1c.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_DbpediaDatabusConfig IF NOT EXISTS FOR (n:DbpediaDatabusConfig) REQUIRE n.appId IS UNIQUE;
