-- Operator runbook: aidocs/34-upstream-upgrade-path.md row U2a / G1-cred
-- Idempotent: IF NOT EXISTS means safe to re-run.
CREATE CONSTRAINT appId_unique_GitCredential IF NOT EXISTS
  FOR (n:GitCredential) REQUIRE n.appId IS UNIQUE;
