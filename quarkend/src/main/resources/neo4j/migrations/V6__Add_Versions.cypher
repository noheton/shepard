MATCH(c:Collection)-[:created_by]->(u:User) WHERE NOT EXISTS((c)-[:has_version]->(:Version)) CREATE (c)-[:has_version]->(v:Version {name: 'initial version', description: 'initial version created by migration at ' + toStringOrNull(datetime()), uid: randomUUID(), createdAt: c.createdAt})<-[:created_by]-(u);

MATCH(c:Collection) WHERE NOT EXISTS((c)-[:has_version]->(:Version)) CREATE (c)-[:has_version]->(v:Version {name: 'initial version', description: 'initial version at ' + toStringOrNull(datetime()), uid: randomUUID(), createdAt: c.createdAt});

MATCH(do:DataObject)<-[:has_dataobject]-(c:Collection)-[:has_version]->(v:Version) WHERE NOT EXISTS ((do)-[:has_version]->(v)) CREATE (do)-[:has_version]->(v);

MATCH (ref:BasicReference)<-[:has_reference]-(do:DataObject)<-[:has_dataobject]-(c:Collection)-[:has_version]->(v:Version) WHERE NOT EXISTS ((ref)-[:has_version]->(v)) CREATE (ref)-[:has_version]->(v);

CREATE CONSTRAINT version_uid FOR (v:Version) REQUIRE v.uid IS UNIQUE;