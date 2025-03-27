// Before the semantic annotation value label and property label where both saved in one database field "name" separated by two colons.
// This violates the First Normal Form of databases that says we should only have single valued attributes.
// Furthermore querying property names and value names (i.e. labels) might be more more expensive and error prone if they violate the 1. NF.
// Therefore these attributes are being split up into two containing one single value each.
// This migration is done under the umbrella of https://gitlab.com/dlr-shepard/shepard/-/issues/481 

match(n:SemanticAnnotation)
where n.name is not null
with split(n.name, "::") as s, n
with s[0] as k, s[1] as v, n
set n.propertyName = k
set n.valueName = v
remove n.name