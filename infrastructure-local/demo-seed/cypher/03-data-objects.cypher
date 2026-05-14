// DX5a — demo seed step 03: data objects across the five
// Collections (~12 total).
//
// The shepard data-object graph carries a name + description plus a
// shepardId for legacy long-id paths. The demo posture uses
// synthetic shepardIds 2001-2099 (Collections live at 1001-1005,
// DataObjects at 2001-2099, references at 3001+).
//
// Idempotent: MERGE on appId.

MATCH (alice:User {username: 'alice'})
MATCH (cyclic:Collection {appId: 'demo-collection-cyclic-fatigue-2026q1'})
MATCH (merra:Collection {appId: 'demo-collection-merra2-subset'})
MATCH (notebook:Collection {appId: 'demo-collection-lab-notebook-2026'})
MATCH (materials:Collection {appId: 'demo-collection-materials-catalogue'})
MATCH (showcase:Collection {appId: 'demo-collection-public-showcase'})

// -------- cyclic-fatigue (3 data objects) --------

MERGE (cyclic_acq:DataObject {appId: 'demo-do-cyclic-acquisition'})
  ON CREATE SET cyclic_acq.name = 'Specimen-04 acquisition run',
                cyclic_acq.description = 'Raw load-cell + extensometer CSV from the 2026-Q1-04 run.',
                cyclic_acq.shepardId = 2001,
                cyclic_acq.createdAt = timestamp(),
                cyclic_acq.updatedAt = timestamp();
MERGE (cyclic_acq)-[:CREATED_BY]->(alice);
MERGE (cyclic)-[:has_dataobject]->(cyclic_acq);

MERGE (cyclic_pre:DataObject {appId: 'demo-do-cyclic-preprocessed'})
  ON CREATE SET cyclic_pre.name = 'Specimen-04 pre-processed bundle',
                cyclic_pre.description = 'De-spiked + zero-corrected time series ready for analysis.',
                cyclic_pre.shepardId = 2002,
                cyclic_pre.createdAt = timestamp(),
                cyclic_pre.updatedAt = timestamp();
MERGE (cyclic_pre)-[:CREATED_BY]->(alice);
MERGE (cyclic)-[:has_dataobject]->(cyclic_pre);

MERGE (cyclic_res:DataObject {appId: 'demo-do-cyclic-results'})
  ON CREATE SET cyclic_res.name = 'Specimen-04 results graph',
                cyclic_res.description = 'Stress-strain plot + cycle-count histogram.',
                cyclic_res.shepardId = 2003,
                cyclic_res.createdAt = timestamp(),
                cyclic_res.updatedAt = timestamp();
MERGE (cyclic_res)-[:CREATED_BY]->(alice);
MERGE (cyclic)-[:has_dataobject]->(cyclic_res);

// -------- MERRA-2 (2 data objects) --------

MERGE (merra_subset:DataObject {appId: 'demo-do-merra2-subset'})
  ON CREATE SET merra_subset.name = 'M2T1NXSLV 2026-01 monthly mean',
                merra_subset.description = 'Single-level monthly-mean cut for January 2026.',
                merra_subset.shepardId = 2010,
                merra_subset.createdAt = timestamp(),
                merra_subset.updatedAt = timestamp();
MERGE (merra_subset)-[:CREATED_BY]->(alice);
MERGE (merra)-[:has_dataobject]->(merra_subset);

MERGE (merra_meta:DataObject {appId: 'demo-do-merra2-metadata'})
  ON CREATE SET merra_meta.name = 'MERRA-2 metadata bundle',
                merra_meta.description = 'JSON metadata: variable list, units, projection, provenance.',
                merra_meta.shepardId = 2011,
                merra_meta.createdAt = timestamp(),
                merra_meta.updatedAt = timestamp();
MERGE (merra_meta)-[:CREATED_BY]->(alice);
MERGE (merra)-[:has_dataobject]->(merra_meta);

