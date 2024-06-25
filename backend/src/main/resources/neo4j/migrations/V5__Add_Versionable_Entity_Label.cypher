MATCH (n:BasicEntity) WHERE (n:Collection OR n:DataObject OR n:BasicReference) SET n:VersionableEntity;

MATCH (n:VersionableEntity) WHERE (n.shepardId IS NULL) SET n.shepardId = id(n);

CREATE INDEX idx_VersionableEntity_shepardId FOR (ve:VersionableEntity) ON (ve.shepardId);