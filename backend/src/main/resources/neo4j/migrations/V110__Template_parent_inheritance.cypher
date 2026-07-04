// TPL-INHERIT — ShepardTemplate single-parent inheritance
//
// Design: aidocs/integrations/123 (operator: "template editor for admin
// templates should support inheritance").
//
// Adds an OPTIONAL, NULLABLE `parentTemplateAppId` property to :ShepardTemplate.
// Neo4j is schema-less on properties, so the additive nullable property needs
// no DDL change — existing :ShepardTemplate rows simply lack the property and
// OGM reads the absence as null. A null parent means "root template" (no
// inheritance — the body stands alone), which is the pre-feature behaviour.
//
// This migration is therefore a NOOP for data, but it DOES create a lookup
// index so the ancestor-walk in TemplateInheritanceResolver.resolveChain /
// .wouldCreateCycle stays cheap on instances with many templates.
//
// Idempotent: `IF NOT EXISTS` guard. Fail-fast: a syntax error aborts startup
// via MigrationsRunner (post-A1e propagates MigrationsException).
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V110__Template_parent_inheritance.cypher
//   Rollback: V110_R__Template_parent_inheritance.cypher (drops index + strips property)
//   Verify (children with a declared parent):
//     MATCH (c:ShepardTemplate)
//     WHERE c.parentTemplateAppId IS NOT NULL
//     MATCH (p:ShepardTemplate {appId: c.parentTemplateAppId})
//     RETURN c.name AS child, p.name AS parent ORDER BY parent, child;
//
// aidocs/16 TPL-INHERIT
CREATE INDEX shepard_template_parent_appid IF NOT EXISTS
FOR (t:ShepardTemplate) ON (t.parentTemplateAppId);
