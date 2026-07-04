// Rollback for V107__Template_icon_keys.cypher
//
// Removes `iconKey` from every :ShepardTemplate whose name was set
// by V107. Idempotent — re-running is a no-op once iconKey is gone.
//
// Note: this rollback is conservative — it only unsets icons on the
// shipped+spec template names V107 touched. Custom admin-set icons
// on OTHER templates (set via PATCH /v2/templates/{appId}) are left
// untouched.
//
// aidocs/16 TEMPLATE-ICONS-1

MATCH (t:ShepardTemplate)
WHERE t.name IN [
  // V100 shipped
  'MFFD AFP Layup',
  'MFFD Ultrasonic Welding',
  'MFFD Resistance Welding',
  'MFFD Stud Welding',
  'MFFD NDT Inspection',
  'MFFD Frame Welding',
  'MFFD Stringer Connection',
  'MFFD LBR Cleats Assembly',
  // V93 / V101 / V102 / V103 shipped
  'EquipmentItem',
  'Generic Test Run',
  'Wet Lab Sample',
  'Process Step (Manufacturing)',
  'Quality Inspection / NCR',
  'Research Collection',
  'Citable Dataset',
  'Cross-ply TCP temperature',
  'Disposition record',
  // aidocs/integrations/122 §4 spec names
  'MFFDStepRoot',
  'MFFDLayer',
  'MFFDPlyGroup',
  'MFFDTrack',
  'MFFDExecution',
  'MFFDBridgeWeldExecution',
  'MFFDSpotWeld',
  'MFFDNDTScan',
  'MFFDCell',
  'MFFDLayerOverview'
]
REMOVE t.iconKey;
