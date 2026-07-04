// FTOGGLE-AUTOSWEEP-1 — rollback: drop the :AutosweepConfig.appId unique constraint.
DROP CONSTRAINT AutosweepConfig_appId_unique IF EXISTS;
