// Add a relationship from a timeseries to a container if all its references all lie within one container.
match(ts:Timeseries)<-[:has_payload]-(tsr:TimeseriesReference)-[ic:is_in_container]->(tsc:TimeseriesContainer)
with *, count(ic) as tsc_count where tsc_count = 1
merge (ts)-[:is_in_container]->(tsc);
