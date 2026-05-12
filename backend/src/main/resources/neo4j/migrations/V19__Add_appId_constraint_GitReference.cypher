// G1a — :GitReference appId uniqueness constraint. Same shape as
// V13 (Role), V15 (Activity), V17 (CollectionProperties), V18
// (ShepardTemplate). Idempotent.
//
// :GitReference is born here per aidocs/38 — no legacy rows to
// migrate from. Mode-(a) loose-link rows land first via the new
// /v2/data-objects/{appId}/git-references REST surface.
CREATE CONSTRAINT appId_unique_GitReference IF NOT EXISTS FOR (n:GitReference) REQUIRE n.appId IS UNIQUE;
