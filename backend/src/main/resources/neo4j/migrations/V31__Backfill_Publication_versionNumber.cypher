// KIP1h — Phase-1 versioned PIDs backfill.
//
// :Publication rows minted by pre-KIP1h shepard (the in-core
// MockMinter, PID format mock:shepard:<kind>:<appId>:<epoch-millis>)
// carry no versionNumber property. New rows minted by the LocalMinter
// plugin (KIP1h) always set versionNumber on save; this backfill
// stamps versionNumber=1 onto every legacy row so the resolver, the
// PublicationIO wire shape, and any future findLatestVersionNumber
// query treat the legacy rows as the first version of their entity.
//
// Idempotent: only touches rows where versionNumber IS NULL. Re-running
// the migration after a downgrade-and-reupgrade is a no-op.
//
// Fail-fast: MigrationsRunner (post-A1e) propagates the exception so a
// partial backfill aborts startup; operators see the failure rather
// than half-migrated data.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row KIP1h.
// To verify post-migration:
//   cypher-shell> MATCH (p:Publication) WHERE p.versionNumber IS NULL RETURN count(p);
//   → must return 0.
// To list legacy MockMinter rows:
//   cypher-shell> MATCH (p:Publication) WHERE p.minterId = 'mock' RETURN p.pid, p.versionNumber;

MATCH (p:Publication) WHERE p.versionNumber IS NULL SET p.versionNumber = 1;
