// This migration is a database repair fix for the following bug: https://gitlab.com/dlr-shepard/shepard/-/issues/363
// It renames all properties of Collections and Dataobjects (that use attributes) from 
// 'attributes.test' to 'attributes||test', so they can utilize the new delimiter character sequence that is '||'.

MATCH (n:DataObject|Collection)
WITH n, keys(n) AS propertyKeys
UNWIND propertyKeys AS key
WITH n, key
WHERE key STARTS WITH 'attributes.'
WITH n, key, replace(key, 'attributes.', 'attributes||') as newKey
SET n[newKey] = n[key]
REMOVE n[key]