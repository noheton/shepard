// QM1c rollback — remove the V102-seeded Disposition record template.
//
// Scoped by `source = 'V102-builtin'` so admins / researchers who edit the
// seeded template (and bump its source / version) are not affected.
// Same shape as V101_R / V100_R: DETACH DELETE only system-seeded rows.

MATCH (t:ShepardTemplate {name: 'Disposition record', version: 1, source: 'V102-builtin'})
DETACH DELETE t;
