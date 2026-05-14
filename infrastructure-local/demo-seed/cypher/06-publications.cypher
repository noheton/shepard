// DX5a — demo seed step 06: pre-minted LocalMinter Publication.
//
// Mints a :Publication tied to the public-showcase Collection so
// `GET /v2/.well-known/kip/<pid-suffix>` returns a real KIP record
// out of the box. The PID shape matches what KIP1h's LocalMinter
// would produce had alice clicked the Publish button in the UI:
//   shepard:local.demo.shepard:collection:01HF...:v1
//
// The KIP resolver looks up :Publication rows by pid-suffix, so
// the suffix below is exactly what an HTTP client should request
// after the demo comes up:
//
//   curl http://localhost:8080/v2/.well-known/kip/01HFDEMO0PUBLICSHOWCASEPID01v1
//
// Idempotent: MERGE on pid.

MATCH (alice:User {username: 'alice'})
MATCH (showcase:Collection {appId: 'demo-collection-public-showcase'})

MERGE (pub:Publication {pid: 'shepard:local.demo.shepard:collection:01HFDEMO0PUBLICSHOWCASEPID01v1'})
  ON CREATE SET pub.appId = randomUUID(),
                pub.pidSuffix = '01HFDEMO0PUBLICSHOWCASEPID01v1',
                pub.minterId = 'local',
                pub.targetKind = 'Collection',
                pub.targetAppId = 'demo-collection-public-showcase',
                pub.licence = 'CC-BY-4.0',
                pub.title = 'Public showcase: shepard demo',
                pub.publishedAt = timestamp(),
                pub.publisherUsername = 'alice',
                pub.versionNumber = 1;
MERGE (pub)-[:CREATED_BY]->(alice);
MERGE (showcase)-[:HAS_PUBLICATION]->(pub);
