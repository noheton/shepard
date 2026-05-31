// TPL-SEED-DEMO-1 — Six casual-researcher built-in ShepardTemplates.
//
// Seeds six DATAOBJECT_RECIPE / COLLECTION_RECIPE templates so the
// "Create from template" entry point has useful options on every Shepard
// instance out of the box. Before this migration, fresh instances had
// only the V93 EquipmentItem template (calibration record) + whatever
// VIEW_RECIPE templates each showcase script seeded — neither covered
// the casual researcher path (run a test, log a sample, run a process
// step, file an NCR, mint a research collection, mint a citable
// dataset).
//
// Pattern mirrors V93 (`V93__Seed_EquipmentItem_template.cypher`):
//   - One MERGE per template, keyed on {name, version} (copy-on-write).
//   - `source: 'V99-builtin'` rollback handle on every row.
//   - `createdBy: 'system'`; `retired: false`.
//   - Bodies follow the JSON DSL surface validated by
//     TemplateBodyValidator + consumed by TemplateInstantiationRest:
//       * `dataobjects[0].name` — instantiation name fallback.
//       * `dataobjects[0].attributes` — Map<String,String> applied to
//         the new DataObject as default attribute values (empty-string
//         placeholders so an instantiated DataObject carries the
//         expected attribute keys ready for editing).
//       * `dataObject.attributes` — descriptive schema array
//         (name + type + required + format) consumed by picker UI.
//     COLLECTION_RECIPE bodies follow the same shape with `collection`
//     at the top level + `collections[0]` for instantiation hooks.
//
// Idempotency: MERGE on {name, version} — re-running is a no-op.
// Rollback: V99_R__ deletes only rows with `source = 'V99-builtin'`,
// so user-authored templates with colliding names are untouched.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V99__Demo_templates_seed.cypher
//   Rollback: V99_R__Demo_templates_seed.cypher
//   Verify:   MATCH (t:ShepardTemplate {source: 'V99-builtin'}) RETURN t.name, t.templateKind, t.retired;
//             → should return 6 rows; all retired=false.
//
// aidocs/16 TPL-SEED-DEMO-1; aidocs/34 V99 row.

// ---------------------------------------------------------------------
// 1. Generic Test Run — DATAOBJECT_RECIPE
//    Covers LUMEN-style hot-fire runs + MFFD-style process steps
//    generically. Six casual fields + free-form notes.
// ---------------------------------------------------------------------
MERGE (t1:ShepardTemplate {name: 'Generic Test Run', version: 1})
ON CREATE SET
  t1.appId         = randomUUID(),
  t1.templateKind  = 'DATAOBJECT_RECIPE',
  t1.description   = 'A generic test run — one execution of a defined test protocol on a bench or rig. Captures who ran it, where, when, with what outcome, and any free-form notes. Use as a starting point for hot-fire campaigns (LUMEN-style), wind-tunnel runs, or shop-floor process tests.',
  t1.body          = '{"dataObject":{"name":"TestRun","attributes":[{"name":"test_id","type":"STRING","required":true},{"name":"test_engineer","type":"STRING","required":true},{"name":"bench","type":"STRING","required":true},{"name":"started_at","type":"STRING","required":true,"format":"date-time"},{"name":"ended_at","type":"STRING","required":false,"format":"date-time"},{"name":"outcome","type":"STRING","required":true,"enum":["PASS","FAIL","HOLD"]},{"name":"notes","type":"STRING","required":false,"format":"markdown"}]},"dataobjects":[{"name":"TestRun","attributes":{"test_id":"","test_engineer":"","bench":"","started_at":"","ended_at":"","outcome":"","notes":""}}]}',
  t1.tags          = ['test-run', 'campaign', 'generic', 'casual'],
  t1.createdBy     = 'system',
  t1.createdAt     = timestamp(),
  t1.updatedAt     = timestamp(),
  t1.retired       = false,
  t1.source        = 'V99-builtin'
;

