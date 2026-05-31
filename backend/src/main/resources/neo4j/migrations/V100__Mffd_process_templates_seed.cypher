// TPL-SEED-MFFD-1 — eight MFFD process-step ShepardTemplates (DATAOBJECT_RECIPE)
//
// Seeds one built-in :ShepardTemplate per step of the MFFD process chain
// (CLAUDE.md §"MFFD domain context", project_mffd_domain_context.md):
//
//   1. MFFD AFP Layup            — CF/LMPAEK ply consolidation by heated-TCP KR210
//   2. MFFD Ultrasonic Welding   — high-frequency vibration stringer↔skin joint
//   3. MFFD Resistance Welding   — Joule heating of the thermoplastic interlayer
//   4. MFFD Stud Welding         — surface attachment of fastening features
//   5. MFFD NDT Inspection       — ultrasonic / thermography / visual quality gate
//   6. MFFD Frame Welding        — Q1+Q2 parallel tracks merge here
//   7. MFFD Stringer Connection  — stringer↔frame joint
//   8. MFFD LBR Cleats Assembly  — LBR iiwa-driven force-torque cleat install
//
// Shape — same as V93 (T1i EquipmentItem):
//   * templateKind = 'DATAOBJECT_RECIPE'
//   * MERGE key = {name, version: 1}      (composite, copy-on-write)
//   * source = 'V100-mffd'                (rollback handle)
//   * createdBy = 'system', retired = false
//
// Body shape — superset of V93:
//   * `dataObject.attributes[]` — schema for the Create dialog (name/type/required[/format/enum])
//   * `dataObject.annotations[]` — `{predicate, value}` seed for SemanticAnnotation pre-fill.
//     Instantiator currently ignores this; TPL-INSTANTIATE-ANNOTATIONS-1 backlog
//     row tracks wiring it through.
//   * `dataobjects[0].attributes` — flat default-values map; consumed by
//     TemplateInstantiationRest.extractAttributes (V93 already relies on this).
//
// Each template seeds two cross-cutting annotations:
//   urn:shepard:domain = "aerospace-manufacturing"
//   urn:shepard:mffd:process-type = "<canonical-name>"
//   urn:shepard:mffd:step-number = "<1..8>"
// Templates that involve the thermoplastic composite also seed:
//   urn:shepard:material = "CF/LMPAEK"
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V100__Mffd_process_templates_seed.cypher
//   Rollback: V100_R__Mffd_process_templates_seed.cypher
//             (DETACH DELETE WHERE source = 'V100-mffd')
//   Verify:   MATCH (t:ShepardTemplate {source: 'V100-mffd'})
//             RETURN t.name, t.version, t.appId ORDER BY t.name;
//             → 8 rows, all retired = false
//
// aidocs/16 TPL-SEED-MFFD-1

