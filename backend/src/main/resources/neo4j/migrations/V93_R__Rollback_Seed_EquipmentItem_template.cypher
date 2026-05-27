// V93 rollback — T1i EquipmentItem built-in ShepardTemplate
//
// Removes ONLY the system-seeded EquipmentItem template (source = 'V93-builtin').
// Any user-authored EquipmentItem templates (lacking the source marker) are
// intentionally left untouched.
//
// Run ONLY if you need to roll back V93.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V93_R__Rollback_Seed_EquipmentItem_template.cypher
//   Verify:  MATCH (t:ShepardTemplate {name: 'EquipmentItem', source: 'V93-builtin'}) RETURN count(t);
//            → should return 0 after rollback

MATCH (t:ShepardTemplate {name: 'EquipmentItem', source: 'V93-builtin'})
DETACH DELETE t;
