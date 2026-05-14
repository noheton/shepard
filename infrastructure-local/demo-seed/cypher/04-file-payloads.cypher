// DX5a — demo seed step 04: file-payload pointers.
//
// Mints :FileContainer + :ShepardFile nodes for the seed-files
// bundle mounted into the backend at /var/shepard-demo/seed-files/.
//
// NOTE: in the demo posture the file *blobs* are NOT loaded into
// GridFS; this seed only mints the graph metadata so the
// `Collection -> FileContainer -> ShepardFile` shape is visible
// in the UI. A casual user clicking "Download" gets a 404 — the
// path forward (FS1a or a Mongo-side blob seeder) is documented
// in `infrastructure-local/README-demo.md §"Caveats"`.
//
// Idempotent: MERGE on appId.

MATCH (alice:User {username: 'alice'})
MATCH (cyclic:Collection {appId: 'demo-collection-cyclic-fatigue-2026q1'})
MATCH (cyclic_acq:DataObject {appId: 'demo-do-cyclic-acquisition'})
MATCH (cyclic_res:DataObject {appId: 'demo-do-cyclic-results'})
MATCH (merra_meta:DataObject {appId: 'demo-do-merra2-metadata'})
MATCH (note_mar:DataObject {appId: 'demo-do-lab-notebook-mar'})

// Default file-container on the cyclic-fatigue Collection.
MERGE (cyclic_fc:FileContainer {appId: 'demo-fc-cyclic-default'})
  ON CREATE SET cyclic_fc.name = 'Default file container — cyclic-fatigue 2026-Q1',
                cyclic_fc.shepardId = 4001,
                cyclic_fc.createdAt = timestamp(),
                cyclic_fc.updatedAt = timestamp();
MERGE (cyclic)-[:has_default_file_container]->(cyclic_fc);
MERGE (cyclic_fc)-[:CREATED_BY]->(alice);

// CSV acquisition file.
MERGE (csv:ShepardFile {appId: 'demo-file-cyclic-acquisition-csv'})
  ON CREATE SET csv.name = 'specimen-04-acquisition.csv',
                csv.shepardId = 5001,
                csv.contentType = 'text/csv',
                csv.size = 832,
                csv.createdAt = timestamp(),
                csv.updatedAt = timestamp();
MERGE (csv)-[:file_in_container]->(cyclic_fc);
MERGE (csv)-[:CREATED_BY]->(alice);

// PNG of the stress-strain plot.
MERGE (png:ShepardFile {appId: 'demo-file-cyclic-results-png'})
  ON CREATE SET png.name = 'stress-strain-plot.png',
                png.shepardId = 5002,
                png.contentType = 'image/png',
                png.size = 1795,
                png.createdAt = timestamp(),
                png.updatedAt = timestamp();
MERGE (png)-[:file_in_container]->(cyclic_fc);
MERGE (png)-[:CREATED_BY]->(alice);

// MERRA-2 metadata JSON.
MERGE (merra_fc:FileContainer {appId: 'demo-fc-merra2'})
  ON CREATE SET merra_fc.name = 'MERRA-2 metadata container',
                merra_fc.shepardId = 4002,
                merra_fc.createdAt = timestamp(),
                merra_fc.updatedAt = timestamp();
MERGE (merra_fc)-[:CREATED_BY]->(alice);

MERGE (merra_json:ShepardFile {appId: 'demo-file-merra2-metadata-json'})
  ON CREATE SET merra_json.name = 'merra2-metadata.json',
                merra_json.shepardId = 5003,
                merra_json.contentType = 'application/json',
                merra_json.size = 488,
                merra_json.createdAt = timestamp(),
                merra_json.updatedAt = timestamp();
MERGE (merra_json)-[:file_in_container]->(merra_fc);
MERGE (merra_json)-[:CREATED_BY]->(alice);

// Lab-notebook Markdown.
MERGE (notebook_fc:FileContainer {appId: 'demo-fc-lab-notebook'})
  ON CREATE SET notebook_fc.name = 'Lab notebook 2026 container',
                notebook_fc.shepardId = 4003,
                notebook_fc.createdAt = timestamp(),
                notebook_fc.updatedAt = timestamp();
MERGE (notebook_fc)-[:CREATED_BY]->(alice);

MERGE (notebook_md:ShepardFile {appId: 'demo-file-lab-notebook-mar'})
  ON CREATE SET notebook_md.name = 'session-2026-03-12.md',
                notebook_md.shepardId = 5004,
                notebook_md.contentType = 'text/markdown',
                notebook_md.size = 1166,
                notebook_md.createdAt = timestamp(),
                notebook_md.updatedAt = timestamp();
MERGE (notebook_md)-[:file_in_container]->(notebook_fc);
MERGE (notebook_md)-[:CREATED_BY]->(alice);
