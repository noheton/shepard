// V100 rollback — TPL-SEED-MFFD-1 MFFD process-step ShepardTemplates
//
// Removes ONLY the system-seeded MFFD templates (source = 'V100-mffd').
// User-authored templates with the same names (lacking the source marker)
// are intentionally left untouched.
//
// Run ONLY if you need to roll back V100.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V100_R__Mffd_process_templates_seed.cypher
//   Verify:  MATCH (t:ShepardTemplate {source: 'V100-mffd'}) RETURN count(t);
//            → should return 0 after rollback

MATCH (t:ShepardTemplate {source: 'V100-mffd'})
DETACH DELETE t;
