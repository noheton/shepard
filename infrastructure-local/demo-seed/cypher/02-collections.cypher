// DX5a — demo seed step 02: five example Collections covering
// the typical shepard use cases.
//
// Permission shapes:
//   - "Public showcase: shepard demo"         — owner alice, all
//                                                logged-in users
//                                                readable.
//   - "Cyclic-fatigue test campaign 2026-Q1"  — owner alice, bob
//                                                read+write.
//   - "MERRA-2 reanalysis subset"             — owner alice, bob
//                                                read.
//   - "Lab notebook 2026"                     — owner alice, bob
//                                                read.
//   - "DLR-internal materials catalogue"      — owner alice ONLY
//                                                (private).
//
// Idempotent: MERGE on the deterministic (synthetic) appId so
// re-running this file on a populated graph is a no-op.

MATCH (alice:User {username: 'alice'})
MATCH (bob:User {username: 'bob'})

MERGE (cyclic:Collection {appId: 'demo-collection-cyclic-fatigue-2026q1'})
  ON CREATE SET cyclic.name = 'Cyclic-fatigue test campaign 2026-Q1',
                cyclic.description = 'Mechanical-test data from the 2026-Q1 cyclic-fatigue campaign on AA7075 specimens. Includes raw acquisition CSVs, the lab notebook, and a results bundle.',
                cyclic.shepardId = 1001,
                cyclic.createdAt = timestamp(),
                cyclic.updatedAt = timestamp();
MERGE (cyclic)-[:CREATED_BY]->(alice);
MERGE (cyclic)-[:UPDATED_BY]->(alice);

MERGE (cyclicPerm:Permissions {appId: 'demo-perm-cyclic-fatigue'})
  ON CREATE SET cyclicPerm.permissionType = 'PRIVATE';
MERGE (cyclic)-[:has_permissions]->(cyclicPerm);
MERGE (cyclicPerm)-[:owned_by]->(alice);
MERGE (cyclicPerm)-[:readable_by]->(bob);
MERGE (cyclicPerm)-[:writeable_by]->(bob);

MERGE (merra:Collection {appId: 'demo-collection-merra2-subset'})
  ON CREATE SET merra.name = 'MERRA-2 reanalysis subset',
                merra.description = 'Subset of NASA MERRA-2 reanalysis cuts for the DLR-CCM 2026 atmospheric study. References upstream DOI 10.5067/RKPHT8KC1Y1T (M2T1NXSLV) plus a derived-product CSV.',
                merra.shepardId = 1002,
                merra.createdAt = timestamp(),
                merra.updatedAt = timestamp();
MERGE (merra)-[:CREATED_BY]->(alice);
MERGE (merra)-[:UPDATED_BY]->(alice);

MERGE (merraPerm:Permissions {appId: 'demo-perm-merra2'})
  ON CREATE SET merraPerm.permissionType = 'PRIVATE';
MERGE (merra)-[:has_permissions]->(merraPerm);
MERGE (merraPerm)-[:owned_by]->(alice);
MERGE (merraPerm)-[:readable_by]->(bob);

MERGE (notebook:Collection {appId: 'demo-collection-lab-notebook-2026'})
  ON CREATE SET notebook.name = 'Lab notebook 2026',
                notebook.description = 'Daily lab notebook entries for 2026; one entry per session. Doubles as the J1 lab-journal smoke target.',
                notebook.shepardId = 1003,
                notebook.createdAt = timestamp(),
                notebook.updatedAt = timestamp();
MERGE (notebook)-[:CREATED_BY]->(alice);
MERGE (notebook)-[:UPDATED_BY]->(alice);

MERGE (notebookPerm:Permissions {appId: 'demo-perm-lab-notebook'})
  ON CREATE SET notebookPerm.permissionType = 'PRIVATE';
MERGE (notebook)-[:has_permissions]->(notebookPerm);
MERGE (notebookPerm)-[:owned_by]->(alice);
MERGE (notebookPerm)-[:readable_by]->(bob);

MERGE (materials:Collection {appId: 'demo-collection-materials-catalogue'})
  ON CREATE SET materials.name = 'DLR-internal materials catalogue',
                materials.description = 'Internal catalogue of mechanical-test specimens. Private to the materials team; the demo posture has only Alice in that team.',
                materials.shepardId = 1004,
                materials.createdAt = timestamp(),
                materials.updatedAt = timestamp();
MERGE (materials)-[:CREATED_BY]->(alice);
MERGE (materials)-[:UPDATED_BY]->(alice);

MERGE (matPerm:Permissions {appId: 'demo-perm-materials'})
  ON CREATE SET matPerm.permissionType = 'PRIVATE';
MERGE (materials)-[:has_permissions]->(matPerm);
MERGE (matPerm)-[:owned_by]->(alice);

MERGE (showcase:Collection {appId: 'demo-collection-public-showcase'})
  ON CREATE SET showcase.name = 'Public showcase: shepard demo',
                showcase.description = 'Walk-through Collection a new user can poke at without an account context. References external DBpedia Databus + a published LocalMinter PID.',
                showcase.shepardId = 1005,
                showcase.createdAt = timestamp(),
                showcase.updatedAt = timestamp();
MERGE (showcase)-[:CREATED_BY]->(alice);
MERGE (showcase)-[:UPDATED_BY]->(alice);

MERGE (showcasePerm:Permissions {appId: 'demo-perm-showcase'})
  ON CREATE SET showcasePerm.permissionType = 'PUBLIC';
MERGE (showcase)-[:has_permissions]->(showcasePerm);
MERGE (showcasePerm)-[:owned_by]->(alice);
MERGE (showcasePerm)-[:readable_by]->(bob);