// ───────────────────────────────────────────────────────────────────────────
// 1. MFFD AFP Layup
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD AFP Layup', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Automated Fibre Placement step — CF/LMPAEK consolidation by a heated-TCP KR210 R2700/2 robot. Captures TCP temperature, consolidation force, ply count, roller pressure, and the optional TimeseriesReference for the 3D thermal trail.',
  t.body           = '{"dataObject":{"name":"MFFD AFP Layup","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"AFP Layup"},{"name":"step-number","type":"INTEGER","required":true,"default":"1"},{"name":"equipment-id","type":"STRING","required":true,"description":"appId of an :EquipmentItem (V93) template instance"},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"tcp-temperature-c","type":"NUMBER","required":true,"description":"Heated tool-center-point temperature (°C)"},{"name":"consolidation-force-n","type":"NUMBER","required":true},{"name":"ply-count","type":"INTEGER","required":true},{"name":"roller-pressure-bar","type":"NUMBER","required":false},{"name":"tcp-trail-ref","type":"STRING","required":false,"description":"TimeseriesReference appId — 3D thermal trail (see project_mffd_seed_demo)"}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"AFP Layup"},{"predicate":"urn:shepard:mffd:step-number","value":"1"},{"predicate":"urn:shepard:material","value":"CF/LMPAEK"}]},"dataobjects":[{"attributes":{"process-type":"AFP Layup","step-number":"1"}}]}',
  t.tags           = ['mffd', 'afp', 'layup', 'cf-lmpaek', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 2. MFFD Ultrasonic Welding
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD Ultrasonic Welding', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Ultrasonic welding step — high-frequency vibration joint between stringer and skin in CF/LMPAEK thermoplastic composite. Captures sonotrode frequency, amplitude, hold time, and total weld energy.',
  t.body           = '{"dataObject":{"name":"MFFD Ultrasonic Welding","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"Ultrasonic Welding"},{"name":"step-number","type":"INTEGER","required":true,"default":"2"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"frequency-khz","type":"NUMBER","required":true},{"name":"amplitude-um","type":"NUMBER","required":true},{"name":"hold-time-ms","type":"NUMBER","required":true},{"name":"weld-energy-j","type":"NUMBER","required":true}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"Ultrasonic Welding"},{"predicate":"urn:shepard:mffd:step-number","value":"2"},{"predicate":"urn:shepard:material","value":"CF/LMPAEK"}]},"dataobjects":[{"attributes":{"process-type":"Ultrasonic Welding","step-number":"2"}}]}',
  t.tags           = ['mffd', 'welding', 'ultrasonic', 'cf-lmpaek', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 3. MFFD Resistance Welding
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD Resistance Welding', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Resistance welding step — Joule heating of the thermoplastic interlayer between two CF/LMPAEK adherends. Captures weld current, voltage, weld time, and electrode pressing force.',
  t.body           = '{"dataObject":{"name":"MFFD Resistance Welding","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"Resistance Welding"},{"name":"step-number","type":"INTEGER","required":true,"default":"3"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"current-a","type":"NUMBER","required":true},{"name":"voltage-v","type":"NUMBER","required":true},{"name":"weld-time-ms","type":"NUMBER","required":true},{"name":"electrode-force-n","type":"NUMBER","required":true}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"Resistance Welding"},{"predicate":"urn:shepard:mffd:step-number","value":"3"},{"predicate":"urn:shepard:material","value":"CF/LMPAEK"}]},"dataobjects":[{"attributes":{"process-type":"Resistance Welding","step-number":"3"}}]}',
  t.tags           = ['mffd', 'welding', 'resistance', 'cf-lmpaek', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 4. MFFD Stud Welding
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD Stud Welding', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Stud welding step — surface attachment of fastening features. Captures stud type, pull-test strength result, and time-to-flash measurement.',
  t.body           = '{"dataObject":{"name":"MFFD Stud Welding","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"Stud Welding"},{"name":"step-number","type":"INTEGER","required":true,"default":"4"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"stud-type","type":"STRING","required":true},{"name":"pull-test-result-mpa","type":"NUMBER","required":false,"description":"Pull-test peak stress (MPa)"},{"name":"time-to-flash-ms","type":"NUMBER","required":false}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"Stud Welding"},{"predicate":"urn:shepard:mffd:step-number","value":"4"}]},"dataobjects":[{"attributes":{"process-type":"Stud Welding","step-number":"4"}}]}',
  t.tags           = ['mffd', 'welding', 'stud', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 5. MFFD NDT Inspection
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD NDT Inspection', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Non-destructive testing quality gate — ultrasonic, thermography, visual, or X-ray. Result is one of PASS / FAIL / CONCESSION / REWORK; defect-list captured as JSON array. EN 9100 audit-trail anchor (rework loop visibility).',
  t.body           = '{"dataObject":{"name":"MFFD NDT Inspection","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"NDT Inspection"},{"name":"step-number","type":"INTEGER","required":true,"default":"5"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"inspection-method","type":"STRING","required":true,"enum":["ULTRASONIC","THERMOGRAPHY","VISUAL","X-RAY"]},{"name":"result","type":"STRING","required":true,"enum":["PASS","FAIL","CONCESSION","REWORK"]},{"name":"defect-list","type":"STRING","required":false,"format":"json","description":"JSON array of defects (location, severity, classification)"},{"name":"inspector-id","type":"STRING","required":true}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"NDT Inspection"},{"predicate":"urn:shepard:mffd:step-number","value":"5"}]},"dataobjects":[{"attributes":{"process-type":"NDT Inspection","step-number":"5"}}]}',
  t.tags           = ['mffd', 'ndt', 'quality', 'aerospace-manufacturing', 'din-en-9100', 'easa-part-21g'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 6. MFFD Frame Welding
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD Frame Welding', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Frame welding step — Q1 and Q2 parallel tracks merge here (mffd-showcase narrative). Same kinematics as ultrasonic welding plus frame-identity and joining-track designator.',
  t.body           = '{"dataObject":{"name":"MFFD Frame Welding","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"Frame Welding"},{"name":"step-number","type":"INTEGER","required":true,"default":"6"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"frequency-khz","type":"NUMBER","required":true},{"name":"amplitude-um","type":"NUMBER","required":true},{"name":"hold-time-ms","type":"NUMBER","required":true},{"name":"weld-energy-j","type":"NUMBER","required":true},{"name":"frame-id","type":"STRING","required":true},{"name":"joining-track","type":"STRING","required":true,"enum":["Q1","Q2","merged"]}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"Frame Welding"},{"predicate":"urn:shepard:mffd:step-number","value":"6"},{"predicate":"urn:shepard:material","value":"CF/LMPAEK"}]},"dataobjects":[{"attributes":{"process-type":"Frame Welding","step-number":"6"}}]}',
  t.tags           = ['mffd', 'welding', 'frame', 'cf-lmpaek', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 7. MFFD Stringer Connection (a.k.a. Stringerverbindung)
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD Stringer Connection', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'Stringer-to-frame joint (German: Stringerverbindung). Joint type is either a bracket or a direct weld; captures stringer identity and joint-strength test result.',
  t.body           = '{"dataObject":{"name":"MFFD Stringer Connection","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"Stringer Connection"},{"name":"step-number","type":"INTEGER","required":true,"default":"7"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"joint-type","type":"STRING","required":true,"enum":["bracket","direct-weld"]},{"name":"stringer-id","type":"STRING","required":true},{"name":"joint-strength-test-result-mpa","type":"NUMBER","required":false}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"Stringer Connection"},{"predicate":"urn:shepard:mffd:step-number","value":"7"},{"predicate":"urn:shepard:material","value":"CF/LMPAEK"}]},"dataobjects":[{"attributes":{"process-type":"Stringer Connection","step-number":"7"}}]}',
  t.tags           = ['mffd', 'stringer', 'joint', 'cf-lmpaek', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';

// ───────────────────────────────────────────────────────────────────────────
// 8. MFFD LBR Cleats Assembly
// ───────────────────────────────────────────────────────────────────────────
MERGE (t:ShepardTemplate {name: 'MFFD LBR Cleats Assembly', version: 1})
ON CREATE SET
  t.appId          = randomUUID(),
  t.templateKind   = 'DATAOBJECT_RECIPE',
  t.description    = 'LBR iiwa-driven force-torque cleat assembly step. Captures cleat count, the LBR program identifier, and the optional TimeseriesReference for the joint torque sensors.',
  t.body           = '{"dataObject":{"name":"MFFD LBR Cleats Assembly","attributes":[{"name":"process-type","type":"STRING","required":true,"default":"LBR Cleats Assembly"},{"name":"step-number","type":"INTEGER","required":true,"default":"8"},{"name":"equipment-id","type":"STRING","required":true},{"name":"operator-id","type":"STRING","required":true},{"name":"started-at","type":"STRING","required":true,"format":"date-time"},{"name":"ended-at","type":"STRING","required":false,"format":"date-time"},{"name":"notes","type":"STRING","required":false,"format":"markdown"},{"name":"parent-collection-app-id","type":"STRING","required":false},{"name":"cleat-count","type":"INTEGER","required":true},{"name":"lbr-program-id","type":"STRING","required":true},{"name":"force-torque-profile-ref","type":"STRING","required":false,"description":"TimeseriesReference appId — joint force/torque sensor channels"}],"annotations":[{"predicate":"urn:shepard:domain","value":"aerospace-manufacturing"},{"predicate":"urn:shepard:mffd:process-type","value":"LBR Cleats Assembly"},{"predicate":"urn:shepard:mffd:step-number","value":"8"}]},"dataobjects":[{"attributes":{"process-type":"LBR Cleats Assembly","step-number":"8"}}]}',
  t.tags           = ['mffd', 'assembly', 'lbr-iiwa', 'cleats', 'aerospace-manufacturing', 'din-en-9100'],
  t.createdBy      = 'system',
  t.createdAt      = timestamp(),
  t.updatedAt      = timestamp(),
  t.retired        = false,
  t.source         = 'V100-mffd';