// -------- Lab notebook 2026 (3 data objects) --------

MERGE (note_jan:DataObject {appId: 'demo-do-lab-notebook-jan'})
  ON CREATE SET note_jan.name = 'Session 2026-01-15',
                note_jan.description = 'Cyclic-fatigue setup notes + safety checklist.',
                note_jan.shepardId = 2020,
                note_jan.createdAt = timestamp(),
                note_jan.updatedAt = timestamp();
MERGE (note_jan)-[:CREATED_BY]->(alice);
MERGE (notebook)-[:has_dataobject]->(note_jan);

MERGE (note_feb:DataObject {appId: 'demo-do-lab-notebook-feb'})
  ON CREATE SET note_feb.name = 'Session 2026-02-03',
                note_feb.description = 'Acquisition channel calibration + first specimen run.',
                note_feb.shepardId = 2021,
                note_feb.createdAt = timestamp(),
                note_feb.updatedAt = timestamp();
MERGE (note_feb)-[:CREATED_BY]->(alice);
MERGE (notebook)-[:has_dataobject]->(note_feb);

MERGE (note_mar:DataObject {appId: 'demo-do-lab-notebook-mar'})
  ON CREATE SET note_mar.name = 'Session 2026-03-12',
                note_mar.description = 'Specimen-04 cyclic-fatigue full run + initial review.',
                note_mar.shepardId = 2022,
                note_mar.createdAt = timestamp(),
                note_mar.updatedAt = timestamp();
MERGE (note_mar)-[:CREATED_BY]->(alice);
MERGE (notebook)-[:has_dataobject]->(note_mar);

// -------- DLR-internal materials (2 data objects) --------

MERGE (mat_aa7075:DataObject {appId: 'demo-do-mat-aa7075'})
  ON CREATE SET mat_aa7075.name = 'AA7075-T6 specimen lot',
                mat_aa7075.description = 'Heat-treatment batch + dimensional specs for the AA7075 specimens used in the 2026-Q1 campaign.',
                mat_aa7075.shepardId = 2030,
                mat_aa7075.createdAt = timestamp(),
                mat_aa7075.updatedAt = timestamp();
MERGE (mat_aa7075)-[:CREATED_BY]->(alice);
MERGE (materials)-[:has_dataobject]->(mat_aa7075);

MERGE (mat_ti6al:DataObject {appId: 'demo-do-mat-ti6al4v'})
  ON CREATE SET mat_ti6al.name = 'Ti-6Al-4V reference coupon',
                mat_ti6al.description = 'Reference coupon for cross-material comparison.',
                mat_ti6al.shepardId = 2031,
                mat_ti6al.createdAt = timestamp(),
                mat_ti6al.updatedAt = timestamp();
MERGE (mat_ti6al)-[:CREATED_BY]->(alice);
MERGE (materials)-[:has_dataobject]->(mat_ti6al);

// -------- Public showcase (2 data objects) --------

MERGE (show_walk:DataObject {appId: 'demo-do-showcase-walkthrough'})
  ON CREATE SET show_walk.name = 'Demo walkthrough',
                show_walk.description = 'Two-paragraph "what is shepard" tour. Click around from here.',
                show_walk.shepardId = 2040,
                show_walk.createdAt = timestamp(),
                show_walk.updatedAt = timestamp();
MERGE (show_walk)-[:CREATED_BY]->(alice);
MERGE (showcase)-[:has_dataobject]->(show_walk);

MERGE (show_link:DataObject {appId: 'demo-do-showcase-external-link'})
  ON CREATE SET show_link.name = 'External-reference showcase',
                show_link.description = 'Demonstrates the DBpedia Databus reference + the LocalMinter PID resolver.',
                show_link.shepardId = 2041,
                show_link.createdAt = timestamp(),
                show_link.updatedAt = timestamp();
MERGE (show_link)-[:CREATED_BY]->(alice);
MERGE (showcase)-[:has_dataobject]->(show_link);
