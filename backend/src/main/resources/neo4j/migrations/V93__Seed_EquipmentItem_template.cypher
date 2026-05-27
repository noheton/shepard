// T1i — EquipmentItem built-in ShepardTemplate (DIN EN 9100 §7.1.5 calibration record)
//
// Seeds a built-in DATAOBJECT_RECIPE template with 5 mandatory calibration fields:
//   serial_number, manufacturer, model, calibration_valid_until, calibration_cert_id
//
// MERGE key: {name: 'EquipmentItem', version: 1} — name alone is NOT unique
// (copy-on-write versioning means a v2 of this template carries the same name).
// The composite {name, version} key ensures idempotency on re-run.
//
// The `source: 'V93-builtin'` property is the rollback handle. The companion
// rollback file (V93_R__) deletes only rows matching that marker so user-authored
// EquipmentItem templates are never touched.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V93__Seed_EquipmentItem_template.cypher
//   Rollback: V93_R__Rollback_Seed_EquipmentItem_template.cypher (deletes seeded row)
//   Verify:   MATCH (t:ShepardTemplate {name: 'EquipmentItem', version: 1}) RETURN t.appId, t.retired, t.body;
//
// aidocs/16 T1i
MERGE (t:ShepardTemplate {name: 'EquipmentItem', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Equipment item with calibration record fields (DIN EN 9100 §7.1.5). Five mandatory attributes: serial_number, manufacturer, model, calibration_valid_until (ISO 8601 date), calibration_cert_id.',
  t.body           = '{"dataObject":{"name":"EquipmentItem","attributes":[{"name":"serial_number","type":"STRING","required":true},{"name":"manufacturer","type":"STRING","required":true},{"name":"model","type":"STRING","required":true},{"name":"calibration_valid_until","type":"STRING","required":true,"format":"date"},{"name":"calibration_cert_id","type":"STRING","required":true}]}}',
  t.tags           = ['calibration', 'equipment', 'din-en-9100', 'quality'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V93-builtin'
ON MATCH SET
  t.description    = 'Equipment item with calibration record fields (DIN EN 9100 §7.1.5). Five mandatory attributes: serial_number, manufacturer, model, calibration_valid_until (ISO 8601 date), calibration_cert_id.'
;
