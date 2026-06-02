// QM1c — Seed the EN 9100 §8 Disposition record STRUCTURED_RECIPE template.
//
// Lands as part of the GAP-3 / QM1 family (`aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-3`):
// once a DataObject moves into `NCR_OPEN` / `CONCESSION_PENDING`, an
// auditor needs a typed StructuredDataReference record carrying the
// disposition decision. This template is the canonical skeleton —
// admins clone or extend it; the EN 9100 §8.7 fields stay consistent
// across instances.
//
// The accompanying V101 "Quality Inspection / NCR" template
// (DATAOBJECT_RECIPE) covers the inspection event itself. THIS template
// is the structured payload that hangs off the NCR DataObject as a
// StructuredDataReference once disposition is decided.
//
// Body shape — STRUCTURED_RECIPE (validated by TemplateBodyValidator after QM1c):
//   { "structuredData": { "name": "DispositionRecord", "fields": [...] } }
// Each field carries:
//   - name        — record key
//   - type        — STRING | NUMBER | BOOLEAN | DATE-TIME
//   - required    — true | false
//   - format      — markdown | date-time | orcid | uuid | enum (with values)
//   - description — short admin / auditor crib
//
// EN 9100 §8.7 disposition vocabulary (`use-as-is | rework | scrap | concession`)
// is enforced via the `disposition.enum` array. `approver_orcid` carries
// the FAIR2-style researcher identifier; `approver_username` is the
// in-Shepard handle so legacy installs without ORCID adoption stay usable.
//
// Idempotency: MERGE on {name, version}; re-running is a no-op.
// Rollback: V103_R__Disposition_record_template.cypher.
//
// Operator runbook:
//   Apply via the standard MigrationsRunner (Flyway-style ordering).
//   Manual run: `cypher-shell -u neo4j -p <password> -f V103__Disposition_record_template.cypher`
//   Verify:    `MATCH (t:ShepardTemplate {name: 'Disposition record', version: 1}) RETURN t.appId, t.templateKind, t.retired;`
//              → returns exactly one row; templateKind = STRUCTURED_RECIPE; retired = false.
//
// aidocs/16 QM1c; aidocs/34 V103 row.

MERGE (t:ShepardTemplate {name: 'Disposition record', version: 1})
ON CREATE SET
  t.appId         = randomUUID(),
  t.templateKind  = 'STRUCTURED_RECIPE',
  t.description   = 'EN 9100 §8.7 disposition record — the structured payload attached to an NCR_OPEN / CONCESSION_PENDING DataObject when an auditor decides the outcome (use-as-is, rework, scrap, or concession). Fields capture the NCR identity, defect classification, the disposition decision, the approver (ORCID + Shepard username), the decision timestamp, and free-form markdown notes. Aligns to ISO 9001 §8.7 control of non-conforming outputs.',
  t.body          = '{"structuredData":{"name":"DispositionRecord","fields":[{"name":"ncr_id","type":"STRING","required":true,"description":"Non-conformance report identifier (operator-visible). Free-form; site-specific format (e.g. NCR-2026-0042)."},{"name":"defect_type","type":"STRING","required":true,"format":"vocabulary","description":"Controlled-vocabulary term identifying the defect class (e.g. urn:mffd:defect:porosity)."},{"name":"disposition","type":"STRING","required":true,"format":"enum","enum":["use-as-is","rework","scrap","concession"],"description":"EN 9100 §8.7 disposition decision."},{"name":"approver_orcid","type":"STRING","required":false,"format":"orcid","description":"ORCID of the disposition approver (preferred)."},{"name":"approver_username","type":"STRING","required":true,"description":"Shepard username of the disposition approver (always present)."},{"name":"decided_at","type":"STRING","required":true,"format":"date-time","description":"ISO 8601 timestamp of the disposition decision (UTC)."},{"name":"notes","type":"STRING","required":false,"format":"markdown","description":"Free-form auditor notes; evidence pointers; ECR references."}]}}',
  t.tags          = ['quality', 'ncr', 'disposition', 'din-en-9100', 'structured', 'qm1c'],
  t.createdBy     = 'system',
  t.createdAt     = timestamp(),
  t.updatedAt     = timestamp(),
  t.retired       = false,
  t.source        = 'V103-builtin'
;
