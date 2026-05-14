// DX5a — demo seed step 05: external references.
//
// Two reference shapes are seeded:
//   - A DBpedia Databus reference pointing at a real public
//     artifact (instance-types snapshot) — exercises the
//     `reference-dbpedia-databus` plugin (REF1c).
//   - A generic BasicReference (the upstream-compatible
//     `:BasicReference` label) for the cross-Collection link
//     from `Public showcase` to `Cyclic-fatigue 2026-Q1`.
//
// Idempotent: MERGE on appId.

MATCH (alice:User {username: 'alice'})
MATCH (showcase:Collection {appId: 'demo-collection-public-showcase'})
MATCH (cyclic:Collection {appId: 'demo-collection-cyclic-fatigue-2026q1'})
MATCH (show_link:DataObject {appId: 'demo-do-showcase-external-link'})

// DBpedia Databus reference (REF1c). Real, public URI of the
// 2022-12-01 instance-types snapshot — exercises the plugin's
// resolver without needing operator credentials.
MERGE (dbpedia_ref:DbpediaDatabusReference {appId: 'demo-ref-dbpedia-databus-instance-types'})
  ON CREATE SET dbpedia_ref.name = 'DBpedia instance-types snapshot (2022-12-01)',
                dbpedia_ref.shepardId = 3001,
                dbpedia_ref.databusUri = 'https://databus.dbpedia.org/dbpedia/snapshot/instance-types/2022.12.01',
                dbpedia_ref.createdAt = timestamp(),
                dbpedia_ref.updatedAt = timestamp();
MERGE (show_link)-[:has_reference]->(dbpedia_ref);
MERGE (dbpedia_ref)-[:CREATED_BY]->(alice);

// Cross-Collection BasicReference: Showcase -> Cyclic-fatigue.
// This is the upstream-compatible reference shape (no plugin
// needed). Bob has read access via the cyclic-fatigue Permissions.
MERGE (showcase_link:BasicReference {appId: 'demo-ref-showcase-to-cyclic'})
  ON CREATE SET showcase_link.name = 'Showcase -> Cyclic-fatigue link',
                showcase_link.shepardId = 3002,
                showcase_link.createdAt = timestamp(),
                showcase_link.updatedAt = timestamp();
MERGE (show_link)-[:has_reference]->(showcase_link);
MERGE (showcase_link)-[:points_to]->(cyclic);
MERGE (showcase_link)-[:CREATED_BY]->(alice);
