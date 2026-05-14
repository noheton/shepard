// DX5a — demo seed step 01: users + their permission scaffolding.
//
// Mints the four Keycloak test users into Neo4j so :Permissions
// nodes can attach to them. All four users were imported via the
// Keycloak realm export at boot; the rows below are the graph-side
// counterparts (the OIDC user-sync usually mints these on first
// login, but the demo wants them present pre-login).
//
// Idempotent: MERGE on username. Re-running this file on a
// populated graph is a no-op.

MERGE (alice:User {username: 'alice'})
  ON CREATE SET alice.appId = randomUUID(),
                alice.firstName = 'Alice',
                alice.lastName = 'Researcher',
                alice.email = 'alice@demo.shepard.local',
                alice.displayName = 'Alice Researcher';

MERGE (bob:User {username: 'bob'})
  ON CREATE SET bob.appId = randomUUID(),
                bob.firstName = 'Bob',
                bob.lastName = 'Reviewer',
                bob.email = 'bob@demo.shepard.local',
                bob.displayName = 'Bob Reviewer';

MERGE (admin:User {username: 'admin'})
  ON CREATE SET admin.appId = randomUUID(),
                admin.firstName = 'Demo',
                admin.lastName = 'Admin',
                admin.email = 'admin@demo.shepard.local',
                admin.displayName = 'Demo Admin';

MERGE (harvester:User {username: 'harvester'})
  ON CREATE SET harvester.appId = randomUUID(),
                harvester.firstName = 'Demo',
                harvester.lastName = 'Harvester (service)',
                harvester.email = 'harvester@demo.shepard.local',
                harvester.displayName = 'Demo Harvester';

// The bootstrap step (run-seeder.sh consume_bootstrap_if_present)
// already wired admin -> Role{name:'instance-admin'}. The MERGE
// below is the idempotent fallback for re-runs.
MERGE (r:Role {name: 'instance-admin'})
  ON CREATE SET r.appId = randomUUID();
MERGE (admin)-[:HAS_ROLE]->(r);
