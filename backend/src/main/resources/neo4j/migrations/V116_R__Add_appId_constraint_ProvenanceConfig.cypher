// FTOGGLE-PROV-1 rollback — drop the :ProvenanceConfig.appId uniqueness constraint.
//
// Idempotent: DROP CONSTRAINT ... IF EXISTS is a no-op when the constraint
// is already absent. Safe to re-run.

DROP CONSTRAINT appId_unique_ProvenanceConfig IF EXISTS;