// ---------------------------------------------------------------------
// 2. Wet Lab Sample — DATAOBJECT_RECIPE
//    Casual lab-bench use. Sample-tracking fundamentals.
// ---------------------------------------------------------------------
MERGE (t2:ShepardTemplate {name: 'Wet Lab Sample', version: 1})
ON CREATE SET
  t2.appId         = randomUUID(),
  t2.templateKind  = 'DATAOBJECT_RECIPE',
  t2.description   = 'A wet-lab sample — physical material prepared at the bench. Captures provenance (who prepared it, what material, in what amount) and disposal accountability. Use for chemistry, biology, materials prep, and any bench-tracked sample.',
  t2.body          = '{"dataObject":{"name":"WetLabSample","attributes":[{"name":"sample_id","type":"STRING","required":true},{"name":"material","type":"STRING","required":true},{"name":"mass_mg","type":"NUMBER","required":false},{"name":"volume_ml","type":"NUMBER","required":false},{"name":"concentration","type":"STRING","required":false},{"name":"prepared_by","type":"STRING","required":true},{"name":"prepared_at","type":"STRING","required":true,"format":"date-time"},{"name":"disposal_by","type":"STRING","required":false,"format":"date"}]},"dataobjects":[{"name":"WetLabSample","attributes":{"sample_id":"","material":"","mass_mg":"","volume_ml":"","concentration":"","prepared_by":"","prepared_at":"","disposal_by":""}}]}',
  t2.tags          = ['sample', 'wet-lab', 'bench', 'casual'],
  t2.createdBy     = 'system',
  t2.createdAt     = timestamp(),
  t2.updatedAt     = timestamp(),
  t2.retired       = false,
  t2.source        = 'V99-builtin'
;

// ---------------------------------------------------------------------
// 3. Process Step (Manufacturing) — DATAOBJECT_RECIPE
//    Mirrors MFFD AFP / welding step structure. Equipment + operator +
//    set-point environment.
// ---------------------------------------------------------------------
MERGE (t3:ShepardTemplate {name: 'Process Step (Manufacturing)', version: 1})
ON CREATE SET
  t3.appId         = randomUUID(),
  t3.templateKind  = 'DATAOBJECT_RECIPE',
  t3.description   = 'A manufacturing process step — one execution of a defined production operation on a machine by an operator. Captures equipment identity, operator identity, set-point environment (temperature, pressure), and timing. Mirrors the MFFD AFP layup / ultrasonic-welding / resistance-welding / stud-welding chain.',
  t3.body          = '{"dataObject":{"name":"ProcessStep","attributes":[{"name":"step_name","type":"STRING","required":true},{"name":"equipment_id","type":"STRING","required":true},{"name":"operator_id","type":"STRING","required":true},{"name":"started_at","type":"STRING","required":true,"format":"date-time"},{"name":"ended_at","type":"STRING","required":false,"format":"date-time"},{"name":"temperature_c","type":"NUMBER","required":false},{"name":"pressure_bar","type":"NUMBER","required":false},{"name":"notes","type":"STRING","required":false,"format":"markdown"}]},"dataobjects":[{"name":"ProcessStep","attributes":{"step_name":"","equipment_id":"","operator_id":"","started_at":"","ended_at":"","temperature_c":"","pressure_bar":"","notes":""}}]}',
  t3.tags          = ['process-step', 'manufacturing', 'mffd', 'shop-floor'],
  t3.createdBy     = 'system',
  t3.createdAt     = timestamp(),
  t3.updatedAt     = timestamp(),
  t3.retired       = false,
  t3.source        = 'V99-builtin'
;

// ---------------------------------------------------------------------
// 4. Quality Inspection / NCR — DATAOBJECT_RECIPE
//    Covers EN 9100 §8.7 non-conformance flow. References the
//    inspected DataObject by appId so lineage stays queryable.
// ---------------------------------------------------------------------
MERGE (t4:ShepardTemplate {name: 'Quality Inspection / NCR', version: 1})
ON CREATE SET
  t4.appId         = randomUUID(),
  t4.templateKind  = 'DATAOBJECT_RECIPE',
  t4.description   = 'A quality inspection record — including non-conformance reports (NCRs). Captures inspector identity, inspection type (visual, NDT, dimensional, …), result (PASS/FAIL/CONCESSION/REWORK), and a free-form non-conformance detail. The `reference_do_app_id` field links to the inspected DataObject so an auditor can trace the chain. Covers EN 9100 §8.7 control of non-conforming outputs.',
  t4.body          = '{"dataObject":{"name":"QualityInspection","attributes":[{"name":"inspection_type","type":"STRING","required":true},{"name":"inspector","type":"STRING","required":true},{"name":"inspected_at","type":"STRING","required":true,"format":"date-time"},{"name":"result","type":"STRING","required":true,"enum":["PASS","FAIL","CONCESSION","REWORK"]},{"name":"non_conformance_detail","type":"STRING","required":false,"format":"markdown"},{"name":"reference_do_app_id","type":"STRING","required":false,"format":"uuid"}]},"dataobjects":[{"name":"QualityInspection","attributes":{"inspection_type":"","inspector":"","inspected_at":"","result":"","non_conformance_detail":"","reference_do_app_id":""}}]}',
  t4.tags          = ['quality', 'inspection', 'ncr', 'din-en-9100'],
  t4.createdBy     = 'system',
  t4.createdAt     = timestamp(),
  t4.updatedAt     = timestamp(),
  t4.retired       = false,
  t4.source        = 'V99-builtin'
