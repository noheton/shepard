// T1a — :ShepardTemplate appId uniqueness constraint. Same shape as
// V13 (Role), V15 (Activity), V17 (CollectionProperties). Idempotent.
//
// :ShepardTemplate is born here per aidocs/54 — no legacy data to
// migrate from. The previously-designed __templates double-underscore
// hack from aidocs/39 was never shipped, so greenfield + upgrading
// deployments both hit zero existing rows.
CREATE CONSTRAINT appId_unique_ShepardTemplate IF NOT EXISTS FOR (n:ShepardTemplate) REQUIRE n.appId IS UNIQUE;
