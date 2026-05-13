// G1b — :GitReference.mode default backfill. Any pre-G1b :GitReference row
// has no `mode` property (G1a never wrote one); G1b ships the tracked-artifact
// preview which discriminates by mode. Default every existing row to
// "LOOSE_LINK" so the new code sees a non-null value and never has to guess.
//
// Idempotent: WHERE n.mode IS NULL ensures re-runs are no-ops once the
// property is set. Additive: ZERO upgrade-tracker risk class — operators can
// run cypher-shell against this and the only effect is the property write.
//
// Operator runbook: this migration is safe to run live; the write touches
// only :GitReference rows that are missing a `mode` property. On greenfield
// instances the MATCH returns zero rows and the migration is a no-op.
MATCH (g:GitReference)
WHERE g.mode IS NULL
SET g.mode = 'LOOSE_LINK';
