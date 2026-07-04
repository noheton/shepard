// Rollback for V110__Template_parent_inheritance.cypher (TPL-INHERIT).
//
// Two-step, idempotent, safe to re-run:
//   1. Drop the parent-appId lookup index.
//   2. Strip the `parentTemplateAppId` property from every :ShepardTemplate
//      node where it was set. After rollback, all templates are treated as
//      roots (no inheritance) — the pre-feature behaviour is restored.
//
// Run order: stop the backend, run this Cypher, restart on the prior version.
//
// Note: stripping the property is a DATA mutation but a benign one — a child
// template reverts to standing on its own (delta-only) body. If the prior
// version's instantiation expected the flattened body, re-author the affected
// children's bodies to inline the inherited fields BEFORE rolling back. The
// forward feature is additive, so a clean roll-forward needs no data fixup.
//
//   MATCH (t:ShepardTemplate) WHERE t.parentTemplateAppId IS NOT NULL
//   REMOVE t.parentTemplateAppId;
DROP INDEX shepard_template_parent_appid IF EXISTS;

MATCH (t:ShepardTemplate)
WHERE t.parentTemplateAppId IS NOT NULL
REMOVE t.parentTemplateAppId;
