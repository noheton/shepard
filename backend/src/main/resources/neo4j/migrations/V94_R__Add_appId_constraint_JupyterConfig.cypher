// J1e rollback — drops the :JupyterConfig.appId uniqueness constraint.
//
// Run this before downgrading to a version prior to J1e. If any
// :JupyterConfig nodes exist, they are preserved — only the uniqueness
// constraint is removed.
//
// Safe to re-run: DROP CONSTRAINT ... IF EXISTS is a no-op when the
// constraint is already absent.

DROP CONSTRAINT appId_unique_JupyterConfig IF EXISTS;