;

// ---------------------------------------------------------------------
// 5. Research Collection — COLLECTION_RECIPE
//    Covers FAIR / research-data-manager personas. The PI + funding +
//    embargo + license metadata that DMP compliance requires.
// ---------------------------------------------------------------------
MERGE (t5:ShepardTemplate {name: 'Research Collection', version: 1})
ON CREATE SET
  t5.appId         = randomUUID(),
  t5.templateKind  = 'COLLECTION_RECIPE',
  t5.description   = 'A research collection — the top-level container for a research project. Captures Principal Investigator, host institution, funding source, embargo window, license, and a pointer to the Data Management Plan (DMP). Aligns to FAIR principles + DFG / Horizon Europe DMP requirements.',
  t5.body          = '{"collection":{"name":"ResearchCollection","attributes":[{"name":"principal_investigator","type":"STRING","required":true},{"name":"institution","type":"STRING","required":true},{"name":"funding_source","type":"STRING","required":false},{"name":"embargo_until","type":"STRING","required":false,"format":"date"},{"name":"license","type":"STRING","required":false,"enum":["CC-BY-4.0","CC-BY-SA-4.0","CC0-1.0","DLR-internal","proprietary"]},{"name":"dmp_link","type":"STRING","required":false,"format":"uri"}]},"collections":[{"name":"ResearchCollection","attributes":{"principal_investigator":"","institution":"","funding_source":"","embargo_until":"","license":"","dmp_link":""}}]}',
  t5.tags          = ['collection', 'research', 'fair', 'dmp', 'casual'],
  t5.createdBy     = 'system',
  t5.createdAt     = timestamp(),
  t5.updatedAt     = timestamp(),
  t5.retired       = false,
  t5.source        = 'V99-builtin'
;

// ---------------------------------------------------------------------
// 6. Citable Dataset — COLLECTION_RECIPE
//    For published / citable datasets. DOI-shaped metadata so an
//    operator can lift the record straight to DataCite / Helmholtz
//    Unhide.
// ---------------------------------------------------------------------
MERGE (t6:ShepardTemplate {name: 'Citable Dataset', version: 1})
ON CREATE SET
  t6.appId         = randomUUID(),
  t6.templateKind  = 'COLLECTION_RECIPE',
  t6.description   = 'A citable dataset — a Collection prepared for publication and citation. Captures DOI, creator ORCID + name, publisher, publication year, and subject keywords. Shape is DataCite-friendly so the Collection can be exported to Helmholtz Unhide / DataCite / re3data without re-keying.',
  t6.body          = '{"collection":{"name":"CitableDataset","attributes":[{"name":"doi","type":"STRING","required":false,"format":"doi"},{"name":"creator_orcid","type":"STRING","required":true,"format":"orcid"},{"name":"creator_name","type":"STRING","required":true},{"name":"publisher","type":"STRING","required":true},{"name":"publication_year","type":"STRING","required":true,"format":"year"},{"name":"subject_keywords","type":"STRING","required":false}]},"collections":[{"name":"CitableDataset","attributes":{"doi":"","creator_orcid":"","creator_name":"","publisher":"","publication_year":"","subject_keywords":""}}]}',
  t6.tags          = ['collection', 'citable', 'datacite', 'publication', 'fair'],
  t6.createdBy     = 'system',
  t6.createdAt     = timestamp(),
  t6.updatedAt     = timestamp(),
  t6.retired       = false,
  t6.source        = 'V99-builtin'
;
