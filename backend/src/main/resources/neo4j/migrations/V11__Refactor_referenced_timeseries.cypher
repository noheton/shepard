/*
* This migration is done under the umbrella of https://gitlab.com/dlr-shepard/shepard/-/issues/688.
*
* The first major migration is about making sure that the combination of a 5 tuple identifying a timeseries and
* the relation to its container is reflected in the data model.
* All the statements here will be executed as a single transaction as per https://neo4j.com/labs/neo4j-migrations/2.0/concepts/#concepts_transactions.
*/

// If a timeseries is referenced by multiple references, that lie in different containers:
// Create a new timeseries for each and set a relation from each respective reference to it.
// Reference the respective container from the timeseries and delete the now obsolete timeseries.
// The label `is_in_container` is a bit misleading since a reference points towards a container.
// However to maintain backwards compatibility and not blow up the migration too much we keep it for now.
match(tsc1:TimeseriesContainer)<-[ic1:is_in_container]-(tsr1:TimeseriesReference)-[:has_payload]->(ts:Timeseries)
  <-[:has_payload]-(tsr2:TimeseriesReference)-[ic2:is_in_container]->(tsc2:TimeseriesContainer)
  where tsc1 <> tsc2
create (ts1:Timeseries) set ts1 = ts
merge (tsr1)-[:has_payload]->(ts1)
merge (ts1)-[:is_in_container]->(tsc1)
detach delete ts;

// Now get rid of duplicate timeseries nodes created in the previous statement.
match(tsr1:TimeseriesReference)<-[:has_payload]-(ts1:Timeseries)-[:is_in_container]->(tsc:TimeseriesContainer)<-[:is_in_container]-(ts2:Timeseries)
  where ts1.device = ts2.device and ts1.field = ts2.field and ts1.location = ts2.location and ts1.measurement = ts2.measurement and ts1.symbolicName = ts2.symbolicName
  and id(ts1) < id(ts2)
merge (tsr1)-[:has_payload]->(ts2)
detach delete ts1;

// Add a relationship from a timeseries to a container if all its references all lie within one container.
match(ts:Timeseries)<-[:has_payload]-(tsr:TimeseriesReference)-[ic:is_in_container]->(tsc:TimeseriesContainer)
with *, count(ic) as tsc_count where tsc_count = 1
merge (ts)-[:is_in_container]->(tsc);
