// KRL-CONFIG-1 rollback — drops the :KrlInterpreterConfigSingleton.appId uniqueness constraint.
//
// Run this before downgrading to a version prior to KRL-CONFIG-1. If any
// :KrlInterpreterConfigSingleton nodes exist, they are preserved — only the
// uniqueness constraint is removed.
//
// Safe to re-run: DROP CONSTRAINT ... IF EXISTS is a no-op when the
// constraint is already absent.

DROP CONSTRAINT appId_unique_KrlInterpreterConfigSingleton IF EXISTS;
